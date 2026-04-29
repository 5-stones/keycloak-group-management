package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.entity.GroupInvitationEntity
import com.weare5stones.keycloak.groupmgmt.util.TokenGenerator
import jakarta.persistence.EntityManager
import org.keycloak.connections.jpa.JpaConnectionProvider
import org.keycloak.models.KeycloakSession
import java.util.concurrent.TimeUnit

class InvitationService(private val session: KeycloakSession) {

    companion object {
        const val DEFAULT_TTL_HOURS = 72L
        const val TTL_REALM_ATTRIBUTE = "group-invitation-ttl-hours"
    }

    private val em: EntityManager
        get() = session.getProvider(JpaConnectionProvider::class.java).entityManager

    fun create(
        realmId: String,
        groupId: String,
        email: String,
        inviterUserId: String,
        roles: List<String> = emptyList(),
        ttlHours: Long? = null
    ): GroupInvitationEntity {
        val realm = session.getContext().realm
        val normalizedEmail = email.lowercase().trim()
        val normalizedRoles = roles.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        normalizedRoles.forEach { GroupRoleService.assertRoleAllowed(realm, it) }

        val existing = findByEmailAndGroup(realmId, groupId, normalizedEmail)
        if (existing != null) {
            throw InvitationAlreadyExistsException("An invitation already exists for $normalizedEmail in this group")
        }

        val group = session.groups().getGroupById(realm, groupId)
        if (group != null) {
            val searchParams = mapOf(
                org.keycloak.models.UserModel.EMAIL to normalizedEmail,
                org.keycloak.models.UserModel.EXACT to "true"
            )
            val existingUser = session.users().searchForUserStream(realm, searchParams, null, null)
                .findFirst()
                .orElse(null)
            if (existingUser != null && existingUser.isMemberOf(group)) {
                throw IllegalStateException("User $normalizedEmail is already a member of this group")
            }
        }
        val effectiveTtl = ttlHours
            ?: realm.getAttribute(TTL_REALM_ATTRIBUTE)?.toLongOrNull()
            ?: DEFAULT_TTL_HOURS

        val now = System.currentTimeMillis()
        val entity = GroupInvitationEntity().apply {
            this.realmId = realmId
            this.groupId = groupId
            this.email = normalizedEmail
            this.inviterUserId = inviterUserId
            setRolesList(normalizedRoles)
            this.token = TokenGenerator.generateToken()
            this.createdAt = now
            this.expiresAt = now + TimeUnit.HOURS.toMillis(effectiveTtl)
        }

        em.persist(entity)
        return entity
    }

    fun findById(id: String): GroupInvitationEntity? {
        return em.find(GroupInvitationEntity::class.java, id)
    }

    fun findByToken(token: String): GroupInvitationEntity? {
        return em.createQuery(
            "SELECT i FROM GroupInvitationEntity i WHERE i.token = :token",
            GroupInvitationEntity::class.java
        )
            .setParameter("token", token)
            .resultList
            .firstOrNull()
    }

    fun findByEmailAndGroup(realmId: String, groupId: String, email: String): GroupInvitationEntity? {
        return em.createQuery(
            "SELECT i FROM GroupInvitationEntity i WHERE i.realmId = :realmId AND i.groupId = :groupId AND i.email = :email",
            GroupInvitationEntity::class.java
        )
            .setParameter("realmId", realmId)
            .setParameter("groupId", groupId)
            .setParameter("email", email)
            .resultList
            .firstOrNull()
    }

    fun findByGroup(realmId: String, groupId: String): List<GroupInvitationEntity> {
        return em.createQuery(
            "SELECT i FROM GroupInvitationEntity i WHERE i.realmId = :realmId AND i.groupId = :groupId ORDER BY i.createdAt DESC",
            GroupInvitationEntity::class.java
        )
            .setParameter("realmId", realmId)
            .setParameter("groupId", groupId)
            .resultList
    }

    fun delete(id: String): Boolean {
        val entity = findById(id) ?: return false
        em.remove(entity)
        return true
    }

    fun accept(token: String, userId: String): GroupInvitationEntity? {
        val entity = findByToken(token) ?: return null

        if (entity.expiresAt < System.currentTimeMillis()) {
            em.remove(entity)
            return null
        }

        val realm = session.getContext().realm
        val group = session.groups().getGroupById(realm, entity.groupId) ?: return null
        val user = session.users().getUserById(realm, userId) ?: return null

        user.joinGroup(group)
        val invitedRoles = entity.getRolesList()
        if (invitedRoles.isNotEmpty()) {
            // Inviter already passed the grant-policy check at create time; the acceptor
            // is being assigned roles, not granting them — skip the check here.
            GroupRoleService.setRoles(
                session, realm, user, group.id, userId, invitedRoles,
                enforceGrantPolicy = false,
            )
        }
        em.remove(entity)

        return entity
    }
}

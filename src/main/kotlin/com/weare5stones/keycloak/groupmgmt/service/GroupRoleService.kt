package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.entity.GroupMemberRoleEntity
import jakarta.persistence.EntityManager
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.NotFoundException
import org.jboss.logging.Logger
import org.keycloak.connections.jpa.JpaConnectionProvider
import org.keycloak.models.GroupModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.util.JsonSerialization

object GroupRoleService {

    private val logger = Logger.getLogger(GroupRoleService::class.java)

    const val ADMIN_ROLE = "admin"
    const val MEMBER_ROLE = "member"
    const val MAX_ROLES_PER_MEMBER = 32
    const val ALLOWED_ROLES_ATTRIBUTE = "group-mgmt-allowed-roles"
    const val ROLE_PERMISSIONS_ATTRIBUTE = "group-mgmt-role-permissions"
    private val ROLE_REGEX = Regex("^[a-z0-9_-]{1,64}$")

    // ---------- Permission vocabulary ----------
    // Permissions are hardcoded primitives that gate REST endpoints. Operators
    // assign them to roles via the [ROLE_PERMISSIONS_ATTRIBUTE] realm attribute.
    // [ADMIN_ROLE] grants all permissions implicitly.

    const val PERM_GROUP_WRITE = "group:write"
    const val PERM_MEMBERS_READ = "members:read"
    const val PERM_MEMBERS_WRITE = "members:write"
    const val PERM_ROLES_WRITE = "roles:write"
    const val PERM_INVITATIONS_READ = "invitations:read"
    const val PERM_INVITATIONS_WRITE = "invitations:write"

    val ALL_PERMISSIONS: Set<String> = setOf(
        PERM_GROUP_WRITE,
        PERM_MEMBERS_READ,
        PERM_MEMBERS_WRITE,
        PERM_ROLES_WRITE,
        PERM_INVITATIONS_READ,
        PERM_INVITATIONS_WRITE,
    )

    private fun em(session: KeycloakSession): EntityManager =
        session.getProvider(JpaConnectionProvider::class.java).entityManager

    fun validateRole(role: String) {
        require(ROLE_REGEX.matches(role)) {
            "Invalid role '$role': must match ${ROLE_REGEX.pattern}"
        }
    }

    /**
     * Returns the realm's role vocabulary excluding [ADMIN_ROLE]. [MEMBER_ROLE] is always
     * present (implicit, like `admin`); the [ALLOWED_ROLES_ATTRIBUTE] only adds further roles.
     */
    fun getAllowedRoles(realm: RealmModel): Set<String> {
        val raw = realm.getAttribute(ALLOWED_ROLES_ATTRIBUTE).orEmpty()
        val configured = raw.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != ADMIN_ROLE }
            .toSet()
        return configured + MEMBER_ROLE
    }

    /**
     * Validates [role] for assignment in [realm]: format check + allowlist enforcement.
     * [ADMIN_ROLE] is always allowed regardless of the configured allowlist.
     */
    fun assertRoleAllowed(realm: RealmModel, role: String) {
        validateRole(role)
        if (role == ADMIN_ROLE) return
        val allowed = getAllowedRoles(realm)
        require(role in allowed) {
            "Role '$role' is not in this realm's allowed list (${allowed.sorted().joinToString()}). " +
                "Update the '$ALLOWED_ROLES_ATTRIBUTE' realm attribute to add it."
        }
    }

    // ---------- Reads ----------

    fun getRoles(session: KeycloakSession, realmId: String, groupId: String, userId: String): Set<String> =
        getRoles(em(session), realmId, groupId, userId)

    internal fun getRoles(em: EntityManager, realmId: String, groupId: String, userId: String): Set<String> {
        return em.createQuery(
            "SELECT r.role FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.groupId = :groupId AND r.userId = :userId",
            String::class.java
        )
            .setParameter("realmId", realmId)
            .setParameter("groupId", groupId)
            .setParameter("userId", userId)
            .resultList
            .toSet()
    }

    fun getAllMemberRoles(
        session: KeycloakSession,
        realmId: String,
        groupId: String
    ): Map<String, Set<String>> = getAllMemberRoles(em(session), realmId, groupId)

    internal fun getAllMemberRoles(
        em: EntityManager,
        realmId: String,
        groupId: String,
    ): Map<String, Set<String>> {
        @Suppress("UNCHECKED_CAST")
        val rows = em.createQuery(
            "SELECT r.userId, r.role FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.groupId = :groupId"
        )
            .setParameter("realmId", realmId)
            .setParameter("groupId", groupId)
            .resultList as List<Array<Any>>

        val result = mutableMapOf<String, MutableSet<String>>()
        for (row in rows) {
            val userId = row[0] as String
            val role = row[1] as String
            result.getOrPut(userId) { mutableSetOf() }.add(role)
        }
        return result
    }

    fun getMembersWithRole(
        session: KeycloakSession,
        realmId: String,
        groupId: String,
        role: String
    ): List<String> = getMembersWithRole(em(session), realmId, groupId, role)

    internal fun getMembersWithRole(
        em: EntityManager,
        realmId: String,
        groupId: String,
        role: String,
    ): List<String> {
        return em.createQuery(
            "SELECT r.userId FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.groupId = :groupId AND r.role = :role",
            String::class.java
        )
            .setParameter("realmId", realmId)
            .setParameter("groupId", groupId)
            .setParameter("role", role)
            .resultList
    }

    fun getRolesForUserInGroups(
        session: KeycloakSession,
        realmId: String,
        userId: String,
        groupIds: Collection<String>
    ): Map<String, Set<String>> = getRolesForUserInGroups(em(session), realmId, userId, groupIds)

    internal fun getRolesForUserInGroups(
        em: EntityManager,
        realmId: String,
        userId: String,
        groupIds: Collection<String>,
    ): Map<String, Set<String>> {
        if (groupIds.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, MutableSet<String>>()
        groupIds.chunked(500).forEach { chunk ->
            @Suppress("UNCHECKED_CAST")
            val rows = em.createQuery(
                "SELECT r.groupId, r.role FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.userId = :userId AND r.groupId IN :groupIds"
            )
                .setParameter("realmId", realmId)
                .setParameter("userId", userId)
                .setParameter("groupIds", chunk)
                .resultList as List<Array<Any>>

            for (row in rows) {
                val groupId = row[0] as String
                val role = row[1] as String
                result.getOrPut(groupId) { mutableSetOf() }.add(role)
            }
        }
        return result
    }

    // ---------- Writes ----------

    /**
     * Add a single role. Idempotent: if the role already exists, no-op.
     * No last-admin guard (adding can never remove the last admin).
     */
    fun addRole(session: KeycloakSession, realm: RealmModel, groupId: String, userId: String, role: String) {
        assertRoleAllowed(realm, role)
        val current = getRoles(session, realm.id, groupId, userId)
        if (role in current) return
        check(current.size < MAX_ROLES_PER_MEMBER) {
            "Member already has the maximum of $MAX_ROLES_PER_MEMBER roles"
        }
        val entity = GroupMemberRoleEntity().apply {
            this.realmId = realm.id
            this.groupId = groupId
            this.userId = userId
            this.role = role
        }
        em(session).persist(entity)
    }

    /**
     * Replace the full role set for a (group, user). Idempotent: no-op when unchanged.
     *
     * Guards (in order):
     *  - Validates each role against [assertRoleAllowed] (vocabulary + format).
     *  - When [enforceGrantPolicy] is true, blocks the actor from adding any role whose
     *    permissions exceed their own (privilege-escalation prevention).
     *  - Throws IllegalStateException if removing 'admin' from the lone admin (last-admin guard);
     *    realm admins bypass.
     *
     * [enforceGrantPolicy] should be `true` for direct user-driven calls (REST). It is
     * intended to be `false` only for system-driven applications such as accepting an
     * invitation, where the inviter already passed the grant-policy check at create time.
     */
    fun setRoles(
        session: KeycloakSession,
        realm: RealmModel,
        actor: UserModel,
        groupId: String,
        userId: String,
        roles: Collection<String>,
        enforceGrantPolicy: Boolean = true,
    ) {
        val normalized = roles.map { it.lowercase() }.toSet()
        check(normalized.size <= MAX_ROLES_PER_MEMBER) {
            "Cannot assign more than $MAX_ROLES_PER_MEMBER roles to a member"
        }
        normalized.forEach { assertRoleAllowed(realm, it) }

        val current = getRoles(session, realm.id, groupId, userId)
        if (current == normalized) return

        val toAdd = normalized - current
        val toRemove = current - normalized

        if (enforceGrantPolicy && toAdd.isNotEmpty()) {
            val group = session.groups().getGroupById(realm, groupId)
                ?: throw NotFoundException("Group not found")
            assertCanGrantRoles(session, realm, group, actor, toAdd)
        }

        val removingAdmin = ADMIN_ROLE in current && ADMIN_ROLE !in normalized
        if (removingAdmin) {
            enforceLastAdminGuard(session, realm, actor, groupId, userId)
        }

        val entityManager = em(session)
        if (toRemove.isNotEmpty()) {
            entityManager.createQuery(
                "DELETE FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.groupId = :groupId AND r.userId = :userId AND r.role IN :roles"
            )
                .setParameter("realmId", realm.id)
                .setParameter("groupId", groupId)
                .setParameter("userId", userId)
                .setParameter("roles", toRemove)
                .executeUpdate()
        }
        for (role in toAdd) {
            val entity = GroupMemberRoleEntity().apply {
                this.realmId = realm.id
                this.groupId = groupId
                this.userId = userId
                this.role = role
            }
            entityManager.persist(entity)
        }
    }

    /**
     * Remove all roles for a (group, user). Used when a member is removed from the group.
     * Subject to last-admin guard.
     */
    fun removeAllRoles(
        session: KeycloakSession,
        realm: RealmModel,
        actor: UserModel,
        groupId: String,
        userId: String
    ) {
        val current = getRoles(session, realm.id, groupId, userId)
        if (current.isEmpty()) return
        if (ADMIN_ROLE in current) {
            enforceLastAdminGuard(session, realm, actor, groupId, userId)
        }
        em(session).createQuery(
            "DELETE FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.groupId = :groupId AND r.userId = :userId"
        )
            .setParameter("realmId", realm.id)
            .setParameter("groupId", groupId)
            .setParameter("userId", userId)
            .executeUpdate()
    }

    private fun enforceLastAdminGuard(
        session: KeycloakSession,
        realm: RealmModel,
        actor: UserModel,
        groupId: String,
        userId: String
    ) {
        if (isRealmAdmin(session, realm, actor)) return
        val admins = getMembersWithRole(session, realm.id, groupId, ADMIN_ROLE)
        if (admins.size <= 1 && userId in admins) {
            // Inheritance: an admin on any ancestor inherits down and keeps the group
            // manageable, so removing the last direct admin is allowed in that case.
            val ancestorIds = ancestorIdsOf(session, realm.id, groupId)
            if (ancestorIds.isNotEmpty() && hasAnyAdmin(em(session), realm.id, ancestorIds)) return
            throw IllegalStateException("Group must have at least one admin")
        }
    }

    /**
     * Returns true if any group in [groupIds] has at least one user with the [ADMIN_ROLE]
     * in [realmId]. Single indexed lookup against `fs_group_member_role`.
     */
    private fun hasAnyAdmin(em: EntityManager, realmId: String, groupIds: Collection<String>): Boolean {
        if (groupIds.isEmpty()) return false
        return em.createQuery(
            "SELECT 1 FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.role = :role AND r.groupId IN :groupIds",
            Int::class.javaObjectType,
        )
            .setParameter("realmId", realmId)
            .setParameter("role", ADMIN_ROLE)
            .setParameter("groupIds", groupIds)
            .setMaxResults(1)
            .resultList
            .isNotEmpty()
    }

    // ---------- Permissions ----------

    /**
     * Parses the realm's role-permission map. Returns role-name -> permission set.
     * Returns empty map if the attribute is unset or the JSON is invalid (logged at debug).
     * Use [parseRolePermissions] for validation that throws on bad input.
     */
    fun getRolePermissions(realm: RealmModel): Map<String, Set<String>> {
        val raw = realm.getAttribute(ROLE_PERMISSIONS_ATTRIBUTE)?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        return try {
            parseRolePermissions(raw, allowedRoles = null)
        } catch (e: IllegalArgumentException) {
            logger.debugf(
                "Ignoring malformed '%s' on realm '%s': %s",
                ROLE_PERMISSIONS_ATTRIBUTE, realm.name, e.message,
            )
            emptyMap()
        }
    }

    /**
     * Parses and validates a role-permissions JSON string. Throws [IllegalArgumentException]
     * with a human-friendly message on any structural or vocabulary issue. If [allowedRoles]
     * is non-null, every key in the JSON must be present in it (admin/member always allowed).
     */
    fun parseRolePermissions(json: String, allowedRoles: Set<String>?): Map<String, Set<String>> {
        val parsed: Map<*, *> = try {
            JsonSerialization.readValue(json, Map::class.java)
        } catch (e: Exception) {
            throw IllegalArgumentException("must be a JSON object: ${e.message}")
        }
        val result = mutableMapOf<String, Set<String>>()
        for ((rawKey, rawValue) in parsed) {
            val role = (rawKey as? String)?.lowercase()?.trim()
                ?: throw IllegalArgumentException("role names must be strings")
            require(ROLE_REGEX.matches(role)) { "role '$role' is not a valid role name" }
            require(role != ADMIN_ROLE) { "'admin' is reserved and cannot have explicit permissions" }
            if (allowedRoles != null) {
                require(role in allowedRoles || role == MEMBER_ROLE) {
                    "role '$role' is not in the realm's allowed-roles list"
                }
            }
            require(rawValue is List<*>) { "permissions for '$role' must be an array of strings" }
            val perms = rawValue.map {
                val perm = (it as? String)?.trim()
                    ?: throw IllegalArgumentException("permissions for '$role' must be strings")
                require(perm in ALL_PERMISSIONS) {
                    "unknown permission '$perm' for role '$role' (valid: ${ALL_PERMISSIONS.sorted().joinToString()})"
                }
                perm
            }.toSet()
            result[role] = perms
        }
        return result
    }

    /**
     * Returns the IDs of every ancestor of [groupId] in [realmId], **excluding [groupId]
     * itself**. Empty for top-level groups. Single SQL round-trip via [GroupHierarchy].
     *
     * The "excluding self" shape exists because the EM-level [hasPermission] and
     * [evaluateGrantOnGroup] take `ancestorGroupIds` (ancestors only) and prepend the
     * leaf themselves — that contract is stable across many tests and is what callers
     * like the public [hasPermission], [assertCanGrantRoles], and [enforceLastAdminGuard]
     * need. Other callers ([getEffectivePermissions], [isGroupAdmin]) skip the EM-level
     * indirection and feed the full chain straight to [getRolesForUserInGroups]; those
     * use [GroupHierarchy.ancestorIds] (full chain) directly instead.
     */
    private fun ancestorIdsOf(session: KeycloakSession, realmId: String, groupId: String): List<String> =
        GroupHierarchy.ancestorIds(em(session), realmId, groupId).filter { it != groupId }

    /**
     * Returns the full set of permissions [user] holds on [group]. Realm admins and
     * `admin`-role holders effectively have every permission. Otherwise: the union of
     * permissions granted by each explicitly-assigned role on [group] OR any ancestor
     * (permission inheritance flows down the group tree), plus the `member` role's
     * permissions if the user is a member of [group] itself.
     */
    fun getEffectivePermissions(
        session: KeycloakSession,
        realm: RealmModel,
        group: GroupModel,
        user: UserModel,
    ): Set<String> {
        val realmAdmin = isRealmAdmin(session, realm, user)
        if (realmAdmin) return ALL_PERMISSIONS
        val chain = GroupHierarchy.ancestorIds(em(session), realm.id, group.id)
        val rolesByGroup = getRolesForUserInGroups(session, realm.id, user.id, chain)
        val groupAdmin = rolesByGroup.values.any { ADMIN_ROLE in it }
        val unionRoles = rolesByGroup.values.flatten().toSet()
        return computeEffectivePermissions(
            isRealmAdmin = false,
            isGroupAdmin = groupAdmin,
            actorAssignedRoles = unionRoles,
            isGroupMember = if (groupAdmin) false else user.isMemberOf(group),
            rolePermissionsMap = getRolePermissions(realm),
        )
    }

    /**
     * Decision returned by [evaluateGrant]. Sealed so tests can pattern-match on the
     * specific reason for a denial.
     */
    internal sealed class GrantDecision {
        object Allowed : GrantDecision()
        data class DeniedAdminRequired(val role: String) : GrantDecision()
        data class DeniedMissingPermissions(val role: String, val missing: Set<String>) : GrantDecision()
    }

    /**
     * Pure privilege-escalation evaluator. Given already-resolved actor state and the
     * realm's role-permissions map, returns whether [rolesToGrant] is allowed. This
     * function performs no I/O and is the canonical place to test the rule semantics.
     */
    internal fun evaluateGrant(
        actorIsAdmin: Boolean,
        actorPermissions: Set<String>,
        rolePermissionsMap: Map<String, Set<String>>,
        rolesToGrant: Collection<String>,
    ): GrantDecision {
        if (actorIsAdmin) return GrantDecision.Allowed
        for (role in rolesToGrant) {
            if (role == ADMIN_ROLE) return GrantDecision.DeniedAdminRequired(role)
            val perms = rolePermissionsMap[role].orEmpty()
            val missing = perms - actorPermissions
            if (missing.isNotEmpty()) return GrantDecision.DeniedMissingPermissions(role, missing)
        }
        return GrantDecision.Allowed
    }

    /**
     * Pure effective-permission resolver. Given already-resolved actor state, returns
     * the actor's effective permission set. Realm admins and group admins are treated
     * as holding [ALL_PERMISSIONS]; everyone else gets the union of their assigned
     * roles' permissions plus, if they're a group member, the `member` baseline.
     */
    internal fun computeEffectivePermissions(
        isRealmAdmin: Boolean,
        isGroupAdmin: Boolean,
        actorAssignedRoles: Set<String>,
        isGroupMember: Boolean,
        rolePermissionsMap: Map<String, Set<String>>,
    ): Set<String> {
        if (isRealmAdmin || isGroupAdmin) return ALL_PERMISSIONS
        val perms = actorAssignedRoles.flatMapTo(mutableSetOf()) { rolePermissionsMap[it] ?: emptySet() }
        if (isGroupMember) {
            perms.addAll(rolePermissionsMap[MEMBER_ROLE].orEmpty())
        }
        return perms
    }

    /**
     * Asserts that [actor] is allowed to grant each role in [rolesToGrant] on [group].
     * Realm admins and group admins bypass. Otherwise, the actor must hold every
     * permission that each role grants — preventing privilege escalation. The `admin`
     * role can only be granted by an existing admin (group or realm).
     *
     * Throws [ForbiddenException] when a role would escalate beyond the actor's perms.
     */
    fun assertCanGrantRoles(
        session: KeycloakSession,
        realm: RealmModel,
        group: GroupModel,
        actor: UserModel,
        rolesToGrant: Collection<String>,
    ) {
        val realmAdmin = isRealmAdmin(session, realm, actor)
        when (val decision = evaluateGrantOnGroup(
            em = em(session),
            realmId = realm.id,
            groupId = group.id,
            actorUserId = actor.id,
            isRealmAdmin = realmAdmin,
            isGroupMember = actor.isMemberOf(group),
            rolePermissionsMap = getRolePermissions(realm),
            rolesToGrant = rolesToGrant,
            ancestorGroupIds = if (realmAdmin) emptyList() else ancestorIdsOf(session, realm.id, group.id),
        )) {
            GrantDecision.Allowed -> return
            is GrantDecision.DeniedAdminRequired ->
                throw ForbiddenException("Only admins can grant the '${decision.role}' role")
            is GrantDecision.DeniedMissingPermissions ->
                throw ForbiddenException(
                    "Cannot grant role '${decision.role}' — it includes permissions you do not hold (${decision.missing.sorted().joinToString()})"
                )
        }
    }

    /**
     * EM-backed evaluator that resolves the actor's assigned roles from the database
     * and applies the same group-admin/realm-admin/group-member logic as the public
     * [assertCanGrantRoles]. Returns a [GrantDecision] instead of throwing, so tests
     * can assert on the precise outcome without dealing with exceptions.
     *
     * Permission inheritance: the actor's effective `admin` flag and effective
     * permission set are taken across [groupId] plus every group in
     * [ancestorGroupIds] (closest ancestor first). An `admin` on any ancestor counts
     * as admin here; other roles contribute their permissions to the union.
     */
    internal fun evaluateGrantOnGroup(
        em: EntityManager,
        realmId: String,
        groupId: String,
        actorUserId: String,
        isRealmAdmin: Boolean,
        isGroupMember: Boolean,
        rolePermissionsMap: Map<String, Set<String>>,
        rolesToGrant: Collection<String>,
        ancestorGroupIds: List<String> = emptyList(),
    ): GrantDecision {
        if (rolesToGrant.isEmpty()) return GrantDecision.Allowed
        val chain = listOf(groupId) + ancestorGroupIds
        val rolesByGroup = if (isRealmAdmin) emptyMap()
            else getRolesForUserInGroups(em, realmId, actorUserId, chain)
        val groupAdmin = rolesByGroup.values.any { ADMIN_ROLE in it }
        val unionRoles = rolesByGroup.values.flatten().toSet()
        val actorPerms = computeEffectivePermissions(
            isRealmAdmin = isRealmAdmin,
            isGroupAdmin = groupAdmin,
            actorAssignedRoles = unionRoles,
            isGroupMember = if (isRealmAdmin || groupAdmin) false else isGroupMember,
            rolePermissionsMap = rolePermissionsMap,
        )
        return evaluateGrant(isRealmAdmin || groupAdmin, actorPerms, rolePermissionsMap, rolesToGrant)
    }

    /**
     * Returns true if [user] holds [permission] on [group]. Order of checks:
     *  1. Realm admin → always true
     *  2. User has the `admin` role in the group → true
     *  3. User has any explicitly-assigned role granting [permission] → true
     *  4. The `member` role grants [permission] AND user is a member of the group → true
     *     (i.e. permissions on the `member` role are baseline perms for every group member)
     */
    fun hasPermission(
        session: KeycloakSession,
        realm: RealmModel,
        group: GroupModel,
        user: UserModel,
        permission: String,
    ): Boolean {
        val realmAdmin = isRealmAdmin(session, realm, user)
        if (realmAdmin) return true
        return hasPermission(
            em = em(session),
            realmId = realm.id,
            groupId = group.id,
            userId = user.id,
            permission = permission,
            isRealmAdmin = false,
            isGroupMember = user.isMemberOf(group),
            rolePermissionsMap = getRolePermissions(realm),
            ancestorGroupIds = ancestorIdsOf(session, realm.id, group.id),
        )
    }

    /**
     * EM-backed permission check. Resolves the user's assigned roles from the database
     * and applies the same precedence as the public [hasPermission]. Take pre-resolved
     * `isRealmAdmin`, `isGroupMember`, and `rolePermissionsMap` so tests can vary them
     * without spinning up Keycloak.
     *
     * Permission inheritance: roles held on any group in [ancestorGroupIds] (closest
     * ancestor first) flow down to descendants. The `admin` role on any ancestor
     * grants every permission on this group. Other roles contribute their mapped
     * permissions to the union. The `member`-role baseline applies only at the leaf
     * group and only when [isGroupMember] is true.
     */
    internal fun hasPermission(
        em: EntityManager,
        realmId: String,
        groupId: String,
        userId: String,
        permission: String,
        isRealmAdmin: Boolean,
        isGroupMember: Boolean,
        rolePermissionsMap: Map<String, Set<String>>,
        ancestorGroupIds: List<String> = emptyList(),
    ): Boolean {
        if (isRealmAdmin) return true
        val chain = listOf(groupId) + ancestorGroupIds
        val rolesByGroup = getRolesForUserInGroups(em, realmId, userId, chain)
        if (rolesByGroup.values.any { ADMIN_ROLE in it }) return true
        val unionRoles = rolesByGroup.values.flatten().toSet()
        if (unionRoles.any { permission in (rolePermissionsMap[it] ?: emptySet()) }) return true
        val memberPerms = rolePermissionsMap[MEMBER_ROLE].orEmpty()
        if (permission in memberPerms && isGroupMember) return true
        return false
    }

    /**
     * Resolves the group and asserts [user] has [permission] on it. Throws 404 if the
     * group does not exist, 403 if the permission is not held.
     */
    fun requirePermission(
        session: KeycloakSession,
        realm: RealmModel,
        groupId: String,
        user: UserModel,
        permission: String,
    ): GroupModel {
        val group = session.groups().getGroupById(realm, groupId)
            ?: throw NotFoundException("Group not found")
        if (!hasPermission(session, realm, group, user, permission)) {
            throw ForbiddenException("You do not have '$permission' on this group")
        }
        return group
    }

    // ---------- Auth helpers (carried over from GroupAdminService) ----------

    fun requireGroupAdmin(session: KeycloakSession, realm: RealmModel, groupId: String, user: UserModel): GroupModel {
        val group = session.groups().getGroupById(realm, groupId)
            ?: throw NotFoundException("Group not found")
        if (!isGroupAdmin(session, realm, group, user)) {
            throw ForbiddenException("You are not an admin of this group")
        }
        return group
    }

    fun isGroupAdmin(session: KeycloakSession, realm: RealmModel, group: GroupModel, user: UserModel): Boolean {
        if (isRealmAdmin(session, realm, user)) return true
        val chain = GroupHierarchy.ancestorIds(em(session), realm.id, group.id)
        val rolesByGroup = getRolesForUserInGroups(session, realm.id, user.id, chain)
        return rolesByGroup.values.any { ADMIN_ROLE in it }
    }

    /**
     * Three checks because Keycloak grants realm-admin authority via three different routes:
     * (1) the master realm's `admin` realm-role on the user;
     * (2) `manage-users` on the per-realm `realm-management` client (granted to realm admins
     *     created inside that realm);
     * (3) `manage-users` on the master realm's `${realm.name}-realm` proxy client (granted to
     *     master-realm admins acting on a specific realm). All three must be honoured to
     *     match Keycloak's own admin-console authorization model.
     */
    fun isRealmAdmin(session: KeycloakSession, realm: RealmModel, user: UserModel): Boolean {
        val adminRole = realm.getRole("admin")
        if (adminRole != null && user.hasRole(adminRole)) return true

        val realmManagementClient = realm.getClientByClientId("realm-management")
        if (realmManagementClient != null) {
            val manageUsersRole = realmManagementClient.getRole("manage-users")
            if (manageUsersRole != null && user.hasRole(manageUsersRole)) return true
        }

        val masterRealmClient = realm.getClientByClientId("${realm.name}-realm")
        if (masterRealmClient != null) {
            val manageUsersRole = masterRealmClient.getRole("manage-users")
            if (manageUsersRole != null && user.hasRole(manageUsersRole)) return true
        }

        return false
    }
}

package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.util.PagedResponse
import com.weare5stones.keycloak.groupmgmt.util.pagedResponse
import com.weare5stones.keycloak.groupmgmt.util.paginationParams
import jakarta.persistence.criteria.Expression
import org.keycloak.connections.jpa.JpaConnectionProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.models.jpa.entities.UserEntity
import org.keycloak.models.jpa.entities.UserGroupMembershipEntity

class GroupMemberService(private val session: KeycloakSession) {

    private val em get() = session.getProvider(JpaConnectionProvider::class.java).entityManager
    private val realm get() = session.context.realm

    fun findMembers(
        groupId: String,
        search: String?,
        sortBy: String,
        sortDir: String,
        page: Int,
        pageSize: Int,
        roleFilter: String? = null
    ): PagedResponse<Map<String, Any?>> {
        val params = paginationParams(page, pageSize)
        val cb = em.criteriaBuilder

        // Resolve role filter to user IDs (or short-circuit if empty).
        val roleFilteredUserIds: List<String>? = roleFilter?.let {
            val normalized = it.lowercase().trim()
            GroupRoleService.validateRole(normalized)
            GroupRoleService.getMembersWithRole(session, realm.id, groupId, normalized)
        }
        if (roleFilteredUserIds != null && roleFilteredUserIds.isEmpty()) {
            return pagedResponse(emptyList(), 0L, params)
        }

        // Count query
        val countQuery = cb.createQuery(Long::class.java)
        val countRoot = countQuery.from(UserGroupMembershipEntity::class.java)
        val countUser = countRoot.get<UserEntity>("user")
        countQuery.select(cb.count(countRoot))
        val countPredicates = mutableListOf(cb.equal(countRoot.get<String>("groupId"), groupId))
        addSearchPredicates(countPredicates, countUser, search, cb)
        if (roleFilteredUserIds != null) {
            countPredicates.add(countUser.get<String>("id").`in`(roleFilteredUserIds))
        }
        countQuery.where(*countPredicates.toTypedArray())
        val totalCount = em.createQuery(countQuery).singleResult

        // Data query
        val dataQuery = cb.createQuery(UserEntity::class.java)
        val dataRoot = dataQuery.from(UserGroupMembershipEntity::class.java)
        val dataUser = dataRoot.get<UserEntity>("user")
        dataQuery.select(dataUser)
        val dataPredicates = mutableListOf(cb.equal(dataRoot.get<String>("groupId"), groupId))
        addSearchPredicates(dataPredicates, dataUser, search, cb)
        if (roleFilteredUserIds != null) {
            dataPredicates.add(dataUser.get<String>("id").`in`(roleFilteredUserIds))
        }
        dataQuery.where(*dataPredicates.toTypedArray())

        // Sort
        val ascending = sortDir.lowercase() != "desc"
        val sortExpression: Expression<*> = when (sortBy.lowercase()) {
            "email" -> cb.lower(dataUser.get("email"))
            "name" -> cb.lower(cb.concat(cb.concat(dataUser.get("firstName"), cb.literal(" ")), dataUser.get("lastName")))
            else -> cb.lower(dataUser.get("username"))
        }
        dataQuery.orderBy(if (ascending) cb.asc(sortExpression) else cb.desc(sortExpression))

        val typedQuery = em.createQuery(dataQuery)
        typedQuery.firstResult = params.offset
        typedQuery.maxResults = params.pageSize

        val users = typedQuery.resultList
        val rolesByUser = GroupRoleService.getAllMemberRoles(session, realm.id, groupId)

        val members = users.map { entity ->
            mapOf<String, Any?>(
                "id" to entity.id,
                "username" to entity.username,
                "email" to entity.email,
                "firstName" to entity.firstName,
                "lastName" to entity.lastName,
                "roles" to (rolesByUser[entity.id]?.sorted() ?: emptyList<String>())
            )
        }

        return pagedResponse(members, totalCount, params)
    }

    private fun addSearchPredicates(
        predicates: MutableList<jakarta.persistence.criteria.Predicate>,
        user: jakarta.persistence.criteria.Path<UserEntity>,
        search: String?,
        cb: jakarta.persistence.criteria.CriteriaBuilder
    ) {
        if (!search.isNullOrBlank()) {
            val pattern = cb.literal("%${search.trim().lowercase()}%")
            predicates.add(
                cb.or(
                    cb.like(cb.lower(user.get("username")), pattern),
                    cb.like(cb.lower(user.get("email")), pattern),
                    cb.like(cb.lower(user.get("firstName")), pattern),
                    cb.like(cb.lower(user.get("lastName")), pattern)
                )
            )
        }
    }
}

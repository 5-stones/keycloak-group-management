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

    fun findMembers(
        groupId: String,
        adminIds: Set<String>,
        search: String?,
        sortBy: String,
        sortDir: String,
        page: Int,
        pageSize: Int
    ): PagedResponse<Map<String, Any?>> {
        val params = paginationParams(page, pageSize)
        val cb = em.criteriaBuilder

        // Count query
        val countQuery = cb.createQuery(Long::class.java)
        val countRoot = countQuery.from(UserGroupMembershipEntity::class.java)
        val countUser = countRoot.get<UserEntity>("user")
        countQuery.select(cb.count(countRoot))
        val countPredicates = mutableListOf(cb.equal(countRoot.get<String>("groupId"), groupId))
        addSearchPredicates(countPredicates, countUser, search, cb)
        countQuery.where(*countPredicates.toTypedArray())
        val totalCount = em.createQuery(countQuery).singleResult

        // Data query
        val dataQuery = cb.createQuery(UserEntity::class.java)
        val dataRoot = dataQuery.from(UserGroupMembershipEntity::class.java)
        val dataUser = dataRoot.get<UserEntity>("user")
        dataQuery.select(dataUser)
        val dataPredicates = mutableListOf(cb.equal(dataRoot.get<String>("groupId"), groupId))
        addSearchPredicates(dataPredicates, dataUser, search, cb)
        dataQuery.where(*dataPredicates.toTypedArray())

        // Sort
        val ascending = sortDir.lowercase() != "desc"
        val sortExpression: Expression<*> = when (sortBy.lowercase()) {
            "email" -> cb.lower(dataUser.get("email"))
            "name" -> cb.lower(cb.concat(cb.concat(dataUser.get("firstName"), cb.literal(" ")), dataUser.get("lastName")))
            "admin" -> cb.selectCase<Int>()
                .`when`(dataUser.get<String>("id").`in`(adminIds), if (ascending) 0 else 1)
                .otherwise(if (ascending) 1 else 0)
            else -> cb.lower(dataUser.get("username"))
        }
        dataQuery.orderBy(if (ascending) cb.asc(sortExpression) else cb.desc(sortExpression))

        val typedQuery = em.createQuery(dataQuery)
        typedQuery.firstResult = params.offset
        typedQuery.maxResults = params.pageSize

        val members = typedQuery.resultList.map { entity ->
            mapOf<String, Any?>(
                "id" to entity.id,
                "username" to entity.username,
                "email" to entity.email,
                "firstName" to entity.firstName,
                "lastName" to entity.lastName,
                "isGroupAdmin" to (entity.id in adminIds)
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

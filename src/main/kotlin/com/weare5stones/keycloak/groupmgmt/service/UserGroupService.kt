package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.util.PagedResponse
import com.weare5stones.keycloak.groupmgmt.util.pagedResponse
import com.weare5stones.keycloak.groupmgmt.util.paginationParams
import org.keycloak.connections.jpa.JpaConnectionProvider
import org.keycloak.models.GroupModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.jpa.entities.GroupEntity
import org.keycloak.models.jpa.entities.UserGroupMembershipEntity

class UserGroupService(private val session: KeycloakSession) {

    private val em get() = session.getProvider(JpaConnectionProvider::class.java).entityManager

    fun findGroups(
        realm: RealmModel,
        userId: String,
        isRealmAdmin: Boolean,
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
        val countRoot = countQuery.from(GroupEntity::class.java)
        countQuery.select(cb.count(countRoot))
        val countPredicates = mutableListOf(cb.equal(countRoot.get<String>("realm"), realm.id))
        if (!isRealmAdmin) {
            countPredicates.add(countRoot.get<String>("id").`in`(membershipSubquery(countQuery, userId)))
        }
        if (!search.isNullOrBlank()) {
            countPredicates.add(cb.like(cb.lower(countRoot.get("name")), cb.literal("%${search.trim().lowercase()}%")))
        }
        countQuery.where(*countPredicates.toTypedArray())
        val totalCount = em.createQuery(countQuery).singleResult

        // Data query
        val dataQuery = cb.createQuery(GroupEntity::class.java)
        val dataRoot = dataQuery.from(GroupEntity::class.java)
        dataQuery.select(dataRoot)
        val dataPredicates = mutableListOf(cb.equal(dataRoot.get<String>("realm"), realm.id))
        if (!isRealmAdmin) {
            dataPredicates.add(dataRoot.get<String>("id").`in`(membershipSubquery(dataQuery, userId)))
        }
        if (!search.isNullOrBlank()) {
            dataPredicates.add(cb.like(cb.lower(dataRoot.get("name")), cb.literal("%${search.trim().lowercase()}%")))
        }
        dataQuery.where(*dataPredicates.toTypedArray())

        // Sort
        val ascending = sortDir.lowercase() != "desc"
        val sortExpression = when (sortBy.lowercase()) {
            else -> cb.lower(dataRoot.get<String>("name"))
        }
        dataQuery.orderBy(if (ascending) cb.asc(sortExpression) else cb.desc(sortExpression))

        val typedQuery = em.createQuery(dataQuery)
        typedQuery.firstResult = params.offset
        typedQuery.maxResults = params.pageSize

        val groupEntities = typedQuery.resultList
        val rolesByGroup = GroupRoleService.getRolesForUserInGroups(
            session, realm.id, userId, groupEntities.map { it.id }
        )

        val groups = groupEntities.map { entity ->
            val groupModel = session.groups().getGroupById(realm, entity.id)
            mapOf<String, Any?>(
                "id" to entity.id,
                "name" to entity.name,
                "path" to (groupModel?.let { buildGroupPath(it) } ?: "/${entity.name}"),
                "roles" to (rolesByGroup[entity.id]?.sorted() ?: emptyList<String>())
            )
        }

        return pagedResponse(groups, totalCount, params)
    }

    private fun membershipSubquery(
        query: jakarta.persistence.criteria.AbstractQuery<*>,
        userId: String
    ): jakarta.persistence.criteria.Subquery<String> {
        val cb = em.criteriaBuilder
        val subquery = query.subquery(String::class.java)
        val memberRoot = subquery.from(UserGroupMembershipEntity::class.java)
        subquery.select(memberRoot.get("groupId"))
        subquery.where(cb.equal(memberRoot.get<Any>("user").get<String>("id"), userId))
        return subquery
    }

    private fun buildGroupPath(group: GroupModel): String {
        val parts = mutableListOf(group.name)
        var parent = group.parent
        while (parent != null) {
            parts.add(0, parent.name)
            parent = parent.parent
        }
        return "/" + parts.joinToString("/")
    }
}

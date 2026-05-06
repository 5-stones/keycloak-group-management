package com.weare5stones.keycloak.groupmgmt.service

import jakarta.persistence.EntityManager

/**
 * Group-tree traversal queries against Keycloak's `KEYCLOAK_GROUP` table, expressed
 * as HQL recursive CTEs against the `GroupEntity` JPA mapping. Hibernate compiles
 * each query to a dialect-appropriate `WITH RECURSIVE` SQL on Postgres / MariaDB.
 *
 * Both queries are realm-scoped: the CTE filters every step by `realm = :realmId`,
 * so traversal cannot cross into another realm's tree even if a group somehow held
 * a parent reference outside its realm.
 *
 * Top-level groups in Keycloak hold `parentId = " "` (a single space, the value of
 * `GroupEntity.TOP_PARENT_ID`), not NULL. The recursive walk terminates naturally
 * because no group exists with `id = " "`.
 */
internal object GroupHierarchy {

    /** Mirrors `org.keycloak.models.jpa.entities.GroupEntity.TOP_PARENT_ID`. */
    const val TOP_PARENT_ID = " "

    /** Display-friendly ancestor record returned by [ancestors]. */
    data class AncestorRecord(val id: String, val name: String)

    /**
     * Returns the IDs of the realm's top-level groups (those with no parent).
     * Single SQL round-trip. Useful as the "tree roots" entry-point query.
     */
    fun topLevelGroupIds(em: EntityManager, realmId: String): List<String> =
        em.createQuery(
            "SELECT g.id FROM GroupEntity g WHERE g.realm = :realmId AND g.parentId = :topParentId",
            String::class.java,
        )
            .setParameter("realmId", realmId)
            .setParameter("topParentId", TOP_PARENT_ID)
            .resultList

    /**
     * Returns [groupId] plus every ancestor of it, in a single SQL round-trip.
     * Order is leaf-first (group itself, then parent, then grandparent, …).
     * Returns an empty list if [groupId] does not exist in [realmId].
     *
     * Convenience wrapper over [ancestorChains] for the single-group case.
     */
    fun ancestorIds(em: EntityManager, realmId: String, groupId: String): List<String> =
        ancestorChains(em, realmId, listOf(groupId))[groupId] ?: emptyList()

    /**
     * Returns every ancestor of [groupId] as `{id, name}` records, ordered root-first.
     * Excludes [groupId] itself. Single SQL round-trip via the same recursive-CTE
     * pattern as [ancestorIds]; this variant projects names for display use cases
     * (e.g. clickable breadcrumbs) so callers don't need a follow-up name lookup.
     *
     * Empty list for top-level groups (which have no ancestors) or non-existent
     * groups.
     */
    fun ancestors(em: EntityManager, realmId: String, groupId: String): List<AncestorRecord> {
        val hql = """
            WITH chain AS (
                SELECT g.id AS id, g.parentId AS parentId, g.name AS name, 0 AS depth
                FROM GroupEntity g
                WHERE g.id = :groupId AND g.realm = :realmId
                UNION ALL
                SELECT p.id AS id, p.parentId AS parentId, p.name AS name, c.depth + 1 AS depth
                FROM GroupEntity p, chain c
                WHERE p.id = c.parentId AND p.realm = :realmId
            )
            SELECT c.id, c.name FROM chain c
            WHERE c.depth > 0
            ORDER BY c.depth DESC
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        val rows = em.createQuery(hql)
            .setParameter("groupId", groupId)
            .setParameter("realmId", realmId)
            .resultList as List<Array<Any>>
        return rows.map { AncestorRecord(it[0] as String, it[1] as String) }
    }

    /**
     * For each ID in [groupIds], returns the chain of `(rowId + every ancestor of rowId)`,
     * keyed by `rowId`. Single SQL round-trip via one recursive CTE.
     *
     * Used by listing endpoints that need to compute inheritance-aware effective roles
     * for many rows in one shot — given the chain for each row, a single batched
     * `getRolesForUserInGroups` call covers every group in any chain, and per-row
     * effective roles are derived in app code without further DB hits.
     *
     * Empty map if [groupIds] is empty.
     */
    fun ancestorChains(
        em: EntityManager,
        realmId: String,
        groupIds: Collection<String>,
    ): Map<String, List<String>> {
        if (groupIds.isEmpty()) return emptyMap()
        // Hibernate HQL CTEs only resolve column references that map to entity fields,
        // so we can't project a custom `rowId`-style label through the recursion.
        // Instead we walk up *all* rows in one CTE that projects only entity fields
        // (`id`, `parentId`), then assemble each row's chain in app code by following
        // the resulting parent map. Same SQL round-trip count, just a little more
        // post-processing in JVM.
        val hql = """
            WITH chain AS (
                SELECT g.id AS id, g.parentId AS parentId
                FROM GroupEntity g
                WHERE g.id IN :groupIds AND g.realm = :realmId
                UNION ALL
                SELECT p.id AS id, p.parentId AS parentId
                FROM GroupEntity p, chain c
                WHERE p.id = c.parentId AND p.realm = :realmId
            )
            SELECT DISTINCT c.id, c.parentId FROM chain c
        """.trimIndent()
        @Suppress("UNCHECKED_CAST")
        val rows = em.createQuery(hql)
            .setParameter("groupIds", groupIds)
            .setParameter("realmId", realmId)
            .resultList as List<Array<Any>>
        val parentMap = rows.associate { (it[0] as String) to (it[1] as String) }

        val result = mutableMapOf<String, List<String>>()
        for (rowId in groupIds) {
            if (rowId !in parentMap) continue // doesn't exist in this realm
            val chain = mutableListOf<String>()
            var current: String? = rowId
            while (current != null && current != TOP_PARENT_ID) {
                chain.add(current)
                current = parentMap[current]
            }
            result[rowId] = chain
        }
        return result
    }

    /**
     * Returns every group reachable by walking down from any group in [rootIds],
     * including the roots themselves. Single SQL round-trip. Returns an empty list
     * if [rootIds] is empty.
     */
    fun descendantIds(em: EntityManager, realmId: String, rootIds: Collection<String>): List<String> {
        if (rootIds.isEmpty()) return emptyList()
        val hql = """
            WITH chain AS (
                SELECT g.id AS id
                FROM GroupEntity g
                WHERE g.id IN :rootIds AND g.realm = :realmId
                UNION ALL
                SELECT child.id AS id
                FROM GroupEntity child, chain c
                WHERE child.parentId = c.id AND child.realm = :realmId
            )
            SELECT c.id FROM chain c
        """.trimIndent()
        return em.createQuery(hql, String::class.java)
            .setParameter("rootIds", rootIds)
            .setParameter("realmId", realmId)
            .resultList
    }
}

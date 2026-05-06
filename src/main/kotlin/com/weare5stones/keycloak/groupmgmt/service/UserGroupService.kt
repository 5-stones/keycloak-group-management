package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.util.PagedResponse
import com.weare5stones.keycloak.groupmgmt.util.PaginationParams
import com.weare5stones.keycloak.groupmgmt.util.fullPath
import com.weare5stones.keycloak.groupmgmt.util.pagedResponse
import com.weare5stones.keycloak.groupmgmt.util.paginationParams
import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import org.keycloak.connections.jpa.JpaConnectionProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.jpa.entities.GroupEntity

class UserGroupService(private val session: KeycloakSession) {

    private val em get() = session.getProvider(JpaConnectionProvider::class.java).entityManager

    /**
     * Lists groups visible to [userId]. Visibility is shaped by [scope] and [parentId]:
     *
     * - **No `parentId`, `scope=inherited`**: descendant closure of (direct memberships ∪
     *   role holdings). Realm admins see every group in the realm.
     * - **No `parentId`, `scope=direct`**: only the user's *entry points* — direct memberships ∪
     *   role holdings. Realm admins see top-level realm groups (their natural entry points).
     * - **`parentId` set**: direct children of `parentId`. Visibility is always inheritance-aware
     *   here (`scope` is ignored), since drilling into a group implies access to its subtree.
     *   For non-admins, children outside the user's descendant closure are hidden — so passing
     *   a `parentId` they have no access to returns an empty list.
     */
    fun findGroups(
        realm: RealmModel,
        userId: String,
        isRealmAdmin: Boolean,
        scope: String,
        parentId: String?,
        role: String?,
        search: String?,
        sortBy: String,
        sortDir: String,
        page: Int,
        pageSize: Int
    ): PagedResponse<Map<String, Any?>> {
        val params = paginationParams(page, pageSize)

        // Pre-resolve user's direct memberships + role-holdings once so we can reuse them
        // for both visibility (asRoots) and per-row enrichment (`isDirectMember`).
        // Realm admins skip this — they don't need it for visibility, and per-row member
        // status falls back to a small page-scoped query.
        val assignments = if (isRealmAdmin) null else directAssignments(realm, userId)

        val effectiveVisibleIds = computeVisibleIds(realm, userId, isRealmAdmin, scope, parentId, role, assignments)
        if (effectiveVisibleIds != null && effectiveVisibleIds.isEmpty()) {
            return pagedResponse(emptyList(), 0L, params)
        }

        val totalCount = countMatchingGroups(realm, effectiveVisibleIds, parentId, search)

        val groupEntities = fetchPage(realm, effectiveVisibleIds, parentId, search, sortBy, sortDir, params)
        val groupIdList = groupEntities.map { it.id }

        // Per-row inheritance-aware enrichment:
        //   - chains[rowId]   = rowId + every ancestor (single recursive CTE)
        //   - rolesByGroup    = user's direct roles on every chain node (one IN query)
        //   - pageMemberships = which page rows the user is a direct member of
        //                       (reused from `assignments` for non-admins; tiny scoped
        //                        query for realm admins)
        //   - parentsWithChildren = which page rows have any subgroups, for the UI's
        //                           drill-in affordance
        val chains = GroupHierarchy.ancestorChains(em, realm.id, groupIdList)
        val rolesByGroup = GroupRoleService.getRolesForUserInGroups(
            session, realm.id, userId, chains.values.flatten().toSet()
        )
        val pageMemberships = computePageMemberships(userId, groupIdList, assignments)
        val parentsWithChildren = computeParentsWithChildren(realm, groupIdList)

        val groups = groupEntities.map { entity ->
            enrichRow(realm, entity, chains, rolesByGroup, parentsWithChildren, pageMemberships, isRealmAdmin)
        }

        return pagedResponse(groups, totalCount, params)
    }

    /**
     * Resolves the set of group IDs that should be visible for this request, applying
     * scope (`direct` vs `inherited`), `parentId` drill-down, role filter, and the
     * realm-admin bypass. Returns `null` when no narrowing applies (realm admin in
     * inherited mode); empty list when filters compose to "nothing visible".
     */
    private fun computeVisibleIds(
        realm: RealmModel,
        userId: String,
        isRealmAdmin: Boolean,
        scope: String,
        parentId: String?,
        role: String?,
        assignments: DirectAssignments?,
    ): List<String>? {
        val visibleIds: List<String>? = when {
            // Realm admin, no parentId, scope=direct: top-level realm groups as entry points.
            isRealmAdmin && parentId == null && scope == SCOPE_DIRECT -> GroupHierarchy.topLevelGroupIds(em, realm.id)
            // Realm admin in any other case: no visibility filter — they own the realm.
            isRealmAdmin -> null
            // Non-admin, no parentId, scope=direct: explicit assignments only, no expansion.
            parentId == null && scope == SCOPE_DIRECT -> assignments!!.asRoots.toList()
            // Non-admin, scope=inherited (with or without parentId), or any non-admin parentId
            // navigation: full descendant closure provides visibility.
            else -> {
                val roots = assignments!!.asRoots
                if (roots.isEmpty()) emptyList() else GroupHierarchy.descendantIds(em, realm.id, roots)
            }
        }

        // Role filter narrows the visible set to groups where the user effectively holds
        // the role. `null` means no narrowing — either no filter requested, or realm-admin
        // synthesizes `admin` everywhere so role=admin doesn't restrict.
        val roleFilterIds: List<String>? = if (role.isNullOrBlank()) null
            else computeRoleFilter(em, realm.id, userId, isRealmAdmin, scope, parentId, role)

        // Combine visibility + role filter. `null` on either side means "no constraint".
        return when {
            visibleIds == null && roleFilterIds == null -> null
            visibleIds == null -> roleFilterIds
            roleFilterIds == null -> visibleIds
            else -> visibleIds.toSet().intersect(roleFilterIds.toSet()).toList()
        }
    }

    private fun countMatchingGroups(
        realm: RealmModel,
        visibleIds: List<String>?,
        parentId: String?,
        search: String?,
    ): Long {
        val cb = em.criteriaBuilder
        val q = cb.createQuery(Long::class.java)
        val root = q.from(GroupEntity::class.java)
        q.select(cb.count(root))
        q.where(*buildPredicates(cb, root, realm, visibleIds, parentId, search).toTypedArray())
        return em.createQuery(q).singleResult
    }

    private fun fetchPage(
        realm: RealmModel,
        visibleIds: List<String>?,
        parentId: String?,
        search: String?,
        sortBy: String,
        sortDir: String,
        params: PaginationParams,
    ): List<GroupEntity> {
        val cb = em.criteriaBuilder
        val q = cb.createQuery(GroupEntity::class.java)
        val root = q.from(GroupEntity::class.java)
        q.select(root)
        q.where(*buildPredicates(cb, root, realm, visibleIds, parentId, search).toTypedArray())

        val ascending = sortDir.lowercase() != "desc"
        val sortExpression = when (sortBy.lowercase()) {
            else -> cb.lower(root.get<String>("name"))
        }
        q.orderBy(if (ascending) cb.asc(sortExpression) else cb.desc(sortExpression))

        val typed = em.createQuery(q)
        typed.firstResult = params.offset
        typed.maxResults = params.pageSize
        return typed.resultList
    }

    private fun buildPredicates(
        cb: CriteriaBuilder,
        root: Root<GroupEntity>,
        realm: RealmModel,
        visibleIds: List<String>?,
        parentId: String?,
        search: String?,
    ): List<Predicate> {
        val predicates = mutableListOf(cb.equal(root.get<String>("realm"), realm.id))
        if (visibleIds != null) {
            predicates.add(root.get<String>("id").`in`(visibleIds))
        }
        if (parentId != null) {
            predicates.add(cb.equal(root.get<String>("parentId"), parentId))
        }
        if (!search.isNullOrBlank()) {
            predicates.add(cb.like(cb.lower(root.get("name")), cb.literal("%${search.trim().lowercase()}%")))
        }
        return predicates
    }

    private fun computePageMemberships(
        userId: String,
        groupIdList: List<String>,
        assignments: DirectAssignments?,
    ): Set<String> = when {
        groupIdList.isEmpty() -> emptySet()
        assignments != null -> assignments.memberships.intersect(groupIdList.toSet())
        else -> em.createQuery(
            "SELECT m.groupId FROM UserGroupMembershipEntity m " +
                "WHERE m.user.id = :userId AND m.groupId IN :groupIds",
            String::class.java,
        )
            .setParameter("userId", userId)
            .setParameter("groupIds", groupIdList)
            .resultList
            .toSet()
    }

    private fun computeParentsWithChildren(realm: RealmModel, groupIdList: List<String>): Set<String> =
        if (groupIdList.isEmpty()) emptySet() else
            em.createQuery(
                "SELECT DISTINCT child.parentId FROM GroupEntity child " +
                    "WHERE child.realm = :realmId AND child.parentId IN :groupIds",
                String::class.java,
            )
                .setParameter("realmId", realm.id)
                .setParameter("groupIds", groupIdList)
                .resultList
                .toSet()

    /**
     * Builds the response shape for a single listing row, including inheritance-aware
     * role attribution (`direct` / `inherited` / `realm-admin` source labels) and the
     * `isDirectMember` / `hasChildren` flags the SPA uses for affordance gating.
     */
    private fun enrichRow(
        realm: RealmModel,
        entity: GroupEntity,
        chains: Map<String, List<String>>,
        rolesByGroup: Map<String, Set<String>>,
        parentsWithChildren: Set<String>,
        pageMemberships: Set<String>,
        isRealmAdmin: Boolean,
    ): Map<String, Any?> {
        val groupModel = session.groups().getGroupById(realm, entity.id)
        val chain = chains[entity.id] ?: listOf(entity.id)
        val directRoles: Set<String> = rolesByGroup[entity.id] ?: emptySet()
        // Inherited = roles from any chain node *other* than the row itself, minus
        // any role already direct (direct-wins for the source label).
        val inheritedRoles: Set<String> = chain
            .asSequence()
            .filter { it != entity.id }
            .flatMap { (rolesByGroup[it] ?: emptySet()).asSequence() }
            .toSet() - directRoles
        val storedRoles = (
            directRoles.map { mapOf("name" to it, "source" to "direct") } +
                inheritedRoles.map { mapOf("name" to it, "source" to "inherited") }
            ).sortedBy { it["name"] }
        // Realm admins effectively hold `admin` on every group via Keycloak-level
        // grants (no `fs_group_member_role` row backs this). Surface that as a
        // synthetic role with `source = "realm-admin"` so consumers see a complete
        // role picture without needing to replicate the synthesis. Skipped if the
        // user already has a stored `admin` role here — the more specific source
        // (direct or inherited) wins.
        val rolesWithSource = if (isRealmAdmin && storedRoles.none { it["name"] == GroupRoleService.ADMIN_ROLE }) {
            listOf(mapOf("name" to GroupRoleService.ADMIN_ROLE, "source" to "realm-admin")) + storedRoles
        } else {
            storedRoles
        }
        return mapOf(
            "id" to entity.id,
            "name" to entity.name,
            "path" to (groupModel?.fullPath() ?: "/${entity.name}"),
            "parentId" to entity.parentId.takeIf { it != GroupHierarchy.TOP_PARENT_ID },
            "hasChildren" to (entity.id in parentsWithChildren),
            "isDirectMember" to (entity.id in pageMemberships),
            "roles" to rolesWithSource,
        )
    }

    private data class DirectAssignments(
        val memberships: Set<String>,
        val roleHoldings: Set<String>,
    ) {
        /** Group IDs the user has been *explicitly* added to: memberships ∪ role holdings. */
        val asRoots: Set<String> get() = memberships + roleHoldings
    }

    private fun directAssignments(realm: RealmModel, userId: String): DirectAssignments {
        val memberships = em.createQuery(
            "SELECT m.groupId FROM UserGroupMembershipEntity m WHERE m.user.id = :userId",
            String::class.java,
        ).setParameter("userId", userId).resultList.toSet()

        val roleHoldings = em.createQuery(
            "SELECT DISTINCT r.groupId FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.userId = :userId",
            String::class.java,
        )
            .setParameter("realmId", realm.id)
            .setParameter("userId", userId)
            .resultList
            .toSet()

        return DirectAssignments(memberships, roleHoldings)
    }

    companion object {
        const val SCOPE_DIRECT = "direct"
        const val SCOPE_INHERITED = "inherited"

        /**
         * Group IDs where the user effectively holds [role]. Caller must only invoke this
         * when a non-blank `role` was actually provided — the call site guards on this so
         * the no-filter path doesn't pay any DB cost.
         *
         * Returns `null` when the filter wouldn't narrow (realm-admin + `role=admin`,
         * since realm admins are synthetic-admin on every group via the `realm-admin`
         * source). Otherwise returns the matching group IDs:
         * - Direct holdings (`fs_group_member_role` rows) under `scope=direct`.
         * - Descendant closure of those direct holdings under `scope=inherited` or any
         *   `parentId` navigation, so inherited holdings count.
         *
         * Pure data-access — only needs an `EntityManager`, so it's testable without a
         * `KeycloakSession`.
         */
        internal fun computeRoleFilter(
            em: EntityManager,
            realmId: String,
            userId: String,
            isRealmAdmin: Boolean,
            scope: String,
            parentId: String?,
            role: String,
        ): List<String>? {
            val roleName = role.lowercase()
            if (isRealmAdmin && roleName == GroupRoleService.ADMIN_ROLE) return null

            val directHoldings = em.createQuery(
                "SELECT r.groupId FROM GroupMemberRoleEntity r WHERE r.realmId = :realmId AND r.userId = :userId AND r.role = :role",
                String::class.java,
            )
                .setParameter("realmId", realmId)
                .setParameter("userId", userId)
                .setParameter("role", roleName)
                .resultList

            if (directHoldings.isEmpty()) return emptyList()

            val expandToInheritance = parentId != null || scope != SCOPE_DIRECT
            return if (expandToInheritance) {
                GroupHierarchy.descendantIds(em, realmId, directHoldings.toSet())
            } else {
                directHoldings
            }
        }
    }
}

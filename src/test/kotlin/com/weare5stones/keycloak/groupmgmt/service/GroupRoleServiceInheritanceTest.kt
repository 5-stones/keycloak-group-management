package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.entity.GroupMemberRoleEntity
import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService.GrantDecision
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Permission-inheritance tests: a user holding a role on an ancestor group inherits
 * that role's permissions on every descendant group. Authorization decisions on a
 * child group must consider the actor's roles in the chain of ancestor groups.
 *
 * These tests pass the ancestor chain via [GroupRoleService.hasPermission]'s and
 * [GroupRoleService.evaluateGrantOnGroup]'s `ancestorGroupIds` parameter (closest
 * ancestor first). At the public API layer the chain is built by walking
 * [org.keycloak.models.GroupModel.parent].
 *
 * Hierarchy used by the tests:
 *
 *     grandparent-group
 *       └── parent-group
 *             └── child-group
 *                   └── grandchild-group
 *
 * Cousin (no ancestor relationship): cousin-group.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupRoleServiceInheritanceTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager

    private val rolePerms = mapOf(
        "manager" to setOf(
            "group:write", "members:read", "members:write", "roles:write",
            "invitations:read", "invitations:write",
        ),
        "viewer" to setOf("members:read", "invitations:read"),
        "auditor" to setOf("members:read"),
    )

    private val ALL_PERMS = listOf(
        "group:write",
        "members:read", "members:write",
        "roles:write",
        "invitations:read", "invitations:write",
    )

    // Hierarchy expressed as ancestor chains (closest ancestor first):
    //   parent      → [grandparent]
    //   child       → [parent, grandparent]
    //   grandchild  → [child, parent, grandparent]
    //   cousin      → []  (no relation to the chain above)
    private val parentAncestors = listOf("grandparent-group")
    private val childAncestors = listOf("parent-group", "grandparent-group")
    private val grandchildAncestors = listOf("child-group", "parent-group", "grandparent-group")

    @BeforeAll
    fun setUp() {
        emf = Persistence.createEntityManagerFactory("group-mgmt-test")
        em = emf.createEntityManager()
    }

    @AfterAll
    fun tearDown() {
        em.close()
        emf.close()
    }

    @AfterEach
    fun cleanData() {
        em.transaction.begin()
        em.createQuery("DELETE FROM GroupMemberRoleEntity").executeUpdate()
        em.transaction.commit()
        em.clear()
    }

    private fun persist(realmId: String, groupId: String, userId: String, role: String) {
        em.transaction.begin()
        em.persist(GroupMemberRoleEntity().apply {
            this.realmId = realmId
            this.groupId = groupId
            this.userId = userId
            this.role = role
        })
        em.transaction.commit()
    }

    private fun canDo(
        groupId: String,
        userId: String,
        permission: String,
        ancestorGroupIds: List<String> = emptyList(),
        realmId: String = "realm-1",
        isRealmAdmin: Boolean = false,
        isGroupMember: Boolean = false,
        rolePermsMap: Map<String, Set<String>> = rolePerms,
    ): Boolean = GroupRoleService.hasPermission(
        em, realmId, groupId, userId, permission,
        isRealmAdmin = isRealmAdmin,
        isGroupMember = isGroupMember,
        rolePermissionsMap = rolePermsMap,
        ancestorGroupIds = ancestorGroupIds,
    )

    private fun grantAttempt(
        groupId: String,
        actorUserId: String,
        rolesToGrant: Collection<String>,
        ancestorGroupIds: List<String> = emptyList(),
        realmId: String = "realm-1",
        isRealmAdmin: Boolean = false,
        isGroupMember: Boolean = false,
        rolePermsMap: Map<String, Set<String>> = rolePerms,
    ): GrantDecision = GroupRoleService.evaluateGrantOnGroup(
        em, realmId, groupId, actorUserId,
        isRealmAdmin = isRealmAdmin,
        isGroupMember = isGroupMember,
        rolePermissionsMap = rolePermsMap,
        rolesToGrant = rolesToGrant,
        ancestorGroupIds = ancestorGroupIds,
    )

    // --------------------------------------------------------------------------
    // Scenario 1: Admin on parent inherits to a single direct child.
    // --------------------------------------------------------------------------
    @Nested
    inner class AdminOnParentInheritsToChild {
        @Test
        fun `admin in parent has every permission in direct child`() {
            persist("realm-1", "parent-group", "user-1", "admin")

            for (perm in ALL_PERMS) {
                assertTrue(
                    canDo("child-group", "user-1", perm, ancestorGroupIds = listOf("parent-group")),
                    "Expected admin-on-parent to grant '$perm' on child-group, but it was denied",
                )
            }
        }

        @Test
        fun `admin in parent can grant any role on child including admin`() {
            persist("realm-1", "parent-group", "user-1", "admin")

            for (role in listOf("admin", "manager", "viewer", "auditor")) {
                val decision = grantAttempt(
                    "child-group", "user-1", listOf(role),
                    ancestorGroupIds = listOf("parent-group"),
                )
                assertEquals(
                    GrantDecision.Allowed, decision,
                    "Expected admin-on-parent to be allowed to grant '$role' on child-group, got $decision",
                )
            }
        }

        @Test
        fun `admin-on-parent inheritance does not require child membership`() {
            // An inherited admin from a parent should be able to act on a child even
            // before being added as an explicit member of the child.
            persist("realm-1", "parent-group", "user-1", "admin")

            assertTrue(
                canDo(
                    "child-group", "user-1", "members:write",
                    ancestorGroupIds = listOf("parent-group"),
                    isGroupMember = false,
                ),
            )
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 2: Admin inheritance reaches transitive descendants.
    // --------------------------------------------------------------------------
    @Nested
    inner class AdminOnGrandparentInheritsToDescendants {
        @Test
        fun `admin in grandparent has every permission in grandchild (two levels down)`() {
            persist("realm-1", "grandparent-group", "user-1", "admin")

            for (perm in ALL_PERMS) {
                assertTrue(
                    canDo("grandchild-group", "user-1", perm, ancestorGroupIds = grandchildAncestors),
                    "Expected admin-on-grandparent to grant '$perm' on grandchild-group, but it was denied",
                )
            }
        }

        @Test
        fun `admin in grandparent can grant admin on grandchild`() {
            persist("realm-1", "grandparent-group", "user-1", "admin")

            val decision = grantAttempt(
                "grandchild-group", "user-1", listOf("admin"),
                ancestorGroupIds = grandchildAncestors,
            )
            assertEquals(GrantDecision.Allowed, decision)
        }

        @Test
        fun `admin in grandparent has every permission at every intermediate level`() {
            persist("realm-1", "grandparent-group", "user-1", "admin")

            // parent (one level down)
            assertTrue(canDo("parent-group", "user-1", "roles:write", ancestorGroupIds = parentAncestors))
            // child (two levels down)
            assertTrue(canDo("child-group", "user-1", "roles:write", ancestorGroupIds = childAncestors))
            // grandchild (three levels down)
            assertTrue(canDo("grandchild-group", "user-1", "roles:write", ancestorGroupIds = grandchildAncestors))
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 3: Non-admin role permissions also inherit.
    //   A user with `manager` on parent-group should have manager-level perms on
    //   child-group, but not the implicit admin powers (e.g. cannot grant admin).
    // --------------------------------------------------------------------------
    @Nested
    inner class NonAdminRoleInheritsToDescendants {
        @Test
        fun `manager in parent has manager permissions in child`() {
            persist("realm-1", "parent-group", "user-1", "manager")

            // Manager-granted perms inherit:
            assertTrue(canDo("child-group", "user-1", "members:write", ancestorGroupIds = listOf("parent-group")))
            assertTrue(canDo("child-group", "user-1", "roles:write", ancestorGroupIds = listOf("parent-group")))
            assertTrue(canDo("child-group", "user-1", "invitations:write", ancestorGroupIds = listOf("parent-group")))
        }

        @Test
        fun `viewer in grandparent has read-only permissions in grandchild`() {
            persist("realm-1", "grandparent-group", "user-1", "viewer")

            assertTrue(canDo("grandchild-group", "user-1", "members:read", ancestorGroupIds = grandchildAncestors))
            assertTrue(canDo("grandchild-group", "user-1", "invitations:read", ancestorGroupIds = grandchildAncestors))
            // Viewer is read-only — write perms should NOT be inherited.
            assertFalse(canDo("grandchild-group", "user-1", "members:write", ancestorGroupIds = grandchildAncestors))
            assertFalse(canDo("grandchild-group", "user-1", "roles:write", ancestorGroupIds = grandchildAncestors))
        }

        @Test
        fun `manager-on-parent cannot grant admin on child (manager lacks admin)`() {
            persist("realm-1", "parent-group", "user-1", "manager")

            val decision = grantAttempt(
                "child-group", "user-1", listOf("admin"),
                ancestorGroupIds = listOf("parent-group"),
            )
            // Manager's perms inherit, but `admin` is special — only an admin (anywhere
            // in the chain) can grant admin.
            assertTrue(
                decision is GrantDecision.DeniedAdminRequired,
                "Expected DeniedAdminRequired, got $decision",
            )
        }

        @Test
        fun `manager-on-parent can grant viewer on child (within their inherited perms)`() {
            persist("realm-1", "parent-group", "user-1", "manager")

            val decision = grantAttempt(
                "child-group", "user-1", listOf("viewer"),
                ancestorGroupIds = listOf("parent-group"),
            )
            assertEquals(GrantDecision.Allowed, decision)
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 4: Role unions across the ancestor chain.
    //   A user with `viewer` on grandparent and `auditor` on parent should have
    //   the union of both when acting on child.
    // --------------------------------------------------------------------------
    @Nested
    inner class UnionOfInheritedRoles {
        @Test
        fun `permissions union across multiple ancestor levels`() {
            persist("realm-1", "grandparent-group", "user-1", "viewer")
            persist("realm-1", "parent-group", "user-1", "auditor")

            // viewer + auditor both grant members:read
            assertTrue(canDo("child-group", "user-1", "members:read", ancestorGroupIds = childAncestors))
            // viewer grants invitations:read; auditor doesn't, but the union should include it
            assertTrue(canDo("child-group", "user-1", "invitations:read", ancestorGroupIds = childAncestors))
            // Neither role grants write perms — those should still be denied
            assertFalse(canDo("child-group", "user-1", "members:write", ancestorGroupIds = childAncestors))
        }

        @Test
        fun `admin anywhere in chain trumps lesser roles`() {
            persist("realm-1", "grandparent-group", "user-1", "admin")
            persist("realm-1", "parent-group", "user-1", "viewer")

            // Admin on grandparent should grant every permission on child, regardless
            // of the lesser viewer role on parent.
            for (perm in ALL_PERMS) {
                assertTrue(
                    canDo("child-group", "user-1", perm, ancestorGroupIds = childAncestors),
                    "Expected admin-on-grandparent to grant '$perm' on child despite viewer on parent",
                )
            }
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 5: Inheritance flows DOWN only — never up or sideways.
    //   These guardrails ensure inheritance doesn't accidentally over-grant.
    // --------------------------------------------------------------------------
    @Nested
    inner class InheritanceDoesNotFlowUpOrSideways {
        @Test
        fun `admin in child does NOT confer perms on parent`() {
            persist("realm-1", "child-group", "user-1", "admin")

            // Querying perms on the parent — child's ancestors are [parent, grandparent],
            // but parent's ancestors are just [grandparent]. Admin in child must not leak up.
            for (perm in ALL_PERMS) {
                assertFalse(
                    canDo("parent-group", "user-1", perm, ancestorGroupIds = parentAncestors),
                    "Expected admin-on-child to NOT grant '$perm' on parent (inheritance flows down only)",
                )
            }
        }

        @Test
        fun `admin in parent does NOT confer perms on cousin (sibling subtree)`() {
            persist("realm-1", "parent-group", "user-1", "admin")

            // cousin-group is unrelated to parent-group — its ancestor chain is empty
            // (or some other root). Admin in parent must not leak sideways.
            for (perm in ALL_PERMS) {
                assertFalse(
                    canDo("cousin-group", "user-1", perm, ancestorGroupIds = emptyList()),
                    "Expected admin-on-parent to NOT grant '$perm' on cousin-group (no ancestor relation)",
                )
            }
        }

        @Test
        fun `admin in sibling does NOT confer perms via shared parent`() {
            // user-1 is admin of sibling-a-group (not in our main hierarchy).
            // Acting on sibling-b-group, which shares parent-group as an ancestor —
            // but user-1 has no role in parent-group itself, so no inheritance.
            persist("realm-1", "sibling-a-group", "user-1", "admin")

            for (perm in ALL_PERMS) {
                assertFalse(
                    canDo("sibling-b-group", "user-1", perm, ancestorGroupIds = listOf("parent-group")),
                    "Expected admin-on-sibling-a to NOT grant '$perm' on sibling-b via shared parent",
                )
            }
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 6: Realm isolation still applies under inheritance.
    //   A role assignment in realm-1 must never leak into realm-2, even via the
    //   ancestor chain.
    // --------------------------------------------------------------------------
    @Nested
    inner class RealmIsolationUnderInheritance {
        @Test
        fun `realm-1 admin in parent does not inherit to realm-2 child of same name`() {
            persist("realm-1", "parent-group", "user-1", "admin")

            for (perm in ALL_PERMS) {
                assertFalse(
                    canDo(
                        "child-group", "user-1", perm,
                        realmId = "realm-2",
                        ancestorGroupIds = listOf("parent-group"),
                    ),
                    "Expected admin-on-parent in realm-1 to NOT grant '$perm' on child-group in realm-2",
                )
            }
        }
    }
}

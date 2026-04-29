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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * End-to-end permission resolution tests that exercise [GroupRoleService.hasPermission]
 * and [GroupRoleService.evaluateGrantOnGroup] against an H2-backed database.
 *
 * The scenarios specifically focus on cross-group isolation: a user holding a role in
 * one (realm, group) must not gain those permissions in another (realm, group), unless
 * the realm is configured to grant them via the `member` baseline AND the user is in
 * fact a member of the second group.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupRoleServiceCrossGroupTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager

    private val rolePerms = mapOf(
        "manager" to setOf(
            "group:write", "members:read", "members:write", "roles:write",
            "invitations:read", "invitations:write",
        ),
        "viewer" to setOf("members:read", "invitations:read"),
        "auditor" to setOf("members:read"),
        "inviter" to setOf("invitations:read", "invitations:write"),
    )

    private val ALL_PERMS = listOf(
        "group:write",
        "members:read", "members:write",
        "roles:write",
        "invitations:read", "invitations:write",
    )

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

    /**
     * Convenience: run hasPermission against group [groupId] using the given parameters.
     * Defaults assume non-realm-admin and non-member, with no member-baseline configured.
     */
    private fun canDo(
        groupId: String,
        userId: String,
        permission: String,
        realmId: String = "realm-1",
        isRealmAdmin: Boolean = false,
        isGroupMember: Boolean = false,
        rolePermsMap: Map<String, Set<String>> = rolePerms,
    ): Boolean = GroupRoleService.hasPermission(
        em, realmId, groupId, userId, permission,
        isRealmAdmin = isRealmAdmin,
        isGroupMember = isGroupMember,
        rolePermissionsMap = rolePermsMap,
    )

    private fun grantAttempt(
        groupId: String,
        actorUserId: String,
        rolesToGrant: Collection<String>,
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
    )

    // --------------------------------------------------------------------------
    // Scenario 1: Admin in group A, NOT in group B at all.
    //   Expectation: zero access on group B for every permission.
    // --------------------------------------------------------------------------
    @Nested
    inner class AdminInANotInB {
        @Test
        fun `admin in group-a cannot do anything in group-b (not a member)`() {
            persist("realm-1", "group-a", "user-1", "admin")

            for (perm in ALL_PERMS) {
                assertFalse(
                    canDo("group-b", "user-1", perm, isGroupMember = false),
                    "Expected denied for $perm on group-b but it was allowed",
                )
            }
        }

        @Test
        fun `admin in group-a cannot grant any role in group-b`() {
            persist("realm-1", "group-a", "user-1", "admin")

            for (role in listOf("admin", "manager", "viewer")) {
                val decision = grantAttempt(
                    "group-b", "user-1", listOf(role),
                    isGroupMember = false,
                )
                assertTrue(
                    decision !is GrantDecision.Allowed,
                    "Expected $role grant on group-b to be denied for admin-in-A, got $decision",
                )
            }
        }

        @Test
        fun `admin in group-a cannot escalate to admin via group-b`() {
            persist("realm-1", "group-a", "user-1", "admin")

            val decision = grantAttempt(
                "group-b", "user-1", listOf("admin"),
                isGroupMember = false,
            )
            assertIs<GrantDecision.DeniedAdminRequired>(decision)
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 2: Admin in group A, plain member of group B with NO assigned roles.
    //   Expectation: only baseline `member` perms in group B (or nothing if not configured).
    // --------------------------------------------------------------------------
    @Nested
    inner class AdminInAMemberOfBNoRoles {
        @Test
        fun `admin in A, member of B with no member-baseline -- still no perms in B`() {
            persist("realm-1", "group-a", "user-1", "admin")

            for (perm in ALL_PERMS) {
                assertFalse(
                    canDo("group-b", "user-1", perm, isGroupMember = true),
                    "Expected denied for $perm on group-b (no member baseline) but it was allowed",
                )
            }
        }

        @Test
        fun `admin in A, member of B with members-read baseline -- only members-read`() {
            persist("realm-1", "group-a", "user-1", "admin")
            val withBaseline = rolePerms + ("member" to setOf("members:read"))

            assertTrue(canDo("group-b", "user-1", "members:read", isGroupMember = true, rolePermsMap = withBaseline))
            assertFalse(canDo("group-b", "user-1", "members:write", isGroupMember = true, rolePermsMap = withBaseline))
            assertFalse(canDo("group-b", "user-1", "roles:write", isGroupMember = true, rolePermsMap = withBaseline))
            assertFalse(canDo("group-b", "user-1", "group:write", isGroupMember = true, rolePermsMap = withBaseline))
        }

        @Test
        fun `admin in A is treated as non-admin when granting roles in B`() {
            persist("realm-1", "group-a", "user-1", "admin")

            // Even with the manager role available in the vocab, user-1 has no perms in B,
            // so attempting to grant manager (which has lots of perms) should be denied.
            val decision = grantAttempt(
                "group-b", "user-1", listOf("manager"),
                isGroupMember = true,
            )
            val denied = assertIs<GrantDecision.DeniedMissingPermissions>(decision)
            assertEquals(rolePerms["manager"], denied.missing)
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 3: Admin in group A, viewer in group B.
    //   Expectation: full access in A, only viewer's perms in B.
    // --------------------------------------------------------------------------
    @Nested
    inner class AdminInAViewerInB {
        @Test
        fun `admin in A has full access in A`() {
            persist("realm-1", "group-a", "user-1", "admin")
            persist("realm-1", "group-b", "user-1", "viewer")

            for (perm in ALL_PERMS) {
                assertTrue(
                    canDo("group-a", "user-1", perm, isGroupMember = true),
                    "Expected $perm allowed on group-a (admin) but denied",
                )
            }
        }

        @Test
        fun `viewer in B can only read in B, not write`() {
            persist("realm-1", "group-a", "user-1", "admin")
            persist("realm-1", "group-b", "user-1", "viewer")

            assertTrue(canDo("group-b", "user-1", "members:read", isGroupMember = true))
            assertTrue(canDo("group-b", "user-1", "invitations:read", isGroupMember = true))
            assertFalse(canDo("group-b", "user-1", "members:write", isGroupMember = true))
            assertFalse(canDo("group-b", "user-1", "roles:write", isGroupMember = true))
            assertFalse(canDo("group-b", "user-1", "invitations:write", isGroupMember = true))
            assertFalse(canDo("group-b", "user-1", "group:write", isGroupMember = true))
        }

        @Test
        fun `viewer in B cannot grant manager (escalation across groups blocked)`() {
            persist("realm-1", "group-a", "user-1", "admin")
            persist("realm-1", "group-b", "user-1", "viewer")

            val decision = grantAttempt(
                "group-b", "user-1", listOf("manager"),
                isGroupMember = true,
            )
            assertIs<GrantDecision.DeniedMissingPermissions>(decision)
        }

        @Test
        fun `viewer in B cannot grant admin in B`() {
            persist("realm-1", "group-a", "user-1", "admin")
            persist("realm-1", "group-b", "user-1", "viewer")

            val decision = grantAttempt(
                "group-b", "user-1", listOf("admin"),
                isGroupMember = true,
            )
            assertIs<GrantDecision.DeniedAdminRequired>(decision)
        }

        @Test
        fun `viewer in B can grant a role they hold (auditor has subset of viewer)`() {
            persist("realm-1", "group-a", "user-1", "admin")
            persist("realm-1", "group-b", "user-1", "viewer")

            // auditor has only members:read, which viewer has.
            val decision = grantAttempt(
                "group-b", "user-1", listOf("auditor"),
                isGroupMember = true,
            )
            assertEquals(GrantDecision.Allowed, decision)
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 4: Manager in A, no roles in B (but is a member of B).
    //   Expectation: full manager access in A, only baseline in B.
    // --------------------------------------------------------------------------
    @Nested
    inner class ManagerInANoRolesInB {
        @Test
        fun `manager in A can write members in A`() {
            persist("realm-1", "group-a", "user-1", "manager")

            assertTrue(canDo("group-a", "user-1", "members:write", isGroupMember = true))
            assertTrue(canDo("group-a", "user-1", "roles:write", isGroupMember = true))
            assertTrue(canDo("group-a", "user-1", "invitations:write", isGroupMember = true))
        }

        @Test
        fun `manager in A cannot write members in B (different group)`() {
            persist("realm-1", "group-a", "user-1", "manager")

            assertFalse(canDo("group-b", "user-1", "members:write", isGroupMember = true))
            assertFalse(canDo("group-b", "user-1", "roles:write", isGroupMember = true))
            assertFalse(canDo("group-b", "user-1", "invitations:write", isGroupMember = true))
        }

        @Test
        fun `manager in A cannot grant viewer in B`() {
            persist("realm-1", "group-a", "user-1", "manager")

            val decision = grantAttempt(
                "group-b", "user-1", listOf("viewer"),
                isGroupMember = true,
            )
            // user-1 has empty perms in B (no roles, no member-baseline)
            assertIs<GrantDecision.DeniedMissingPermissions>(decision)
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 5: Realm admin bypass.
    //   Expectation: full access in any group, including ones they don't belong to.
    // --------------------------------------------------------------------------
    @Nested
    inner class RealmAdminEverywhere {
        @Test
        fun `realm admin has every permission on every group with no DB rows`() {
            // No persisted role rows — realm admin should bypass entirely.
            for (perm in ALL_PERMS) {
                assertTrue(
                    canDo("any-group", "realm-admin-user", perm, isRealmAdmin = true),
                    "Expected $perm allowed for realm admin on any-group",
                )
            }
        }

        @Test
        fun `realm admin can grant admin role even with no row in the group`() {
            val decision = grantAttempt(
                "group-b", "realm-admin-user", listOf("admin"),
                isRealmAdmin = true,
            )
            assertEquals(GrantDecision.Allowed, decision)
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 6: Realm-level isolation.
    //   Same group_id and user_id can mean different things in different realms.
    // --------------------------------------------------------------------------
    @Nested
    inner class RealmIsolation {
        @Test
        fun `admin in realm-1 group-shared has zero access in realm-2 group-shared`() {
            persist("realm-1", "group-shared", "user-1", "admin")

            for (perm in ALL_PERMS) {
                assertFalse(
                    canDo("group-shared", "user-1", perm, realmId = "realm-2", isGroupMember = true),
                    "Expected $perm denied for cross-realm access on group-shared, but allowed",
                )
            }
        }

        @Test
        fun `realm-1 admin cannot grant roles in realm-2 same group_id`() {
            persist("realm-1", "group-shared", "user-1", "admin")

            val decision = grantAttempt(
                "group-shared", "user-1", listOf("manager"),
                realmId = "realm-2", isGroupMember = true,
            )
            assertIs<GrantDecision.DeniedMissingPermissions>(decision)
        }
    }

    // --------------------------------------------------------------------------
    // Scenario 7: Multi-role user across multiple groups.
    //   Sanity-check that perms compose correctly per-group.
    // --------------------------------------------------------------------------
    @Nested
    inner class MultiRoleAcrossGroups {
        @Test
        fun `user with manager+viewer in same group gets union of perms`() {
            persist("realm-1", "group-a", "user-1", "manager")
            persist("realm-1", "group-a", "user-1", "viewer")

            // viewer is a subset of manager, so the union is just manager.
            assertTrue(canDo("group-a", "user-1", "members:write", isGroupMember = true))
            assertTrue(canDo("group-a", "user-1", "members:read", isGroupMember = true))
        }

        @Test
        fun `user with auditor in A and inviter in B has different perms in each`() {
            persist("realm-1", "group-a", "user-1", "auditor")
            persist("realm-1", "group-b", "user-1", "inviter")

            // group-a (auditor): only members:read
            assertTrue(canDo("group-a", "user-1", "members:read", isGroupMember = true))
            assertFalse(canDo("group-a", "user-1", "invitations:read", isGroupMember = true))
            assertFalse(canDo("group-a", "user-1", "invitations:write", isGroupMember = true))

            // group-b (inviter): invitations:read + invitations:write
            assertFalse(canDo("group-b", "user-1", "members:read", isGroupMember = true))
            assertTrue(canDo("group-b", "user-1", "invitations:read", isGroupMember = true))
            assertTrue(canDo("group-b", "user-1", "invitations:write", isGroupMember = true))
        }

        @Test
        fun `user with auditor in A cannot grant inviter in B`() {
            persist("realm-1", "group-a", "user-1", "auditor")
            // user-1 has no roles in B
            val decision = grantAttempt(
                "group-b", "user-1", listOf("inviter"),
                isGroupMember = true,
            )
            assertIs<GrantDecision.DeniedMissingPermissions>(decision)
        }
    }
}

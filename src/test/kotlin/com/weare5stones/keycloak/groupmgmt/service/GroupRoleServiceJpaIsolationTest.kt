package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.entity.GroupMemberRoleEntity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.Persistence
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that the data-access layer in [GroupRoleService] correctly scopes queries
 * by `(realm_id, group_id, user_id)` so that role assignments in one (realm, group)
 * never leak into queries against another. These exercise actual JPQL against an
 * in-memory H2 database — the unit suite alone can't catch a missing predicate.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupRoleServiceJpaIsolationTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager

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

    // ---------- getRoles ----------

    @Test
    fun `getRoles returns only the queried group's roles for the user`() {
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-1", "group-b", "user-1", "viewer")

        assertEquals(setOf("manager"), GroupRoleService.getRoles(em, "realm-1", "group-a", "user-1"))
        assertEquals(setOf("viewer"), GroupRoleService.getRoles(em, "realm-1", "group-b", "user-1"))
    }

    @Test
    fun `getRoles returns empty when user has no roles in the queried group`() {
        persist("realm-1", "group-a", "user-1", "manager")
        assertTrue(GroupRoleService.getRoles(em, "realm-1", "group-b", "user-1").isEmpty())
    }

    @Test
    fun `getRoles is realm-scoped - same group_id in different realms is isolated`() {
        // groupId is treated as an opaque string here; verify our WHERE clause filters by realmId.
        persist("realm-1", "shared-group", "user-1", "manager")
        persist("realm-2", "shared-group", "user-1", "viewer")

        assertEquals(setOf("manager"), GroupRoleService.getRoles(em, "realm-1", "shared-group", "user-1"))
        assertEquals(setOf("viewer"), GroupRoleService.getRoles(em, "realm-2", "shared-group", "user-1"))
    }

    @Test
    fun `getRoles is user-scoped - other users in the same group don't leak`() {
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-1", "group-a", "user-2", "viewer")

        assertEquals(setOf("manager"), GroupRoleService.getRoles(em, "realm-1", "group-a", "user-1"))
        assertEquals(setOf("viewer"), GroupRoleService.getRoles(em, "realm-1", "group-a", "user-2"))
    }

    // ---------- getMembersWithRole ----------

    @Test
    fun `getMembersWithRole does not leak users from other groups`() {
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-1", "group-b", "user-2", "manager") // different group, same role
        persist("realm-1", "group-a", "user-3", "viewer")  // same group, different role

        val managersInA = GroupRoleService.getMembersWithRole(em, "realm-1", "group-a", "manager")
        assertEquals(listOf("user-1"), managersInA, "user-2 should not appear (different group); user-3 should not (different role)")
    }

    @Test
    fun `getMembersWithRole is realm-scoped`() {
        persist("realm-1", "shared-group", "user-1", "manager")
        persist("realm-2", "shared-group", "user-2", "manager")

        assertEquals(listOf("user-1"), GroupRoleService.getMembersWithRole(em, "realm-1", "shared-group", "manager"))
        assertEquals(listOf("user-2"), GroupRoleService.getMembersWithRole(em, "realm-2", "shared-group", "manager"))
    }

    @Test
    fun `getMembersWithRole returns empty for an unassigned role`() {
        persist("realm-1", "group-a", "user-1", "manager")
        assertTrue(GroupRoleService.getMembersWithRole(em, "realm-1", "group-a", "viewer").isEmpty())
    }

    // ---------- getAllMemberRoles ----------

    @Test
    fun `getAllMemberRoles returns one entry per user with all their roles in the group`() {
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-1", "group-a", "user-1", "billing")
        persist("realm-1", "group-a", "user-2", "viewer")
        persist("realm-1", "group-b", "user-1", "admin") // different group: must not appear

        val all = GroupRoleService.getAllMemberRoles(em, "realm-1", "group-a")
        assertEquals(2, all.size)
        assertEquals(setOf("manager", "billing"), all["user-1"])
        assertEquals(setOf("viewer"), all["user-2"])
    }

    @Test
    fun `getAllMemberRoles is realm-scoped`() {
        persist("realm-1", "shared-group", "user-1", "manager")
        persist("realm-2", "shared-group", "user-1", "viewer")

        assertEquals(setOf("manager"), GroupRoleService.getAllMemberRoles(em, "realm-1", "shared-group")["user-1"])
        assertEquals(setOf("viewer"), GroupRoleService.getAllMemberRoles(em, "realm-2", "shared-group")["user-1"])
    }

    // ---------- getRolesForUserInGroups (JWT mapper batch) ----------

    @Test
    fun `getRolesForUserInGroups filters by groupIds in the IN-clause`() {
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-1", "group-b", "user-1", "viewer")
        persist("realm-1", "group-c", "user-1", "auditor") // not in the requested set

        val result = GroupRoleService.getRolesForUserInGroups(
            em, "realm-1", "user-1", listOf("group-a", "group-b"),
        )
        assertEquals(2, result.size, "Expected exactly 2 entries, got: $result")
        assertEquals(setOf("manager"), result["group-a"])
        assertEquals(setOf("viewer"), result["group-b"])
    }

    @Test
    fun `getRolesForUserInGroups is user-scoped within the requested groups`() {
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-1", "group-a", "user-2", "viewer") // different user, same group

        val result = GroupRoleService.getRolesForUserInGroups(
            em, "realm-1", "user-1", listOf("group-a"),
        )
        assertEquals(setOf("manager"), result["group-a"])
        assertEquals(1, result.size)
    }

    @Test
    fun `getRolesForUserInGroups is realm-scoped`() {
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-2", "group-a", "user-1", "viewer")

        val r1 = GroupRoleService.getRolesForUserInGroups(em, "realm-1", "user-1", listOf("group-a"))
        val r2 = GroupRoleService.getRolesForUserInGroups(em, "realm-2", "user-1", listOf("group-a"))
        assertEquals(setOf("manager"), r1["group-a"])
        assertEquals(setOf("viewer"), r2["group-a"])
    }

    @Test
    fun `getRolesForUserInGroups returns empty map when input is empty`() {
        persist("realm-1", "group-a", "user-1", "manager")
        val result = GroupRoleService.getRolesForUserInGroups(em, "realm-1", "user-1", emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRolesForUserInGroups omits groups the user has no roles in`() {
        persist("realm-1", "group-a", "user-1", "manager")
        // group-b has no rows for user-1
        val result = GroupRoleService.getRolesForUserInGroups(
            em, "realm-1", "user-1", listOf("group-a", "group-b"),
        )
        assertEquals(setOf("manager"), result["group-a"])
        assertTrue(result["group-b"].isNullOrEmpty(), "Expected no entry for group-b, got: ${result["group-b"]}")
    }

    // ---------- Cross-cutting: realistic scenario ----------

    @Test
    fun `realistic - manager in group A, plain member in group B - has zero roles in B`() {
        // user-1 is a manager in group-a but has no role rows in group-b
        // (still a Keycloak group-member of B presumably, but that's tracked outside this entity).
        persist("realm-1", "group-a", "user-1", "manager")
        persist("realm-1", "group-b", "user-2", "manager") // someone else

        assertEquals(setOf("manager"), GroupRoleService.getRoles(em, "realm-1", "group-a", "user-1"))
        assertTrue(GroupRoleService.getRoles(em, "realm-1", "group-b", "user-1").isEmpty())

        // Confirm user-1 doesn't appear in group-b's manager list
        assertEquals(listOf("user-2"), GroupRoleService.getMembersWithRole(em, "realm-1", "group-b", "manager"))
    }
}

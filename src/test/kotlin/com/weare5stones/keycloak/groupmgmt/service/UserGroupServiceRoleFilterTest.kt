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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [UserGroupService.computeRoleFilter] — the role-narrowing logic
 * behind `GET /me/groups?role=...`. Tests run against H2 with the same schema
 * stubs as the rest of the suite (`KeycloakGroupTestEntity` standing in for
 * Keycloak's `GroupEntity`, our own `GroupMemberRoleEntity`).
 *
 * Hierarchy used in the tests (per realm "realm-1"):
 *
 *     acme       (top-level)
 *       └── eng
 *             └── web
 *
 *     other      (top-level, separate subtree)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserGroupServiceRoleFilterTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager

    private val TOP = " "

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
        em.createQuery("DELETE FROM GroupEntity").executeUpdate()
        em.transaction.commit()
        em.clear()
    }

    private fun makeGroup(id: String, parentId: String, realm: String, name: String = id) {
        em.transaction.begin()
        em.persist(KeycloakGroupTestEntity().apply {
            this.id = id
            this.parentId = parentId
            this.realm = realm
            this.name = name
        })
        em.transaction.commit()
    }

    private fun persistRole(realmId: String, groupId: String, userId: String, role: String) {
        em.transaction.begin()
        em.persist(GroupMemberRoleEntity().apply {
            this.realmId = realmId
            this.groupId = groupId
            this.userId = userId
            this.role = role
        })
        em.transaction.commit()
    }

    private fun seedTree() {
        makeGroup("acme", TOP, "realm-1")
        makeGroup("eng", "acme", "realm-1")
        makeGroup("web", "eng", "realm-1")
        makeGroup("other", TOP, "realm-1")
    }

    // --------------------------------------------------------------------------
    // Realm-admin behavior
    // --------------------------------------------------------------------------

    @Test
    fun `realm admin + role=admin returns null (no narrowing)`() {
        seedTree()
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = true, scope = "direct", parentId = null, role = "admin",
        )
        assertNull(result, "Realm admins are synthetic-admin everywhere; filter should not narrow")
    }

    @Test
    fun `realm admin + non-admin role narrows to direct holdings only`() {
        seedTree()
        // Realm admin happens to ALSO have an explicit manager assignment
        persistRole("realm-1", "eng", "realm-admin-user", "manager")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "realm-admin-user",
            isRealmAdmin = true, scope = "direct", parentId = null, role = "manager",
        )
        // Realm admin's "manager" status is treated like any other user — only their
        // explicit holdings count, not the realm-everywhere semantic.
        assertEquals(listOf("eng"), result)
    }

    // --------------------------------------------------------------------------
    // No holdings → empty filter set
    // --------------------------------------------------------------------------

    @Test
    fun `user with no holdings of the role returns empty list`() {
        seedTree()
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false, scope = "direct", parentId = null, role = "admin",
        )
        assertEquals(emptyList(), result, "No matching role rows → filter excludes everything")
    }

    // --------------------------------------------------------------------------
    // scope=direct semantics
    // --------------------------------------------------------------------------

    @Test
    fun `scope=direct returns only direct holdings (no descendant expansion)`() {
        seedTree()
        persistRole("realm-1", "acme", "user-1", "admin")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false, scope = "direct", parentId = null, role = "admin",
        )
        assertEquals(listOf("acme"), result, "scope=direct: descendants (eng, web) NOT included")
    }

    @Test
    fun `scope=direct with multiple direct holdings returns the full set`() {
        seedTree()
        persistRole("realm-1", "acme", "user-1", "viewer")
        persistRole("realm-1", "other", "user-1", "viewer")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false, scope = "direct", parentId = null, role = "viewer",
        )
        assertEquals(setOf("acme", "other"), result?.toSet())
    }

    // --------------------------------------------------------------------------
    // scope=inherited semantics
    // --------------------------------------------------------------------------

    @Test
    fun `scope=inherited expands direct holdings to descendant closure`() {
        seedTree()
        persistRole("realm-1", "acme", "user-1", "admin")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false, scope = "inherited", parentId = null, role = "admin",
        )
        assertEquals(setOf("acme", "eng", "web"), result?.toSet())
    }

    @Test
    fun `scope=inherited only expands from rooted holdings (siblings excluded)`() {
        seedTree()
        // Admin only on /eng — should NOT include /acme (ancestor) or /other (cousin),
        // only /eng + descendants.
        persistRole("realm-1", "eng", "user-1", "admin")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false, scope = "inherited", parentId = null, role = "admin",
        )
        assertEquals(setOf("eng", "web"), result?.toSet())
    }

    // --------------------------------------------------------------------------
    // parentId implies inherited semantics regardless of `scope`
    // --------------------------------------------------------------------------

    @Test
    fun `parentId set forces inherited expansion even when scope=direct`() {
        seedTree()
        persistRole("realm-1", "acme", "user-1", "admin")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false,
            scope = "direct",
            parentId = "acme", // drilling down implies tree-walk
            role = "admin",
        )
        // Even though scope=direct, parentId set → expand to descendants so children
        // of /acme that inherit admin can match.
        assertEquals(setOf("acme", "eng", "web"), result?.toSet())
    }

    // --------------------------------------------------------------------------
    // Realm scoping
    // --------------------------------------------------------------------------

    @Test
    fun `role rows in another realm do not leak into the filter`() {
        seedTree()
        // user-1 has admin on /acme in realm-2 (different realm)
        makeGroup("acme-r2", TOP, "realm-2")
        persistRole("realm-2", "acme-r2", "user-1", "admin")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false, scope = "direct", parentId = null, role = "admin",
        )
        assertTrue(result?.isEmpty() == true, "realm-2 holdings must not affect realm-1 filter")
    }

    // --------------------------------------------------------------------------
    // Case-insensitivity
    // --------------------------------------------------------------------------

    @Test
    fun `role match is case-insensitive`() {
        seedTree()
        persistRole("realm-1", "acme", "user-1", "admin")
        val result = UserGroupService.computeRoleFilter(
            em, "realm-1", "user-1",
            isRealmAdmin = false, scope = "direct", parentId = null, role = "ADMIN",
        )
        assertEquals(listOf("acme"), result)
    }
}

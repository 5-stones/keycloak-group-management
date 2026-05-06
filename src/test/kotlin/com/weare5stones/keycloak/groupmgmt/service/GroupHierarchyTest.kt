package com.weare5stones.keycloak.groupmgmt.service

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
 * Validates [GroupHierarchy.ancestorIds] and [GroupHierarchy.descendantIds] against
 * an H2-backed instance of Keycloak's `KEYCLOAK_GROUP` table (mapped by
 * [KeycloakGroupTestEntity]). The HQL these tests exercise is identical to what
 * runs in production against Postgres / MariaDB; H2 here serves as a fast
 * functional check.
 *
 * Hierarchy used by the tests (per realm "realm-1"):
 *
 *     gp       (top-level, parentId = TOP)
 *      └── p   (parentId = gp)
 *           └── c   (parentId = p)
 *                └── gc  (parentId = c)
 *
 *     cousin   (top-level, separate subtree)
 *
 * "realm-2" has its own gp/p with the same names but different IDs to exercise
 * realm isolation.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupHierarchyTest {

    private lateinit var emf: EntityManagerFactory
    private lateinit var em: EntityManager

    private val TOP = " " // Keycloak's GroupEntity.TOP_PARENT_ID

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

    private fun seedRealm1() {
        makeGroup("gp", TOP, "realm-1")
        makeGroup("p", "gp", "realm-1")
        makeGroup("c", "p", "realm-1")
        makeGroup("gc", "c", "realm-1")
        makeGroup("cousin", TOP, "realm-1")
    }

    // --------------------------------------------------------------------------
    // ancestorIds
    // --------------------------------------------------------------------------

    @Test
    fun `ancestorIds of top-level group returns just itself`() {
        seedRealm1()
        val result = GroupHierarchy.ancestorIds(em, "realm-1", "gp")
        assertEquals(setOf("gp"), result.toSet())
    }

    @Test
    fun `ancestorIds of one-level-deep group returns itself plus parent`() {
        seedRealm1()
        val result = GroupHierarchy.ancestorIds(em, "realm-1", "p")
        assertEquals(setOf("p", "gp"), result.toSet())
    }

    @Test
    fun `ancestorIds of three-levels-deep group returns full chain`() {
        seedRealm1()
        val result = GroupHierarchy.ancestorIds(em, "realm-1", "gc")
        assertEquals(setOf("gc", "c", "p", "gp"), result.toSet())
    }

    @Test
    fun `ancestorIds returns empty for non-existent group`() {
        seedRealm1()
        val result = GroupHierarchy.ancestorIds(em, "realm-1", "does-not-exist")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ancestorIds is realm-scoped (does not cross into other realm)`() {
        seedRealm1()
        // realm-2 has groups with the SAME parent IDs but different IDs — if the CTE
        // crossed realms it would walk those.
        makeGroup("gp-r2", TOP, "realm-2")
        makeGroup("p-r2", "gp-r2", "realm-2")

        // Querying gp-r2 in realm-1 should return empty (different realm).
        val crossRealm = GroupHierarchy.ancestorIds(em, "realm-1", "gp-r2")
        assertTrue(crossRealm.isEmpty(), "Expected empty across realms, got $crossRealm")

        // Querying gp-r2 in realm-2 should return just itself.
        val sameRealm = GroupHierarchy.ancestorIds(em, "realm-2", "gp-r2")
        assertEquals(setOf("gp-r2"), sameRealm.toSet())
    }

    @Test
    fun `ancestorIds does not include cousin (sibling subtree)`() {
        seedRealm1()
        val result = GroupHierarchy.ancestorIds(em, "realm-1", "c")
        assertTrue("cousin" !in result, "Expected 'cousin' to NOT be in $result")
    }

    // --------------------------------------------------------------------------
    // descendantIds
    // --------------------------------------------------------------------------

    @Test
    fun `descendantIds of leaf group returns just itself`() {
        seedRealm1()
        val result = GroupHierarchy.descendantIds(em, "realm-1", listOf("gc"))
        assertEquals(setOf("gc"), result.toSet())
    }

    @Test
    fun `descendantIds of mid-tree group returns itself plus all descendants`() {
        seedRealm1()
        val result = GroupHierarchy.descendantIds(em, "realm-1", listOf("p"))
        assertEquals(setOf("p", "c", "gc"), result.toSet())
    }

    @Test
    fun `descendantIds of top-level group returns entire subtree`() {
        seedRealm1()
        val result = GroupHierarchy.descendantIds(em, "realm-1", listOf("gp"))
        assertEquals(setOf("gp", "p", "c", "gc"), result.toSet())
    }

    @Test
    fun `descendantIds with multiple roots returns union of all subtrees`() {
        seedRealm1()
        val result = GroupHierarchy.descendantIds(em, "realm-1", listOf("p", "cousin"))
        assertEquals(setOf("p", "c", "gc", "cousin"), result.toSet())
    }

    @Test
    fun `descendantIds returns empty list for empty input`() {
        seedRealm1()
        val result = GroupHierarchy.descendantIds(em, "realm-1", emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `descendantIds is realm-scoped`() {
        seedRealm1()
        makeGroup("gp-r2", TOP, "realm-2")
        makeGroup("p-r2", "gp-r2", "realm-2")
        makeGroup("c-r2", "p-r2", "realm-2")

        // Querying gp in realm-1 must not pick up realm-2's tree.
        val realm1 = GroupHierarchy.descendantIds(em, "realm-1", listOf("gp"))
        assertEquals(setOf("gp", "p", "c", "gc"), realm1.toSet())

        // And vice versa.
        val realm2 = GroupHierarchy.descendantIds(em, "realm-2", listOf("gp-r2"))
        assertEquals(setOf("gp-r2", "p-r2", "c-r2"), realm2.toSet())
    }

    // --------------------------------------------------------------------------
    // ancestorChains (batched: many rows -> their chains)
    // --------------------------------------------------------------------------

    @Test
    fun `ancestorChains for empty input returns empty map`() {
        seedRealm1()
        assertEquals(emptyMap<String, List<String>>(), GroupHierarchy.ancestorChains(em, "realm-1", emptyList()))
    }

    @Test
    fun `ancestorChains maps each row to row + ancestors`() {
        seedRealm1()
        val result = GroupHierarchy.ancestorChains(em, "realm-1", listOf("c", "p"))
        assertEquals(setOf("c", "p", "gp"), result["c"]?.toSet())
        assertEquals(setOf("p", "gp"), result["p"]?.toSet())
    }

    @Test
    fun `ancestorChains for multiple rows sharing ancestors keeps each row's chain attributed`() {
        // Add a sibling under p so we can test shared ancestors.
        makeGroup("c2", "p", "realm-1")
        seedRealm1()
        val result = GroupHierarchy.ancestorChains(em, "realm-1", listOf("c", "c2"))
        // Both c and c2 share parent p and grandparent gp, but each row's chain is
        // independently complete.
        assertEquals(setOf("c", "p", "gp"), result["c"]?.toSet())
        assertEquals(setOf("c2", "p", "gp"), result["c2"]?.toSet())
    }

    @Test
    fun `ancestorChains for top-level group returns just itself`() {
        seedRealm1()
        val result = GroupHierarchy.ancestorChains(em, "realm-1", listOf("gp"))
        assertEquals(setOf("gp"), result["gp"]?.toSet())
    }

    @Test
    fun `ancestorChains is realm-scoped`() {
        seedRealm1()
        makeGroup("gp-r2", TOP, "realm-2")
        makeGroup("p-r2", "gp-r2", "realm-2")

        // Querying p-r2 in realm-1 returns empty
        assertTrue(GroupHierarchy.ancestorChains(em, "realm-1", listOf("p-r2")).isEmpty())
        // Querying p-r2 in realm-2 returns its chain
        val r2 = GroupHierarchy.ancestorChains(em, "realm-2", listOf("p-r2"))
        assertEquals(setOf("p-r2", "gp-r2"), r2["p-r2"]?.toSet())
    }

    // --------------------------------------------------------------------------
    // ancestors (id + name, root-first)
    // --------------------------------------------------------------------------

    @Test
    fun `ancestors of top-level group is empty`() {
        seedRealm1()
        assertTrue(GroupHierarchy.ancestors(em, "realm-1", "gp").isEmpty())
    }

    @Test
    fun `ancestors of one-level-deep group returns just the parent`() {
        seedRealm1()
        val result = GroupHierarchy.ancestors(em, "realm-1", "p")
        assertEquals(1, result.size)
        assertEquals("gp", result[0].id)
        assertEquals("gp", result[0].name)
    }

    @Test
    fun `ancestors of three-levels-deep group returns full chain root-first`() {
        seedRealm1()
        val result = GroupHierarchy.ancestors(em, "realm-1", "gc")
        // Root-first ordering: gp (root), p, c (closest ancestor) — excludes gc itself.
        assertEquals(listOf("gp", "p", "c"), result.map { it.id })
        assertEquals(listOf("gp", "p", "c"), result.map { it.name })
    }

    @Test
    fun `ancestors excludes the group itself`() {
        seedRealm1()
        val result = GroupHierarchy.ancestors(em, "realm-1", "c")
        assertTrue(result.none { it.id == "c" }, "Expected 'c' to NOT be in $result")
    }

    @Test
    fun `ancestors returns empty for non-existent group`() {
        seedRealm1()
        assertTrue(GroupHierarchy.ancestors(em, "realm-1", "does-not-exist").isEmpty())
    }

    @Test
    fun `ancestors is realm-scoped`() {
        seedRealm1()
        makeGroup("gp-r2", TOP, "realm-2")
        makeGroup("p-r2", "gp-r2", "realm-2")

        // Querying p-r2 in realm-1 returns empty
        assertTrue(GroupHierarchy.ancestors(em, "realm-1", "p-r2").isEmpty())
        // Querying p-r2 in realm-2 returns its parent
        val result = GroupHierarchy.ancestors(em, "realm-2", "p-r2")
        assertEquals(listOf("gp-r2"), result.map { it.id })
    }

    // --------------------------------------------------------------------------
    // topLevelGroupIds
    // --------------------------------------------------------------------------

    @Test
    fun `topLevelGroupIds returns groups with TOP_PARENT_ID parent`() {
        seedRealm1()
        val result = GroupHierarchy.topLevelGroupIds(em, "realm-1")
        assertEquals(setOf("gp", "cousin"), result.toSet())
    }

    @Test
    fun `topLevelGroupIds is realm-scoped`() {
        seedRealm1()
        makeGroup("gp-r2", TOP, "realm-2")
        makeGroup("p-r2", "gp-r2", "realm-2")

        assertEquals(setOf("gp", "cousin"), GroupHierarchy.topLevelGroupIds(em, "realm-1").toSet())
        assertEquals(setOf("gp-r2"), GroupHierarchy.topLevelGroupIds(em, "realm-2").toSet())
    }

    @Test
    fun `topLevelGroupIds returns empty for unknown realm`() {
        seedRealm1()
        assertTrue(GroupHierarchy.topLevelGroupIds(em, "no-such-realm").isEmpty())
    }

    @Test
    fun `descendantIds does not flow up the tree`() {
        seedRealm1()
        val result = GroupHierarchy.descendantIds(em, "realm-1", listOf("c"))
        // Should NOT include p or gp (those are ancestors)
        assertTrue("p" !in result, "Expected 'p' to NOT be in descendants of c, got $result")
        assertTrue("gp" !in result, "Expected 'gp' to NOT be in descendants of c, got $result")
    }
}

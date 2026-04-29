package com.weare5stones.keycloak.groupmgmt.service

import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService.GrantDecision
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for the pure privilege-escalation logic in [GroupRoleService.evaluateGrant]
 * and [GroupRoleService.computeEffectivePermissions]. These do not require a Keycloak
 * session — they exercise the decision rules directly with already-resolved inputs.
 */
class GroupRoleServiceGrantTest {

    private val rolePerms = mapOf(
        "viewer" to setOf("members:read", "invitations:read"),
        "editor" to setOf("group:write"),
        "manager" to setOf(
            "group:write", "members:read", "members:write", "roles:write",
            "invitations:read", "invitations:write",
        ),
        "auditor" to setOf("members:read"),
        "inviter" to setOf("invitations:read", "invitations:write"),
        "tag-only" to emptySet(),
    )

    @Nested
    inner class EvaluateGrantBypasses {

        @Test
        fun `admin actor can grant any role including admin`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = true,
                actorPermissions = emptySet(),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("manager", "admin", "viewer"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `empty roles to grant is always allowed`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = emptySet(),
                rolePermissionsMap = rolePerms,
                rolesToGrant = emptyList(),
            )
            assertEquals(GrantDecision.Allowed, result)
        }
    }

    @Nested
    inner class EvaluateGrantAdminEscalation {

        @Test
        fun `non-admin cannot grant admin even with full perm set`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = GroupRoleService.ALL_PERMISSIONS,
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("admin"),
            )
            val decision = assertIs<GrantDecision.DeniedAdminRequired>(result)
            assertEquals("admin", decision.role)
        }

        @Test
        fun `admin role denial reported even when other roles in the list are fine`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = setOf("members:read", "invitations:read"),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer", "admin"),
            )
            assertIs<GrantDecision.DeniedAdminRequired>(result)
        }
    }

    @Nested
    inner class EvaluateGrantPermissionSubset {

        @Test
        fun `actor can grant role whose perms are a subset of theirs`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = setOf("members:read", "invitations:read", "members:write"),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer"), // requires members:read, invitations:read
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `actor can grant role whose perms exactly match theirs`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = setOf("members:read"),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("auditor"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `actor cannot grant role whose perms exceed theirs - reports missing`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = setOf("members:read"),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer"), // viewer requires invitations:read too
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals("viewer", decision.role)
            assertEquals(setOf("invitations:read"), decision.missing)
        }

        @Test
        fun `actor with only roles-write cannot grant any non-tag role`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = setOf("roles:write"),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("auditor"), // requires members:read
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals(setOf("members:read"), decision.missing)
        }

        @Test
        fun `actor can grant tag-only roles even with no permissions`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = emptySet(),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("tag-only"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `actor can grant role missing entirely from permission map (treated as tag)`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = emptySet(),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("undeclared-role"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `denial reports the first failing role only`() {
            // viewer (members:read, invitations:read) fails first; manager would also fail
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = emptySet(),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer", "manager"),
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals("viewer", decision.role)
        }

        @Test
        fun `manager can grant viewer because manager perms superset viewer perms`() {
            val managerPerms = rolePerms["manager"]!!
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = managerPerms,
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `inviter cannot grant viewer (missing members-read)`() {
            val inviterPerms = rolePerms["inviter"]!!
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = inviterPerms,
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer"),
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals(setOf("members:read"), decision.missing)
        }

        @Test
        fun `granting multiple roles all pass when each role is allowed`() {
            val managerPerms = rolePerms["manager"]!!
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = managerPerms,
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer", "auditor", "inviter"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `editor with only group-write can grant editor-equivalent role`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = setOf("group:write"),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("editor"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `editor cannot grant viewer (no members-read)`() {
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = setOf("group:write"),
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("viewer"),
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals(setOf("members:read", "invitations:read"), decision.missing)
        }

        @Test
        fun `manager (now with group-write) can grant editor`() {
            val managerPerms = rolePerms["manager"]!!
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = managerPerms,
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("editor"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `actor without group-write cannot grant role requiring it`() {
            // viewer perms = {members:read, invitations:read}; viewer can't grant editor
            val result = GroupRoleService.evaluateGrant(
                actorIsAdmin = false,
                actorPermissions = rolePerms["viewer"]!!,
                rolePermissionsMap = rolePerms,
                rolesToGrant = listOf("editor"),
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals(setOf("group:write"), decision.missing)
        }
    }

    @Nested
    inner class PermissionVocabulary {

        @Test
        fun `ALL_PERMISSIONS contains group-write`() {
            assertTrue(
                "group:write" in GroupRoleService.ALL_PERMISSIONS,
                "Expected ALL_PERMISSIONS to include group:write, got ${GroupRoleService.ALL_PERMISSIONS}",
            )
        }

        @Test
        fun `ALL_PERMISSIONS contains every documented permission constant`() {
            val expected = setOf(
                GroupRoleService.PERM_GROUP_WRITE,
                GroupRoleService.PERM_MEMBERS_READ,
                GroupRoleService.PERM_MEMBERS_WRITE,
                GroupRoleService.PERM_ROLES_WRITE,
                GroupRoleService.PERM_INVITATIONS_READ,
                GroupRoleService.PERM_INVITATIONS_WRITE,
            )
            assertEquals(expected, GroupRoleService.ALL_PERMISSIONS)
        }
    }

    @Nested
    inner class ComputeEffectivePermissions {

        @Test
        fun `realm admin gets all permissions regardless of assigned roles`() {
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = true,
                isGroupAdmin = false,
                actorAssignedRoles = emptySet(),
                isGroupMember = false,
                rolePermissionsMap = rolePerms,
            )
            assertEquals(GroupRoleService.ALL_PERMISSIONS, perms)
        }

        @Test
        fun `group admin gets all permissions regardless of role-permissions config`() {
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = false,
                isGroupAdmin = true,
                actorAssignedRoles = setOf("admin"),
                isGroupMember = true,
                rolePermissionsMap = emptyMap(), // even with no config, admin = ALL
            )
            assertEquals(GroupRoleService.ALL_PERMISSIONS, perms)
        }

        @Test
        fun `assigned roles contribute their permissions in union`() {
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = false,
                isGroupAdmin = false,
                actorAssignedRoles = setOf("auditor", "inviter"),
                isGroupMember = true,
                rolePermissionsMap = rolePerms,
            )
            assertEquals(
                setOf("members:read", "invitations:read", "invitations:write"),
                perms,
            )
        }

        @Test
        fun `member-role baseline is included for group members`() {
            val baseline = mapOf("member" to setOf("members:read"))
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = false,
                isGroupAdmin = false,
                actorAssignedRoles = emptySet(),
                isGroupMember = true,
                rolePermissionsMap = baseline,
            )
            assertEquals(setOf("members:read"), perms)
        }

        @Test
        fun `member-role baseline is excluded for non-members`() {
            val baseline = mapOf("member" to setOf("members:read"))
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = false,
                isGroupAdmin = false,
                actorAssignedRoles = emptySet(),
                isGroupMember = false,
                rolePermissionsMap = baseline,
            )
            assertEquals(emptySet(), perms)
        }

        @Test
        fun `assigned roles and member baseline combine for group members`() {
            val map = mapOf(
                "member" to setOf("members:read"),
                "inviter" to setOf("invitations:read", "invitations:write"),
            )
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = false,
                isGroupAdmin = false,
                actorAssignedRoles = setOf("inviter"),
                isGroupMember = true,
                rolePermissionsMap = map,
            )
            assertEquals(
                setOf("members:read", "invitations:read", "invitations:write"),
                perms,
            )
        }

        @Test
        fun `roles missing from permission map contribute no permissions`() {
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = false,
                isGroupAdmin = false,
                actorAssignedRoles = setOf("undefined-role"),
                isGroupMember = false,
                rolePermissionsMap = rolePerms,
            )
            assertTrue(perms.isEmpty(), "Expected empty perms for unmapped role, got $perms")
        }
    }

    @Nested
    inner class EndToEndScenarios {
        // These compose computeEffectivePermissions + evaluateGrant to verify the full
        // chain a non-admin actor goes through when calling setRoles or createInvitation.

        private fun decideGrant(
            actorAssignedRoles: Set<String>,
            isGroupMember: Boolean,
            rolePermsMap: Map<String, Set<String>>,
            rolesToGrant: Collection<String>,
        ): GrantDecision {
            val isAdmin = "admin" in actorAssignedRoles
            val perms = GroupRoleService.computeEffectivePermissions(
                isRealmAdmin = false,
                isGroupAdmin = isAdmin,
                actorAssignedRoles = actorAssignedRoles,
                isGroupMember = isGroupMember,
                rolePermissionsMap = rolePermsMap,
            )
            return GroupRoleService.evaluateGrant(isAdmin, perms, rolePermsMap, rolesToGrant)
        }

        @Test
        fun `manager can invite as viewer`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("manager"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("viewer"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `viewer cannot invite as manager`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("viewer"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("manager"),
            )
            assertIs<GrantDecision.DeniedMissingPermissions>(result)
        }

        @Test
        fun `auditor with only members-read cannot invite as inviter`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("auditor"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("inviter"),
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals(setOf("invitations:read", "invitations:write"), decision.missing)
        }

        @Test
        fun `member-baseline alone is enough to grant a role with the same perms`() {
            // Realm grants every member members:read via member-baseline.
            // A user with no explicit role can still grant 'auditor' (members:read).
            val mapWithBaseline = mapOf(
                "member" to setOf("members:read"),
                "auditor" to setOf("members:read"),
            )
            // ...but the actor still needs roles:write to even reach this code in
            // production (gated by requirePermission); we test the grant rule alone here.
            val result = decideGrant(
                actorAssignedRoles = emptySet(),
                isGroupMember = true,
                rolePermsMap = mapWithBaseline,
                rolesToGrant = listOf("auditor"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `non-member with same role assignment loses baseline and is denied`() {
            val mapWithBaseline = mapOf(
                "member" to setOf("members:read"),
                "auditor" to setOf("members:read"),
            )
            val result = decideGrant(
                actorAssignedRoles = emptySet(),
                isGroupMember = false, // not a group member -> no baseline
                rolePermsMap = mapWithBaseline,
                rolesToGrant = listOf("auditor"),
            )
            assertIs<GrantDecision.DeniedMissingPermissions>(result)
        }

        @Test
        fun `admin can grant admin to others`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("admin"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("admin"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `editor can invite as editor (rename rights propagation)`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("editor"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("editor"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `editor cannot invite as manager (manager has more than group-write)`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("editor"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("manager"),
            )
            assertIs<GrantDecision.DeniedMissingPermissions>(result)
        }

        @Test
        fun `manager can invite as editor`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("manager"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("editor"),
            )
            assertEquals(GrantDecision.Allowed, result)
        }

        @Test
        fun `viewer cannot invite as editor (no group-write)`() {
            val result = decideGrant(
                actorAssignedRoles = setOf("viewer"),
                isGroupMember = true,
                rolePermsMap = rolePerms,
                rolesToGrant = listOf("editor"),
            )
            val decision = assertIs<GrantDecision.DeniedMissingPermissions>(result)
            assertEquals(setOf("group:write"), decision.missing)
        }
    }
}

package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AuthenticationManager

class ConfigResource(
    private val session: KeycloakSession,
    private val auth: AuthenticationManager.AuthResult
) {

    companion object {
        val CONFIG_KEYS = listOf(
            "group-mgmt-post-accept-url",
            "group-mgmt-invitation-client-id",
            "group-invitation-ttl-hours",
            GroupRoleService.ALLOWED_ROLES_ATTRIBUTE,
            GroupRoleService.ROLE_PERMISSIONS_ATTRIBUTE,
        )

        private fun parseCsvRoles(raw: String): Set<String> =
            raw.split(',')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && it != GroupRoleService.ADMIN_ROLE }
                .toSet()
    }

    private val realm = session.getContext().realm

    private fun requireRealmAdmin() {
        if (!GroupRoleService.isRealmAdmin(session, realm, auth.user)) {
            throw ForbiddenException("Realm admin access required")
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getConfig(): Response {
        requireRealmAdmin()

        val config = CONFIG_KEYS.associateWith { key ->
            realm.getAttribute(key)
        }

        return Response.ok(config).withCors(auth)
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateConfig(body: Map<String, String?>): Response {
        requireRealmAdmin()

        // Pre-validate role-permissions JSON against the prospective allowed-roles.
        val rolePermsRaw = body[GroupRoleService.ROLE_PERMISSIONS_ATTRIBUTE]
        if (!rolePermsRaw.isNullOrBlank()) {
            val prospectiveAllowedRoles =
                if (body.containsKey(GroupRoleService.ALLOWED_ROLES_ATTRIBUTE)) {
                    parseCsvRoles(body[GroupRoleService.ALLOWED_ROLES_ATTRIBUTE].orEmpty()) +
                        GroupRoleService.MEMBER_ROLE
                } else {
                    GroupRoleService.getAllowedRoles(realm)
                }
            try {
                GroupRoleService.parseRolePermissions(rolePermsRaw, prospectiveAllowedRoles)
            } catch (e: IllegalArgumentException) {
                return errorResponse(
                    Response.Status.BAD_REQUEST,
                    "Invalid ${GroupRoleService.ROLE_PERMISSIONS_ATTRIBUTE}: ${e.message}",
                    auth,
                )
            }
        }

        for ((key, value) in body) {
            if (key !in CONFIG_KEYS) continue
            if (value.isNullOrBlank()) {
                realm.removeAttribute(key)
            } else {
                realm.setAttribute(key, value)
            }
        }

        val config = CONFIG_KEYS.associateWith { key ->
            realm.getAttribute(key)
        }

        return Response.ok(config).withCors(auth)
    }
}

package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.cors.Cors
import org.keycloak.services.managers.AppAuthManager
import org.keycloak.services.managers.AuthenticationManager

class RolesResource(private val session: KeycloakSession) {

    private val tokenAuth = AppAuthManager.BearerTokenAuthenticator(session)
    private val realm = session.getContext().realm

    private fun authenticate(): AuthenticationManager.AuthResult =
        tokenAuth.authenticate() ?: throw NotAuthorizedException("Bearer")

    @OPTIONS
    @Path("{any:.*}")
    fun preflight(): Response =
        Cors.builder().preflight().auth().add(Response.ok())

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun listRoles(): Response {
        val auth = authenticate()
        val allowed = GroupRoleService.getAllowedRoles(realm)
        val allRoles = buildList {
            add(GroupRoleService.ADMIN_ROLE)
            addAll(allowed.sorted())
        }
        val rolePerms = GroupRoleService.getRolePermissions(realm)
            .mapValues { (_, perms) -> perms.sorted() }
        return Response.ok(mapOf(
            "roles" to allRoles,
            "permissions" to GroupRoleService.ALL_PERMISSIONS.sorted(),
            "rolePermissions" to rolePerms,
        )).withCors(auth)
    }
}

package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import com.weare5stones.keycloak.groupmgmt.service.UserGroupService
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.cors.Cors
import org.keycloak.services.managers.AppAuthManager
import org.keycloak.services.managers.AuthenticationManager

class UserGroupsResource(private val session: KeycloakSession) {

    private val tokenAuth = AppAuthManager.BearerTokenAuthenticator(session)
    private val realm = session.getContext().realm
    private val groupService = UserGroupService(session)

    private fun authenticate(): AuthenticationManager.AuthResult =
        tokenAuth.authenticate() ?: throw NotAuthorizedException("Bearer")

    @OPTIONS
    @Path("{any:.*}")
    fun preflight(): Response =
        Cors.builder().preflight().auth().add(Response.ok())

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getMyGroups(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("pageSize") @DefaultValue("20") pageSize: Int,
        @QueryParam("search") search: String?,
        @QueryParam("sortBy") @DefaultValue("name") sortBy: String,
        @QueryParam("sortDir") @DefaultValue("asc") sortDir: String,
        @QueryParam("scope") @DefaultValue("direct") scope: String,
        @QueryParam("parentId") parentId: String?,
        @QueryParam("role") role: String?,
    ): Response {
        val auth = authenticate()
        val normalizedScope = scope.lowercase()
        if (normalizedScope !in setOf("direct", "inherited")) {
            return Response.status(400)
                .entity(mapOf("error" to "scope must be 'direct' or 'inherited'"))
                .withCors(auth)
        }
        val normalizedParent = parentId?.takeIf { it.isNotBlank() }
        val normalizedRole = role?.takeIf { it.isNotBlank() }
        val isRealmAdmin = GroupRoleService.isRealmAdmin(session, realm, auth.user)
        val result = groupService.findGroups(
            realm, auth.user.id, isRealmAdmin,
            scope = normalizedScope,
            parentId = normalizedParent,
            role = normalizedRole,
            search = search,
            sortBy = sortBy,
            sortDir = sortDir,
            page = page,
            pageSize = pageSize,
        )
        return Response.ok(result).withCors(auth)
    }
}

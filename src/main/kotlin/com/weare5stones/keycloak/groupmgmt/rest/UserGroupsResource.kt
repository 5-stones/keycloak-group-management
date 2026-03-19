package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupAdminService
import com.weare5stones.keycloak.groupmgmt.service.UserGroupService
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AuthenticationManager

class UserGroupsResource(
    private val session: KeycloakSession,
    private val auth: AuthenticationManager.AuthResult
) {

    private val realm = session.getContext().realm
    private val groupService = UserGroupService(session)

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getMyGroups(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("pageSize") @DefaultValue("20") pageSize: Int,
        @QueryParam("search") search: String?,
        @QueryParam("sortBy") @DefaultValue("name") sortBy: String,
        @QueryParam("sortDir") @DefaultValue("asc") sortDir: String
    ): Response {
        val isRealmAdmin = GroupAdminService.isRealmAdmin(session, realm, auth.user)
        val result = groupService.findGroups(realm, auth.user.id, isRealmAdmin, search, sortBy, sortDir, page, pageSize)
        return Response.ok(result).withCors(auth)
    }
}

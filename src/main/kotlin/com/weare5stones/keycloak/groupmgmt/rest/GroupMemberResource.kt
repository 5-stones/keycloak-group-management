package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupAdminService
import com.weare5stones.keycloak.groupmgmt.service.GroupMemberService
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AuthenticationManager

class GroupMemberResource(
    private val session: KeycloakSession,
    private val auth: AuthenticationManager.AuthResult,
    private val groupId: String
) {

    private val realm = session.getContext().realm
    private val memberService = GroupMemberService(session)

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun listMembers(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("pageSize") @DefaultValue("20") pageSize: Int,
        @QueryParam("search") search: String?,
        @QueryParam("sortBy") @DefaultValue("username") sortBy: String,
        @QueryParam("sortDir") @DefaultValue("asc") sortDir: String
    ): Response {
        val group = GroupAdminService.requireGroupAdmin(session, realm, groupId, auth.user)
        val adminIds = GroupAdminService.getAdminIds(group).toSet()
        val result = memberService.findMembers(groupId, adminIds, search, sortBy, sortDir, page, pageSize)
        return Response.ok(result).withCors(auth)
    }

    @DELETE
    @Path("{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun removeMember(@PathParam("userId") userId: String): Response {
        val group = GroupAdminService.requireGroupAdmin(session, realm, groupId, auth.user)
        val user = session.users().getUserById(realm, userId)
            ?: throw NotFoundException("User not found")

        user.leaveGroup(group)
        GroupAdminService.demoteFromAdmin(group, userId)

        return Response.noContent().withCors(auth)
    }

    @PUT
    @Path("{userId}/promote")
    @Produces(MediaType.APPLICATION_JSON)
    fun promoteMember(@PathParam("userId") userId: String): Response {
        val group = GroupAdminService.requireGroupAdmin(session, realm, groupId, auth.user)
        val user = session.users().getUserById(realm, userId)
            ?: throw NotFoundException("User not found")

        if (!user.isMemberOf(group)) {
            return errorResponse(Response.Status.BAD_REQUEST, "User is not a member of this group", auth)
        }

        GroupAdminService.promoteToAdmin(group, userId)

        return Response.ok(mapOf(
            "id" to user.id,
            "username" to user.username,
            "isGroupAdmin" to true
        )).withCors(auth)
    }

    @PUT
    @Path("{userId}/demote")
    @Produces(MediaType.APPLICATION_JSON)
    fun demoteMember(@PathParam("userId") userId: String): Response {
        val group = GroupAdminService.requireGroupAdmin(session, realm, groupId, auth.user)
        val user = session.users().getUserById(realm, userId)
            ?: throw NotFoundException("User not found")

        GroupAdminService.demoteFromAdmin(group, userId)

        return Response.ok(mapOf(
            "id" to user.id,
            "username" to user.username,
            "isGroupAdmin" to false
        )).withCors(auth)
    }
}

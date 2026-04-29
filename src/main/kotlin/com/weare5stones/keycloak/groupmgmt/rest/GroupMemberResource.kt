package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupMemberService
import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import jakarta.ws.rs.Consumes
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
        @QueryParam("sortDir") @DefaultValue("asc") sortDir: String,
        @QueryParam("role") role: String?
    ): Response {
        GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_MEMBERS_READ)
        val result = try {
            memberService.findMembers(groupId, search, sortBy, sortDir, page, pageSize, role)
        } catch (e: IllegalArgumentException) {
            return errorResponse(Response.Status.BAD_REQUEST, e.message ?: "Invalid request", auth)
        }
        return Response.ok(result).withCors(auth)
    }

    @DELETE
    @Path("{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun removeMember(@PathParam("userId") userId: String): Response {
        val group = GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_MEMBERS_WRITE)
        val user = session.users().getUserById(realm, userId)
            ?: throw NotFoundException("User not found")

        try {
            GroupRoleService.removeAllRoles(session, realm, auth.user, group.id, userId)
        } catch (e: IllegalStateException) {
            return errorResponse(Response.Status.CONFLICT, e.message ?: "Conflict", auth)
        }
        user.leaveGroup(group)

        return Response.noContent().withCors(auth)
    }

    @PUT
    @Path("{userId}/roles")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun setMemberRoles(
        @PathParam("userId") userId: String,
        body: Map<String, Any?>
    ): Response {
        val group = GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_ROLES_WRITE)
        val user = session.users().getUserById(realm, userId)
            ?: throw NotFoundException("User not found")

        if (!user.isMemberOf(group)) {
            return errorResponse(Response.Status.BAD_REQUEST, "User is not a member of this group", auth)
        }

        val rolesInput = body["roles"]
        if (rolesInput !is List<*>) {
            return errorResponse(Response.Status.BAD_REQUEST, "'roles' must be an array of strings", auth)
        }
        val roles = rolesInput.map { it?.toString() ?: "" }

        try {
            GroupRoleService.setRoles(session, realm, auth.user, group.id, userId, roles)
        } catch (e: IllegalArgumentException) {
            return errorResponse(Response.Status.BAD_REQUEST, e.message ?: "Invalid request", auth)
        } catch (e: IllegalStateException) {
            return errorResponse(Response.Status.CONFLICT, e.message ?: "Conflict", auth)
        }

        val updated = GroupRoleService.getRoles(session, realm.id, group.id, userId).sorted()
        return Response.ok(mapOf(
            "id" to user.id,
            "username" to user.username,
            "roles" to updated
        )).withCors(auth)
    }
}

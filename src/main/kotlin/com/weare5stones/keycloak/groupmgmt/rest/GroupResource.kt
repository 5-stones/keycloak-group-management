package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import com.weare5stones.keycloak.groupmgmt.util.fullPath
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AuthenticationManager

class GroupResource(
    private val session: KeycloakSession,
    private val auth: AuthenticationManager.AuthResult,
    private val groupId: String,
) {

    private val realm = session.getContext().realm

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getGroup(): Response {
        val group = session.groups().getGroupById(realm, groupId)
            ?: throw NotFoundException("Group not found")
        // Any member of the group (or realm admin) can read its basic info.
        if (!GroupRoleService.isRealmAdmin(session, realm, auth.user) && !auth.user.isMemberOf(group)) {
            throw ForbiddenException("You are not a member of this group")
        }
        return Response.ok(toMap(group)).withCors(auth)
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateGroup(body: Map<String, Any?>): Response {
        val group = GroupRoleService.requirePermission(
            session, realm, groupId, auth.user, GroupRoleService.PERM_GROUP_WRITE
        )

        val newName = (body["name"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "'name' is required", auth)

        if (newName == group.name) {
            return Response.ok(toMap(group)).withCors(auth)
        }

        // Reject duplicate name within the same parent (Keycloak treats sibling group
        // names as identifiers — letting two siblings share a name corrupts path-based
        // lookups). Sibling list comes from parent.subGroupsStream or, for top-level
        // groups, realm.topLevelGroupsStream.
        val parent = group.parent
        val siblingsStream = parent?.subGroupsStream
            ?: session.groups().getTopLevelGroupsStream(realm)
        val collision = siblingsStream
            .filter { it.id != group.id && it.name.equals(newName, ignoreCase = true) }
            .findFirst()
            .orElse(null)
        if (collision != null) {
            return errorResponse(
                Response.Status.CONFLICT,
                "A sibling group named '$newName' already exists",
                auth,
            )
        }

        group.name = newName
        return Response.ok(toMap(group)).withCors(auth)
    }

    private fun toMap(group: org.keycloak.models.GroupModel): Map<String, Any?> = mapOf(
        "id" to group.id,
        "name" to group.name,
        "path" to group.fullPath(),
    )
}

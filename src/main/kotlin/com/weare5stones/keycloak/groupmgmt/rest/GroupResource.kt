package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupHierarchy
import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import com.weare5stones.keycloak.groupmgmt.util.fullPath
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.connections.jpa.JpaConnectionProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.services.cors.Cors
import org.keycloak.services.managers.AppAuthManager
import org.keycloak.services.managers.AuthenticationManager

class GroupResource(
    private val session: KeycloakSession,
    private val groupId: String,
) {

    private val tokenAuth = AppAuthManager.BearerTokenAuthenticator(session)
    private val realm = session.getContext().realm
    private val em get() = session.getProvider(JpaConnectionProvider::class.java).entityManager

    private fun authenticate(): AuthenticationManager.AuthResult =
        tokenAuth.authenticate() ?: throw NotAuthorizedException("Bearer")

    @OPTIONS
    @Path("{any:.*}")
    fun preflight(): Response =
        Cors.builder().preflight().auth().add(Response.ok())

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getGroup(): Response {
        val auth = authenticate()
        val group = session.groups().getGroupById(realm, groupId)
            ?: throw NotFoundException("Group not found")
        // Visible to: realm admin, direct members, OR anyone with any effective
        // permission via inheritance (admin/role on this group OR any ancestor).
        val canSee = GroupRoleService.isRealmAdmin(session, realm, auth.user)
            || auth.user.isMemberOf(group)
            || GroupRoleService.getEffectivePermissions(session, realm, group, auth.user).isNotEmpty()
        if (!canSee) {
            throw ForbiddenException("You do not have access to this group")
        }
        val ancestors = GroupHierarchy.ancestors(em, realm.id, groupId)
        return Response.ok(toMap(group, ancestors)).withCors(auth)
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateGroup(body: Map<String, Any?>): Response {
        val auth = authenticate()
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

    private fun toMap(
        group: org.keycloak.models.GroupModel,
        ancestors: List<GroupHierarchy.AncestorRecord> = emptyList(),
    ): Map<String, Any?> = mapOf(
        "id" to group.id,
        "name" to group.name,
        "path" to group.fullPath(),
        "parentId" to group.parent?.id,
        "ancestors" to ancestors.map { mapOf("id" to it.id, "name" to it.name) },
    )
}

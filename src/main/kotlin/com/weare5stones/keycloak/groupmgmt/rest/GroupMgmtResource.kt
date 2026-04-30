package com.weare5stones.keycloak.groupmgmt.rest

import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AppAuthManager
import org.keycloak.services.cors.Cors

/**
 * Top-level resource for the plugin. Layout:
 *  - `/api/...` — authenticated JSON endpoints (groups, members, invitations, config, roles, etc.)
 *  - `/admin/...` — bundled admin SPA (static files, served by [UiResource])
 *  - `/invitations/accept` — email-linked invitation landing page (HTML or JSON), kept at the
 *    top level so the URLs in already-sent invitation emails remain stable.
 */
class GroupMgmtResource(private val session: KeycloakSession) {

    private val auth: AppAuthManager.BearerTokenAuthenticator =
        AppAuthManager.BearerTokenAuthenticator(session)

    @OPTIONS
    @Path("{any:.*}")
    fun preflight(): Response {
        return Cors.builder().preflight().auth().add(Response.ok())
    }

    // ---------- /api/... — authenticated JSON ----------

    @Path("api/groups/{groupId}")
    fun group(@PathParam("groupId") groupId: String): GroupResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return GroupResource(session, authResult, groupId)
    }

    @Path("api/groups/{groupId}/invitations")
    fun groupInvitations(@PathParam("groupId") groupId: String): GroupInvitationResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return GroupInvitationResource(session, authResult, groupId)
    }

    @Path("api/groups/{groupId}/members")
    fun groupMembers(@PathParam("groupId") groupId: String): GroupMemberResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return GroupMemberResource(session, authResult, groupId)
    }

    @Path("api/me/groups")
    fun myGroups(): UserGroupsResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return UserGroupsResource(session, authResult)
    }

    @Path("api/config")
    fun config(): ConfigResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return ConfigResource(session, authResult)
    }

    @Path("api/roles")
    fun roles(): RolesResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return RolesResource(session, authResult)
    }

    // ---------- top-level user-facing endpoints ----------

    /** Email-linked invitation accept flow. Kept at the top level so the URLs
     *  in already-sent invitation emails do not break. */
    @Path("invitations/accept")
    fun invitationAccept(): InvitationAcceptResource {
        return InvitationAcceptResource(session)
    }

    /** Bundled admin SPA. Static files; SPA does its own OIDC. */
    @Path("admin")
    fun adminUi(): UiResource = UiResource(session)
}

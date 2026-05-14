package com.weare5stones.keycloak.groupmgmt.rest

import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import org.keycloak.models.KeycloakSession

/**
 * Top-level resource for the plugin. Layout:
 *  - `/api/...` — authenticated JSON endpoints (groups, members, invitations, config, roles, etc.)
 *  - `/admin/...` — bundled admin SPA (static files, served by [UiResource])
 *  - `/invitations/accept` — email-linked invitation landing page (HTML or JSON), kept at the
 *    top level so the URLs in already-sent invitation emails remain stable.
 *
 * Locators are intentionally pass-throughs: authentication happens *inside* each sub-resource's
 * method handlers, not here. This lets each sub-resource own an `@OPTIONS` preflight handler
 * that runs without auth — which is required for browser CORS preflights from cross-origin
 * callers (the bundled admin SPA is same-origin and so does not exercise this path, but
 * downstream SPAs hosted on different origins do).
 */
class GroupMgmtResource(private val session: KeycloakSession) {

    // ---------- /api/... — authenticated JSON ----------

    @Path("api/groups/{groupId}")
    fun group(@PathParam("groupId") groupId: String): GroupResource =
        GroupResource(session, groupId)

    @Path("api/groups/{groupId}/invitations")
    fun groupInvitations(@PathParam("groupId") groupId: String): GroupInvitationResource =
        GroupInvitationResource(session, groupId)

    @Path("api/groups/{groupId}/members")
    fun groupMembers(@PathParam("groupId") groupId: String): GroupMemberResource =
        GroupMemberResource(session, groupId)

    @Path("api/me/groups")
    fun myGroups(): UserGroupsResource = UserGroupsResource(session)

    @Path("api/config")
    fun config(): ConfigResource = ConfigResource(session)

    @Path("api/roles")
    fun roles(): RolesResource = RolesResource(session)

    // ---------- top-level user-facing endpoints ----------

    /** Email-linked invitation accept flow. Kept at the top level so the URLs
     *  in already-sent invitation emails do not break. */
    @Path("invitations/accept")
    fun invitationAccept(): InvitationAcceptResource = InvitationAcceptResource(session)

    /** Bundled admin SPA. Static files; SPA does its own OIDC. */
    @Path("admin")
    fun adminUi(): UiResource = UiResource(session)
}

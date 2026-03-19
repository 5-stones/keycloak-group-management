package com.weare5stones.keycloak.groupmgmt.rest

import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AppAuthManager
import org.keycloak.services.cors.Cors

class GroupMgmtResource(private val session: KeycloakSession) {

    private val auth: AppAuthManager.BearerTokenAuthenticator =
        AppAuthManager.BearerTokenAuthenticator(session)

    @OPTIONS
    @Path("{any:.*}")
    fun preflight(): Response {
        return Cors.builder().preflight().auth().add(Response.ok())
    }

    @Path("groups/{groupId}/invitations")
    fun groupInvitations(@PathParam("groupId") groupId: String): GroupInvitationResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return GroupInvitationResource(session, authResult, groupId)
    }

    @Path("groups/{groupId}/members")
    fun groupMembers(@PathParam("groupId") groupId: String): GroupMemberResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return GroupMemberResource(session, authResult, groupId)
    }

    @Path("me/groups")
    fun myGroups(): UserGroupsResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return UserGroupsResource(session, authResult)
    }

    @Path("invitations/accept")
    fun invitationAccept(): InvitationAcceptResource {
        return InvitationAcceptResource(session)
    }

    @Path("config")
    fun config(): ConfigResource {
        val authResult = auth.authenticate() ?: throw NotAuthorizedException("Bearer")
        return ConfigResource(session, authResult)
    }
}

package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.InvitationService
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AppAuthManager
import org.keycloak.services.managers.AuthenticationManager
import java.net.URI
import java.net.URLEncoder

class InvitationAcceptResource(
    private val session: KeycloakSession
) {

    companion object {
        const val CLIENT_ID = "group-mgmt"
        const val POST_ACCEPT_URL_ATTR = "group-mgmt-post-accept-url"
    }

    private val invitationService = InvitationService(session)
    private val realm = session.getContext().realm

    /**
     * Browser flow: user clicks link from email.
     * If not authenticated, redirect to Keycloak login page.
     * If authenticated, accept and redirect to configured URL or show HTML fallback.
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    fun acceptViaGet(@QueryParam("token") token: String?): Response {
        if (token.isNullOrBlank()) {
            return respondWithError("invalid_token", "No invitation token was provided.")
        }

        val authResult = AuthenticationManager.authenticateIdentityCookie(session, realm, true)

        if (authResult == null) {
            return redirectToLogin(token)
        }

        return processAcceptance(token, authResult)
    }

    /**
     * API flow: authenticated POST with bearer token (for frontend apps).
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    fun acceptViaPost(@QueryParam("token") token: String?): Response {
        val authResult = AppAuthManager.BearerTokenAuthenticator(session)
            .authenticate()
            ?: return Response.status(Response.Status.UNAUTHORIZED)
                .entity(mapOf("error" to "Authentication required"))
                .build()

        if (token.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Token is required"))
                .withCors(authResult)
        }

        val invitation = invitationService.findByToken(token)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "Invalid or expired invitation"))
                .withCors(authResult)

        val userEmail = authResult.user.email?.lowercase()?.trim()
        if (userEmail != invitation.email) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(mapOf("error" to "This invitation was not sent to your email address"))
                .withCors(authResult)
        }

        val group = session.groups().getGroupById(realm, invitation.groupId)

        val accepted = invitationService.accept(token, authResult.user.id)
            ?: return Response.status(Response.Status.GONE)
                .entity(mapOf("error" to "Invitation has expired"))
                .withCors(authResult)

        return Response.ok(mapOf(
            "message" to "Invitation accepted successfully",
            "groupId" to accepted.groupId,
            "groupName" to (group?.name ?: "")
        )).withCors(authResult)
    }

    private fun processAcceptance(token: String, authResult: AuthenticationManager.AuthResult): Response {
        val invitation = invitationService.findByToken(token)
            ?: return respondWithError("not_found",
                "This invitation is invalid, has already been accepted, or has expired.")

        val userEmail = authResult.user.email?.lowercase()?.trim()
        if (userEmail != invitation.email) {
            return respondWithError("wrong_account",
                "This invitation was sent to ${invitation.email}, " +
                    "but you are logged in as ${userEmail ?: "unknown"}.")
        }

        val group = session.groups().getGroupById(realm, invitation.groupId)
        val groupName = group?.name ?: ""

        val accepted = invitationService.accept(token, authResult.user.id)
            ?: return respondWithError("expired",
                "This invitation has expired. Please request a new one.")

        return respondWithSuccess(accepted.groupId, groupName)
    }

    /**
     * On success: redirect to configured URL with status params, or show HTML fallback.
     */
    private fun respondWithSuccess(groupId: String, groupName: String): Response {
        val postAcceptUrl = realm.getAttribute(POST_ACCEPT_URL_ATTR)
        if (postAcceptUrl != null) {
            val url = buildRedirectUrl(postAcceptUrl, mapOf(
                "result" to "success",
                "group_id" to groupId,
                "group_name" to groupName
            ))
            return Response.temporaryRedirect(URI.create(url)).build()
        }
        return htmlResponse("Invitation Accepted",
            "You have successfully joined <strong>${groupName.ifEmpty { "the group" }}</strong>.")
    }

    /**
     * On error: redirect to configured URL with error params, or show HTML fallback.
     */
    private fun respondWithError(error: String, description: String): Response {
        val postAcceptUrl = realm.getAttribute(POST_ACCEPT_URL_ATTR)
        if (postAcceptUrl != null) {
            val url = buildRedirectUrl(postAcceptUrl, mapOf(
                "result" to "error",
                "error" to error,
                "error_description" to description
            ))
            return Response.temporaryRedirect(URI.create(url)).build()
        }
        return htmlResponse(
            error.replace("_", " ").replaceFirstChar { it.uppercase() },
            description,
            error = true
        )
    }

    private fun buildRedirectUrl(baseUrl: String, params: Map<String, String>): String {
        val separator = if (baseUrl.contains("?")) "&" else "?"
        val query = params.entries.joinToString("&") { (k, v) ->
            "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
        }
        return "$baseUrl$separator$query"
    }

    private fun redirectToLogin(token: String): Response {
        val baseUrl = session.getContext().uri.baseUri.toString().removeSuffix("/")
        val acceptUrl = "$baseUrl/realms/${realm.name}/group-mgmt/invitations/accept?token=${URLEncoder.encode(token, "UTF-8")}"
        val loginUrl = "$baseUrl/realms/${realm.name}/protocol/openid-connect/auth" +
            "?client_id=$CLIENT_ID" +
            "&response_type=none" +
            "&scope=openid" +
            "&redirect_uri=${URLEncoder.encode(acceptUrl, "UTF-8")}"
        return Response.temporaryRedirect(URI.create(loginUrl)).build()
    }

    private fun htmlResponse(title: String, message: String, error: Boolean = false): Response {
        val color = if (error) "#d9534f" else "#5cb85c"
        val html = """
            |<!DOCTYPE html>
            |<html>
            |<head><title>$title</title></head>
            |<body style="font-family: system-ui, sans-serif; display: flex; justify-content: center; padding-top: 80px;">
            |  <div style="max-width: 500px; text-align: center;">
            |    <h1 style="color: $color;">$title</h1>
            |    <p style="font-size: 16px; line-height: 1.6;">$message</p>
            |  </div>
            |</body>
            |</html>
        """.trimMargin()
        val status = if (error) Response.Status.BAD_REQUEST else Response.Status.OK
        return Response.status(status).type(MediaType.TEXT_HTML).entity(html).build()
    }
}

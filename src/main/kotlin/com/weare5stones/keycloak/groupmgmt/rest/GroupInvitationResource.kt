package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import com.weare5stones.keycloak.groupmgmt.service.InvitationAlreadyExistsException
import com.weare5stones.keycloak.groupmgmt.service.InvitationEmailService
import com.weare5stones.keycloak.groupmgmt.service.InvitationService
import com.weare5stones.keycloak.groupmgmt.util.pagedResponse
import com.weare5stones.keycloak.groupmgmt.util.paginationParams
import com.weare5stones.keycloak.groupmgmt.util.toIsoString
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.DefaultValue
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.NotAuthorizedException
import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.OPTIONS
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.QueryParam
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.jboss.logging.Logger
import org.keycloak.models.KeycloakSession
import org.keycloak.services.cors.Cors
import org.keycloak.services.managers.AppAuthManager
import org.keycloak.services.managers.AuthenticationManager
import java.net.URI

class GroupInvitationResource(
    private val session: KeycloakSession,
    private val groupId: String
) {

    companion object {
        private val logger = Logger.getLogger(GroupInvitationResource::class.java)
    }

    private val tokenAuth = AppAuthManager.BearerTokenAuthenticator(session)
    private val realm = session.getContext().realm
    private val invitationService = InvitationService(session)
    private val emailService = InvitationEmailService(session)

    private fun authenticate(): AuthenticationManager.AuthResult =
        tokenAuth.authenticate() ?: throw NotAuthorizedException("Bearer")

    private fun buildAcceptUrl(token: String): String {
        val baseUrl = session.getContext().uri.baseUri.toString().removeSuffix("/")
        return "$baseUrl/realms/${realm.name}/group-mgmt/invitations/accept?token=$token"
    }

    private fun getInviterDisplayName(auth: AuthenticationManager.AuthResult): String {
        return auth.user.let { "${it.firstName ?: ""} ${it.lastName ?: ""}".trim() }
            .ifEmpty { auth.user.username }
    }

    @OPTIONS
    @Path("{any:.*}")
    fun preflight(): Response =
        Cors.builder().preflight().auth().add(Response.ok())

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun createInvitation(body: Map<String, Any?>): Response {
        val auth = authenticate()
        val group = GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_INVITATIONS_WRITE)

        logger.infof("createInvitation: body=%s", body)

        val email = (body["email"] as? String)?.trim()?.takeIf { it.isNotBlank() }
            ?: return errorResponse(Response.Status.BAD_REQUEST, "email is required", auth)

        val ttlHours = (body["ttlHours"] as? Number)?.toLong()
        val rolesInput = body["roles"]
        val roles: List<String> = when (rolesInput) {
            null -> emptyList()
            is List<*> -> rolesInput.map { it?.toString() ?: "" }
            else -> return errorResponse(Response.Status.BAD_REQUEST, "'roles' must be an array of strings", auth)
        }

        // Privilege-escalation guard: the inviter cannot grant roles whose permissions
        // exceed their own. Validated up-front so we don't create an invitation that
        // would be rejected on accept.
        GroupRoleService.assertCanGrantRoles(session, realm, group, auth.user, roles)

        val invitation = try {
            invitationService.create(
                realmId = realm.id,
                groupId = groupId,
                email = email,
                inviterUserId = auth.user.id,
                roles = roles,
                ttlHours = ttlHours
            )
        } catch (e: IllegalArgumentException) {
            return errorResponse(Response.Status.BAD_REQUEST, e.message ?: "Invalid request", auth)
        } catch (e: IllegalStateException) {
            return errorResponse(Response.Status.CONFLICT, e.message ?: "Conflict", auth)
        } catch (e: InvitationAlreadyExistsException) {
            return errorResponse(Response.Status.CONFLICT, e.message ?: "Conflict", auth)
        }

        val inviterName = getInviterDisplayName(auth)
        logger.infof("createInvitation: sending email to '%s', inviterName='%s', groupName='%s'", invitation.email, inviterName, group.name)

        emailService.sendInvitationEmail(
            realm = realm,
            recipientEmail = invitation.email,
            inviterName = inviterName,
            groupName = group.name,
            acceptUrl = buildAcceptUrl(invitation.token),
            expiresAt = invitation.expiresAt.toIsoString()
        )

        return Response.created(URI.create(invitation.id))
            .entity(invitation.toMap(group.name))
            .withCors(auth)
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun listInvitations(
        @QueryParam("page") @DefaultValue("1") page: Int,
        @QueryParam("pageSize") @DefaultValue("20") pageSize: Int
    ): Response {
        val auth = authenticate()
        val group = GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_INVITATIONS_READ)
        val params = paginationParams(page, pageSize)
        val all = invitationService.findByGroup(realm.id, groupId)
        val paged = pagedResponse(
            data = all.drop(params.offset).take(params.pageSize).map { it.toMap(group.name) },
            totalCount = all.size.toLong(),
            params = params
        )
        return Response.ok(paged).withCors(auth)
    }

    @GET
    @Path("{invitationId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun getInvitation(@PathParam("invitationId") invitationId: String): Response {
        val auth = authenticate()
        val group = GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_INVITATIONS_READ)
        val invitation = invitationService.findById(invitationId)
            ?: throw NotFoundException("Invitation not found")
        if (invitation.groupId != groupId) throw NotFoundException("Invitation not found")
        return Response.ok(invitation.toMap(group.name)).withCors(auth)
    }

    @POST
    @Path("{invitationId}/resend")
    @Produces(MediaType.APPLICATION_JSON)
    fun resendInvitation(@PathParam("invitationId") invitationId: String): Response {
        val auth = authenticate()
        val group = GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_INVITATIONS_WRITE)
        val invitation = invitationService.findById(invitationId)
            ?: throw NotFoundException("Invitation not found")
        if (invitation.groupId != groupId) throw NotFoundException("Invitation not found")

        if (invitation.expiresAt < System.currentTimeMillis()) {
            return errorResponse(Response.Status.GONE, "Invitation has expired", auth)
        }

        emailService.sendInvitationEmail(
            realm = realm,
            recipientEmail = invitation.email,
            inviterName = getInviterDisplayName(auth),
            groupName = group.name,
            acceptUrl = buildAcceptUrl(invitation.token),
            expiresAt = invitation.expiresAt.toIsoString()
        )

        return Response.ok(invitation.toMap(group.name)).withCors(auth)
    }

    @DELETE
    @Path("{invitationId}")
    @Produces(MediaType.APPLICATION_JSON)
    fun deleteInvitation(@PathParam("invitationId") invitationId: String): Response {
        val auth = authenticate()
        GroupRoleService.requirePermission(session, realm, groupId, auth.user, GroupRoleService.PERM_INVITATIONS_WRITE)
        val invitation = invitationService.findById(invitationId)
            ?: throw NotFoundException("Invitation not found")
        if (invitation.groupId != groupId) throw NotFoundException("Invitation not found")
        invitationService.delete(invitationId)
        return Response.noContent().withCors(auth)
    }
}

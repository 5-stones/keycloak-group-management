package com.weare5stones.keycloak.groupmgmt.rest

import com.weare5stones.keycloak.groupmgmt.service.GroupAdminService
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession
import org.keycloak.services.managers.AuthenticationManager

class ConfigResource(
    private val session: KeycloakSession,
    private val auth: AuthenticationManager.AuthResult
) {

    companion object {
        val CONFIG_KEYS = listOf(
            "group-mgmt-post-accept-url",
            "group-invitation-ttl-hours"
        )
    }

    private val realm = session.getContext().realm

    private fun requireRealmAdmin() {
        if (!GroupAdminService.isRealmAdmin(session, realm, auth.user)) {
            throw ForbiddenException("Realm admin access required")
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    fun getConfig(): Response {
        requireRealmAdmin()

        val config = CONFIG_KEYS.associateWith { key ->
            realm.getAttribute(key)
        }

        return Response.ok(config).withCors(auth)
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    fun updateConfig(body: Map<String, String?>): Response {
        requireRealmAdmin()

        for ((key, value) in body) {
            if (key !in CONFIG_KEYS) continue
            if (value.isNullOrBlank()) {
                realm.removeAttribute(key)
            } else {
                realm.setAttribute(key, value)
            }
        }

        val config = CONFIG_KEYS.associateWith { key ->
            realm.getAttribute(key)
        }

        return Response.ok(config).withCors(auth)
    }
}

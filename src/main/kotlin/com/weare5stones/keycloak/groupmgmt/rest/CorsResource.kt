package com.weare5stones.keycloak.groupmgmt.rest

import jakarta.ws.rs.core.Response
import org.keycloak.services.cors.Cors
import org.keycloak.services.managers.AuthenticationManager

fun Response.ResponseBuilder.withCors(auth: AuthenticationManager.AuthResult): Response {
    return Cors.builder()
        .auth()
        .allowedOrigins(auth.token)
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .exposedHeaders("Location")
        .add(this)
}

fun errorResponse(status: Response.Status, message: String, auth: AuthenticationManager.AuthResult): Response {
    return Response.status(status)
        .entity(mapOf("error" to message))
        .withCors(auth)
}

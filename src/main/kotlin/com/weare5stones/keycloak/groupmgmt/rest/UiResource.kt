package com.weare5stones.keycloak.groupmgmt.rest

import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Response
import org.keycloak.models.KeycloakSession

/**
 * Serves the bundled admin SPA (`admin/` Vite output) from the plugin JAR.
 *
 * - Bundle layout in the JAR: `admin-ui/index.html` plus `admin-ui/assets/` files.
 * - Mounted at `/realms/{realm}/group-mgmt/config/...`.
 * - Files (anything containing a `.` after the last `/`) are served as-is.
 * - Any other path returns `index.html` so React Router can handle SPA routing on
 *   direct navigation / refresh.
 *
 * No auth gate at the file level — these are static assets. The SPA does its own
 * OIDC login and calls authenticated endpoints under `/api/...` once loaded.
 *
 * If the bundle was not packaged into the JAR (operator chose backend-only build),
 * each request returns 404 with a hint pointing at the build instructions.
 */
class UiResource(private val session: KeycloakSession) {

    @GET
    @Path("{path:.*}")
    fun serve(@PathParam("path") path: String): Response {
        val resourcePath = resolveResourcePath(path)
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
            ?: return notFound(resourcePath)

        // index.html gets a `<base href>` injected pointing at the SPA root for the
        // current realm. This lets the browser resolve relative asset URLs correctly
        // regardless of how deep the current SPA route is (e.g. /admin/config refreshes).
        if (resourcePath == "admin-ui/index.html") {
            val realm = session.context.realm.name
            val basePath = "/realms/$realm/group-mgmt/config/"
            val html = stream.bufferedReader().use { it.readText() }
            val withBase = injectBaseHref(html, basePath)
            return Response.ok(withBase)
                .type("text/html;charset=utf-8")
                .header("X-Content-Type-Options", "nosniff")
                .build()
        }

        return Response.ok(stream)
            .type(guessMediaType(resourcePath))
            .header("X-Content-Type-Options", "nosniff")
            .build()
    }

    private fun injectBaseHref(html: String, basePath: String): String {
        val baseTag = "<base href=\"$basePath\">"
        // Insert immediately after the opening <head> tag (case-insensitive on tag name
        // but Vite's output uses lowercase, so a literal match is reliable).
        return html.replaceFirst("<head>", "<head>$baseTag")
    }

    private fun resolveResourcePath(path: String): String {
        val safe = path.trimStart('/').takeIf { it.isNotEmpty() } ?: return "admin-ui/index.html"
        // Reject path traversal early.
        if (safe.contains("..")) return "admin-ui/index.html"
        // Anything that doesn't look like a file (no extension on the last segment)
        // is treated as an SPA route → serve index.html.
        val lastSegment = safe.substringAfterLast('/')
        return if (lastSegment.contains('.')) "admin-ui/$safe" else "admin-ui/index.html"
    }

    private fun guessMediaType(resourcePath: String): String = when {
        resourcePath.endsWith(".html") -> "text/html;charset=utf-8"
        resourcePath.endsWith(".js") -> "application/javascript;charset=utf-8"
        resourcePath.endsWith(".mjs") -> "application/javascript;charset=utf-8"
        resourcePath.endsWith(".css") -> "text/css;charset=utf-8"
        resourcePath.endsWith(".json") -> "application/json;charset=utf-8"
        resourcePath.endsWith(".svg") -> "image/svg+xml"
        resourcePath.endsWith(".png") -> "image/png"
        resourcePath.endsWith(".jpg") || resourcePath.endsWith(".jpeg") -> "image/jpeg"
        resourcePath.endsWith(".ico") -> "image/x-icon"
        resourcePath.endsWith(".woff") -> "font/woff"
        resourcePath.endsWith(".woff2") -> "font/woff2"
        resourcePath.endsWith(".map") -> "application/json;charset=utf-8"
        else -> "application/octet-stream"
    }

    private fun notFound(resourcePath: String): Response {
        val body = """
            |<!DOCTYPE html>
            |<html><head><title>Admin UI not bundled</title></head>
            |<body style="font-family: system-ui, sans-serif; max-width: 600px; margin: 60px auto; padding: 20px;">
            |  <h1>Admin UI not packaged in this JAR</h1>
            |  <p>Requested resource: <code>$resourcePath</code></p>
            |  <p>To bundle the SPA into the plugin JAR, run:</p>
            |  <pre style="background:#f5f5f5;padding:10px;border-radius:4px;">./gradlew bundleAdminUi build</pre>
            |  <p>Then redeploy the JAR. See README for details.</p>
            |</body></html>
        """.trimMargin()
        return Response.status(Response.Status.NOT_FOUND)
            .type("text/html;charset=utf-8")
            .entity(body)
            .build()
    }
}

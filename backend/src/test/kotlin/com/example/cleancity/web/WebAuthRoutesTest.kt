package com.example.cleancity.web

import com.example.cleancity.plugins.SecurityHeaders
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebAuthRoutesTest {

    private fun app(block: suspend (HttpClient) -> Unit) = testApplication {
        application { routing { webAuthRoutes() } }
        block(client)
    }

    // The strict, app-wide CSP from SecurityHeaders blocks inline <script> and fetch.
    // These pages depend on both, so they MUST carry their own relaxed CSP and the
    // strict one must be absent (browsers enforce the intersection of multiple CSP
    // headers — a second strict header would silently re-block the script).
    private val STRICT_API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"

    @Test
    fun `email-landing pages keep a CSP that allows their inline script`() = testApplication {
        application {
            install(SecurityHeaders)
            routing { webAuthRoutes() }
        }
        for (path in listOf("/verify-email", "/reset-password", "/accept-invite")) {
            val r = client.get("$path?token=tok123")
            val csps = r.headers.getAll("Content-Security-Policy") ?: emptyList()
            assertTrue(csps.isNotEmpty(), "$path must carry a CSP")
            assertTrue(
                csps.any { it.contains("script-src 'unsafe-inline'") },
                "$path must allow its inline button handler, was: $csps"
            )
            assertTrue(
                csps.none { it == STRICT_API_CSP },
                "$path must not also carry the strict API CSP (intersection would block the script), was: $csps"
            )
        }
    }

    @Test
    fun `verify-email page renders token and posts to auth endpoint`() = app { client ->
        val r = client.get("/verify-email?token=abc123def")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("abc123def"), "token must be embedded")
        assertTrue(body.contains("/auth/verify-email"), "must POST to verify endpoint")
        assertTrue(body.contains("Подтвердить email"))
    }

    @Test
    fun `reset-password page renders form posting to reset endpoint`() = app { client ->
        val r = client.get("/reset-password?token=tok123")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("/auth/reset-password"))
        assertTrue(body.contains("tok123"))
    }

    @Test
    fun `accept-invite page renders form posting to invite endpoint`() = app { client ->
        val r = client.get("/accept-invite?token=inv999")
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue(body.contains("/auth/admin/accept-invite"))
        assertTrue(body.contains("inv999"))
    }

    @Test
    fun `missing token returns 400 error page`() = app { client ->
        val r = client.get("/verify-email")
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertTrue(r.bodyAsText().contains("повреждена"))
    }

    @Test
    fun `malformed token returns 400`() = app { client ->
        val r = client.get("/verify-email?token=%3Cscript%3E")
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }
}

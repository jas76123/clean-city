package com.example.cleancity.plugins

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecurityHeadersTest {

    private fun assertHasSecurityHeaders(headers: io.ktor.http.Headers) {
        val hsts = headers["Strict-Transport-Security"]
        assertNotNull(hsts, "HSTS header must be present")
        assertTrue(hsts!!.contains("max-age="), "HSTS must include max-age")
        assertTrue(hsts.contains("includeSubDomains"), "HSTS must include includeSubDomains")
        assertTrue(hsts.contains("preload"), "HSTS must include preload directive")

        val csp = headers["Content-Security-Policy"]
        assertNotNull(csp, "CSP header must be present")
        assertTrue(csp!!.contains("default-src 'none'"), "CSP for API must be deny-by-default")
        assertTrue(csp.contains("frame-ancestors 'none'"), "CSP must forbid framing")

        assertEquals("DENY", headers["X-Frame-Options"])
        assertEquals("nosniff", headers["X-Content-Type-Options"])
        assertEquals("no-referrer", headers["Referrer-Policy"])

        val pp = headers["Permissions-Policy"]
        assertNotNull(pp, "Permissions-Policy must be present")
        assertTrue(pp!!.contains("geolocation=()"), "Permissions-Policy must disable geolocation")
        assertTrue(pp.contains("camera=()"), "Permissions-Policy must disable camera")
        assertTrue(pp.contains("microphone=()"), "Permissions-Policy must disable microphone")
    }

    @Test
    fun `security headers present on 200 response`() = testApplication {
        application {
            install(SecurityHeaders)
            routing { get("/ok") { call.respondText("ok") } }
        }

        val resp = client.get("/ok")
        assertEquals(HttpStatusCode.OK, resp.status)
        assertHasSecurityHeaders(resp.headers)
    }

    @Test
    fun `security headers present on 404 response`() = testApplication {
        application {
            install(SecurityHeaders)
            routing { /* no routes */ }
        }

        val resp = client.get("/does-not-exist")
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertHasSecurityHeaders(resp.headers)
    }

    @Test
    fun `security headers present on 500 response handled by StatusPages`() = testApplication {
        application {
            install(SecurityHeaders)
            install(StatusPages) {
                exception<Throwable> { call, _ ->
                    call.respondText(text = "boom", status = HttpStatusCode.InternalServerError)
                }
            }
            routing { get("/boom") { error("boom") } }
        }

        val resp = client.get("/boom")
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
        assertHasSecurityHeaders(resp.headers)
    }
}

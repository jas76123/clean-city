package com.example.cleancity.web

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

// Tokens from createEmailToken are hex. Allow a safe whitelist of characters.
// Mirrors HtmlPages.SAFE_TOKEN (defense-in-depth): this is the primary gate (returns
// an error page), that is the secondary check (throws if a caller bypasses this gate).
private val TOKEN_RE = Regex("^[A-Za-z0-9._-]{1,512}$")

// HTML pages for email links. Registered at the ROOT routing (NOT under /auth),
// because AuthService builds links as baseUrl + "/verify-email", "/reset-password",
// "/accept-invite". Mutations are done by page JS via existing /auth/...-POST endpoints.
fun Route.webAuthRoutes() {
    get("/verify-email") {
        val token = call.request.queryParameters["token"]
        if (token == null || !TOKEN_RE.matches(token)) {
            call.respondText(HtmlPages.error(), ContentType.Text.Html, HttpStatusCode.BadRequest)
            return@get
        }
        call.respondText(HtmlPages.verifyEmail(token), ContentType.Text.Html, HttpStatusCode.OK)
    }
    get("/reset-password") {
        val token = call.request.queryParameters["token"]
        if (token == null || !TOKEN_RE.matches(token)) {
            call.respondText(HtmlPages.error(), ContentType.Text.Html, HttpStatusCode.BadRequest)
            return@get
        }
        call.respondText(HtmlPages.resetPassword(token), ContentType.Text.Html, HttpStatusCode.OK)
    }
    get("/accept-invite") {
        val token = call.request.queryParameters["token"]
        if (token == null || !TOKEN_RE.matches(token)) {
            call.respondText(HtmlPages.error(), ContentType.Text.Html, HttpStatusCode.BadRequest)
            return@get
        }
        call.respondText(HtmlPages.acceptInvite(token), ContentType.Text.Html, HttpStatusCode.OK)
    }
}

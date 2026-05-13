package com.example.cleancity.analytics

import com.example.cleancity.ForbiddenException
import com.example.cleancity.shared.models.AnalyticsPeriod
import com.example.cleancity.shared.models.MessageResponse
import com.example.cleancity.shared.models.UserRole
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

private val ADMIN_ROLES = setOf(UserRole.ADMIN, UserRole.OPERATOR, UserRole.INSPECTOR)

fun Route.analyticsRoutes(service: AnalyticsService) {
    authenticate("auth-jwt") {
        route("/analytics") {
            get("/overview") {
                if (!call.requireAdmin()) return@get
                call.respond(service.overview())
            }
            get("/by-category") {
                if (!call.requireAdmin()) return@get
                call.respond(service.byCategory(call.period()))
            }
            get("/by-district") {
                if (!call.requireAdmin()) return@get
                call.respond(service.byDistrict(call.period()))
            }
            get("/sla") {
                if (!call.requireAdmin()) return@get
                call.respond(service.sla(call.period()))
            }
            get("/votes-impact") {
                if (!call.requireAdmin()) return@get
                call.respond(service.votesImpact(call.period()))
            }
        }
    }
}

private fun ApplicationCall.period(): AnalyticsPeriod {
    val raw = request.queryParameters["period"]?.trim()?.uppercase() ?: return AnalyticsPeriod.ALL
    return runCatching { AnalyticsPeriod.valueOf(raw) }
        .getOrElse { throw IllegalArgumentException("Invalid period '$raw' (allowed: WEEK, MONTH, ALL)") }
}

private suspend fun ApplicationCall.requireAdmin(): Boolean {
    val principal = principal<JWTPrincipal>()
    val role = runCatching { UserRole.valueOf(principal!!.payload.getClaim("role").asString()) }.getOrNull()
    if (principal?.payload?.subject?.toLongOrNull() == null) {
        respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
        return false
    }
    if (role !in ADMIN_ROLES) throw ForbiddenException("Только админ имеет доступ к аналитике")
    return true
}

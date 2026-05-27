package com.example.cleancity.analytics

import com.example.cleancity.ForbiddenException
import com.example.cleancity.UnauthorizedException
import com.example.cleancity.shared.models.AnalyticsPeriod
import com.example.cleancity.shared.models.UserRole
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
                call.requireAdmin()
                call.respond(service.overview())
            }
            get("/operational") {
                call.requireAdmin()
                call.respond(service.operational())
            }
            get("/burning") {
                call.requireAdmin()
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull()
                    ?: AnalyticsConfig.BURNING_QUEUE_DEFAULT_LIMIT).coerceIn(1, 100)
                call.respond(service.burning(limit = limit))
            }
            get("/strategic") {
                call.requireAdmin()
                call.respond(service.strategic(call.period()))
            }
            get("/reopen") {
                call.requireAdmin()
                call.respond(service.reopen(call.period()))
            }
            get("/by-category") {
                call.requireAdmin()
                call.respond(service.byCategory(call.period()))
            }
            get("/by-district") {
                call.requireAdmin()
                call.respond(service.byDistrict(call.period()))
            }
            get("/sla") {
                call.requireAdmin()
                call.respond(service.sla(call.period()))
            }
            get("/votes-impact") {
                call.requireAdmin()
                call.respond(service.votesImpact(call.period()))
            }
            get("/trends") {
                call.requireAdmin()
                val groupByRaw = call.request.queryParameters["groupBy"]?.trim()?.lowercase() ?: "day"
                require(groupByRaw in setOf("day", "week", "month")) {
                    "Invalid groupBy '$groupByRaw' (allowed: day, week, month)"
                }
                call.respond(service.trendsRange(call.period(), groupBy = groupByRaw))
            }
        }
    }
}

private fun ApplicationCall.period(): AnalyticsPeriod {
    val raw = request.queryParameters["period"]?.trim()?.uppercase() ?: return AnalyticsPeriod.ALL
    return runCatching { AnalyticsPeriod.valueOf(raw) }
        .getOrElse { throw IllegalArgumentException("Invalid period '$raw' (allowed: WEEK, MONTH, QUARTER, YEAR, ALL)") }
}

private fun ApplicationCall.requireAdmin() {
    val principal = principal<JWTPrincipal>()
    if (principal?.payload?.subject?.toLongOrNull() == null) {
        throw UnauthorizedException("Not authenticated")
    }
    val role = runCatching { UserRole.valueOf(principal.payload.getClaim("role").asString()) }.getOrNull()
    if (role !in ADMIN_ROLES) throw ForbiddenException("Только админ имеет доступ к аналитике")
}

package com.example.cleancity.moderation

import com.example.cleancity.BadRequestException
import com.example.cleancity.ErrorCodes
import com.example.cleancity.ForbiddenException
import com.example.cleancity.NotFoundException
import com.example.cleancity.UnauthorizedException
import com.example.cleancity.auth.clientIp
import com.example.cleancity.auth.userAgentSafe
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.responses.admin.BanResidentRequest
import com.example.cleancity.shared.responses.admin.ModerationSummaryResponse
import com.example.cleancity.shared.responses.admin.WarnResidentRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.moderationRoutes(service: ModerationService) {
    route("/auth") {
        authenticate("auth-jwt") {

            get("/admin/residents/{id}/moderation") {
                requireStaff(call)
                val id = pathId(call)
                try {
                    val s = service.getSummary(id)
                    call.respond(
                        HttpStatusCode.OK,
                        ModerationSummaryResponse(s.rejectedCountSinceWarning, s.flagged, s.isWarned, s.isBanned)
                    )
                } catch (_: ResidentNotFoundException) {
                    throw NotFoundException("Пользователь не найден")
                }
            }

            post("/admin/residents/{id}/warn") {
                val actorId = requireStaff(call)
                val id = pathId(call)
                val req = call.receive<WarnResidentRequest>()
                try {
                    service.warn(actorId, id, req.complaintId, req.reason, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: Exception) { mapModerationError(e) }
            }

            post("/admin/residents/{id}/ban") {
                val actorId = requireStaff(call)
                val id = pathId(call)
                val req = call.receive<BanResidentRequest>()
                try {
                    service.ban(actorId, id, req.reason, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: Exception) { mapModerationError(e) }
            }

            post("/admin/residents/{id}/unban") {
                val actorId = requireStaff(call)
                val id = pathId(call)
                try {
                    service.unban(actorId, id, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.NoContent)
                } catch (_: ResidentNotFoundException) {
                    throw NotFoundException("Пользователь не найден")
                } catch (_: NotAResidentException) {
                    throw ForbiddenException("Это не житель", ErrorCodes.MODERATION_NOT_RESIDENT)
                }
            }
        }
    }
}

private fun mapModerationError(e: Exception): Nothing = when (e) {
    is ResidentNotFoundException -> throw NotFoundException("Пользователь не найден")
    is NotAResidentException -> throw ForbiddenException("Это не житель", ErrorCodes.MODERATION_NOT_RESIDENT)
    is ReasonRequiredException -> throw BadRequestException("Нужна причина", ErrorCodes.MODERATION_REASON_REQUIRED)
    else -> throw e
}

private fun requireStaff(call: ApplicationCall): Long {
    val principal = call.principal<JWTPrincipal>() ?: throw UnauthorizedException("Not authenticated")
    val role = runCatching {
        principal.payload.getClaim("role").asString()?.let { UserRole.valueOf(it) }
    }.getOrNull() ?: throw UnauthorizedException("Not authenticated")
    if (role != UserRole.ADMIN && role != UserRole.OPERATOR) {
        throw ForbiddenException("Только сотрудники", ErrorCodes.FORBIDDEN)
    }
    return principal.payload.subject?.toLongOrNull() ?: throw UnauthorizedException("Not authenticated")
}

private fun pathId(call: ApplicationCall): Long =
    call.parameters["id"]?.toLongOrNull()
        ?: throw BadRequestException("Invalid user id", ErrorCodes.VALIDATION_BAD_FIELD)

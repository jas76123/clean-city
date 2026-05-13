package com.example.cleancity.votes

import com.example.cleancity.BadRequestException
import com.example.cleancity.ErrorCodes
import com.example.cleancity.UnauthorizedException
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * POST/DELETE /complaints/{id}/votes — идемпотентное голосование.
 * Подключается рядом с complaintRoutes; путь начинается с /complaints,
 * чтобы JWT-конфиг и общая структура /complaints оставалась логически целой.
 */
fun Route.voteRoutes(service: VoteService) {
    route("/complaints/{id}/votes") {
        authenticate("auth-jwt") {

            post {
                val userId = call.requireUserId()
                val complaintId = call.complaintId()
                call.respond(service.addVote(complaintId, userId))
            }

            delete {
                val userId = call.requireUserId()
                val complaintId = call.complaintId()
                call.respond(service.removeVote(complaintId, userId))
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireUserId(): Long {
    return principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
        ?: throw UnauthorizedException("Not authenticated")
}

private fun io.ktor.server.application.ApplicationCall.complaintId(): Long {
    return parameters["id"]?.toLongOrNull()
        ?: throw BadRequestException("Invalid complaint id", ErrorCodes.VALIDATION_BAD_FIELD)
}

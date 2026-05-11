package com.example.cleancity.votes

import com.example.cleancity.shared.models.MessageResponse
import io.ktor.http.HttpStatusCode
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
                val userId = call.requireUserId() ?: return@post
                val complaintId = call.complaintId() ?: return@post
                call.respond(service.addVote(complaintId, userId))
            }

            delete {
                val userId = call.requireUserId() ?: return@delete
                val complaintId = call.complaintId() ?: return@delete
                call.respond(service.removeVote(complaintId, userId))
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireUserId(): Long? {
    val id = principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
    if (id == null) respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
    return id
}

private suspend fun io.ktor.server.application.ApplicationCall.complaintId(): Long? {
    val id = parameters["id"]?.toLongOrNull()
    if (id == null) respond(HttpStatusCode.BadRequest, MessageResponse("Invalid complaint id"))
    return id
}

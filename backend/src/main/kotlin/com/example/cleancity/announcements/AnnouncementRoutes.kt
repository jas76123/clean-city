package com.example.cleancity.announcements

import com.example.cleancity.complaints.Viewer
import com.example.cleancity.shared.models.MessageResponse
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.CreateAnnouncementRequest
import com.example.cleancity.shared.requests.UpdateAnnouncementRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.announcementRoutes(service: AnnouncementService) {
    route("/announcements") {

        authenticate("auth-jwt", optional = true) {
            get {
                val viewer = call.viewer()
                val district = call.request.queryParameters["district"]?.takeIf { it.isNotBlank() }
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 10).coerceIn(1, 100)
                call.respond(service.list(viewer, district, limit))
            }
        }

        authenticate("auth-jwt") {
            post {
                val actor = call.authenticated() ?: return@post
                val req = call.receive<CreateAnnouncementRequest>()
                call.respond(HttpStatusCode.Created, service.create(actor, req))
            }

            patch("/{id}") {
                val actor = call.authenticated() ?: return@patch
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@patch call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid id"))
                val req = call.receive<UpdateAnnouncementRequest>()
                call.respond(service.update(actor, id, req))
            }

            delete("/{id}") {
                val actor = call.authenticated() ?: return@delete
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid id"))
                service.expire(actor, id)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.viewer(): Viewer {
    val principal = principal<JWTPrincipal>() ?: return Viewer.Guest
    val userId = principal.payload.subject?.toLongOrNull() ?: return Viewer.Guest
    val role = runCatching { UserRole.valueOf(principal.payload.getClaim("role").asString()) }
        .getOrDefault(UserRole.RESIDENT)
    return Viewer.Authenticated(userId, role)
}

private suspend fun ApplicationCall.authenticated(): Viewer.Authenticated? {
    val v = viewer()
    if (v !is Viewer.Authenticated) {
        respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
        return null
    }
    return v
}

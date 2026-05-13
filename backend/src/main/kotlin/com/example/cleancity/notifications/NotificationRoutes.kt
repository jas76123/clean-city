package com.example.cleancity.notifications

import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.MessageResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.notificationRoutes(repo: NotificationRepository) {
    authenticate("auth-jwt") {
        route("/notifications") {

            get {
                val userId = call.userId() ?: return@get
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 100)
                val offset = (call.request.queryParameters["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)

                val (rows, total) = repo.listForUser(userId, limit, offset)
                val items = rows.map { row ->
                    NotificationResponse(
                        id = row.id,
                        kind = row.kind,
                        title = row.title,
                        body = row.body,
                        iconStyle = row.iconStyle,
                        complaintId = row.complaintId,
                        announcementId = row.announcementId,
                        readAt = row.readAt?.toString(),
                        createdAt = row.createdAt.toString()
                    )
                }
                call.respond(
                    NotificationListResponse(
                        items = items,
                        total = total,
                        hasMore = (offset.toLong() + items.size) < total
                    )
                )
            }

            get("/unread-count") {
                val userId = call.userId() ?: return@get
                call.respond(UnreadCountResponse(count = repo.countUnreadForUser(userId)))
            }

            patch("/read-all") {
                val userId = call.userId() ?: return@patch
                val count = repo.markAllRead(userId)
                call.respond(MarkAllReadResponse(markedCount = count))
            }

            patch("/{id}/read") {
                val userId = call.userId() ?: return@patch
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid id"))
                    return@patch
                }
                val ok = repo.markRead(notificationId = id, userId = userId)
                if (!ok) {
                    call.respond(HttpStatusCode.NotFound, MessageResponse("Notification not found"))
                    return@patch
                }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private suspend fun ApplicationCall.userId(): Long? {
    val sub = principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
    if (sub == null) {
        respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
        return null
    }
    return sub
}

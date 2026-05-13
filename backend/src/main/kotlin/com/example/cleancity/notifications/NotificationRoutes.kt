package com.example.cleancity.notifications

import com.example.cleancity.BadRequestException
import com.example.cleancity.ErrorCodes
import com.example.cleancity.NotFoundException
import com.example.cleancity.UnauthorizedException
import com.example.cleancity.shared.models.MarkAllReadResponse
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
                val userId = call.userId()
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
                val userId = call.userId()
                call.respond(UnreadCountResponse(count = repo.countUnreadForUser(userId)))
            }

            patch("/read-all") {
                val userId = call.userId()
                val count = repo.markAllRead(userId)
                call.respond(MarkAllReadResponse(markedCount = count))
            }

            patch("/{id}/read") {
                val userId = call.userId()
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: throw BadRequestException("Invalid id", ErrorCodes.VALIDATION_BAD_FIELD)
                val ok = repo.markRead(notificationId = id, userId = userId)
                if (!ok) throw NotFoundException("Notification not found")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private fun ApplicationCall.userId(): Long {
    return principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
        ?: throw UnauthorizedException("Not authenticated")
}

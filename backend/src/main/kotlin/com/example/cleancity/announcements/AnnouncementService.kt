package com.example.cleancity.announcements

import com.example.cleancity.ForbiddenException
import com.example.cleancity.NotFoundException
import com.example.cleancity.complaints.Viewer
import com.example.cleancity.notifications.NotificationService
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.shared.models.AnnouncementsListResponse
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.CreateAnnouncementRequest
import com.example.cleancity.shared.requests.UpdateAnnouncementRequest
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

class AnnouncementService(
    private val repo: AnnouncementRepository,
    private val notifications: NotificationService
) {
    companion object {
        private val ADMIN_ROLES = setOf(UserRole.ADMIN, UserRole.OPERATOR, UserRole.INSPECTOR)
        private const val MAX_TITLE = 300
        private const val MAX_BODY = 5000
    }

    fun list(viewer: Viewer, district: String?, limit: Int): AnnouncementsListResponse {
        val safeLimit = limit.coerceIn(1, 100)
        val (rows, total) = repo.listActive(district, safeLimit)
        return AnnouncementsListResponse(items = rows.map { it.toResponse() }, total = total)
    }

    fun get(id: Long): AnnouncementResponse =
        repo.findById(id)?.toResponse() ?: throw NotFoundException("Объявление не найдено")

    fun create(actor: Viewer.Authenticated, req: CreateAnnouncementRequest): AnnouncementResponse {
        if (actor.role !in ADMIN_ROLES) throw ForbiddenException("Только админ может публиковать объявления")
        val title = req.title.trim()
        require(title.isNotBlank()) { "Title is required" }
        require(title.length <= MAX_TITLE) { "Title too long (max $MAX_TITLE)" }
        val body = req.body.trim()
        require(body.isNotBlank()) { "Body is required" }
        require(body.length <= MAX_BODY) { "Body too long (max $MAX_BODY)" }
        val expiresAt = req.expiresAt?.let { parseTs(it) }
        // Срок в прошлом бессмысленен: объявление сразу истекло бы, а push жителям уже улетел.
        require(expiresAt == null || expiresAt.isAfter(OffsetDateTime.now())) {
            "expiresAt must be in the future"
        }
        val districts = req.districts.map { it.trim() }.filter { it.isNotBlank() }

        val newId = transaction {
            val id = repo.insert(
                title = title,
                body = body,
                iconStyle = req.iconStyle,
                category = req.category,
                districts = districts,
                authorId = actor.userId,
                expiresAt = expiresAt
            )
            val storedDistricts = repo.findById(id)?.districts.orEmpty()
            val recipients = repo.recipientIdsForDistricts(storedDistricts)
            if (recipients.isNotEmpty()) {
                notifications.notify(
                    recipientUserIds = recipients,
                    kind = NotificationKind.ANNOUNCEMENT,
                    title = title,
                    body = body,
                    iconStyle = req.iconStyle.name,
                    complaintId = null,
                    announcementId = id
                )
            }
            id
        }

        return repo.findById(newId)!!.toResponse()
    }

    fun update(actor: Viewer.Authenticated, id: Long, req: UpdateAnnouncementRequest): AnnouncementResponse {
        if (actor.role !in ADMIN_ROLES) throw ForbiddenException("Только админ может править объявления")
        val existing = repo.findById(id) ?: throw NotFoundException("Объявление не найдено")

        val title = req.title?.trim()?.also { require(it.isNotBlank() && it.length <= MAX_TITLE) { "Invalid title" } }
        val body = req.body?.trim()?.also { require(it.isNotBlank() && it.length <= MAX_BODY) { "Invalid body" } }
        val districts = req.districts?.map { it.trim() }?.filter { it.isNotBlank() }
        val expiresAtParsed = req.expiresAt?.let { if (it.isBlank()) null else parseTs(it) }
        val clearExpires = req.expiresAt?.isBlank() == true
        require(expiresAtParsed == null || expiresAtParsed.isAfter(OffsetDateTime.now())) {
            "expiresAt must be in the future"
        }

        val ok = repo.update(
            id = id,
            title = title,
            body = body,
            iconStyle = req.iconStyle,
            category = req.category,
            districts = districts,
            expiresAt = expiresAtParsed,
            clearExpiresAt = clearExpires
        )
        if (!ok) throw NotFoundException("Объявление не найдено")
        return repo.findById(id)!!.toResponse()
    }

    fun expire(actor: Viewer.Authenticated, id: Long) {
        if (actor.role !in ADMIN_ROLES) throw ForbiddenException("Только админ может снимать объявления")
        repo.findById(id) ?: throw NotFoundException("Объявление не найдено")
        repo.expire(id)
    }

    private fun parseTs(raw: String): OffsetDateTime = try {
        OffsetDateTime.parse(raw)
    } catch (e: Exception) {
        throw IllegalArgumentException("Invalid timestamp '$raw': must be ISO-8601 with offset", e)
    }

    private fun AnnouncementRow.toResponse() = AnnouncementResponse(
        id = id,
        title = title,
        body = body,
        iconStyle = iconStyle,
        category = category,
        districts = districts,
        authorId = authorId,
        publishedAt = publishedAt.toString(),
        expiresAt = expiresAt?.toString()
    )
}

package com.example.cleancity.notifications

import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.shared.models.NotificationKind
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class NotificationRow(
    val id: Long,
    val userId: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val iconStyle: String?,
    val complaintId: Long?,
    val announcementId: Long?,
    val readAt: OffsetDateTime?,
    val createdAt: OffsetDateTime
)

class NotificationRepository {

    fun insertBatch(
        userIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String?,
        complaintId: Long?,
        announcementId: Long?
    ) {
        if (userIds.isEmpty()) return
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Notifications.batchInsert(userIds) { uid ->
            this[Notifications.userId] = uid
            this[Notifications.kind] = kind.name
            this[Notifications.title] = title
            this[Notifications.body] = body
            this[Notifications.iconStyle] = iconStyle
            this[Notifications.complaintId] = complaintId
            this[Notifications.announcementId] = announcementId
            this[Notifications.createdAt] = now
        }
    }

    fun listForUser(userId: Long, limit: Int, offset: Int): Pair<List<NotificationRow>, Long> = transaction {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90)
        val total = Notifications.selectAll().where {
            (Notifications.userId eq userId) and (Notifications.createdAt greater cutoff)
        }.count()
        val items = Notifications.selectAll()
            .where { (Notifications.userId eq userId) and (Notifications.createdAt greater cutoff) }
            .orderBy(Notifications.createdAt to SortOrder.DESC)
            .limit(limit).offset(offset.toLong())
            .map { it.toRow() }
        items to total
    }

    fun countUnreadForUser(userId: Long): Long = transaction {
        val cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(90)
        Notifications.selectAll().where {
            (Notifications.userId eq userId) and
                Notifications.readAt.isNull() and
                (Notifications.createdAt greater cutoff)
        }.count()
    }

    fun markRead(notificationId: Long, userId: Long): Boolean = transaction {
        val exists = Notifications.selectAll().where {
            (Notifications.id eq notificationId) and (Notifications.userId eq userId)
        }.count() > 0
        if (!exists) return@transaction false

        Notifications.update({
            (Notifications.id eq notificationId) and
                (Notifications.userId eq userId) and
                Notifications.readAt.isNull()
        }) {
            it[Notifications.readAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
        true
    }

    fun markAllRead(userId: Long): Int = transaction {
        Notifications.update({
            (Notifications.userId eq userId) and Notifications.readAt.isNull()
        }) {
            it[Notifications.readAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    private fun ResultRow.toRow() = NotificationRow(
        id = this[Notifications.id],
        userId = this[Notifications.userId],
        kind = NotificationKind.valueOf(this[Notifications.kind]),
        title = this[Notifications.title],
        body = this[Notifications.body],
        iconStyle = this[Notifications.iconStyle],
        complaintId = this[Notifications.complaintId],
        announcementId = this[Notifications.announcementId],
        readAt = this[Notifications.readAt],
        createdAt = this[Notifications.createdAt]
    )
}

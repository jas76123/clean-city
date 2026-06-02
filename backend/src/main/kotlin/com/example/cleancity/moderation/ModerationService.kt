package com.example.cleancity.moderation

import com.example.cleancity.auth.AuditLogger
import com.example.cleancity.auth.NoopAuditLogger
import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.auth.UserRow
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.notifications.NotificationService
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.UserRole

class ResidentNotFoundException : RuntimeException()
class NotAResidentException : RuntimeException()
class ReasonRequiredException : RuntimeException()

data class ModerationSummary(
    val rejectedCountSinceWarning: Int,
    val flagged: Boolean,
    val isWarned: Boolean,
    val isBanned: Boolean,
)

class ModerationService(
    private val users: UserRepository,
    private val complaints: ComplaintRepository,
    private val tokens: TokenRepository,
    private val notifications: NotificationService,
    private val audit: AuditLogger = NoopAuditLogger,
) {
    companion object {
        const val REJECTED_FLAG_THRESHOLD = 3
    }

    fun getSummary(residentId: Long): ModerationSummary {
        val user = users.findById(residentId) ?: throw ResidentNotFoundException()
        val warnedAt = users.getWarnedAt(residentId)
        val count = complaints.countRejectedSince(residentId, warnedAt)
        return ModerationSummary(
            rejectedCountSinceWarning = count,
            flagged = count >= REJECTED_FLAG_THRESHOLD,
            isWarned = warnedAt != null,
            isBanned = !user.isActive,
        )
    }

    fun warn(actorId: Long, residentId: Long, complaintId: Long, reason: String, ip: String?, ua: String?) {
        val target = requireResident(residentId)
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) throw ReasonRequiredException()
        notifications.notify(
            recipientUserIds = listOf(target.id),
            kind = NotificationKind.MODERATION_WARNING,
            title = "Предупреждение модерации",
            body = cleanReason,
            iconStyle = "WARNING",
            complaintId = complaintId,
        )
        users.setWarnedAt(residentId)
        audit.log(AuditAction.RESIDENT_WARNED, actorId, "user", residentId.toString(), ip, ua, cleanReason)
    }

    fun ban(actorId: Long, residentId: Long, reason: String, ip: String?, ua: String?) {
        requireResident(residentId)
        val cleanReason = reason.trim()
        if (cleanReason.isEmpty()) throw ReasonRequiredException()
        users.setActive(residentId, false)
        tokens.revokeAllUserRefreshTokens(residentId)
        audit.log(AuditAction.RESIDENT_BANNED, actorId, "user", residentId.toString(), ip, ua, cleanReason)
    }

    fun unban(actorId: Long, residentId: Long, ip: String?, ua: String?) {
        requireResident(residentId)
        users.setActive(residentId, true)
        audit.log(AuditAction.RESIDENT_UNBANNED, actorId, "user", residentId.toString(), ip, ua)
    }

    private fun requireResident(residentId: Long): UserRow {
        val user = users.findById(residentId) ?: throw ResidentNotFoundException()
        if (user.role != UserRole.RESIDENT) throw NotAResidentException()
        return user
    }
}

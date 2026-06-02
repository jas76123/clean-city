package com.example.cleancity.moderation

import com.example.cleancity.auth.AuditLogger
import com.example.cleancity.auth.NoopAuditLogger
import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.auth.UserRow
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.notifications.NotificationService
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

    @Suppress("unused")
    private fun requireResident(residentId: Long): UserRow {
        val user = users.findById(residentId) ?: throw ResidentNotFoundException()
        if (user.role != UserRole.RESIDENT) throw NotAResidentException()
        return user
    }
}

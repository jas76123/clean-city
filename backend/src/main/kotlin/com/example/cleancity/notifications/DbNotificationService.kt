package com.example.cleancity.notifications

import com.example.cleancity.shared.models.NotificationKind
import org.slf4j.LoggerFactory

class DbNotificationService(
    private val repository: NotificationRepository
) : NotificationService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun notify(
        recipientUserIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String?,
        complaintId: Long?,
        announcementId: Long?
    ) {
        if (recipientUserIds.isEmpty()) return
        val unique = recipientUserIds.distinct()
        repository.insertBatch(
            userIds = unique,
            kind = kind,
            title = title,
            body = body,
            iconStyle = iconStyle,
            complaintId = complaintId,
            announcementId = announcementId
        )
        log.info(
            "Notified {} users (deduped from {}): kind={} complaintId={} announcementId={}",
            unique.size, recipientUserIds.size, kind, complaintId, announcementId
        )
    }
}

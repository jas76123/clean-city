package com.example.cleancity.notifications

import com.example.cleancity.shared.models.ComplaintStatus
import org.slf4j.LoggerFactory

/**
 * Точка вызова уведомлений из бизнес-логики. На Day 5 — заглушка (NoopNotificationService).
 * На Day 6 заменяется на FcmNotificationService + INSERT в таблицу notifications.
 *
 * SPEC §5.2: при смене статуса
 *   - IN_PROGRESS / RESOLVED → автор
 *   - REJECTED / DUPLICATE → автор + все, кто проголосовал «+1»
 */
interface NotificationService {

    /**
     * Уведомить о смене статуса жалобы.
     * @param recipientUserIds список user_id, кому нужно отправить (бизнес-логика
     *   уже учла автора и поддержавших согласно SPEC §5.2).
     */
    fun notifyStatusChange(
        complaintId: Long,
        complaintTitle: String,
        toStatus: ComplaintStatus,
        comment: String,
        recipientUserIds: List<Long>
    )
}

/**
 * Заглушка Day 5: пишет в DEBUG-лог и ничего больше не делает.
 * На Day 6 заменяется на полноценную реализацию.
 */
class NoopNotificationService : NotificationService {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun notifyStatusChange(
        complaintId: Long,
        complaintTitle: String,
        toStatus: ComplaintStatus,
        comment: String,
        recipientUserIds: List<Long>
    ) {
        log.debug(
            "Notification stub: complaint={} title='{}' → {} recipients={}",
            complaintId, complaintTitle, toStatus, recipientUserIds.size
        )
    }
}

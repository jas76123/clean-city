package com.example.cleancity.notifications

import com.example.cleancity.shared.models.ComplaintStatus

object NotificationTexts {

    data class StatusChangeText(
        val title: String,
        val body: String,
        val iconStyle: String
    )

    fun statusChange(
        complaintTitle: String,
        toStatus: ComplaintStatus,
        adminComment: String
    ): StatusChangeText = when (toStatus) {
        ComplaintStatus.IN_PROGRESS -> StatusChangeText(
            title = "Ваша жалоба принята в работу",
            body = "«$complaintTitle» — в работе. $adminComment",
            iconStyle = "INFO"
        )
        ComplaintStatus.RESOLVED -> StatusChangeText(
            title = "Ваша жалоба решена",
            body = "«$complaintTitle» — решена. $adminComment",
            iconStyle = "SUCCESS"
        )
        ComplaintStatus.REJECTED -> StatusChangeText(
            title = "Жалоба отклонена",
            body = "«$complaintTitle» закрыта со статусом «Отклонена». " +
                "Комментарий муниципальных служб: $adminComment",
            iconStyle = "WARNING"
        )
        ComplaintStatus.DUPLICATE -> StatusChangeText(
            title = "Жалоба отмечена как дубликат",
            body = "«$complaintTitle» закрыта со статусом «Дубликат». " +
                "Комментарий муниципальных служб: $adminComment",
            iconStyle = "WARNING"
        )
        ComplaintStatus.NEW -> error("NEW не триггерит уведомление о смене статуса")
    }
}

package com.example.cleancity.notifications

import com.example.cleancity.shared.models.NotificationKind

interface NotificationService {

    fun notify(
        recipientUserIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String? = null,
        complaintId: Long? = null,
        announcementId: Long? = null
    )
}

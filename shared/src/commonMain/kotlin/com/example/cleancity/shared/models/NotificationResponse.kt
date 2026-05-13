package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class NotificationResponse(
    val id: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val iconStyle: String? = null,
    val complaintId: Long? = null,
    val announcementId: Long? = null,
    val readAt: String? = null,
    val createdAt: String
)

@Serializable
data class NotificationListResponse(
    val items: List<NotificationResponse>,
    val total: Long,
    val hasMore: Boolean
)

@Serializable
data class UnreadCountResponse(val count: Long)

@Serializable
data class MarkAllReadResponse(val markedCount: Int)

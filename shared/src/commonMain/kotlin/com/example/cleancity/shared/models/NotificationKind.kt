package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationKind {
    COMPLAINT_STATUS,
    ANNOUNCEMENT
}

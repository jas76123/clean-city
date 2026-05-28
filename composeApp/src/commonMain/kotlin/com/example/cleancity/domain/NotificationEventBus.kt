package com.example.cleancity.domain

import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Однонаправленная шина для свежих ANNOUNCEMENT-уведомлений, замеченных
 * polling-циклом. Подписчики: AnnouncementInAppBanner (foreground),
 * AnnouncementBusBridge (background → системная шторка).
 */
class NotificationEventBus {
    private val _newAnnouncements = MutableSharedFlow<NotificationResponse>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val newAnnouncements: SharedFlow<NotificationResponse> = _newAnnouncements

    suspend fun emit(notification: NotificationResponse) {
        _newAnnouncements.emit(notification)
    }
}

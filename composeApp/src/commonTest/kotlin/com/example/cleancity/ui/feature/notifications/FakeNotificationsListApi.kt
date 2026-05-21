package com.example.cleancity.ui.feature.notifications

import com.example.cleancity.data.network.NotificationsApiContract
import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse

class FakeNotificationsListApi : NotificationsApiContract {
    var nextListResult: Result<NotificationListResponse> =
        Result.success(NotificationListResponse(items = emptyList(), total = 0, hasMore = false))
    var nextMarkAllResult: Result<MarkAllReadResponse> =
        Result.success(MarkAllReadResponse(markedCount = 0))
    var markReadShouldThrow: Boolean = false

    val markReadCalls = mutableListOf<Long>()
    var markAllReadCalls: Int = 0
        private set

    override suspend fun unreadCount(): UnreadCountResponse = UnreadCountResponse(count = 0)

    override suspend fun list(limit: Int): NotificationListResponse =
        nextListResult.getOrThrow()

    override suspend fun markRead(id: Long) {
        markReadCalls += id
        if (markReadShouldThrow) throw RuntimeException("network error")
    }

    override suspend fun markAllRead(): MarkAllReadResponse {
        markAllReadCalls += 1
        return nextMarkAllResult.getOrThrow()
    }
}

package com.example.cleancity.data.network

import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import kotlinx.coroutines.CompletableDeferred

class FakeNotificationsApi : NotificationsApiContract {
    var nextCount: Long = 0L
    var shouldThrow: Boolean = false
    var callCount: Int = 0
        private set

    /** Если выставить — следующий вызов unreadCount() ждёт пока этот deferred завершится. */
    var gate: CompletableDeferred<Unit>? = null

    var nextListResult: Result<NotificationListResponse> =
        Result.success(NotificationListResponse(items = emptyList(), total = 0, hasMore = false))
    var nextMarkAllResult: Result<MarkAllReadResponse> =
        Result.success(MarkAllReadResponse(markedCount = 0))
    val markReadCalls = mutableListOf<Long>()
    var markReadShouldThrow: Boolean = false

    override suspend fun unreadCount(): UnreadCountResponse {
        gate?.await()
        callCount += 1
        if (shouldThrow) throw RuntimeException("network error")
        return UnreadCountResponse(count = nextCount)
    }

    override suspend fun list(limit: Int): NotificationListResponse =
        nextListResult.getOrThrow()

    override suspend fun markRead(id: Long) {
        markReadCalls += id
        if (markReadShouldThrow) throw RuntimeException("network error")
    }

    override suspend fun markAllRead(): MarkAllReadResponse =
        nextMarkAllResult.getOrThrow()
}

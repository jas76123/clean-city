package com.example.cleancity.data.network

import com.example.cleancity.shared.models.UnreadCountResponse
import kotlinx.coroutines.CompletableDeferred

class FakeNotificationsApi : NotificationsApiContract {
    var nextCount: Long = 0L
    var shouldThrow: Boolean = false
    var callCount: Int = 0
        private set

    /** Если выставить — следующий вызов unreadCount() ждёт пока этот deferred завершится. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun unreadCount(): UnreadCountResponse {
        gate?.await()
        callCount += 1
        if (shouldThrow) throw RuntimeException("network error")
        return UnreadCountResponse(count = nextCount)
    }
}

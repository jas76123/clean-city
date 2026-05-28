package com.example.cleancity.domain

import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.data.network.NotificationsApiContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground polling-канал уведомлений: каждые 30 сек тянет последние
 * записи, обновляет бейдж и эмитит новые ANNOUNCEMENT в [NotificationEventBus].
 *
 * Замена прежнему unreadCount-only поведению: теперь один запрос даёт
 * и count, и данные для дедупликации (через [AnnouncementSeenFilter]).
 */
class UnreadCountStore(
    private val api: NotificationsApiContract,
    private val seenStore: SeenNotificationStore,
    private val filter: AnnouncementSeenFilter,
    private val bus: NotificationEventBus,
    private val authProvider: suspend () -> Long?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val intervalMillis: Long = 30_000L,
) {
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(dispatcher) {
            while (isActive) {
                pollOnce()
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = 0
    }

    /** Принудительный сброс seenStore (вызывается при logout). */
    suspend fun clearSeen() {
        val userId = authProvider() ?: return
        seenStore.clear(userId)
    }

    /** Локально уменьшить счётчик (при отметке прочитанным). Не уходит ниже нуля. */
    fun decrement(by: Int = 1) {
        _state.value = (_state.value - by).coerceAtLeast(0)
    }

    /** Локально увеличить счётчик (откат отметки прочитанным при ошибке сети). */
    fun increment(by: Int = 1) {
        _state.value = _state.value + by
    }

    private suspend fun pollOnce() {
        val userId = authProvider() ?: return
        val resp = runCatching { api.list(limit = 50) }.getOrNull() ?: return
        val newOnes = filter.newAnnouncements(userId, resp.items)
        newOnes.forEach { bus.emit(it) }
        _state.value = resp.items.count { it.readAt == null }
    }
}

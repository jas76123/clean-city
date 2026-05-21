package com.example.cleancity.domain

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

class UnreadCountStore(
    private val api: NotificationsApiContract,
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
                runCatching { api.unreadCount().count.toInt() }
                    .onSuccess { _state.value = it }
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = 0
    }

    /** Локально уменьшить счётчик (при отметке прочитанным). Не уходит ниже нуля. */
    fun decrement(by: Int = 1) {
        _state.value = (_state.value - by).coerceAtLeast(0)
    }

    /** Локально увеличить счётчик (откат отметки прочитанным при ошибке сети). */
    fun increment(by: Int = 1) {
        _state.value = _state.value + by
    }
}

package com.example.cleancity.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сигнал «юзер тапнул системное уведомление, нужно переключиться на таб
 * Уведомления и пометить запись прочитанной». MainActivity.onNewIntent
 * эмитит, MainShellScreen потребляет.
 *
 * Паттерн повторяет DeepLinkBus.
 */
object NotificationTapBus {
    private val _pending = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    fun emit(notificationId: Long) { _pending.value = notificationId }

    fun consume(id: Long) {
        if (_pending.value == id) _pending.value = null
    }
}

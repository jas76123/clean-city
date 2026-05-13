package com.example.cleancity.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeepLink {
    data class Verify(val token: String) : DeepLink
    data class Reset(val token: String) : DeepLink
}

object DeepLinkBus {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun emit(link: DeepLink) { _pending.value = link }

    fun consume(link: DeepLink) {
        if (_pending.value == link) _pending.value = null
    }
}

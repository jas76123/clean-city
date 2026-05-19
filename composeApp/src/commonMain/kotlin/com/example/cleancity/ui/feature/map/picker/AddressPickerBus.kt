package com.example.cleancity.ui.feature.map.picker

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PickedAddress(
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val district: String?,
)

class AddressPickerBus {
    private val _results = MutableSharedFlow<PickedAddress>(extraBufferCapacity = 1)
    val results: SharedFlow<PickedAddress> = _results.asSharedFlow()

    suspend fun publish(picked: PickedAddress) {
        _results.emit(picked)
    }

    fun tryPublish(picked: PickedAddress): Boolean = _results.tryEmit(picked)
}

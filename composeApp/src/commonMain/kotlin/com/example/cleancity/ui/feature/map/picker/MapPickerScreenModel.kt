package com.example.cleancity.ui.feature.map.picker

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.SochiDefaults
import com.example.cleancity.ui.feature.map.MapSearchProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val GEOCODE_DEBOUNCE_MS = 300L

class MapPickerScreenModel(
    private val initialLat: Double?,
    private val initialLon: Double?,
    private val searchProvider: MapSearchProvider,
    private val bus: AddressPickerBus,
) : ScreenModel {

    data class UiState(
        val currentLat: Double,
        val currentLon: Double,
        val address: String? = null,
        val district: String? = null,
        val isResolving: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(
            currentLat = initialLat ?: SochiDefaults.CENTER.latitude,
            currentLon = initialLon ?: SochiDefaults.CENTER.longitude,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var geocodeJob: Job? = null

    fun onCameraMoved(bbox: BoundingBox) {
        val lat = (bbox.swLat + bbox.neLat) / 2.0
        val lon = (bbox.swLon + bbox.neLon) / 2.0
        _state.update { it.copy(currentLat = lat, currentLon = lon, isResolving = true) }
        geocodeJob?.cancel()
        geocodeJob = screenModelScope.launch {
            delay(GEOCODE_DEBOUNCE_MS)
            searchProvider.reverseGeocode(lat, lon)
                .onSuccess { r ->
                    _state.update {
                        it.copy(address = r.address, district = r.district, isResolving = false)
                    }
                }
                .onFailure {
                    _state.update { it.copy(address = null, isResolving = false) }
                }
        }
    }

    fun confirm() {
        val s = _state.value
        val addr = s.address ?: return
        screenModelScope.launch {
            bus.publish(
                PickedAddress(
                    latitude = s.currentLat,
                    longitude = s.currentLon,
                    address = addr,
                    district = s.district,
                ),
            )
        }
    }
}

package com.example.cleancity.ui.feature.map

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.domain.location.LocationProvider
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.SochiDefaults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapScreenModel(
    private val api: ComplaintsApiContract,
    private val locationProvider: LocationProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScreenModel {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val bboxRequests = MutableSharedFlow<BoundingBox>(extraBufferCapacity = 64)

    init {
        screenModelScope.launch(dispatcher) {
            loadMarkers(SochiDefaults.BBOX, _state.value.selectedCategory)
        }
    }

    private suspend fun loadMarkers(bbox: BoundingBox, category: com.example.cleancity.shared.models.ProblemCategory?) {
        _state.update { it.copy(isLoading = true) }
        runCatching {
            api.getMapMarkers(bbox.swLat, bbox.swLon, bbox.neLat, bbox.neLon, category)
        }.onSuccess { resp ->
            _state.update { it.copy(markers = resp.markers, isLoading = false, error = null) }
        }.onFailure { e ->
            _state.update { it.copy(isLoading = false, error = e.message ?: "Network error") }
        }
    }

    /** Закрытие модели — для тестов. В Voyager обычно вызывается автоматически. */
    fun close() {
        // no-op; screenModelScope отменяется при dispose Screen
    }
}

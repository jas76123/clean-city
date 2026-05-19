package com.example.cleancity.ui.feature.map

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.domain.location.LocationProvider
import com.example.cleancity.domain.location.PermissionStatus
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.domain.map.SochiDefaults
import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class MapScreenModel(
    private val api: ComplaintsApiContract,
    private val locationProvider: LocationProvider,
    private val searchProvider: MapSearchProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScreenModel {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val cameraBbox = MutableSharedFlow<BoundingBox>(extraBufferCapacity = 64)
    private val categoryFlow = MutableStateFlow<ProblemCategory?>(null)
    private val searchQueryFlow = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val pipelineJob = screenModelScope.launch(dispatcher) {
        cameraBbox
            .debounce(800.milliseconds)
            .map { it.rounded(decimals = 4) }
            .onStart { emit(SochiDefaults.BBOX.rounded(decimals = 4)) }
            .distinctUntilChanged()
            .combine(categoryFlow) { bbox, cat -> bbox to cat }
            .mapLatest { (bbox, cat) -> doRequest(bbox, cat) }
            .collect { /* state уже обновлён в doRequest */ }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchJob = screenModelScope.launch(dispatcher) {
        searchQueryFlow
            .debounce(250.milliseconds)
            .distinctUntilChanged()
            .filter { it.trim().length >= 2 }
            .mapLatest { query ->
                _state.update { it.copy(isSearching = true) }
                runCatching { searchProvider.suggest(query, SochiDefaults.BBOX) }
                    .getOrElse { emptyList() }
            }
            .collect { suggestions ->
                _state.update { it.copy(searchSuggestions = suggestions, isSearching = false) }
            }
    }

    fun onCameraMoved(bbox: BoundingBox) {
        cameraBbox.tryEmit(bbox)
    }

    fun selectCategory(category: ProblemCategory?) {
        _state.update { it.copy(selectedCategory = category, isCategorySheetOpen = false) }
        categoryFlow.value = category
    }

    fun onMarkerClick(id: Long) {
        _state.update { it.copy(selectedMarkerId = id) }
    }

    fun closeMarkerSheet() {
        _state.update { it.copy(selectedMarkerId = null) }
    }

    fun toggleCategory(category: ProblemCategory) {
        val newCategory = if (_state.value.selectedCategory == category) null else category
        selectCategory(newCategory)
    }

    fun openCategorySheet() {
        _state.update { it.copy(isCategorySheetOpen = true) }
    }

    fun closeCategorySheet() {
        _state.update { it.copy(isCategorySheetOpen = false) }
    }

    fun onLocationFabClicked(status: PermissionStatus, launchRequest: () -> Unit) {
        when (status) {
            PermissionStatus.NotRequested -> launchRequest()
            PermissionStatus.Denied -> _state.update {
                it.copy(error = "Разрешите геолокацию в настройках")
            }
            PermissionStatus.Granted -> screenModelScope.launch(dispatcher) {
                _state.update { it.copy(isLocating = true) }
                locationProvider.getLastKnownLocation()
                    .onSuccess { loc ->
                        _state.update {
                            it.copy(
                                isLocating = false,
                                lastKnownLocation = loc,
                                cameraPosition = CameraPosition(loc.latitude, loc.longitude, zoom = 15f),
                            )
                        }
                    }
                    .onFailure {
                        _state.update {
                            it.copy(
                                isLocating = false,
                                error = "Не удалось получить местоположение",
                            )
                        }
                    }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun zoomTo(lat: Double, lon: Double, zoom: Float) {
        _state.update { it.copy(cameraPosition = CameraPosition(lat, lon, zoom)) }
    }

    fun onSearchQueryChange(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                searchSuggestions = if (query.trim().length < 2) emptyList() else it.searchSuggestions,
            )
        }
        searchQueryFlow.value = query
    }

    fun onSuggestionSelected(suggestion: MapSuggestion) {
        _state.update {
            it.copy(
                searchQuery = suggestion.title,
                searchSuggestions = emptyList(),
                isSearching = false,
                cameraPosition = CameraPosition(suggestion.latitude, suggestion.longitude, zoom = 16f),
            )
        }
        searchQueryFlow.value = ""
    }

    fun clearSearch() {
        _state.update {
            it.copy(searchQuery = "", searchSuggestions = emptyList(), isSearching = false)
        }
        searchQueryFlow.value = ""
    }

    private suspend fun doRequest(bbox: BoundingBox, cat: ProblemCategory?) {
        _state.update { it.copy(isLoading = true) }
        runCatching { api.getMapMarkers(bbox.swLat, bbox.swLon, bbox.neLat, bbox.neLon, cat) }
            .onSuccess { resp ->
                _state.update {
                    it.copy(
                        markers = resp.markers,
                        isLoading = false,
                        hasInitialDataLoaded = true,
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        hasInitialDataLoaded = true,
                        error = e.message ?: "Network error",
                    )
                }
            }
    }

    /** Закрытие модели — для тестов. В Voyager обычно вызывается автоматически. */
    fun close() {
        // no-op; screenModelScope отменяется при dispose Screen
    }
}

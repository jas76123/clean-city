package com.example.cleancity.ui.map

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.model.ProblemStatus
import com.example.cleancity.model.ProblemType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapScreenModel : ScreenModel {

    private val searchProvider = createMapSearchProvider()

    private val _activeFilter = MutableStateFlow(MapFilter.ALL)
    val activeFilter: StateFlow<MapFilter> = _activeFilter

    private val _selectedMarker = MutableStateFlow<MapMarker?>(null)
    val selectedMarker: StateFlow<MapMarker?> = _selectedMarker

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions

    private val _isCreateMode = MutableStateFlow(false)
    val isCreateMode: StateFlow<Boolean> = _isCreateMode

    private val _createType = MutableStateFlow<ProblemType?>(null)
    val createType: StateFlow<ProblemType?> = _createType

    private val _createDescription = MutableStateFlow("")
    val createDescription: StateFlow<String> = _createDescription

    private val _createAddress = MutableStateFlow("")
    val createAddress: StateFlow<String> = _createAddress

    private val _createLat = MutableStateFlow(0.0)
    private val _createLon = MutableStateFlow(0.0)

    private val _privacyConsent = MutableStateFlow(false)
    val privacyConsent: StateFlow<Boolean> = _privacyConsent

    private val _cameraPosition = MutableStateFlow(CameraPosition())
    val cameraPosition: StateFlow<CameraPosition> = _cameraPosition

    val markers: StateFlow<List<MapMarker>> = combine(
        InMemoryRepository.problems,
        InMemoryRepository.events,
        _activeFilter,
    ) { problems, events, filter ->
        val problemMarkers = problems.map { p ->
            MapMarker(
                id = p.id,
                latitude = p.latitude,
                longitude = p.longitude,
                type = if (p.status == ProblemStatus.SOLVED) MapMarkerType.RESOLVED else MapMarkerType.PROBLEM,
                title = p.title,
                problemType = p.type,
                status = p.status,
            )
        }
        val eventMarkers = events.map { e ->
            MapMarker(
                id = e.id,
                latitude = e.latitude,
                longitude = e.longitude,
                type = MapMarkerType.EVENT,
                title = e.name,
            )
        }
        val all = problemMarkers + eventMarkers
        when (filter) {
            MapFilter.ALL -> all
            MapFilter.PROBLEMS -> all.filter { it.type == MapMarkerType.PROBLEM }
            MapFilter.EVENTS -> all.filter { it.type == MapMarkerType.EVENT }
            MapFilter.RESOLVED -> all.filter { it.type == MapMarkerType.RESOLVED }
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: MapFilter) {
        _activeFilter.value = filter
    }

    fun selectMarker(marker: MapMarker) {
        _selectedMarker.value = marker
    }

    fun clearSelection() {
        _selectedMarker.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            val cam = _cameraPosition.value
            searchProvider.suggest(query, cam.latitude, cam.longitude) { results ->
                _suggestions.value = results
            }
        } else {
            _suggestions.value = emptyList()
        }
    }

    fun selectSuggestion(suggestion: SearchSuggestion) {
        _searchQuery.value = suggestion.title
        _suggestions.value = emptyList()
        _cameraPosition.value = CameraPosition(
            latitude = suggestion.latitude,
            longitude = suggestion.longitude,
            zoom = 16f,
        )
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _suggestions.value = emptyList()
    }

    fun openCreateMode() {
        _isCreateMode.value = true
        _createType.value = null
        _createDescription.value = ""
        _createAddress.value = ""
        _privacyConsent.value = false
    }

    fun closeCreateMode() {
        _isCreateMode.value = false
    }

    fun onMapTap(lat: Double, lon: Double) {
        if (_isCreateMode.value) {
            _createLat.value = lat
            _createLon.value = lon
            searchProvider.reverseGeocode(lat, lon) { address ->
                _createAddress.value = address ?: "${lat}, ${lon}"
            }
        }
    }

    fun setCreateType(type: ProblemType) { _createType.value = type }
    fun setCreateDescription(desc: String) { _createDescription.value = desc }
    fun setCreateAddress(addr: String) { _createAddress.value = addr }
    fun setPrivacyConsent(consent: Boolean) { _privacyConsent.value = consent }

    fun submitProblem() {
        val type = _createType.value ?: return
        val desc = _createDescription.value.ifBlank { "Без описания" }
        val address = _createAddress.value.ifBlank { return }

        InMemoryRepository.addProblem(
            title = "${type.displayName}: $address",
            description = desc,
            type = type,
            latitude = _createLat.value,
            longitude = _createLon.value,
            address = address,
        )
        closeCreateMode()
    }

    fun voteProblem(problemId: String, voteYes: Boolean) {
        InMemoryRepository.voteProblem(problemId, voteYes)
    }

    fun verifyProblem(problemId: String) {
        InMemoryRepository.verifyProblem(problemId)
    }

    fun moveCameraTo(lat: Double, lon: Double, zoom: Float = 16f) {
        _cameraPosition.value = CameraPosition(lat, lon, zoom)
    }
}

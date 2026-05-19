package com.example.cleancity.ui.feature.create

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.domain.location.LocationProvider
import com.example.cleancity.domain.photo.PhotoBytes
import com.example.cleancity.shared.models.DuplicateCandidateResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.ui.feature.map.MapSearchProvider
import com.example.cleancity.ui.feature.map.MapSuggestion
import com.example.cleancity.ui.feature.map.picker.AddressPickerBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_DESCRIPTION_LENGTH = 1000
const val MAX_PHOTOS = 5
private const val DUPLICATE_DEBOUNCE_MS = 400L
private const val SUGGEST_DEBOUNCE_MS = 300L
private const val SUGGEST_MIN_QUERY = 2

enum class AddressSource { None, Gps, Suggest, Picker }

data class CreateComplaintUiState(
    val photos: List<PhotoBytes> = emptyList(),
    val category: ProblemCategory? = null,
    val categoryQuery: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val district: String? = null,
    val description: String = "",
    val locationStatus: LocationStatus = LocationStatus.Idle,
    val duplicates: List<DuplicateCandidateResponse> = emptyList(),
    val isCheckingDuplicates: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val addressSource: AddressSource = AddressSource.None,
    val suggestions: List<MapSuggestion> = emptyList(),
    val isSuggesting: Boolean = false,
) {
    val canSubmit: Boolean
        get() = !isSubmitting &&
            photos.isNotEmpty() &&
            category != null &&
            address.isNotBlank() &&
            description.isNotBlank() &&
            latitude != null &&
            longitude != null
}

sealed interface LocationStatus {
    data object Idle : LocationStatus
    data object Requesting : LocationStatus
    data object PermissionDenied : LocationStatus
    data class Failed(val reason: String) : LocationStatus
    data object Ready : LocationStatus
}

sealed interface CreateComplaintEvent {
    data class CreatedSuccessfully(val complaintId: Long) : CreateComplaintEvent
    data class OpenExistingComplaint(val complaintId: Long) : CreateComplaintEvent
}

class CreateComplaintScreenModel(
    private val complaintsApi: ComplaintsApiContract,
    private val locationProvider: LocationProvider,
    private val searchProvider: MapSearchProvider,
    private val addressPickerBus: AddressPickerBus,
) : ScreenModel {

    private val _state = MutableStateFlow(CreateComplaintUiState())
    val state: StateFlow<CreateComplaintUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CreateComplaintEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<CreateComplaintEvent> = _events.asSharedFlow()

    private var duplicatesJob: Job? = null
    private var suggestJob: Job? = null

    init {
        screenModelScope.launch {
            addressPickerBus.results.collect { picked -> onPickedFromMap(picked) }
        }
    }

    fun onPhotosAdded(added: List<PhotoBytes>) {
        _state.update { s ->
            val remaining = MAX_PHOTOS - s.photos.size
            s.copy(photos = s.photos + added.take(remaining))
        }
    }

    fun onPhotoRemoved(index: Int) {
        _state.update { s ->
            if (index !in s.photos.indices) s else s.copy(photos = s.photos.toMutableList().apply { removeAt(index) })
        }
    }

    fun onCategorySelected(category: ProblemCategory) {
        _state.update { it.copy(category = category) }
        scheduleDuplicatesCheck()
    }

    fun onCategoryQueryChanged(query: String) {
        _state.update { it.copy(categoryQuery = query) }
    }

    fun onAddressChanged(text: String) {
        _state.update { s ->
            val cleared = text.isEmpty()
            val downgradeGps = s.addressSource == AddressSource.Gps && text.isNotEmpty()
            s.copy(
                address = text,
                latitude = if (cleared) null else s.latitude,
                longitude = if (cleared) null else s.longitude,
                district = if (cleared) null else s.district,
                addressSource = when {
                    cleared -> AddressSource.None
                    downgradeGps -> AddressSource.None
                    else -> s.addressSource
                },
                suggestions = if (cleared) emptyList() else s.suggestions,
            )
        }
        scheduleSuggest(text)
    }

    private fun scheduleSuggest(query: String) {
        suggestJob?.cancel()
        if (query.length < SUGGEST_MIN_QUERY) {
            _state.update { it.copy(suggestions = emptyList(), isSuggesting = false) }
            return
        }
        suggestJob = screenModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            _state.update { it.copy(isSuggesting = true) }
            val result = runCatching {
                searchProvider.suggest(query, com.example.cleancity.domain.map.SochiDefaults.SUGGEST_REGION)
            }
            val suggestions = result.getOrNull() ?: emptyList()
            _state.update {
                it.copy(
                    suggestions = suggestions,
                    isSuggesting = false,
                )
            }
            // Если у пользователя ещё нет точных координат (не тапал suggest/picker, GPS не дал),
            // но Яндекс нашёл подсказки по тексту — поднимаем дубликат-чек на координатах первой
            // подсказки как "best guess", чтобы блок «Возможно, эта проблема уже есть» появился
            // и при чисто ручном вводе адреса.
            val firstSuggestion = suggestions.firstOrNull()
            if (firstSuggestion != null && _state.value.latitude == null) {
                scheduleDuplicatesCheck(
                    overrideLat = firstSuggestion.latitude,
                    overrideLon = firstSuggestion.longitude,
                )
            }
        }
    }

    fun onSuggestionTapped(suggestion: MapSuggestion) {
        suggestJob?.cancel()
        _state.update {
            it.copy(
                address = suggestion.title,
                latitude = suggestion.latitude,
                longitude = suggestion.longitude,
                district = null,
                addressSource = AddressSource.Suggest,
                suggestions = emptyList(),
                isSuggesting = false,
            )
        }
        screenModelScope.launch { reverseGeocode(suggestion.latitude, suggestion.longitude) }
        scheduleDuplicatesCheck()
    }

    private fun onPickedFromMap(p: com.example.cleancity.ui.feature.map.picker.PickedAddress) {
        suggestJob?.cancel()
        _state.update {
            it.copy(
                address = p.address,
                latitude = p.latitude,
                longitude = p.longitude,
                district = p.district,
                addressSource = AddressSource.Picker,
                suggestions = emptyList(),
                isSuggesting = false,
            )
        }
        scheduleDuplicatesCheck()
    }

    fun onDescriptionChanged(description: String) {
        if (description.length > MAX_DESCRIPTION_LENGTH) return
        _state.update { it.copy(description = description) }
    }

    /** Called from the Composable once GPS permission is granted. */
    fun onLocationPermissionGranted() {
        _state.update { it.copy(locationStatus = LocationStatus.Requesting) }
        screenModelScope.launch {
            locationProvider.getLastKnownLocation()
                .onSuccess { loc ->
                    _state.update {
                        it.copy(
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            locationStatus = LocationStatus.Ready,
                            addressSource = if (it.addressSource == AddressSource.None) AddressSource.Gps else it.addressSource,
                        )
                    }
                    reverseGeocode(loc.latitude, loc.longitude)
                    scheduleDuplicatesCheck()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(locationStatus = LocationStatus.Failed(e.message ?: "GPS недоступен"))
                    }
                }
        }
    }

    fun onLocationPermissionDenied() {
        _state.update { it.copy(locationStatus = LocationStatus.PermissionDenied) }
    }

    private suspend fun reverseGeocode(lat: Double, lon: Double) {
        searchProvider.reverseGeocode(lat, lon)
            .onSuccess { result ->
                _state.update {
                    // Снапим lat/lon на координаты найденного toponym'а: иначе маркер
                    // жалобы на карте окажется не там, где написан адрес, а дубликат-чек
                    // будет искать вокруг пина, а не вокруг здания. Если геокодер не отдал
                    // координаты — оставляем исходные.
                    val newLat = result.latitude ?: it.latitude
                    val newLon = result.longitude ?: it.longitude
                    // Не перетираем адрес, если пользователь уже что-то ввёл вручную
                    if (it.address.isBlank()) {
                        it.copy(
                            address = result.address,
                            district = result.district,
                            latitude = newLat,
                            longitude = newLon,
                        )
                    } else {
                        it.copy(
                            district = result.district,
                            latitude = newLat,
                            longitude = newLon,
                        )
                    }
                }
            }
        // Сетевая ошибка геокодера — пользователь введёт адрес сам, тихо игнорируем.
    }

    private fun scheduleDuplicatesCheck(
        overrideLat: Double? = null,
        overrideLon: Double? = null,
    ) {
        val s = _state.value
        val cat = s.category ?: return
        val lat = overrideLat ?: s.latitude ?: return
        val lon = overrideLon ?: s.longitude ?: return

        duplicatesJob?.cancel()
        duplicatesJob = screenModelScope.launch {
            delay(DUPLICATE_DEBOUNCE_MS)
            _state.update { it.copy(isCheckingDuplicates = true) }
            runCatching { complaintsApi.findDuplicates(lat, lon, cat) }
                .onSuccess { resp ->
                    _state.update { it.copy(duplicates = resp.items, isCheckingDuplicates = false) }
                }
                .onFailure {
                    _state.update { it.copy(duplicates = emptyList(), isCheckingDuplicates = false) }
                }
        }
    }

    fun voteForDuplicate(complaintId: Long) {
        screenModelScope.launch {
            runCatching { complaintsApi.vote(complaintId) }
                .onSuccess { _events.emit(CreateComplaintEvent.OpenExistingComplaint(complaintId)) }
                .onFailure { e ->
                    _state.update { it.copy(submitError = e.message ?: "Не удалось проголосовать") }
                }
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val request = CreateComplaintRequest(
            category = s.category!!,
            description = s.description,
            latitude = s.latitude!!,
            longitude = s.longitude!!,
            address = s.address,
            district = s.district,
        )
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        screenModelScope.launch {
            runCatching { complaintsApi.create(request, s.photos) }
                .onSuccess { created ->
                    _state.update { it.copy(isSubmitting = false) }
                    _events.emit(CreateComplaintEvent.CreatedSuccessfully(created.id))
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitError = e.message ?: "Не удалось отправить жалобу",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(submitError = null) }
    }
}

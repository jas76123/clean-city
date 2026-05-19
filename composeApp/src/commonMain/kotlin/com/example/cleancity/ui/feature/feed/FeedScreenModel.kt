package com.example.cleancity.ui.feature.feed

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.AnnouncementsApiContract
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.VoteEventBus
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.shared.models.ComplaintResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedMode { ALL, MINE }

sealed interface FeedState {
    data object Initial : FeedState
    data object Loading : FeedState
    data class Error(val message: String) : FeedState
    data class Loaded(
        val announcements: List<AnnouncementResponse>,
        val complaints: List<ComplaintResponse>,
        val mode: FeedMode,
        val isGuest: Boolean,
        val isRefreshing: Boolean,
        val isLoadingMore: Boolean,
        val endReached: Boolean,
        val nextPage: Int,
    ) : FeedState
}

private const val PAGE_SIZE = 20

class FeedScreenModel(
    private val complaintsApi: ComplaintsApiContract,
    private val announcementsApi: AnnouncementsApiContract,
    private val authRepo: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow<FeedState>(FeedState.Initial)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    init {
        // Detail после голоса эмитит событие — обновляем соответствующую
        // карточку в ленте, не дожидаясь pull-to-refresh.
        screenModelScope.launch {
            VoteEventBus.events.collect { ev ->
                _state.update { current ->
                    val loaded = current as? FeedState.Loaded ?: return@update current
                    val updated = loaded.complaints.map {
                        if (it.id == ev.complaintId) {
                            it.copy(votesCount = ev.votesCount, userVoted = ev.userVoted)
                        } else it
                    }
                    loaded.copy(complaints = updated)
                }
            }
        }
    }

    fun loadInitial() {
        if (_state.value is FeedState.Loaded) return
        _state.value = FeedState.Loading
        screenModelScope.launch {
            try {
                val isGuest = !isAuthenticated()
                val mode = FeedMode.ALL
                val (announcements, complaints) = fetchPage(mode, page = 0)
                _state.value = FeedState.Loaded(
                    announcements = announcements,
                    complaints = complaints,
                    mode = mode,
                    isGuest = isGuest,
                    isRefreshing = false,
                    isLoadingMore = false,
                    endReached = complaints.size < PAGE_SIZE,
                    nextPage = 1,
                )
            } catch (e: Throwable) {
                _state.value = FeedState.Error(e.message ?: "Не удалось загрузить ленту")
            }
        }
    }

    fun refresh() {
        val current = _state.value as? FeedState.Loaded ?: return run { loadInitial() }
        _state.value = current.copy(isRefreshing = true)
        screenModelScope.launch {
            try {
                val (announcements, complaints) = fetchPage(current.mode, page = 0)
                _state.value = current.copy(
                    announcements = announcements,
                    complaints = complaints,
                    isRefreshing = false,
                    endReached = complaints.size < PAGE_SIZE,
                    nextPage = 1,
                )
            } catch (e: Throwable) {
                _state.value = current.copy(isRefreshing = false)
            }
        }
    }

    fun setMode(mode: FeedMode) {
        val current = _state.value as? FeedState.Loaded ?: return
        if (current.mode == mode) return
        if (mode == FeedMode.MINE && current.isGuest) return
        _state.value = current.copy(mode = mode, isRefreshing = true)
        screenModelScope.launch {
            try {
                val complaints = fetchComplaints(mode, page = 0)
                val updated = _state.value as? FeedState.Loaded ?: return@launch
                _state.value = updated.copy(
                    complaints = complaints,
                    isRefreshing = false,
                    endReached = complaints.size < PAGE_SIZE,
                    nextPage = 1,
                )
            } catch (e: Throwable) {
                _state.update { s ->
                    if (s is FeedState.Loaded) s.copy(isRefreshing = false) else s
                }
            }
        }
    }

    fun loadNextPage() {
        val current = _state.value as? FeedState.Loaded ?: return
        if (current.isLoadingMore || current.endReached || current.isRefreshing) return
        _state.value = current.copy(isLoadingMore = true)
        screenModelScope.launch {
            try {
                val next = fetchComplaints(current.mode, current.nextPage)
                val now = _state.value as? FeedState.Loaded ?: return@launch
                _state.value = now.copy(
                    complaints = now.complaints + next,
                    isLoadingMore = false,
                    endReached = next.size < PAGE_SIZE,
                    nextPage = now.nextPage + 1,
                )
            } catch (e: Throwable) {
                _state.update { s ->
                    if (s is FeedState.Loaded) s.copy(isLoadingMore = false) else s
                }
            }
        }
    }

    private suspend fun fetchPage(
        mode: FeedMode,
        page: Int,
    ): Pair<List<AnnouncementResponse>, List<ComplaintResponse>> = with(screenModelScope) {
        val annDeferred = async { runCatching { announcementsApi.list(limit = 5) }.getOrNull() }
        val cmpDeferred = async { fetchComplaints(mode, page) }
        val results = awaitAll(annDeferred, cmpDeferred)
        @Suppress("UNCHECKED_CAST")
        val ann = (results[0] as? com.example.cleancity.shared.models.AnnouncementsListResponse)
            ?.items.orEmpty()
        @Suppress("UNCHECKED_CAST")
        val cmp = results[1] as List<ComplaintResponse>
        ann to cmp
    }

    private suspend fun fetchComplaints(mode: FeedMode, page: Int): List<ComplaintResponse> =
        when (mode) {
            FeedMode.ALL -> complaintsApi.list(page = page, size = PAGE_SIZE).items
            FeedMode.MINE -> complaintsApi.mine(page = page, size = PAGE_SIZE).items
        }

    private fun isAuthenticated(): Boolean =
        authRepo.state.value is AuthState.Authenticated
}

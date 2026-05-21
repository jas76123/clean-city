package com.example.cleancity.ui.feature.mycomplaints

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.shared.models.ComplaintResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MyComplaintsState {
    data object Initial : MyComplaintsState
    data object Loading : MyComplaintsState
    data object Empty : MyComplaintsState
    data class Error(val message: String) : MyComplaintsState
    data class Loaded(
        val complaints: List<ComplaintResponse>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val nextPage: Int = 1,
    ) : MyComplaintsState
}

private const val PAGE_SIZE = 20

class MyComplaintsScreenModel(
    private val complaintsApi: ComplaintsApiContract,
) : ScreenModel {

    private val _state = MutableStateFlow<MyComplaintsState>(MyComplaintsState.Initial)
    val state: StateFlow<MyComplaintsState> = _state.asStateFlow()

    fun load() {
        if (_state.value is MyComplaintsState.Loaded) return
        _state.value = MyComplaintsState.Loading
        screenModelScope.launch {
            runCatching { complaintsApi.mine(page = 0, size = PAGE_SIZE) }
                .onSuccess { resp ->
                    _state.value = if (resp.items.isEmpty()) {
                        MyComplaintsState.Empty
                    } else {
                        MyComplaintsState.Loaded(
                            complaints = resp.items,
                            endReached = resp.items.size < PAGE_SIZE,
                            nextPage = 1,
                        )
                    }
                }
                .onFailure { e ->
                    _state.value = MyComplaintsState.Error(
                        e.message ?: "Не удалось загрузить ваши жалобы"
                    )
                }
        }
    }

    fun refresh() {
        val current = _state.value as? MyComplaintsState.Loaded ?: return run { load() }
        _state.value = current.copy(isRefreshing = true)
        screenModelScope.launch {
            runCatching { complaintsApi.mine(page = 0, size = PAGE_SIZE) }
                .onSuccess { resp ->
                    _state.value = current.copy(
                        complaints = resp.items,
                        isRefreshing = false,
                        endReached = resp.items.size < PAGE_SIZE,
                        nextPage = 1,
                    )
                }
                .onFailure {
                    _state.update { s ->
                        (s as? MyComplaintsState.Loaded)?.copy(isRefreshing = false) ?: s
                    }
                }
        }
    }

    fun loadNextPage() {
        val current = _state.value as? MyComplaintsState.Loaded ?: return
        if (current.isLoadingMore || current.endReached || current.isRefreshing) return
        _state.value = current.copy(isLoadingMore = true)
        screenModelScope.launch {
            runCatching { complaintsApi.mine(page = current.nextPage, size = PAGE_SIZE) }
                .onSuccess { resp ->
                    val now = _state.value as? MyComplaintsState.Loaded ?: return@onSuccess
                    _state.value = now.copy(
                        complaints = now.complaints + resp.items,
                        isLoadingMore = false,
                        endReached = resp.items.size < PAGE_SIZE,
                        nextPage = now.nextPage + 1,
                    )
                }
                .onFailure {
                    _state.update { s ->
                        (s as? MyComplaintsState.Loaded)?.copy(isLoadingMore = false) ?: s
                    }
                }
        }
    }
}

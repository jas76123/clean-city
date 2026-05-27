package com.example.cleancity.ui.feature.feed

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.AnnouncementsApiContract
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.ui.util.listErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnnouncementDetailState {
    data object Loading : AnnouncementDetailState
    data class Loaded(val announcement: AnnouncementResponse) : AnnouncementDetailState
    data class Error(val message: String) : AnnouncementDetailState
}

class AnnouncementDetailScreenModel(
    private val api: AnnouncementsApiContract,
) : ScreenModel {

    private val _state = MutableStateFlow<AnnouncementDetailState>(AnnouncementDetailState.Loading)
    val state: StateFlow<AnnouncementDetailState> = _state.asStateFlow()

    fun start(initial: AnnouncementResponse?, id: Long) {
        if (initial != null) {
            _state.value = AnnouncementDetailState.Loaded(initial)
            return
        }
        load(id)
    }

    fun load(id: Long) {
        _state.value = AnnouncementDetailState.Loading
        screenModelScope.launch {
            runCatching { api.getById(id) }
                .onSuccess { _state.value = AnnouncementDetailState.Loaded(it) }
                .onFailure { _state.value = AnnouncementDetailState.Error(listErrorMessage(it)) }
        }
    }
}

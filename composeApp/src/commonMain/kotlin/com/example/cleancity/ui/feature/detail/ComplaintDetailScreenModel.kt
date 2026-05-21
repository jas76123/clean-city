package com.example.cleancity.ui.feature.detail

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.VoteEvent
import com.example.cleancity.domain.VoteEventBus
import com.example.cleancity.shared.models.ComplaintResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Initial : DetailState
    data object Loading : DetailState
    data class Error(val message: String) : DetailState
    data class Loaded(
        val complaint: ComplaintResponse,
        val isGuest: Boolean,
        val isOwnComplaint: Boolean = false,
        val isVoting: Boolean = false,
        val transientError: String? = null,
    ) : DetailState
}

class ComplaintDetailScreenModel(
    private val complaintId: Long,
    private val complaintsApi: ComplaintsApiContract,
    private val authRepo: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow<DetailState>(DetailState.Initial)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load() {
        if (_state.value is DetailState.Loaded) return
        _state.value = DetailState.Loading
        screenModelScope.launch {
            runCatching { complaintsApi.getById(complaintId) }
                .onSuccess { c ->
                    val auth = authRepo.state.value as? AuthState.Authenticated
                    _state.value = DetailState.Loaded(
                        complaint = c,
                        isGuest = auth == null,
                        isOwnComplaint = auth?.user?.id == c.authorId,
                    )
                }
                .onFailure { e ->
                    _state.value = DetailState.Error(e.message ?: "Не удалось загрузить жалобу")
                }
        }
    }

    fun refresh() {
        screenModelScope.launch {
            runCatching { complaintsApi.getById(complaintId) }
                .onSuccess { c ->
                    _state.update { current ->
                        val loaded = current as? DetailState.Loaded
                        if (loaded != null) {
                            loaded.copy(complaint = c)
                        } else {
                            val auth = authRepo.state.value as? AuthState.Authenticated
                            DetailState.Loaded(
                                complaint = c,
                                isGuest = auth == null,
                                isOwnComplaint = auth?.user?.id == c.authorId,
                            )
                        }
                    }
                }
        }
    }

    /**
     * Toggle голоса с optimistic UI: счётчик обновляется до сетевого ответа,
     * при ошибке — откатываем и показываем transientError.
     */
    fun toggleVote() {
        val current = _state.value as? DetailState.Loaded ?: return
        if (current.isGuest || current.isVoting) return

        val previous = current.complaint
        val newVoted = !previous.userVoted
        val newCount = (previous.votesCount + if (newVoted) 1 else -1).coerceAtLeast(0)
        val optimistic = previous.copy(userVoted = newVoted, votesCount = newCount)
        _state.value = current.copy(complaint = optimistic, isVoting = true, transientError = null)

        screenModelScope.launch {
            runCatching {
                if (newVoted) complaintsApi.vote(complaintId)
                else complaintsApi.unvote(complaintId)
            }
                .onSuccess { resp ->
                    _state.update { s ->
                        val loaded = s as? DetailState.Loaded ?: return@update s
                        loaded.copy(
                            complaint = loaded.complaint.copy(
                                userVoted = resp.userVoted,
                                votesCount = resp.votesCount,
                            ),
                            isVoting = false,
                        )
                    }
                    VoteEventBus.emit(VoteEvent(complaintId, resp.votesCount, resp.userVoted))
                }
                .onFailure { e ->
                    _state.update { s ->
                        val loaded = s as? DetailState.Loaded ?: return@update s
                        loaded.copy(
                            complaint = previous,
                            isVoting = false,
                            transientError = e.message ?: "Не удалось сохранить голос",
                        )
                    }
                }
        }
    }

    fun clearTransientError() {
        _state.update { s ->
            if (s is DetailState.Loaded) s.copy(transientError = null) else s
        }
    }
}

package com.example.cleancity.ui.feature.profile

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.UserResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileStats(
    val inProgress: Int = 0,   // NEW
    val inWork: Int = 0,       // IN_PROGRESS
    val resolved: Int = 0,
    val confirmed: Int = 0,    // count /complaints/voted
)

sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState
    data object InProgress : DeleteAccountState
    data class Error(val message: String) : DeleteAccountState
}

sealed interface ProfileState {
    data object Loading : ProfileState
    data object GuestPrompt : ProfileState
    data class Error(val message: String) : ProfileState
    data class Loaded(
        val user: UserResponse,
        val stats: ProfileStats,
    ) : ProfileState
}

private const val STATS_PAGE_SIZE = 100

class ProfileScreenModel(
    private val complaintsApi: ComplaintsApiContract,
    private val authRepo: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    fun load() {
        val auth = authRepo.state.value
        if (auth !is AuthState.Authenticated) {
            _state.value = ProfileState.GuestPrompt
            return
        }
        _state.value = ProfileState.Loading
        screenModelScope.launch {
            try {
                // coroutineScope обязателен: без него падение любого из async
                // распространяется в родительский screenModelScope (Dispatchers.Main)
                // в обход try/catch и валит процесс. coroutineScope локализует сбой —
                // отменяет соседний async и пробрасывает исключение сюда, в catch.
                val (mine, voted) = coroutineScope {
                    val mineDeferred = async { complaintsApi.mine(page = 0, size = STATS_PAGE_SIZE) }
                    val votedDeferred = async { complaintsApi.voted(page = 0, size = 1) }
                    mineDeferred.await() to votedDeferred.await()
                }
                val stats = ProfileStats(
                    inProgress = mine.items.count { it.status == ComplaintStatus.NEW },
                    inWork = mine.items.count { it.status == ComplaintStatus.IN_PROGRESS },
                    resolved = mine.items.count { it.status == ComplaintStatus.RESOLVED },
                    confirmed = voted.total.toInt(),
                )
                _state.value = ProfileState.Loaded(user = auth.user, stats = stats)
            } catch (e: Throwable) {
                _state.value = ProfileState.Error(e.message ?: "Не удалось загрузить профиль")
            }
        }
    }

    fun logout() {
        screenModelScope.launch {
            runCatching { authRepo.logout() }
            // App.kt observe AuthState и сам поменяет root
        }
    }

    fun deleteAccount() {
        if (_deleteState.value == DeleteAccountState.InProgress) return
        _deleteState.value = DeleteAccountState.InProgress
        screenModelScope.launch {
            authRepo.deleteAccount().onFailure {
                _deleteState.value = DeleteAccountState.Error(
                    it.message ?: "Не удалось удалить профиль"
                )
            }
            // При успехе AuthState → Anonymous, App.kt пересоберёт root и снимет
            // этот экран — отдельное Success-состояние не нужно.
        }
    }

    fun dismissDeleteError() {
        if (_deleteState.value !is DeleteAccountState.Error) return
        _deleteState.value = DeleteAccountState.Idle
    }
}

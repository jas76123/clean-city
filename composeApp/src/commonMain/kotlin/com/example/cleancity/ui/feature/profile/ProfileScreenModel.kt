package com.example.cleancity.ui.feature.profile

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.UserResponse
import kotlinx.coroutines.async
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

    fun load() {
        val auth = authRepo.state.value
        if (auth !is AuthState.Authenticated) {
            _state.value = ProfileState.GuestPrompt
            return
        }
        _state.value = ProfileState.Loading
        screenModelScope.launch {
            try {
                val mineDeferred = async { complaintsApi.mine(page = 0, size = STATS_PAGE_SIZE) }
                val votedDeferred = async { complaintsApi.voted(page = 0, size = 1) }
                val mine = mineDeferred.await()
                val voted = votedDeferred.await()
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
}

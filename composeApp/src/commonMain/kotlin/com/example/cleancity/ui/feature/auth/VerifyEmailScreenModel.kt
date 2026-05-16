package com.example.cleancity.ui.feature.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.DeepLink
import com.example.cleancity.domain.DeepLinkBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VerifyStatus {
    data object Waiting : VerifyStatus
    data object Verifying : VerifyStatus
    data class Error(val message: String) : VerifyStatus
}

data class VerifyEmailState(
    val email: String,
    val status: VerifyStatus = VerifyStatus.Waiting,
    val cooldownSec: Int = 0,
)

class VerifyEmailScreenModel(
    email: String,
    private val authRepo: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(VerifyEmailState(email = email))
    val state: StateFlow<VerifyEmailState> = _state.asStateFlow()

    init {
        screenModelScope.launch {
            DeepLinkBus.pending
                .filterNotNull()
                .filterIsInstance<DeepLink.Verify>()
                .collect { link ->
                    _state.update { it.copy(status = VerifyStatus.Verifying) }
                    authRepo.verifyEmail(link.token).fold(
                        onSuccess = { /* App routes via AuthState */ },
                        onFailure = { e ->
                            val msg = when (val p = ErrorMapper.map(e)) {
                                is UiErrorPlacement.FullScreen -> p.message
                                is UiErrorPlacement.Snackbar -> p.message
                                else -> "Не удалось подтвердить email"
                            }
                            _state.update { it.copy(status = VerifyStatus.Error(msg)) }
                        }
                    )
                    DeepLinkBus.consume(link)
                }
        }
    }

    fun resend() {
        screenModelScope.launch {
            authRepo.resendVerification(_state.value.email)
            _state.update { it.copy(cooldownSec = 300) }
            launch {
                while (_state.value.cooldownSec > 0) {
                    delay(1000)
                    _state.update { it.copy(cooldownSec = it.cooldownSec - 1) }
                }
            }
        }
    }

    fun changeEmail() {
        screenModelScope.launch { authRepo.logout() }
    }
}

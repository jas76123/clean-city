package com.example.cleancity.ui.feature.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val emailNotVerifiedFor: String? = null,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= 8 && !loading
}

class LoginScreenModel(private val authRepo: AuthRepository) : ScreenModel {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun setEmail(s: String) = _state.update { it.copy(email = s, emailError = null, snackbar = null) }
    fun setPassword(s: String) = _state.update { it.copy(password = s, passwordError = null, snackbar = null) }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }
    fun dismissEmailNotVerified() = _state.update { it.copy(emailNotVerifiedFor = null) }

    fun submit() {
        val s = _state.value
        if (!Validation.emailFormat(s.email)) {
            _state.update { it.copy(emailError = "Неверный формат email") }
            return
        }
        screenModelScope.launch {
            _state.update { it.copy(loading = true, emailError = null, passwordError = null, snackbar = null, emailNotVerifiedFor = null) }
            authRepo.login(s.email, s.password).fold(
                onSuccess = { /* AuthState changes → App routes away */ },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e, fallbackEmail = s.email)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlineEmail -> st.copy(loading = false, emailError = placement.message)
                            is UiErrorPlacement.InlinePassword -> st.copy(loading = false, passwordError = placement.message)
                            is UiErrorPlacement.EmailNotVerified -> st.copy(loading = false, emailNotVerifiedFor = placement.email)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            is UiErrorPlacement.FullScreen -> st.copy(loading = false, snackbar = placement.message)
                        }
                    }
                },
            )
        }
    }

    fun resendVerification(email: String) {
        screenModelScope.launch {
            authRepo.resendVerification(email)
        }
    }
}

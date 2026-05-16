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

data class RegisterState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val consent: Boolean = false,
    val loading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val nameError: String? = null,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = Validation.emailFormat(email) &&
                Validation.passwordStrength(password) &&
                Validation.fullNameNonBlank(fullName) &&
                consent &&
                !loading
}

class RegisterScreenModel(private val authRepo: AuthRepository) : ScreenModel {

    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun setFullName(s: String) = _state.update { it.copy(fullName = s, nameError = null) }
    fun setEmail(s: String) = _state.update { it.copy(email = s, emailError = null) }
    fun setPassword(s: String) = _state.update { it.copy(password = s, passwordError = null) }
    fun setConsent(b: Boolean) = _state.update { it.copy(consent = b) }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun submit() {
        val s = _state.value
        screenModelScope.launch {
            _state.update { it.copy(loading = true, emailError = null, passwordError = null, nameError = null, snackbar = null) }
            authRepo.register(s.email, s.password, s.fullName).fold(
                onSuccess = { /* App.kt routes to VerifyEmailScreen via AuthState */ },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e, fallbackEmail = s.email)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlineEmail -> st.copy(loading = false, emailError = placement.message)
                            is UiErrorPlacement.InlinePassword -> st.copy(loading = false, passwordError = placement.message)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            else -> st.copy(loading = false, snackbar = "Что-то пошло не так. Попробуйте ещё раз.")
                        }
                    }
                },
            )
        }
    }
}

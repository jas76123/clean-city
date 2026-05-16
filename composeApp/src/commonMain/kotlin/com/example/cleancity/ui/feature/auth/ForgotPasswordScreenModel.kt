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

data class ForgotState(
    val email: String = "",
    val loading: Boolean = false,
    val emailError: String? = null,
    val sent: Boolean = false,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = Validation.emailFormat(email) && !loading && !sent
}

class ForgotPasswordScreenModel(private val authRepo: AuthRepository) : ScreenModel {
    private val _state = MutableStateFlow(ForgotState())
    val state: StateFlow<ForgotState> = _state.asStateFlow()

    fun setEmail(s: String) = _state.update { it.copy(email = s, emailError = null) }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun submit() {
        screenModelScope.launch {
            _state.update { it.copy(loading = true, snackbar = null, emailError = null) }
            authRepo.forgotPassword(_state.value.email).fold(
                onSuccess = { _state.update { it.copy(loading = false, sent = true) } },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlineEmail -> st.copy(loading = false, emailError = placement.message)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            else -> st.copy(loading = false, snackbar = "Что-то пошло не так. Попробуйте ещё раз.")
                        }
                    }
                }
            )
        }
    }
}

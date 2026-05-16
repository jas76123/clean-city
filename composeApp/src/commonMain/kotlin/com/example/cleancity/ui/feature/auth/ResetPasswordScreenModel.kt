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

data class ResetState(
    val token: String,
    val newPassword: String = "",
    val confirm: String = "",
    val loading: Boolean = false,
    val newPasswordError: String? = null,
    val confirmError: String? = null,
    val fullScreenError: String? = null,
    val success: Boolean = false,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = Validation.passwordStrength(newPassword) && newPassword == confirm && !loading
}

class ResetPasswordScreenModel(
    token: String,
    private val authRepo: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ResetState(token = token))
    val state: StateFlow<ResetState> = _state.asStateFlow()

    fun setNewPassword(s: String) = _state.update {
        it.copy(newPassword = s, newPasswordError = null, confirmError = null)
    }
    fun setConfirm(s: String) = _state.update {
        val err = if (s.isNotEmpty() && s != it.newPassword) "Пароли не совпадают" else null
        it.copy(confirm = s, confirmError = err)
    }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun submit() {
        val s = _state.value
        screenModelScope.launch {
            _state.update { it.copy(loading = true, snackbar = null, newPasswordError = null, confirmError = null) }
            authRepo.resetPassword(s.token, s.newPassword).fold(
                onSuccess = { _state.update { it.copy(loading = false, success = true) } },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlinePassword -> st.copy(loading = false, newPasswordError = placement.message)
                            is UiErrorPlacement.FullScreen -> st.copy(loading = false, fullScreenError = placement.message)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            else -> st.copy(loading = false, snackbar = "Что-то пошло не так. Попробуйте ещё раз.")
                        }
                    }
                }
            )
        }
    }
}

package com.example.cleancity.data.repository

import com.example.cleancity.data.network.AuthApiContract
import com.example.cleancity.data.network.UserApiContract
import com.example.cleancity.data.storage.TokenStorage
import com.example.cleancity.domain.AuthState
import com.example.cleancity.shared.requests.auth.ForgotPasswordRequest
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.ResendVerificationRequest
import com.example.cleancity.shared.requests.auth.ResetPasswordRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val authApi: AuthApiContract,
    private val userApi: UserApiContract,
    private val storage: TokenStorage,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun init() {
        val tokens = storage.read()
        if (tokens == null) {
            _state.value = AuthState.Anonymous
            return
        }
        runCatching { userApi.me() }
            .onSuccess { _state.value = AuthState.Authenticated(it) }
            .onFailure {
                storage.clear()
                _state.value = AuthState.Anonymous
            }
    }

    suspend fun register(email: String, password: String, fullName: String): Result<Unit> = runCatching {
        authApi.register(
            RegisterRequest(
                email = email.trim(),
                password = password,
                fullName = fullName.trim(),
                acceptedTerms = true,
            )
        )
        _state.value = AuthState.NeedsVerification(email.trim())
    }

    suspend fun verifyEmail(token: String): Result<Unit> = runCatching {
        val resp = authApi.verifyEmail(VerifyEmailRequest(token))
        storage.write(resp.accessToken, resp.refreshToken)
        _state.value = AuthState.Authenticated(resp.user)
    }

    suspend fun resendVerification(email: String): Result<Unit> = runCatching {
        authApi.resendVerification(ResendVerificationRequest(email))
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val resp = authApi.login(LoginRequest(email.trim(), password))
        storage.write(resp.accessToken, resp.refreshToken)
        _state.value = AuthState.Authenticated(resp.user)
    }

    suspend fun forgotPassword(email: String): Result<Unit> = runCatching {
        authApi.forgotPassword(ForgotPasswordRequest(email.trim()))
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<Unit> = runCatching {
        authApi.resetPassword(ResetPasswordRequest(token, newPassword))
    }

    fun continueAsGuest() { _state.value = AuthState.Guest }
    fun toAnonymous() { _state.value = AuthState.Anonymous }

    suspend fun logout() {
        runCatching { authApi.logout() }
        storage.clear()
        _state.value = AuthState.Anonymous
    }

    internal fun forceAnonymous() {
        _state.value = AuthState.Anonymous
    }
}

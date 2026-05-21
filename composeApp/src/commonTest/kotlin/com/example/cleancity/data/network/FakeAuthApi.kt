package com.example.cleancity.data.network

import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.LoginResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.requests.auth.ForgotPasswordRequest
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.RefreshTokenRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.ResendVerificationRequest
import com.example.cleancity.shared.requests.auth.ResetPasswordRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest

class FakeAuthApi(
    var registerResult: Result<UserResponse>? = null,
    var verifyResult: Result<AuthResponse>? = null,
    var loginResult: Result<LoginResponse>? = null,
    var refreshResult: Result<AuthResponse>? = null,
    var deleteAccountResult: Result<Unit> = Result.success(Unit),
) {
    val logoutCalls = mutableListOf<String>()
    val resendCalls = mutableListOf<String>()
    val forgotCalls = mutableListOf<String>()
    val resetCalls = mutableListOf<Pair<String, String>>()
    var deleteAccountCalls = 0

    fun asAuthApi(): AuthApiContract = object : AuthApiContract {
        override suspend fun register(req: RegisterRequest): UserResponse =
            requireNotNull(registerResult).getOrThrow()
        override suspend fun verifyEmail(req: VerifyEmailRequest): AuthResponse =
            requireNotNull(verifyResult).getOrThrow()
        override suspend fun resendVerification(req: ResendVerificationRequest) {
            resendCalls += req.email
        }
        override suspend fun login(req: LoginRequest): LoginResponse =
            requireNotNull(loginResult).getOrThrow()
        override suspend fun refresh(req: RefreshTokenRequest): AuthResponse =
            requireNotNull(refreshResult).getOrThrow()
        override suspend fun logout(refreshToken: String) { logoutCalls += refreshToken }
        override suspend fun forgotPassword(req: ForgotPasswordRequest) { forgotCalls += req.email }
        override suspend fun resetPassword(req: ResetPasswordRequest) {
            resetCalls += req.token to req.newPassword
        }
        override suspend fun deleteAccount() {
            deleteAccountCalls++
            deleteAccountResult.getOrThrow()
        }
    }
}

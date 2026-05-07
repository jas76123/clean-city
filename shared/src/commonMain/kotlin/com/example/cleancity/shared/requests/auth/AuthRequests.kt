package com.example.cleancity.shared.requests.auth

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class VerifyEmailRequest(
    val token: String
)

@Serializable
data class ResendVerificationRequest(
    val email: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class ForgotPasswordRequest(
    val email: String
)

@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

@Serializable
data class LoginTwoFactorRequest(
    val challengeToken: String,
    val code: String
)

@Serializable
data class TwoFactorVerifyRequest(
    val code: String
)

@Serializable
data class AdminInviteRequest(
    val email: String,
    val role: String   // ADMIN | OPERATOR | INSPECTOR
)

@Serializable
data class AcceptInviteRequest(
    val token: String,
    val password: String
)

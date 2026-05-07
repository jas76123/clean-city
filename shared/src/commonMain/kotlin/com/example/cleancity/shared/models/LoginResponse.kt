package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Ответ на POST /auth/login.
 *
 * Если у юзера НЕ включена 2FA → [requires2fa]=false, [auth] заполнен реальными токенами.
 * Если включена → [requires2fa]=true, [challengeToken] валиден 5 минут — клиент должен
 * вызвать POST /auth/login-2fa с challengeToken + кодом из приложения-аутентификатора.
 */
@Serializable
data class LoginResponse(
    val requires2fa: Boolean = false,
    val challengeToken: String? = null,
    val challengeExpiresIn: Long? = null,
    val auth: AuthResponse? = null
)

@Serializable
data class TwoFactorSetupResponse(
    val secretBase32: String,
    val otpAuthUri: String
)

@Serializable
data class SessionDto(
    val id: Long,
    val issuedIp: String? = null,
    val userAgent: String? = null,
    val createdAt: String,
    val expiresAt: String
)

@Serializable
data class SessionsResponse(
    val sessions: List<SessionDto>
)

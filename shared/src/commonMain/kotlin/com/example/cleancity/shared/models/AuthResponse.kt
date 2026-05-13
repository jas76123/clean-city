package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresIn: Long,        // seconds
    val refreshExpiresIn: Long,
    val user: UserResponse
)

@Serializable
data class MessageResponse(
    val message: String
)

/**
 * Стандартный формат ошибок API: `{code, message}`. Никаких stacktrace или внутренних
 * деталей. Клиенты разбирают ошибки по `code` (строка-константа), `message` — для UI.
 */
@Serializable
data class ApiError(
    val code: String,
    val message: String
)

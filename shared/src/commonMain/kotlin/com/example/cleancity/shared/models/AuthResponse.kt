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

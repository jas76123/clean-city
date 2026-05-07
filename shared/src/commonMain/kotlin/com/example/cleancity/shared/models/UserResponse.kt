package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val id: Long,
    val email: String,
    val role: UserRole,
    val fullName: String? = null,
    val emailVerified: Boolean,
    val createdAt: String
)

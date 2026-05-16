package com.example.cleancity.domain

import com.example.cleancity.shared.models.UserResponse

sealed interface AuthState {
    data object Loading : AuthState
    data object Anonymous : AuthState
    data object Guest : AuthState
    data class NeedsVerification(val email: String) : AuthState
    data class Authenticated(val user: UserResponse) : AuthState
}

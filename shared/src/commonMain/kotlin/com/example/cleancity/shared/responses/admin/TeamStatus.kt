package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
enum class TeamStatus {
    ACTIVE,   // is_active=true && email_verified=true
    FROZEN,   // is_active=false && email_verified=true
    PENDING   // is_active=false && email_verified=false (есть валидный ADMIN_INVITE)
}

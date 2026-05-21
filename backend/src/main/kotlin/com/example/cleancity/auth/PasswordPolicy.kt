package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole

/**
 * Правила сложности пароля. Единый источник для регистрации (AuthService)
 * и bootstrap-админа (AdminBootstrap).
 */
object PasswordPolicy {
    const val MIN_LENGTH_RESIDENT = 8
    const val MIN_LENGTH_ADMIN = 12

    /** Бросает [WeakPasswordException], если пароль не удовлетворяет правилам роли. */
    fun validate(password: String, role: UserRole) {
        val minLen = if (role == UserRole.RESIDENT) MIN_LENGTH_RESIDENT else MIN_LENGTH_ADMIN
        if (password.length < minLen) {
            throw WeakPasswordException("Password must be at least $minLen characters long")
        }
        if (role != UserRole.RESIDENT) {
            if (password.none { it.isDigit() }) {
                throw WeakPasswordException("Password must contain a digit")
            }
            if (password.none { it.isUpperCase() }) {
                throw WeakPasswordException("Password must contain an uppercase letter")
            }
            if (password.none { !it.isLetterOrDigit() }) {
                throw WeakPasswordException("Password must contain a special character")
            }
        }
    }
}

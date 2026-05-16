package com.example.cleancity.domain

object Validation {
    fun emailFormat(email: String): Boolean {
        val e = email.trim()
        if (e.length !in 5..255) return false
        val at = e.indexOf('@')
        if (at <= 0 || at == e.lastIndex) return false
        return e.substring(at + 1).contains('.')
    }

    fun passwordStrength(p: String): Boolean = p.length in 8..100

    fun fullNameNonBlank(n: String): Boolean = n.trim().isNotEmpty()
}

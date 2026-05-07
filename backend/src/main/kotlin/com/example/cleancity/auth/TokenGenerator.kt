package com.example.cleancity.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Генерация cryptographically-secure случайных токенов:
 *   - email-токены (verify, reset password) — 32 байта = 64 hex-символа
 *   - refresh-токены — 48 байт = 96 hex-символов
 *
 * В БД refresh-токен хранится как SHA-256 hex (см. [hashSha256]),
 * чтобы при утечке БД нельзя было использовать токены напрямую.
 */
object TokenGenerator {
    private val secureRandom = SecureRandom()

    fun emailToken(): String = randomHex(32)

    fun refreshToken(): String = randomHex(48)

    fun hashSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.toHex()
    }

    private fun randomHex(byteLength: Int): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}

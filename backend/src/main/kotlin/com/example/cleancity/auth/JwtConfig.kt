package com.example.cleancity.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.cleancity.shared.models.UserRole
import io.ktor.server.application.*
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * JWT-конфигурация. Секрет загружается из окружения (см. .env.example).
 *
 * Время жизни access-токена различается по роли (см. SPEC § 8.3, 8.5):
 *   - админ: 15 минут (более жёстко из-за чувствительности)
 *   - резидент: 60 минут
 * Refresh-токен — 8 часов (админ) / 30 дней (резидент).
 */
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String
) {
    init {
        require(secret.length >= 32) {
            "JWT_SECRET must be at least 32 characters (got ${secret.length}). " +
                "Generate via `openssl rand -base64 48`."
        }
    }

    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(issuer)
        .withAudience(audience)
        .build()

    fun issueAccessToken(userId: Long, role: UserRole, ttl: AccessTtl = AccessTtl.forRole(role)): IssuedToken {
        val now = Instant.now()
        val expires = now.plusSeconds(ttl.seconds)
        val token = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withClaim("role", role.name)
            .withClaim("type", "access")
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(expires))
            .sign(algorithm)
        return IssuedToken(token, ttl.seconds)
    }

    /**
     * Короткоживущий (5 минут) токен между шагом 1 (логин/пароль) и шагом 2 (TOTP)
     * двухшагового входа администратора.
     */
    fun issueTwoFactorChallengeToken(userId: Long, role: UserRole): IssuedToken {
        val ttlSeconds = 5L * 60
        val now = Instant.now()
        val token = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withClaim("role", role.name)
            .withClaim("type", "2fa-challenge")
            .withJWTId(UUID.randomUUID().toString())
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plusSeconds(ttlSeconds)))
            .sign(algorithm)
        return IssuedToken(token, ttlSeconds)
    }

    /**
     * Возвращает userId, если токен — валидный 2fa-challenge. Иначе null.
     */
    fun decodeTwoFactorChallenge(rawToken: String): Long? = try {
        val payload = verifier.verify(rawToken)
        if (payload.getClaim("type").asString() != "2fa-challenge") null
        else payload.subject?.toLongOrNull()
    } catch (_: Exception) {
        null
    }

    companion object {
        fun fromEnvironment(env: ApplicationEnvironment): JwtConfig {
            val secret = env.config.propertyOrNull("jwt.secret")?.getString()?.takeIf { it.isNotBlank() }
                ?: error("JWT_SECRET is not configured. See .env.example.")
            val issuer = env.config.propertyOrNull("jwt.issuer")?.getString() ?: "cleancity"
            val audience = env.config.propertyOrNull("jwt.audience")?.getString() ?: "cleancity-api"
            return JwtConfig(secret, issuer, audience)
        }
    }
}

data class IssuedToken(val token: String, val expiresInSeconds: Long)

enum class AccessTtl(val seconds: Long) {
    ADMIN_15M(15 * 60),
    RESIDENT_1H(60 * 60);

    companion object {
        fun forRole(role: UserRole): AccessTtl = when (role) {
            UserRole.RESIDENT -> RESIDENT_1H
            UserRole.OPERATOR, UserRole.INSPECTOR, UserRole.ADMIN -> ADMIN_15M
        }
    }
}

enum class RefreshTtl(val seconds: Long) {
    ADMIN_8H(8 * 60 * 60),
    RESIDENT_30D(30L * 24 * 60 * 60);

    companion object {
        fun forRole(role: UserRole): RefreshTtl = when (role) {
            UserRole.RESIDENT -> RESIDENT_30D
            UserRole.OPERATOR, UserRole.INSPECTOR, UserRole.ADMIN -> ADMIN_8H
        }
    }
}

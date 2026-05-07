package com.example.cleancity.auth

import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class EmailTokenRow(
    val id: Long,
    val userId: Long,
    val token: String,
    val purpose: EmailTokenPurpose,
    val expiresAt: OffsetDateTime,
    val consumed: Boolean
)

data class RefreshTokenRow(
    val id: Long,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?
)

class TokenRepository {

    // ----- Email tokens -----

    fun createEmailToken(userId: Long, purpose: EmailTokenPurpose, ttlSeconds: Long): String = transaction {
        val rawToken = TokenGenerator.emailToken()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        EmailTokens.insert {
            it[EmailTokens.userId] = userId
            it[EmailTokens.token] = rawToken
            it[EmailTokens.purpose] = purpose.name
            it[EmailTokens.expiresAt] = now.plusSeconds(ttlSeconds)
            it[EmailTokens.consumed] = false
            it[EmailTokens.createdAt] = now
        }
        rawToken
    }

    fun findValidEmailToken(token: String, purpose: EmailTokenPurpose): EmailTokenRow? = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        EmailTokens.selectAll().where {
            (EmailTokens.token eq token) and
                (EmailTokens.purpose eq purpose.name) and
                (EmailTokens.consumed eq false) and
                (EmailTokens.expiresAt greater now)
        }.firstOrNull()?.toEmailTokenRow()
    }

    fun consumeEmailToken(tokenId: Long) = transaction {
        EmailTokens.update({ EmailTokens.id eq tokenId }) { it[EmailTokens.consumed] = true }
    }

    // ----- Refresh tokens -----

    fun createRefreshToken(
        userId: Long,
        rawToken: String,
        ip: String?,
        userAgent: String?,
        ttlSeconds: Long
    ) = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.insert {
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.tokenHash] = TokenGenerator.hashSha256(rawToken)
            it[RefreshTokens.issuedIp] = ip
            it[RefreshTokens.userAgent] = userAgent
            it[RefreshTokens.expiresAt] = now.plusSeconds(ttlSeconds)
            it[RefreshTokens.createdAt] = now
        }
    }

    fun findValidRefreshToken(rawToken: String): RefreshTokenRow? = transaction {
        val hash = TokenGenerator.hashSha256(rawToken)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.selectAll().where {
            (RefreshTokens.tokenHash eq hash) and
                (RefreshTokens.revokedAt.isNull()) and
                (RefreshTokens.expiresAt greater now)
        }.firstOrNull()?.toRefreshTokenRow()
    }

    fun revokeRefreshToken(tokenId: Long) = transaction {
        RefreshTokens.update({ RefreshTokens.id eq tokenId }) {
            it[RefreshTokens.revokedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    fun revokeAllUserRefreshTokens(userId: Long) = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.update({
            (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull()
        }) {
            it[RefreshTokens.revokedAt] = now
        }
    }

    private fun ResultRow.toEmailTokenRow() = EmailTokenRow(
        id = this[EmailTokens.id],
        userId = this[EmailTokens.userId],
        token = this[EmailTokens.token],
        purpose = EmailTokenPurpose.valueOf(this[EmailTokens.purpose]),
        expiresAt = this[EmailTokens.expiresAt],
        consumed = this[EmailTokens.consumed]
    )

    private fun ResultRow.toRefreshTokenRow() = RefreshTokenRow(
        id = this[RefreshTokens.id],
        userId = this[RefreshTokens.userId],
        tokenHash = this[RefreshTokens.tokenHash],
        expiresAt = this[RefreshTokens.expiresAt],
        revokedAt = this[RefreshTokens.revokedAt]
    )
}

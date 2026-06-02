package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessTokenValidatorTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api",
    )

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:atv-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
        }
    }

    private fun seedUser(role: UserRole, isActive: Boolean): Long = transaction {
        Users.insert {
            it[Users.email] = "u${System.nanoTime()}@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = role.name
            it[Users.fullName] = "Тест"
            it[Users.emailVerified] = true
            it[Users.isActive] = isActive
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun accessPayload(userId: Long, role: UserRole) =
        jwtConfig.verifier.verify(jwtConfig.issueAccessToken(userId, role).token)

    @Test
    fun `active user with access token yields a principal`() {
        initDb()
        val id = seedUser(UserRole.RESIDENT, isActive = true)
        val principal = validateAccessPrincipal(accessPayload(id, UserRole.RESIDENT), UserRepository())
        assertEquals(id.toString(), principal?.payload?.subject)
    }

    @Test
    fun `banned (inactive) user with valid access token is rejected`() {
        initDb()
        val id = seedUser(UserRole.RESIDENT, isActive = false)
        // Токен валиден по подписи и не истёк, но пользователь деактивирован (бан).
        assertNull(validateAccessPrincipal(accessPayload(id, UserRole.RESIDENT), UserRepository()))
    }

    @Test
    fun `token for a non-existent user is rejected`() {
        initDb()
        assertNull(validateAccessPrincipal(accessPayload(999_999L, UserRole.RESIDENT), UserRepository()))
    }

    @Test
    fun `non-access token type is rejected`() {
        initDb()
        val id = seedUser(UserRole.ADMIN, isActive = true)
        val challenge = jwtConfig.verifier.verify(jwtConfig.issueTwoFactorChallengeToken(id, UserRole.ADMIN).token)
        assertNull(validateAccessPrincipal(challenge, UserRepository()))
    }
}

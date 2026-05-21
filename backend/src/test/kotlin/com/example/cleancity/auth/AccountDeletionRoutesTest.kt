package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.email.EmailService
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.testutils.installApiErrorHandling
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )
    private val json = Json { ignoreUnknownKeys = true }

    private class NoopEmail : EmailService {
        override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) = Unit
    }

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:acc-del-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(RefreshTokens, EmailTokens, AuditLog, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, AuditLog)
        }
    }

    private fun seedUser(email: String, role: UserRole): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "bcrypt-hash"
            it[Users.role] = role.name
            it[Users.fullName] = "Тест Тестов"
            it[Users.emailVerified] = true
            it[Users.isActive] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun bearerFor(userId: Long, role: UserRole): String =
        jwtConfig.issueAccessToken(userId = userId, role = role).token

    private fun ApplicationTestBuilder.appWithAuth() {
        val service = AuthService(
            users = UserRepository(),
            tokens = TokenRepository(),
            email = NoopEmail(),
            jwt = jwtConfig,
            baseUrl = "http://localhost:8080",
            termsVersion = "test-v1",
            totp = TotpService(),
            audit = DbAuditLogger()
        )
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { c ->
                        if (c.payload.subject != null &&
                            c.payload.getClaim("type").asString() == "access"
                        ) JWTPrincipal(c.payload) else null
                    }
                }
            }
            installApiErrorHandling()
            routing { authRoutes(service, RateLimiter()) }
        }
    }

    @Test
    fun `DELETE auth me without token returns 401`() {
        initDb()
        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
    }

    @Test
    fun `DELETE auth me as non-resident returns 403`() {
        initDb()
        val adminId = seedUser("admin@cleancity.local", UserRole.ADMIN)
        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/me") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }

    @Test
    fun `DELETE auth me as resident returns 204 and anonymizes account`() {
        initDb()
        val residentId = seedUser("resident@cleancity.local", UserRole.RESIDENT)
        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/me") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(residentId, UserRole.RESIDENT)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)

            transaction {
                val row = Users.selectAll().where { Users.id eq residentId }.first()
                assertEquals("deleted_${residentId}@cleancity.local", row[Users.email])
                assertEquals("", row[Users.passwordHash])
                assertNull(row[Users.fullName])
                assertFalse(row[Users.isActive])

                val audited = AuditLog.selectAll()
                    .where { AuditLog.action eq AuditAction.ACCOUNT_DELETED.name }
                    .count()
                assertTrue(audited >= 1)
            }
        }
    }

    @Test
    fun `DELETE auth me is idempotent on retry`() {
        initDb()
        val residentId = seedUser("retry@cleancity.local", UserRole.RESIDENT)
        testApplication {
            appWithAuth()
            val bearer = "Bearer ${bearerFor(residentId, UserRole.RESIDENT)}"
            val first = client.delete("/auth/me") { header(HttpHeaders.Authorization, bearer) }
            val second = client.delete("/auth/me") { header(HttpHeaders.Authorization, bearer) }
            assertEquals(HttpStatusCode.NoContent, first.status)
            assertEquals(HttpStatusCode.NoContent, second.status)
        }
    }
}

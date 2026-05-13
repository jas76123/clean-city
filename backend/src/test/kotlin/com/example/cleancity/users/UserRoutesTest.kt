package com.example.cleancity.users

import com.example.cleancity.auth.AuthService
import com.example.cleancity.auth.DbAuditLogger
import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.auth.RateLimiter
import com.example.cleancity.auth.RegisterInput
import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.TotpService
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.auth.authRoutes
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.email.EmailService
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.testutils.installApiErrorHandling
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
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
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// ---------------------------------------------------------------------------
// Test infrastructure
// ---------------------------------------------------------------------------

private data class Tokens(val accessToken: String)

private class CapturingEmailService : EmailService {
    val bodies = mutableListOf<String>()
    override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) {
        bodies.add(htmlBody)
    }
    fun lastToken(): String =
        Regex("""token=([0-9a-f]+)""").find(bodies.last())?.groupValues?.get(1)
            ?: error("No token found in last email body")
}

private val testJwt = JwtConfig(
    secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
    issuer = "cleancity-test",
    audience = "cleancity-test-api"
)

private val testJson = Json { ignoreUnknownKeys = true }

private fun initDb() {
    Database.connect(
        "jdbc:h2:mem:user-routes-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        driver = "org.h2.Driver"
    )
    transaction {
        SchemaUtils.drop(RefreshTokens, EmailTokens, AuditLog, Users)
        SchemaUtils.create(Users, EmailTokens, RefreshTokens, AuditLog)
    }
}

/**
 * Sets up a minimal Ktor test app with JWT auth, authRoutes and userRoutes wired.
 * The [block] receives the HTTP client and an [AuthService] for direct service calls.
 */
private fun runAuthTestApp(block: suspend ApplicationTestBuilder.(io.ktor.client.HttpClient, AuthService, CapturingEmailService) -> Unit) {
    val emailSvc = CapturingEmailService()
    val authService = AuthService(
        users = UserRepository(),
        tokens = TokenRepository(),
        email = emailSvc,
        jwt = testJwt,
        baseUrl = "http://localhost:8080",
        termsVersion = "test-v1",
        totp = TotpService(),
        audit = DbAuditLogger()
    )
    testApplication {
        application {
            install(ContentNegotiation) { json(testJson) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(testJwt.verifier)
                    validate { c ->
                        if (c.payload.subject != null &&
                            c.payload.getClaim("type").asString() == "access"
                        ) JWTPrincipal(c.payload) else null
                    }
                }
            }
            installApiErrorHandling()
            routing {
                authRoutes(authService, RateLimiter())
                userRoutes(authService)
            }
        }
        block(client, authService, emailSvc)
    }
}

/**
 * Registers a user via [AuthService] directly, then verifies the email,
 * and returns the access token.
 */
private suspend fun registerAndVerify(
    authService: AuthService,
    emailSvc: CapturingEmailService,
    email: String,
    password: String
): Tokens {
    authService.register(RegisterInput(email, password, null, acceptedTerms = true))
    val token = emailSvc.lastToken()
    val auth = authService.verifyEmail(token, ip = null, userAgent = null)
    return Tokens(auth.accessToken)
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

class UserRoutesTest {

    @Test
    fun `GET users me without token returns 401`() {
        initDb()
        runAuthTestApp { client, _, _ ->
            val resp = client.get("/users/me")
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
    }

    @Test
    fun `GET users me with invalid token returns 401`() {
        initDb()
        runAuthTestApp { client, _, _ ->
            val resp = client.get("/users/me") {
                header(HttpHeaders.Authorization, "Bearer not.a.real.jwt")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
    }

    @Test
    fun `GET users me with valid token returns 200 and user`() {
        initDb()
        runAuthTestApp { client, authService, emailSvc ->
            val tokens = registerAndVerify(authService, emailSvc, email = "u1@cleancity.local", password = "Password123")
            val resp = client.get("/users/me") {
                header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val user = testJson.decodeFromString(UserResponse.serializer(), resp.bodyAsText())
            assertEquals("u1@cleancity.local", user.email)
            assertNotNull(user.role)
            assertTrue(user.emailVerified)
        }
    }
}

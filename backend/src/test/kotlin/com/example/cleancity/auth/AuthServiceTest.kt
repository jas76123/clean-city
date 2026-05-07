package com.example.cleancity.auth

import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.email.EmailService
import com.example.cleancity.shared.models.UserRole
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class CapturingEmailService : EmailService {
    data class SentEmail(val to: String, val subject: String, val body: String)
    val sent = mutableListOf<SentEmail>()
    override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) {
        sent.add(SentEmail(to, subject, htmlBody))
    }
    fun lastTokenLink(): String {
        val body = sent.last().body
        return Regex("""token=([0-9a-f]+)""").find(body)?.groupValues?.get(1)
            ?: error("No token in last email body")
    }
}

class AuthServiceTest {

    private lateinit var users: UserRepository
    private lateinit var tokens: TokenRepository
    private lateinit var email: CapturingEmailService
    private lateinit var jwt: JwtConfig
    private lateinit var service: AuthService

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:auth-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.drop(RefreshTokens, EmailTokens, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens)
        }
        users = UserRepository()
        tokens = TokenRepository()
        email = CapturingEmailService()
        jwt = JwtConfig(
            secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
            issuer = "cleancity-test",
            audience = "cleancity-test-api"
        )
        service = AuthService(users, tokens, email, jwt, baseUrl = "http://localhost:8080")
    }

    @Test
    fun `register creates unverified user and sends verify email`() = runBlocking<Unit> {
        val resp = service.register(RegisterInput("alice@example.com", "password123", "Alice"))

        assertEquals("alice@example.com", resp.email)
        assertEquals(UserRole.RESIDENT, resp.role)
        assertFalse(resp.emailVerified)
        assertEquals(1, email.sent.size)
        assertEquals("alice@example.com", email.sent[0].to)
        assertTrue(email.sent[0].subject.contains("регистрацию", ignoreCase = true))
    }

    @Test
    fun `register rejects weak password`() {
        assertFailsWith<WeakPasswordException> {
            runBlocking { service.register(RegisterInput("a@b.com", "short", null)) }
        }
    }

    @Test
    fun `register rejects malformed email`() {
        assertFailsWith<InvalidEmailException> {
            runBlocking { service.register(RegisterInput("not-an-email", "password123", null)) }
        }
    }

    @Test
    fun `register rejects duplicate email`() {
        runBlocking { service.register(RegisterInput("dup@example.com", "password123", null)) }
        assertFailsWith<EmailAlreadyRegisteredException> {
            runBlocking { service.register(RegisterInput("dup@example.com", "password456", null)) }
        }
    }

    @Test
    fun `verify-email activates account and returns AuthResponse`() = runBlocking<Unit> {
        service.register(RegisterInput("verify@example.com", "password123", null))
        val token = email.lastTokenLink()

        val auth = service.verifyEmail(token, ip = "127.0.0.1", userAgent = "test")

        assertTrue(auth.user.emailVerified)
        assertTrue(auth.accessToken.isNotBlank())
        assertTrue(auth.refreshToken.isNotBlank())
    }

    @Test
    fun `verify-email rejects invalid token`() {
        assertFailsWith<TokenInvalidException> {
            service.verifyEmail("garbage-token", ip = null, userAgent = null)
        }
    }

    @Test
    fun `verify-email rejects already-consumed token`() = runBlocking<Unit> {
        service.register(RegisterInput("once@example.com", "password123", null))
        val token = email.lastTokenLink()
        service.verifyEmail(token, null, null)

        assertFailsWith<TokenInvalidException> {
            service.verifyEmail(token, null, null)
        }
    }

    @Test
    fun `login fails when email not verified`() = runBlocking<Unit> {
        service.register(RegisterInput("unverified@example.com", "password123", null))
        assertFailsWith<EmailNotVerifiedException> {
            service.login(LoginInput("unverified@example.com", "password123"), null, null)
        }
    }

    @Test
    fun `login fails on wrong password`() = runBlocking<Unit> {
        service.register(RegisterInput("user@example.com", "password123", null))
        val token = email.lastTokenLink()
        service.verifyEmail(token, null, null)

        assertFailsWith<InvalidCredentialsException> {
            service.login(LoginInput("user@example.com", "wrong-password"), null, null)
        }
    }

    @Test
    fun `login succeeds after verify and returns valid AuthResponse`() = runBlocking<Unit> {
        service.register(RegisterInput("ok@example.com", "password123", null))
        val token = email.lastTokenLink()
        service.verifyEmail(token, null, null)

        val auth = service.login(LoginInput("ok@example.com", "password123"), null, null)

        assertNotNull(auth.accessToken)
        assertNotNull(auth.refreshToken)
        assertTrue(auth.user.emailVerified)
        // Verify that JWT can be parsed
        val decoded = jwt.verifier.verify(auth.accessToken)
        assertEquals(auth.user.id.toString(), decoded.subject)
        assertEquals("RESIDENT", decoded.getClaim("role").asString())
    }

    @Test
    fun `refresh issues new tokens and revokes old`() = runBlocking<Unit> {
        service.register(RegisterInput("refresh@example.com", "password123", null))
        val token = email.lastTokenLink()
        val first = service.verifyEmail(token, null, null)

        val second = service.refresh(first.refreshToken, null, null)

        assertTrue(first.accessToken != second.accessToken)
        assertTrue(first.refreshToken != second.refreshToken)
        // Old refresh token must be unusable now
        assertFailsWith<TokenInvalidException> {
            service.refresh(first.refreshToken, null, null)
        }
    }

    @Test
    fun `logout revokes refresh token`() = runBlocking<Unit> {
        service.register(RegisterInput("logout@example.com", "password123", null))
        val token = email.lastTokenLink()
        val auth = service.verifyEmail(token, null, null)

        service.logout(auth.refreshToken)
        assertFailsWith<TokenInvalidException> {
            service.refresh(auth.refreshToken, null, null)
        }
    }

    @Test
    fun `forgot-password sends reset email when user exists`() = runBlocking<Unit> {
        service.register(RegisterInput("forgot@example.com", "password123", null))
        email.sent.clear()

        service.forgotPassword("forgot@example.com")

        assertEquals(1, email.sent.size)
        assertTrue(email.sent[0].subject.contains("пароля", ignoreCase = true))
    }

    @Test
    fun `forgot-password silent when user does not exist (anti-enum)`() = runBlocking<Unit> {
        service.forgotPassword("ghost@example.com")
        assertEquals(0, email.sent.size)
    }

    @Test
    fun `reset-password updates password, consumes token, revokes all sessions`() = runBlocking<Unit> {
        service.register(RegisterInput("reset@example.com", "oldpass123", null))
        val verifyToken = email.lastTokenLink()
        val auth = service.verifyEmail(verifyToken, null, null)
        email.sent.clear()

        service.forgotPassword("reset@example.com")
        val resetToken = email.lastTokenLink()

        service.resetPassword(resetToken, "newpass456")

        // Old session invalidated
        assertFailsWith<TokenInvalidException> {
            service.refresh(auth.refreshToken, null, null)
        }
        // Old password rejected
        val verifyEmailToken = email.lastTokenLink() // no, sent stays the same
        assertFailsWith<InvalidCredentialsException> {
            service.login(LoginInput("reset@example.com", "oldpass123"), null, null)
        }
        // New password works
        val newAuth = service.login(LoginInput("reset@example.com", "newpass456"), null, null)
        assertNotNull(newAuth.accessToken)
    }
}

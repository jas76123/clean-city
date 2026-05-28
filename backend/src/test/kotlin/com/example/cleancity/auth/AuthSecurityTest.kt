package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.email.EmailService
import com.example.cleancity.shared.models.UserRole
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Тесты для дня 3 (security): lockout, 2FA, sessions, invite, audit.
 */
class AuthSecurityTest {

    private class CapturingEmail : EmailService {
        data class Sent(val to: String, val subject: String, val body: String)
        val sent = mutableListOf<Sent>()
        override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) {
            sent.add(Sent(to, subject, htmlBody))
        }
        fun lastTokenLink(): String =
            Regex("""token=([0-9a-f]+)""").find(sent.last().body)?.groupValues?.get(1)
                ?: error("No token in last email body")
    }

    private lateinit var users: UserRepository
    private lateinit var tokens: TokenRepository
    private lateinit var email: CapturingEmail
    private lateinit var jwt: JwtConfig
    private lateinit var totp: TotpService
    private lateinit var service: AuthService

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:sec-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(RefreshTokens, EmailTokens, AuditLog, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, AuditLog)
        }
        users = UserRepository()
        tokens = TokenRepository()
        email = CapturingEmail()
        jwt = JwtConfig(
            secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
            issuer = "cleancity-test",
            audience = "cleancity-test-api"
        )
        totp = TotpService()
        service = AuthService(
            users = users,
            tokens = tokens,
            email = email,
            jwt = jwt,
            baseUrl = "http://localhost:8080",
            termsVersion = "test-v1",
            totp = totp,
            audit = DbAuditLogger()
        )
    }

    private fun registerAndVerifyResident(emailAddr: String, password: String = "password123"): Long {
        runBlocking { service.register(RegisterInput(emailAddr, password, null, acceptedTerms = true)) }
        val token = email.lastTokenLink()
        val auth = service.verifyEmail(token, ip = null, userAgent = null)
        return auth.user.id
    }

    private fun createAdmin(emailAddr: String, password: String = "Adm1n!Strong"): Long {
        val user = users.create(
            email = emailAddr,
            passwordHash = PasswordHasher.hash(password),
            role = UserRole.ADMIN,
            fullName = "Admin"
        )
        users.markEmailVerifiedAndActive(user.id)
        return user.id
    }

    private fun countAuditAction(action: AuditAction): Int = transaction {
        AuditLog.selectAll().where { AuditLog.action eq action.name }.count().toInt()
    }

    // ----- Lockout -----

    @Test
    fun `5 failed logins lock the account for the next attempt`() = runBlocking<Unit> {
        registerAndVerifyResident("lock@example.com")

        repeat(MAX_FAILED_LOGIN_ATTEMPTS) {
            assertFailsWith<InvalidCredentialsException> {
                service.login(LoginInput("lock@example.com", "wrong-password"), null, null)
            }
        }

        // 6-я попытка — даже с правильным паролем — должна упереться в lockout.
        assertFailsWith<AccountLockedException> {
            service.login(LoginInput("lock@example.com", "password123"), null, null)
        }

        assertTrue(countAuditAction(AuditAction.LOGIN_LOCKED) >= 1)
    }

    @Test
    fun `successful login resets failed attempts counter`() = runBlocking<Unit> {
        registerAndVerifyResident("ok@example.com")

        repeat(MAX_FAILED_LOGIN_ATTEMPTS - 1) {
            assertFailsWith<InvalidCredentialsException> {
                service.login(LoginInput("ok@example.com", "wrong"), null, null)
            }
        }

        // Успешный вход → счётчик сброшен → мы НЕ блокируемся при следующей единичной ошибке.
        val ok = service.login(LoginInput("ok@example.com", "password123"), null, null)
        assertTrue(ok is LoginResult.Success)

        // Ещё одна ошибка после сброса — это первая, не шестая.
        assertFailsWith<InvalidCredentialsException> {
            service.login(LoginInput("ok@example.com", "wrong"), null, null)
        }
    }

    // ----- 2FA -----

    @Test
    fun `setup, verify and login flow for admin with 2FA`() {
        val adminId = createAdmin("admin@example.com")

        val setup = service.setupTwoFactor(adminId, ip = null, userAgent = null)
        assertTrue(setup.secretBase32.isNotBlank())
        assertTrue(setup.otpAuthUri.startsWith("otpauth://totp/CleanCity:admin%40example.com"))

        // До верификации totp_enabled=false — login возвращает Success без challenge.
        val before = service.login(LoginInput("admin@example.com", "Adm1n!Strong"), null, null)
        assertTrue(before is LoginResult.Success)

        // Активируем
        val code = totp.generateCode(setup.secretBase32)
        service.enableTwoFactor(adminId, code, ip = null, userAgent = null)

        // Теперь логин возвращает challenge
        val step1 = service.login(LoginInput("admin@example.com", "Adm1n!Strong"), null, null)
        val challenge = (step1 as LoginResult.TwoFactorRequired).challengeToken
        assertTrue(challenge.isNotBlank())

        // Step 2: правильный код → AuthResponse
        val auth = service.loginWithTwoFactor(challenge, totp.generateCode(setup.secretBase32), null, null)
        assertNotNull(auth.accessToken)
        assertEquals("ADMIN", jwt.verifier.verify(auth.accessToken).getClaim("role").asString())

        assertTrue(countAuditAction(AuditAction.TWOFA_SETUP_STARTED) == 1)
        assertTrue(countAuditAction(AuditAction.TWOFA_ENABLED) == 1)
        assertTrue(countAuditAction(AuditAction.TWOFA_LOGIN_SUCCESS) == 1)
    }

    @Test
    fun `2FA setup forbidden for resident`() {
        val residentId = registerAndVerifyResident("res@example.com")
        assertFailsWith<TwoFactorRoleException> {
            service.setupTwoFactor(residentId, null, null)
        }
    }

    @Test
    fun `2FA verify rejects invalid code`() {
        val adminId = createAdmin("admin2@example.com")
        service.setupTwoFactor(adminId, null, null)
        assertFailsWith<InvalidTotpCodeException> {
            service.enableTwoFactor(adminId, "000000", null, null)
        }
        assertTrue(countAuditAction(AuditAction.TWOFA_FAIL) == 1)
    }

    @Test
    fun `2FA login rejects invalid code`() {
        val adminId = createAdmin("admin3@example.com")
        val setup = service.setupTwoFactor(adminId, null, null)
        service.enableTwoFactor(adminId, totp.generateCode(setup.secretBase32), null, null)

        val step1 = service.login(LoginInput("admin3@example.com", "Adm1n!Strong"), null, null) as LoginResult.TwoFactorRequired
        assertFailsWith<InvalidTotpCodeException> {
            service.loginWithTwoFactor(step1.challengeToken, "000000", null, null)
        }
    }

    @Test
    fun `2FA login rejects garbage challenge`() {
        assertFailsWith<TokenInvalidException> {
            service.loginWithTwoFactor("garbage.challenge.token", "123456", null, null)
        }
    }

    // ----- Sessions -----

    @Test
    fun `listSessions returns active refresh tokens, revokeSession invalidates one`() = runBlocking<Unit> {
        val userId = registerAndVerifyResident("sess@example.com")
        // verifyEmail тоже выдаёт сессию; чтобы тест был детерминированным — отзываем её.
        tokens.revokeAllUserRefreshTokens(userId)

        val first = service.login(LoginInput("sess@example.com", "password123"), "1.1.1.1", "ua-A") as LoginResult.Success
        val second = service.login(LoginInput("sess@example.com", "password123"), "2.2.2.2", "ua-B") as LoginResult.Success

        val sessions = service.listSessions(userId)
        assertEquals(2, sessions.size)
        val ips = sessions.mapNotNull { it.issuedIp }.toSet()
        assertEquals(setOf("1.1.1.1", "2.2.2.2"), ips)

        val revokeId = sessions.first { it.issuedIp == "1.1.1.1" }.id
        assertTrue(service.revokeSession(userId, revokeId, null, null))

        // Refresh-токен от revoked сессии больше не валиден.
        assertFailsWith<TokenInvalidException> {
            service.refresh(first.auth.refreshToken, null, null)
        }
        // Вторая сессия живёт.
        val refreshed = service.refresh(second.auth.refreshToken, null, null)
        assertNotNull(refreshed.refreshToken)

        assertTrue(countAuditAction(AuditAction.SESSION_REVOKED) == 1)
    }

    @Test
    fun `revokeSession does not revoke another user's session`() = runBlocking<Unit> {
        val u1 = registerAndVerifyResident("u1@example.com")
        registerAndVerifyResident("u2@example.com")
        val u2Login = service.login(LoginInput("u2@example.com", "password123"), null, null) as LoginResult.Success

        val u2Sessions = service.listSessions((u2Login.auth.user.id))
        val u2SessionId = u2Sessions.first().id

        // u1 пытается отозвать сессию u2 — не должно работать.
        assertFalse(service.revokeSession(u1, u2SessionId, null, null))
    }

    // ----- Admin invite -----

    @Test
    fun `inviteAdmin creates inactive user and accept activates them`() = runBlocking<Unit> {
        val actorId = createAdmin("inviter@example.com")

        val invited = service.inviteAdmin(
            actorId = actorId,
            targetEmail = "newadmin@example.com",
            targetFullName = "Test User",
            targetRole = UserRole.OPERATOR,
            ip = null,
            userAgent = null
        )
        assertEquals("newadmin@example.com", invited.email)
        assertEquals(UserRole.OPERATOR, invited.role)

        val invitedUser = users.findById(invited.id)!!
        assertFalse(invitedUser.isActive)
        assertFalse(invitedUser.emailVerified)
        assertTrue(invitedUser.mustChangePassword)

        val token = email.lastTokenLink()
        val auth = service.acceptInvite(token, "Operator!Pass1", null, null)
        assertNotNull(auth.accessToken)
        assertEquals(UserRole.OPERATOR, auth.user.role)

        val activated = users.findById(invited.id)!!
        assertTrue(activated.isActive)
        assertTrue(activated.emailVerified)
        assertFalse(activated.mustChangePassword)

        assertTrue(countAuditAction(AuditAction.ADMIN_INVITE_SENT) == 1)
        assertTrue(countAuditAction(AuditAction.ADMIN_INVITE_ACCEPTED) == 1)
    }

    @Test
    fun `acceptInvite rejects weak password by admin rules`() = runBlocking<Unit> {
        val actorId = createAdmin("inviter2@example.com")
        service.inviteAdmin(actorId, "newadmin2@example.com", "Test User", UserRole.ADMIN, null, null)
        val token = email.lastTokenLink()

        assertFailsWith<WeakPasswordException> {
            service.acceptInvite(token, "short", null, null)
        }
    }

    @Test
    fun `inviteAdmin refuses RESIDENT role`() = runBlocking<Unit> {
        val actorId = createAdmin("inviter3@example.com")
        assertFailsWith<IllegalArgumentException> {
            service.inviteAdmin(actorId, "x@example.com", "Test User", UserRole.RESIDENT, null, null)
        }
    }

    @Test
    fun `inviteAdmin saves fullName and uses it`() = runBlocking<Unit> {
        val actorId = createAdmin("inviter4@example.com")

        val invited = service.inviteAdmin(
            actorId = actorId,
            targetEmail = "named@example.com",
            targetFullName = "Иван Иванов",
            targetRole = UserRole.OPERATOR,
            ip = null,
            userAgent = null
        )
        val saved = users.findById(invited.id)!!
        assertEquals("Иван Иванов", saved.fullName)
    }

    @Test
    fun `inviteAdmin rejects blank fullName`() = runBlocking<Unit> {
        val actorId = createAdmin("inviter5@example.com")
        assertFailsWith<InvalidFullNameException> {
            service.inviteAdmin(actorId, "x2@example.com", "   ", UserRole.OPERATOR, null, null)
        }
    }

    @Test
    fun `inviteAdmin trims fullName`() = runBlocking<Unit> {
        val actorId = createAdmin("inviter6@example.com")
        val invited = service.inviteAdmin(actorId, "y@example.com", "  Анна Сидорова  ", UserRole.OPERATOR, null, null)
        assertEquals("Анна Сидорова", users.findById(invited.id)!!.fullName)
    }

    @Test
    fun `acceptInvite rejects expired or unknown token`() {
        assertFailsWith<TokenInvalidException> {
            service.acceptInvite("garbage", "Strong!Pass1", null, null)
        }
    }

    // ----- Audit -----

    @Test
    fun `successful login writes LOGIN_SUCCESS audit row`() = runBlocking<Unit> {
        registerAndVerifyResident("aud@example.com")
        // verifyEmail тоже логирует LOGIN_SUCCESS, поэтому сначала чистим...
        transaction { AuditLog.deleteAll() }
        service.login(LoginInput("aud@example.com", "password123"), "9.9.9.9", "ua-T")

        assertTrue(countAuditAction(AuditAction.LOGIN_SUCCESS) == 1)
    }
}

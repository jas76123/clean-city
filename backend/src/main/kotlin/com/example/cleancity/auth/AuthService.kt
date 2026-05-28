package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.email.EmailService
import com.example.cleancity.email.EmailTemplates
import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.responses.admin.AuditEntryDto
import com.example.cleancity.shared.responses.admin.TeamMemberDto
import com.example.cleancity.shared.responses.admin.TeamStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset

private const val VERIFY_TOKEN_TTL_SECONDS = 24L * 60 * 60         // 24 часа
private const val RESET_TOKEN_TTL_SECONDS = 60L * 60                // 1 час
private const val INVITE_TOKEN_TTL_SECONDS = 7L * 24 * 60 * 60       // 7 дней

const val MAX_FAILED_LOGIN_ATTEMPTS = 5
const val LOCKOUT_DURATION_MINUTES = 15L

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

class EmailNotVerifiedException(msg: String = "Email not verified") : RuntimeException(msg)
class InvalidCredentialsException(msg: String = "Invalid email or password") : RuntimeException(msg)
class TokenInvalidException(msg: String = "Invalid or expired token") : RuntimeException(msg)
class WeakPasswordException(msg: String) : IllegalArgumentException(msg)
class InvalidEmailException(msg: String = "Invalid email format") : IllegalArgumentException(msg)
class InvalidFullNameException(msg: String = "ФИО обязательно") : IllegalArgumentException(msg)
class EmailAlreadyRegisteredException(msg: String = "Email already registered") : RuntimeException(msg)
class AccountLockedException(val lockedUntil: OffsetDateTime) :
    RuntimeException("Account locked until $lockedUntil")
class TwoFactorAlreadyEnabledException : RuntimeException("2FA already enabled")
class TwoFactorNotConfiguredException : RuntimeException("2FA not configured")
class InvalidTotpCodeException : RuntimeException("Invalid 2FA code")
class TwoFactorRoleException : RuntimeException("2FA available only for admin/operator/inspector")

class SelfFreezeException : RuntimeException("Нельзя заморозить собственный аккаунт")
class LastActiveAdminException : RuntimeException("Это последний активный администратор")
class InviteNotAcceptedException : RuntimeException("Сотрудник ещё не принял приглашение")
class NotAPendingInviteException : RuntimeException("Это не pending-приглашение")

/**
 * Результат шага 1 логина: либо успех (резидент или админ без 2FA),
 * либо требование 2FA с challenge-токеном.
 */
sealed class LoginResult {
    data class Success(val auth: AuthResponse) : LoginResult()
    data class TwoFactorRequired(val challengeToken: String, val expiresInSeconds: Long) : LoginResult()
}

data class TwoFactorSetupResponse(
    val secretBase32: String,
    val otpAuthUri: String
)

class TermsNotAcceptedException(msg: String = "Acceptance of terms is required") : IllegalArgumentException(msg)

class AuthService(
    private val users: UserRepository,
    private val tokens: TokenRepository,
    private val email: EmailService,
    private val jwt: JwtConfig,
    private val baseUrl: String,
    private val termsVersion: String,
    private val totp: TotpService = TotpService(),
    private val audit: AuditLogger = NoopAuditLogger,
    private val auditLog: AuditLogRepository = AuditLogRepository()
) {

    /**
     * Регистрация. Возвращает [UserResponse] (не AuthResponse — сначала верификация email).
     * Шлёт письмо с verify-ссылкой.
     */
    suspend fun register(req: RegisterInput): UserResponse {
        validateEmail(req.email)
        validatePassword(req.password, role = UserRole.RESIDENT)
        if (!req.acceptedTerms) throw TermsNotAcceptedException()

        val existing = users.findByEmail(req.email)
        if (existing != null) throw EmailAlreadyRegisteredException()

        val user = users.create(
            email = req.email,
            passwordHash = PasswordHasher.hash(req.password),
            role = UserRole.RESIDENT,
            fullName = req.fullName,
            acceptedTermsVersion = termsVersion
        )

        val token = tokens.createEmailToken(user.id, EmailTokenPurpose.VERIFY_EMAIL, VERIFY_TOKEN_TTL_SECONDS)
        sendVerifyEmail(user.email, token)

        return user.toResponse()
    }

    suspend fun resendVerification(emailAddr: String) {
        val user = users.findByEmail(emailAddr) ?: return  // защита от user enumeration
        if (user.emailVerified) return
        val token = tokens.createEmailToken(user.id, EmailTokenPurpose.VERIFY_EMAIL, VERIFY_TOKEN_TTL_SECONDS)
        sendVerifyEmail(user.email, token)
    }

    /**
     * Verify-email — после успешной верификации сразу логиним пользователя.
     */
    fun verifyEmail(token: String, ip: String?, userAgent: String?): AuthResponse {
        val emailToken = tokens.findValidEmailToken(token, EmailTokenPurpose.VERIFY_EMAIL)
            ?: throw TokenInvalidException()
        val user = users.findById(emailToken.userId) ?: throw TokenInvalidException()

        tokens.consumeEmailToken(emailToken.id)
        users.markEmailVerified(user.id)
        users.updateLastLogin(user.id, ip)

        val refreshed = user.copy(emailVerified = true)
        return issueAuthResponse(refreshed, ip, userAgent).also {
            audit.log(AuditAction.LOGIN_SUCCESS, refreshed.id, "user", refreshed.id.toString(), ip, userAgent)
        }
    }

    /**
     * Шаг 1 входа. Возвращает либо AuthResponse (если 2FA не требуется),
     * либо TwoFactorRequired с challenge-токеном.
     *
     * Lockout: после [MAX_FAILED_LOGIN_ATTEMPTS] подряд неуспешных попыток
     * аккаунт блокируется на [LOCKOUT_DURATION_MINUTES] минут.
     */
    fun login(req: LoginInput, ip: String?, userAgent: String?): LoginResult {
        val user = users.findByEmail(req.email) ?: throw InvalidCredentialsException()

        // 1) Активный замок?
        user.lockedUntil?.let { until ->
            if (until.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
                audit.log(AuditAction.LOGIN_LOCKED, user.id, "user", user.id.toString(), ip, userAgent)
                throw AccountLockedException(until)
            }
        }

        if (!user.isActive) throw InvalidCredentialsException()

        // 2) Пароль
        if (!PasswordHasher.verify(req.password, user.passwordHash)) {
            handleFailedAttempt(user, ip, userAgent)
            throw InvalidCredentialsException()
        }

        if (!user.emailVerified) {
            audit.log(AuditAction.LOGIN_FAIL, user.id, "user", user.id.toString(), ip, userAgent, "email_not_verified")
            throw EmailNotVerifiedException()
        }

        // 3) Если у юзера включена 2FA — возвращаем challenge, токены не выдаём
        if (user.totpEnabled) {
            val challenge = jwt.issueTwoFactorChallengeToken(user.id, user.role)
            return LoginResult.TwoFactorRequired(challenge.token, challenge.expiresInSeconds)
        }

        users.updateLastLogin(user.id, ip)
        audit.log(AuditAction.LOGIN_SUCCESS, user.id, "user", user.id.toString(), ip, userAgent)
        return LoginResult.Success(issueAuthResponse(user, ip, userAgent))
    }

    /**
     * Шаг 2 двухшагового входа: обмен challenge-токена + TOTP-кода на access/refresh.
     */
    fun loginWithTwoFactor(challengeToken: String, code: String, ip: String?, userAgent: String?): AuthResponse {
        val userId = jwt.decodeTwoFactorChallenge(challengeToken) ?: throw TokenInvalidException()
        val user = users.findById(userId) ?: throw TokenInvalidException()
        if (!user.isActive || !user.totpEnabled || user.totpSecret == null) {
            throw TokenInvalidException()
        }
        if (!totp.verify(user.totpSecret, code)) {
            audit.log(AuditAction.TWOFA_FAIL, user.id, "user", user.id.toString(), ip, userAgent)
            throw InvalidTotpCodeException()
        }

        users.updateLastLogin(user.id, ip)
        audit.log(AuditAction.TWOFA_LOGIN_SUCCESS, user.id, "user", user.id.toString(), ip, userAgent)
        return issueAuthResponse(user, ip, userAgent)
    }

    /**
     * Refresh — старый refresh инвалидируется, выдаётся новая пара. Защита от replay.
     */
    fun refresh(rawRefreshToken: String, ip: String?, userAgent: String?): AuthResponse {
        val record = tokens.findValidRefreshToken(rawRefreshToken) ?: throw TokenInvalidException()
        val user = users.findById(record.userId) ?: throw TokenInvalidException()
        if (!user.isActive) throw TokenInvalidException()

        // Rotation: revoke old, issue new
        tokens.revokeRefreshToken(record.id)
        return issueAuthResponse(user, ip, userAgent)
    }

    fun logout(rawRefreshToken: String) {
        val record = tokens.findValidRefreshToken(rawRefreshToken) ?: return
        tokens.revokeRefreshToken(record.id)
        audit.log(AuditAction.REFRESH_REVOKED, record.userId, "refresh_token", record.id.toString())
    }

    /**
     * Forgot password — ВСЕГДА возвращает успех (защита от user enumeration).
     */
    suspend fun forgotPassword(emailAddr: String) {
        val user = users.findByEmail(emailAddr) ?: return
        if (!user.isActive) return
        val token = tokens.createEmailToken(user.id, EmailTokenPurpose.RESET_PASSWORD, RESET_TOKEN_TTL_SECONDS)
        val link = "$baseUrl/reset-password?token=$token"
        val (subject, html) = EmailTemplates.resetPassword(link)
        email.send(user.email, subject, html)
    }

    /**
     * Reset password — после успеха инвалидируем все refresh-токены пользователя.
     */
    fun resetPassword(token: String, newPassword: String, ip: String? = null, userAgent: String? = null) {
        val emailToken = tokens.findValidEmailToken(token, EmailTokenPurpose.RESET_PASSWORD)
            ?: throw TokenInvalidException()
        val user = users.findById(emailToken.userId) ?: throw TokenInvalidException()
        validatePassword(newPassword, role = user.role)

        users.updatePasswordHash(user.id, PasswordHasher.hash(newPassword))
        tokens.consumeEmailToken(emailToken.id)
        tokens.revokeAllUserRefreshTokens(user.id)
        audit.log(AuditAction.PASSWORD_RESET, user.id, "user", user.id.toString(), ip, userAgent)
    }

    // ----- 2FA setup -----

    /**
     * Старт настройки 2FA для админа/оператора/инспектора.
     * Если 2FA уже включён — кидаем 409. Если просто настраивается заново — перетираем secret.
     */
    fun setupTwoFactor(userId: Long, ip: String?, userAgent: String?): TwoFactorSetupResponse {
        val user = users.findById(userId) ?: throw TokenInvalidException()
        if (user.role == UserRole.RESIDENT) throw TwoFactorRoleException()
        if (user.totpEnabled) throw TwoFactorAlreadyEnabledException()

        val secret = totp.generateSecretBase32()
        users.setTotpSecret(user.id, secret)
        val uri = totp.otpAuthUri(secret, user.email)

        audit.log(AuditAction.TWOFA_SETUP_STARTED, user.id, "user", user.id.toString(), ip, userAgent)
        return TwoFactorSetupResponse(secret, uri)
    }

    fun enableTwoFactor(userId: Long, code: String, ip: String?, userAgent: String?) {
        val user = users.findById(userId) ?: throw TokenInvalidException()
        if (user.totpEnabled) throw TwoFactorAlreadyEnabledException()
        val secret = user.totpSecret ?: throw TwoFactorNotConfiguredException()
        if (!totp.verify(secret, code)) {
            audit.log(AuditAction.TWOFA_FAIL, user.id, "user", user.id.toString(), ip, userAgent)
            throw InvalidTotpCodeException()
        }
        users.enableTotp(user.id)
        audit.log(AuditAction.TWOFA_ENABLED, user.id, "user", user.id.toString(), ip, userAgent)
    }

    // ----- Sessions -----

    fun listSessions(userId: Long): List<SessionView> = tokens.listActiveRefreshTokens(userId).map {
        SessionView(
            id = it.id,
            issuedIp = it.issuedIp,
            userAgent = it.userAgent,
            createdAt = it.createdAt.toString(),
            expiresAt = it.expiresAt.toString()
        )
    }

    /**
     * Возвращает true, если сессия принадлежала юзеру и была отозвана.
     */
    fun revokeSession(userId: Long, sessionId: Long, ip: String?, userAgent: String?): Boolean {
        val ok = tokens.revokeRefreshTokenIfOwnedBy(userId, sessionId)
        if (ok) audit.log(AuditAction.SESSION_REVOKED, userId, "refresh_token", sessionId.toString(), ip, userAgent)
        return ok
    }

    /**
     * Самостоятельное удаление аккаунта жителем (152-ФЗ): анонимизация
     * персональных данных + отзыв всех refresh-токенов + запись в audit_log.
     */
    fun deleteOwnAccount(userId: Long, ip: String?, userAgent: String?) {
        users.softDeleteAndAnonymize(userId)
        tokens.revokeAllUserRefreshTokens(userId)
        audit.log(AuditAction.ACCOUNT_DELETED, userId, "user", userId.toString(), ip, userAgent)
    }

    // ----- Admin invite -----

    /**
     * Приглашение нового администратора. Создаёт неактивного юзера + email-токен ADMIN_INVITE.
     * Отправляет письмо с ссылкой `/accept-invite?token=...`.
     */
    suspend fun inviteAdmin(
        actorId: Long,
        targetEmail: String,
        targetFullName: String,
        targetRole: UserRole,
        ip: String?,
        userAgent: String?
    ): UserResponse {
        if (targetRole == UserRole.RESIDENT) throw IllegalArgumentException("Use registration for residents")
        validateEmail(targetEmail)
        val normalizedName = targetFullName.trim()
        if (normalizedName.isEmpty()) throw InvalidFullNameException()
        val existing = users.findByEmail(targetEmail)
        if (existing != null) throw EmailAlreadyRegisteredException()

        val placeholder = PasswordHasher.hash(java.util.UUID.randomUUID().toString())
        val user = users.create(
            email = targetEmail,
            passwordHash = placeholder,
            role = targetRole,
            fullName = normalizedName,
            isActive = false,
            emailVerified = false,
            mustChangePassword = true
        )

        val token = tokens.createEmailToken(user.id, EmailTokenPurpose.ADMIN_INVITE, INVITE_TOKEN_TTL_SECONDS)
        val link = "$baseUrl/accept-invite?token=$token"
        val invitedBy = users.findById(actorId)?.email ?: "Администратор CleanCity"
        val (subject, html) = EmailTemplates.adminInvite(link, invitedBy, normalizedName)
        email.send(user.email, subject, html)

        audit.log(AuditAction.ADMIN_INVITE_SENT, actorId, "user", user.id.toString(), ip, userAgent, "role=${targetRole.name}")
        return user.toResponse()
    }

    fun acceptInvite(token: String, password: String, ip: String?, userAgent: String?): AuthResponse {
        val emailToken = tokens.findValidEmailToken(token, EmailTokenPurpose.ADMIN_INVITE)
            ?: throw TokenInvalidException()
        val user = users.findById(emailToken.userId) ?: throw TokenInvalidException()
        validatePassword(password, role = user.role)

        users.updatePasswordHash(user.id, PasswordHasher.hash(password))
        users.markEmailVerifiedAndActive(user.id)
        tokens.consumeEmailToken(emailToken.id)

        val activated = user.copy(emailVerified = true, isActive = true)
        users.updateLastLogin(user.id, ip)
        audit.log(AuditAction.ADMIN_INVITE_ACCEPTED, user.id, "user", user.id.toString(), ip, userAgent)
        return issueAuthResponse(activated, ip, userAgent)
    }

    // ----- private helpers -----

    private fun handleFailedAttempt(user: UserRow, ip: String?, userAgent: String?) {
        val newCount = users.incrementFailedAttempts(user.id)
        if (newCount >= MAX_FAILED_LOGIN_ATTEMPTS) {
            val until = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(LOCKOUT_DURATION_MINUTES)
            users.lockUntil(user.id, until)
            audit.log(AuditAction.LOGIN_LOCKED, user.id, "user", user.id.toString(), ip, userAgent)
        } else {
            audit.log(AuditAction.LOGIN_FAIL, user.id, "user", user.id.toString(), ip, userAgent, "attempt=$newCount")
        }
    }

    private suspend fun sendVerifyEmail(to: String, token: String) {
        val link = "$baseUrl/verify-email?token=$token"
        val (subject, html) = EmailTemplates.verifyEmail(link)
        email.send(to, subject, html)
    }

    private fun issueAuthResponse(user: UserRow, ip: String?, userAgent: String?): AuthResponse {
        val access = jwt.issueAccessToken(user.id, user.role)
        val refreshTtl = RefreshTtl.forRole(user.role).seconds
        val rawRefresh = TokenGenerator.refreshToken()
        tokens.createRefreshToken(user.id, rawRefresh, ip, userAgent, refreshTtl)

        return AuthResponse(
            accessToken = access.token,
            refreshToken = rawRefresh,
            accessExpiresIn = access.expiresInSeconds,
            refreshExpiresIn = refreshTtl,
            user = user.toResponse()
        )
    }

    private fun validateEmail(email: String) {
        if (!EMAIL_REGEX.matches(email)) throw InvalidEmailException()
    }

    private fun validatePassword(password: String, role: UserRole) =
        PasswordPolicy.validate(password, role)

    fun getUserById(userId: Long): UserResponse? =
        users.findById(userId)?.toResponse()

    private fun UserRow.toResponse() = UserResponse(
        id = id,
        email = email,
        role = role,
        fullName = fullName,
        emailVerified = emailVerified,
        createdAt = createdAt.toString()
    )

    suspend fun listTeamMembers(status: TeamStatus?): List<TeamMemberDto> {
        val rows = users.listByTeamStatus(status)
        return rows.map { it.toTeamMemberDto() }
    }

    suspend fun recentAuditEvents(limit: Int = 50): List<AuditEntryDto> {
        val rows = auditLog.findRecent(limit.coerceIn(1, 50))
        return rows.map {
            AuditEntryDto(
                id = it.id,
                timestamp = it.createdAt.toString(),
                actorEmail = it.actorEmail,
                action = it.action,
                targetType = it.targetType,
                targetId = it.targetId,
                ip = it.ip,
                details = it.details
            )
        }
    }

    /**
     * Замораживает сотрудника:
     *  - I1: нельзя замораживать самого себя
     *  - I2: если targetRole == ADMIN, должен остаться ≥1 активный ADMIN после операции
     *  - I5: все refresh-токены target ревокаются
     */
    suspend fun freezeUser(actorId: Long, targetId: Long, ip: String?, ua: String?) {
        if (actorId == targetId) throw SelfFreezeException()

        val target = users.findById(targetId)
            ?: throw IllegalArgumentException("User not found")
        if (target.role == UserRole.RESIDENT) {
            throw IllegalArgumentException("Cannot freeze a resident via team API")
        }

        if (target.role == UserRole.ADMIN) {
            val activeAdminsAfter = users.listByTeamStatus(TeamStatus.ACTIVE)
                .count { it.role == UserRole.ADMIN && it.id != targetId }
            if (activeAdminsAfter < 1) throw LastActiveAdminException()
        }

        users.setActive(targetId, false)
        tokens.revokeAllUserRefreshTokens(targetId)
        audit.log(AuditAction.ADMIN_USER_FROZEN, actorId, "user", targetId.toString(), ip, ua)
    }

    /**
     * Размораживает сотрудника. I3: target должен быть с email_verified=true
     * (frozen, не pending).
     */
    suspend fun unfreezeUser(actorId: Long, targetId: Long, ip: String?, ua: String?) {
        val target = users.findById(targetId)
            ?: throw IllegalArgumentException("User not found")
        if (target.role == UserRole.RESIDENT) {
            throw IllegalArgumentException("Cannot unfreeze a resident via team API")
        }
        if (!target.emailVerified) throw InviteNotAcceptedException()

        users.setActive(targetId, true)
        audit.log(AuditAction.ADMIN_USER_UNFROZEN, actorId, "user", targetId.toString(), ip, ua)
    }

    /**
     * Отзыв pending-приглашения. I4: target должен быть pending
     * (is_active=false, email_verified=false). После — invalidate токенов + delete row.
     */
    suspend fun revokeInvitation(actorId: Long, targetId: Long, ip: String?, ua: String?) {
        val target = users.findById(targetId)
            ?: throw IllegalArgumentException("User not found")
        if (target.isActive || target.emailVerified) throw NotAPendingInviteException()

        tokens.invalidateInviteForUser(targetId)
        users.delete(targetId)
        audit.log(AuditAction.ADMIN_INVITE_REVOKED, actorId, "user", targetId.toString(), ip, ua)
    }

    private fun UserRow.toTeamMemberDto(): TeamMemberDto {
        val status = when {
            isActive && emailVerified -> TeamStatus.ACTIVE
            !isActive && emailVerified -> TeamStatus.FROZEN
            else -> TeamStatus.PENDING
        }
        return TeamMemberDto(
            id = id,
            email = email,
            fullName = fullName,
            role = role.name,
            status = status,
            createdAt = createdAt.toString(),
            lastLoginAt = lastLoginAt?.toString(),
            invitedAt = if (status == TeamStatus.PENDING) createdAt.toString() else null
        )
    }
}

/**
 * Внутренний DTO регистрации. `acceptedTerms` помечается true по умолчанию для
 * удобства тестов; продакшн-вход через [authRoutes] всегда явно проксирует
 * значение из [com.example.cleancity.shared.requests.auth.RegisterRequest],
 * где default = false (152-ФЗ требует явного согласия).
 */
data class RegisterInput(
    val email: String,
    val password: String,
    val fullName: String?,
    val acceptedTerms: Boolean = true
)

data class LoginInput(
    val email: String,
    val password: String
)

data class SessionView(
    val id: Long,
    val issuedIp: String?,
    val userAgent: String?,
    val createdAt: String,
    val expiresAt: String
)

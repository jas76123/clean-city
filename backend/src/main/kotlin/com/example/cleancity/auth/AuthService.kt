package com.example.cleancity.auth

import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.email.EmailService
import com.example.cleancity.email.EmailTemplates
import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole

private const val MIN_PASSWORD_LENGTH_RESIDENT = 8
private const val MIN_PASSWORD_LENGTH_ADMIN = 12

private const val VERIFY_TOKEN_TTL_SECONDS = 24L * 60 * 60         // 24 часа
private const val RESET_TOKEN_TTL_SECONDS = 60L * 60                // 1 час

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

class EmailNotVerifiedException(msg: String = "Email not verified") : RuntimeException(msg)
class InvalidCredentialsException(msg: String = "Invalid email or password") : RuntimeException(msg)
class TokenInvalidException(msg: String = "Invalid or expired token") : RuntimeException(msg)
class WeakPasswordException(msg: String) : IllegalArgumentException(msg)
class InvalidEmailException(msg: String = "Invalid email format") : IllegalArgumentException(msg)
class EmailAlreadyRegisteredException(msg: String = "Email already registered") : RuntimeException(msg)

class AuthService(
    private val users: UserRepository,
    private val tokens: TokenRepository,
    private val email: EmailService,
    private val jwt: JwtConfig,
    private val baseUrl: String
) {

    /**
     * Регистрация. Возвращает [UserResponse] (не AuthResponse — сначала верификация email).
     * Шлёт письмо с verify-ссылкой.
     */
    suspend fun register(req: RegisterInput): UserResponse {
        validateEmail(req.email)
        validatePassword(req.password, role = UserRole.RESIDENT)

        val existing = users.findByEmail(req.email)
        if (existing != null) throw EmailAlreadyRegisteredException()

        val user = users.create(
            email = req.email,
            passwordHash = PasswordHasher.hash(req.password),
            role = UserRole.RESIDENT,
            fullName = req.fullName
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

        return issueAuthResponse(user.copy(emailVerified = true), ip, userAgent)
    }

    /**
     * Вход. Возвращает 403-сигнал через [EmailNotVerifiedException], если email не подтверждён.
     */
    fun login(req: LoginInput, ip: String?, userAgent: String?): AuthResponse {
        val user = users.findByEmail(req.email) ?: throw InvalidCredentialsException()
        if (!user.isActive) throw InvalidCredentialsException()
        if (!PasswordHasher.verify(req.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }
        if (!user.emailVerified) throw EmailNotVerifiedException()

        users.updateLastLogin(user.id, ip)
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
    fun resetPassword(token: String, newPassword: String) {
        val emailToken = tokens.findValidEmailToken(token, EmailTokenPurpose.RESET_PASSWORD)
            ?: throw TokenInvalidException()
        val user = users.findById(emailToken.userId) ?: throw TokenInvalidException()
        validatePassword(newPassword, role = user.role)

        users.updatePasswordHash(user.id, PasswordHasher.hash(newPassword))
        tokens.consumeEmailToken(emailToken.id)
        tokens.revokeAllUserRefreshTokens(user.id)
    }

    // ----- private helpers -----

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

    private fun validatePassword(password: String, role: UserRole) {
        val minLen = if (role == UserRole.RESIDENT) MIN_PASSWORD_LENGTH_RESIDENT else MIN_PASSWORD_LENGTH_ADMIN
        if (password.length < minLen) {
            throw WeakPasswordException("Password must be at least $minLen characters long")
        }
        if (role != UserRole.RESIDENT) {
            // Админу строже: цифра + буква в верхнем регистре + спецсимвол
            require(password.any { it.isDigit() }) { throw WeakPasswordException("Password must contain a digit") }
            require(password.any { it.isUpperCase() }) { throw WeakPasswordException("Password must contain an uppercase letter") }
            require(password.any { !it.isLetterOrDigit() }) { throw WeakPasswordException("Password must contain a special character") }
        }
    }

    private fun UserRow.toResponse() = UserResponse(
        id = id,
        email = email,
        role = role,
        fullName = fullName,
        emailVerified = emailVerified,
        createdAt = createdAt.toString()
    )
}

data class RegisterInput(
    val email: String,
    val password: String,
    val fullName: String?
)

data class LoginInput(
    val email: String,
    val password: String
)

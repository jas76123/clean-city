package com.example.cleancity.auth

import com.example.cleancity.BadRequestException
import com.example.cleancity.ConflictException
import com.example.cleancity.ErrorCodes
import com.example.cleancity.ForbiddenException
import com.example.cleancity.LockedException
import com.example.cleancity.NotFoundException
import com.example.cleancity.UnauthorizedException
import com.example.cleancity.shared.models.LoginResponse
import com.example.cleancity.shared.models.MessageResponse
import com.example.cleancity.shared.models.SessionDto
import com.example.cleancity.shared.models.SessionsResponse
import com.example.cleancity.shared.models.TwoFactorSetupResponse
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.auth.AcceptInviteRequest
import com.example.cleancity.shared.requests.auth.AdminInviteRequest
import com.example.cleancity.shared.requests.auth.ForgotPasswordRequest
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.LoginTwoFactorRequest
import com.example.cleancity.shared.requests.auth.RefreshTokenRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.ResendVerificationRequest
import com.example.cleancity.shared.requests.auth.ResetPasswordRequest
import com.example.cleancity.shared.requests.auth.TwoFactorVerifyRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.authRoutes(
    service: AuthService,
    limiter: RateLimiter = RateLimiter()
) {
    route("/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()
            try {
                val user = service.register(
                    RegisterInput(req.email, req.password, req.fullName, req.acceptedTerms)
                )
                call.respond(HttpStatusCode.Created, user)
            } catch (e: InvalidEmailException) {
                throw BadRequestException(e.message ?: "Invalid email", ErrorCodes.VALIDATION_INVALID_EMAIL)
            } catch (e: WeakPasswordException) {
                throw BadRequestException(e.message ?: "Weak password", ErrorCodes.VALIDATION_WEAK_PASSWORD)
            } catch (_: TermsNotAcceptedException) {
                throw BadRequestException("Acceptance of terms is required", ErrorCodes.VALIDATION_TERMS_REQUIRED)
            } catch (_: EmailAlreadyRegisteredException) {
                throw ConflictException("Email already registered", ErrorCodes.EMAIL_ALREADY_REGISTERED)
            }
        }

        post("/verify-email") {
            val req = call.receive<VerifyEmailRequest>()
            try {
                val auth = service.verifyEmail(req.token, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                throw BadRequestException("Invalid or expired token", ErrorCodes.AUTH_INVALID_TOKEN)
            }
        }

        post("/resend-verification") {
            val req = call.receive<ResendVerificationRequest>()
            service.resendVerification(req.email)
            call.respond(HttpStatusCode.OK, MessageResponse("If the account exists and is not verified, a new email has been sent"))
        }

        post("/login") {
            if (rateLimitOr429(limiter, "login", RateLimits.LOGIN_LIMIT, RateLimits.LOGIN_WINDOW_SECONDS)) return@post

            val req = call.receive<LoginRequest>()
            try {
                when (val result = service.login(LoginInput(req.email, req.password), call.clientIp(), call.userAgentSafe())) {
                    is LoginResult.Success -> {
                        call.respond(HttpStatusCode.OK, LoginResponse(auth = result.auth))
                    }
                    is LoginResult.TwoFactorRequired -> {
                        call.respond(
                            HttpStatusCode.OK,
                            LoginResponse(
                                requires2fa = true,
                                challengeToken = result.challengeToken,
                                challengeExpiresIn = result.expiresInSeconds
                            )
                        )
                    }
                }
            } catch (e: AccountLockedException) {
                throw LockedException("Account locked. Try again after ${e.lockedUntil}")
            } catch (_: EmailNotVerifiedException) {
                throw ForbiddenException("Please verify your email first", ErrorCodes.AUTH_EMAIL_UNVERIFIED)
            } catch (_: InvalidCredentialsException) {
                throw UnauthorizedException("Invalid email or password", ErrorCodes.AUTH_INVALID_CREDENTIALS)
            }
        }

        post("/login-2fa") {
            if (rateLimitOr429(limiter, "login-2fa", RateLimits.TWOFA_LIMIT, RateLimits.TWOFA_WINDOW_SECONDS)) return@post

            val req = call.receive<LoginTwoFactorRequest>()
            try {
                val auth = service.loginWithTwoFactor(req.challengeToken, req.code, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                throw UnauthorizedException("Invalid or expired challenge", ErrorCodes.AUTH_INVALID_TOKEN)
            } catch (_: InvalidTotpCodeException) {
                throw UnauthorizedException("Invalid 2FA code", ErrorCodes.AUTH_2FA_INVALID)
            }
        }

        post("/refresh") {
            val req = call.receive<RefreshTokenRequest>()
            try {
                val auth = service.refresh(req.refreshToken, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                throw UnauthorizedException("Invalid or expired refresh token", ErrorCodes.AUTH_INVALID_TOKEN)
            }
        }

        post("/logout") {
            val req = call.receive<RefreshTokenRequest>()
            service.logout(req.refreshToken)
            call.respond(HttpStatusCode.OK, MessageResponse("Logged out"))
        }

        post("/forgot-password") {
            if (rateLimitOr429(limiter, "forgot", RateLimits.FORGOT_LIMIT, RateLimits.FORGOT_WINDOW_SECONDS)) return@post
            val req = call.receive<ForgotPasswordRequest>()
            service.forgotPassword(req.email)
            call.respond(HttpStatusCode.OK, MessageResponse("If the email exists, a reset link has been sent"))
        }

        post("/reset-password") {
            val req = call.receive<ResetPasswordRequest>()
            try {
                service.resetPassword(req.token, req.newPassword, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, MessageResponse("Password updated. Please log in again."))
            } catch (_: TokenInvalidException) {
                throw BadRequestException("Invalid or expired token", ErrorCodes.AUTH_INVALID_TOKEN)
            } catch (e: WeakPasswordException) {
                throw BadRequestException(e.message ?: "Weak password", ErrorCodes.VALIDATION_WEAK_PASSWORD)
            }
        }

        post("/admin/accept-invite") {
            val req = call.receive<AcceptInviteRequest>()
            try {
                val auth = service.acceptInvite(req.token, req.password, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                throw BadRequestException("Invalid or expired token", ErrorCodes.AUTH_INVALID_TOKEN)
            } catch (e: WeakPasswordException) {
                throw BadRequestException(e.message ?: "Weak password", ErrorCodes.VALIDATION_WEAK_PASSWORD)
            }
        }

        // ----- Authenticated endpoints -----
        authenticate("auth-jwt") {

            post("/2fa/setup") {
                if (rateLimitOr429(limiter, "2fa-setup", RateLimits.TWOFA_LIMIT, RateLimits.TWOFA_WINDOW_SECONDS)) return@post
                val userId = call.requireUserId()
                try {
                    val resp = service.setupTwoFactor(userId, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.OK, TwoFactorSetupResponse(resp.secretBase32, resp.otpAuthUri))
                } catch (_: TwoFactorRoleException) {
                    throw ForbiddenException("2FA available only for admin/operator/inspector", ErrorCodes.AUTH_2FA_NOT_AVAILABLE)
                } catch (_: TwoFactorAlreadyEnabledException) {
                    throw ConflictException("2FA already enabled", ErrorCodes.AUTH_2FA_ALREADY_ENABLED)
                }
            }

            post("/2fa/verify") {
                if (rateLimitOr429(limiter, "2fa-verify", RateLimits.TWOFA_LIMIT, RateLimits.TWOFA_WINDOW_SECONDS)) return@post
                val userId = call.requireUserId()
                val req = call.receive<TwoFactorVerifyRequest>()
                try {
                    service.enableTwoFactor(userId, req.code, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.OK, MessageResponse("2FA enabled"))
                } catch (_: TwoFactorNotConfiguredException) {
                    throw BadRequestException("Call /auth/2fa/setup first", ErrorCodes.AUTH_2FA_SETUP_REQUIRED)
                } catch (_: TwoFactorAlreadyEnabledException) {
                    throw ConflictException("2FA already enabled", ErrorCodes.AUTH_2FA_ALREADY_ENABLED)
                } catch (_: InvalidTotpCodeException) {
                    throw UnauthorizedException("Invalid 2FA code", ErrorCodes.AUTH_2FA_INVALID)
                }
            }

            get("/sessions") {
                val userId = call.requireUserId()
                val list = service.listSessions(userId).map {
                    SessionDto(
                        id = it.id,
                        issuedIp = it.issuedIp,
                        userAgent = it.userAgent,
                        createdAt = it.createdAt,
                        expiresAt = it.expiresAt
                    )
                }
                call.respond(HttpStatusCode.OK, SessionsResponse(list))
            }

            delete("/sessions/{id}") {
                val userId = call.requireUserId()
                val sessionId = call.parameters["id"]?.toLongOrNull()
                    ?: throw BadRequestException("Invalid session id", ErrorCodes.VALIDATION_BAD_FIELD)
                val ok = service.revokeSession(userId, sessionId, call.clientIp(), call.userAgentSafe())
                if (ok) call.respond(HttpStatusCode.OK, MessageResponse("Session revoked"))
                else throw NotFoundException("Session not found", ErrorCodes.SESSION_NOT_FOUND)
            }

            delete("/me") {
                val userId = call.requireUserId()
                if (call.requireRole() != UserRole.RESIDENT) {
                    throw ForbiddenException("Сотрудники удаляются администратором")
                }
                service.deleteOwnAccount(userId, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.NoContent)
            }

            post("/admin/invite") {
                val actorId = call.requireUserId()
                val role = call.requireRole()
                if (role !in setOf(UserRole.ADMIN, UserRole.OPERATOR, UserRole.INSPECTOR)) {
                    throw ForbiddenException("Admins only")
                }
                val req = call.receive<AdminInviteRequest>()
                val targetRole = runCatching { UserRole.valueOf(req.role) }.getOrNull()
                if (targetRole == null || targetRole == UserRole.RESIDENT) {
                    throw BadRequestException("Invalid invite role", ErrorCodes.VALIDATION_BAD_FIELD)
                }
                try {
                    val invited = service.inviteAdmin(actorId, req.email, targetRole, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.Created, invited)
                } catch (e: InvalidEmailException) {
                    throw BadRequestException(e.message ?: "Invalid email", ErrorCodes.VALIDATION_INVALID_EMAIL)
                } catch (_: EmailAlreadyRegisteredException) {
                    throw ConflictException("Email already registered", ErrorCodes.EMAIL_ALREADY_REGISTERED)
                }
            }
        }
    }
}

internal fun io.ktor.server.application.ApplicationCall.requireUserId(): Long {
    return principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
        ?: throw UnauthorizedException("Not authenticated")
}

private fun io.ktor.server.application.ApplicationCall.requireRole(): UserRole {
    val raw = principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
    return runCatching { raw?.let { UserRole.valueOf(it) } }.getOrNull()
        ?: throw UnauthorizedException("Not authenticated")
}

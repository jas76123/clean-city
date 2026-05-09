package com.example.cleancity.auth

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

private val LOCKED_STATUS = HttpStatusCode(423, "Locked")

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
                call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Invalid email"))
            } catch (e: WeakPasswordException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Weak password"))
            } catch (_: TermsNotAcceptedException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse("Acceptance of terms is required"))
            } catch (_: EmailAlreadyRegisteredException) {
                call.respond(HttpStatusCode.Conflict, MessageResponse("Email already registered"))
            }
        }

        post("/verify-email") {
            val req = call.receive<VerifyEmailRequest>()
            try {
                val auth = service.verifyEmail(req.token, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid or expired token"))
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
                call.respond(LOCKED_STATUS, MessageResponse("Account locked. Try again after ${e.lockedUntil}"))
            } catch (_: EmailNotVerifiedException) {
                call.respond(HttpStatusCode.Forbidden, MessageResponse("Please verify your email first"))
            } catch (_: InvalidCredentialsException) {
                call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid email or password"))
            }
        }

        post("/login-2fa") {
            if (rateLimitOr429(limiter, "login-2fa", RateLimits.TWOFA_LIMIT, RateLimits.TWOFA_WINDOW_SECONDS)) return@post

            val req = call.receive<LoginTwoFactorRequest>()
            try {
                val auth = service.loginWithTwoFactor(req.challengeToken, req.code, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid or expired challenge"))
            } catch (_: InvalidTotpCodeException) {
                call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid 2FA code"))
            }
        }

        post("/refresh") {
            val req = call.receive<RefreshTokenRequest>()
            try {
                val auth = service.refresh(req.refreshToken, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid or expired refresh token"))
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
                call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid or expired token"))
            } catch (e: WeakPasswordException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Weak password"))
            }
        }

        post("/admin/accept-invite") {
            val req = call.receive<AcceptInviteRequest>()
            try {
                val auth = service.acceptInvite(req.token, req.password, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: TokenInvalidException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid or expired token"))
            } catch (e: WeakPasswordException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Weak password"))
            }
        }

        // ----- Authenticated endpoints -----
        authenticate("auth-jwt") {

            post("/2fa/setup") {
                if (rateLimitOr429(limiter, "2fa-setup", RateLimits.TWOFA_LIMIT, RateLimits.TWOFA_WINDOW_SECONDS)) return@post
                val userId = call.requireUserId() ?: return@post
                try {
                    val resp = service.setupTwoFactor(userId, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.OK, TwoFactorSetupResponse(resp.secretBase32, resp.otpAuthUri))
                } catch (_: TwoFactorRoleException) {
                    call.respond(HttpStatusCode.Forbidden, MessageResponse("2FA available only for admin/operator/inspector"))
                } catch (_: TwoFactorAlreadyEnabledException) {
                    call.respond(HttpStatusCode.Conflict, MessageResponse("2FA already enabled"))
                }
            }

            post("/2fa/verify") {
                if (rateLimitOr429(limiter, "2fa-verify", RateLimits.TWOFA_LIMIT, RateLimits.TWOFA_WINDOW_SECONDS)) return@post
                val userId = call.requireUserId() ?: return@post
                val req = call.receive<TwoFactorVerifyRequest>()
                try {
                    service.enableTwoFactor(userId, req.code, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.OK, MessageResponse("2FA enabled"))
                } catch (_: TwoFactorNotConfiguredException) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("Call /auth/2fa/setup first"))
                } catch (_: TwoFactorAlreadyEnabledException) {
                    call.respond(HttpStatusCode.Conflict, MessageResponse("2FA already enabled"))
                } catch (_: InvalidTotpCodeException) {
                    call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid 2FA code"))
                }
            }

            get("/sessions") {
                val userId = call.requireUserId() ?: return@get
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
                val userId = call.requireUserId() ?: return@delete
                val sessionId = call.parameters["id"]?.toLongOrNull()
                if (sessionId == null) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid session id"))
                    return@delete
                }
                val ok = service.revokeSession(userId, sessionId, call.clientIp(), call.userAgentSafe())
                if (ok) call.respond(HttpStatusCode.OK, MessageResponse("Session revoked"))
                else call.respond(HttpStatusCode.NotFound, MessageResponse("Session not found"))
            }

            post("/admin/invite") {
                val actorId = call.requireUserId() ?: return@post
                val role = call.requireRole() ?: return@post
                if (role !in setOf(UserRole.ADMIN, UserRole.OPERATOR, UserRole.INSPECTOR)) {
                    call.respond(HttpStatusCode.Forbidden, MessageResponse("Admins only"))
                    return@post
                }
                val req = call.receive<AdminInviteRequest>()
                val targetRole = runCatching { UserRole.valueOf(req.role) }.getOrNull()
                if (targetRole == null || targetRole == UserRole.RESIDENT) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid invite role"))
                    return@post
                }
                try {
                    val invited = service.inviteAdmin(actorId, req.email, targetRole, call.clientIp(), call.userAgentSafe())
                    call.respond(HttpStatusCode.Created, invited)
                } catch (e: InvalidEmailException) {
                    call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Invalid email"))
                } catch (_: EmailAlreadyRegisteredException) {
                    call.respond(HttpStatusCode.Conflict, MessageResponse("Email already registered"))
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.requireUserId(): Long? {
    val id = principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
    if (id == null) respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
    return id
}

private suspend fun io.ktor.server.application.ApplicationCall.requireRole(): UserRole? {
    val raw = principal<JWTPrincipal>()?.payload?.getClaim("role")?.asString()
    val role = runCatching { raw?.let { UserRole.valueOf(it) } }.getOrNull()
    if (role == null) respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
    return role
}

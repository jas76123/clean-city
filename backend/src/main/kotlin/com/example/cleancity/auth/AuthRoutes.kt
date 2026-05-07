package com.example.cleancity.auth

import com.example.cleancity.shared.models.MessageResponse
import com.example.cleancity.shared.requests.auth.ForgotPasswordRequest
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.RefreshTokenRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.ResendVerificationRequest
import com.example.cleancity.shared.requests.auth.ResetPasswordRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(service: AuthService) {
    route("/auth") {

        post("/register") {
            val req = call.receive<RegisterRequest>()
            try {
                val user = service.register(RegisterInput(req.email, req.password, req.fullName))
                call.respond(HttpStatusCode.Created, user)
            } catch (e: InvalidEmailException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Invalid email"))
            } catch (e: WeakPasswordException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Weak password"))
            } catch (e: EmailAlreadyRegisteredException) {
                call.respond(HttpStatusCode.Conflict, MessageResponse("Email already registered"))
            }
        }

        post("/verify-email") {
            val req = call.receive<VerifyEmailRequest>()
            try {
                val auth = service.verifyEmail(req.token, call.clientIp(), call.userAgent())
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
            val req = call.receive<LoginRequest>()
            try {
                val auth = service.login(LoginInput(req.email, req.password), call.clientIp(), call.userAgent())
                call.respond(HttpStatusCode.OK, auth)
            } catch (_: EmailNotVerifiedException) {
                call.respond(HttpStatusCode.Forbidden, MessageResponse("Please verify your email first"))
            } catch (_: InvalidCredentialsException) {
                call.respond(HttpStatusCode.Unauthorized, MessageResponse("Invalid email or password"))
            }
        }

        post("/refresh") {
            val req = call.receive<RefreshTokenRequest>()
            try {
                val auth = service.refresh(req.refreshToken, call.clientIp(), call.userAgent())
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
            val req = call.receive<ForgotPasswordRequest>()
            service.forgotPassword(req.email)
            // Всегда 200 — защита от user enumeration
            call.respond(HttpStatusCode.OK, MessageResponse("If the email exists, a reset link has been sent"))
        }

        post("/reset-password") {
            val req = call.receive<ResetPasswordRequest>()
            try {
                service.resetPassword(req.token, req.newPassword)
                call.respond(HttpStatusCode.OK, MessageResponse("Password updated. Please log in again."))
            } catch (_: TokenInvalidException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse("Invalid or expired token"))
            } catch (e: WeakPasswordException) {
                call.respond(HttpStatusCode.BadRequest, MessageResponse(e.message ?: "Weak password"))
            }
        }
    }
}

private fun ApplicationCall.clientIp(): String? =
    request.header("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
        ?: request.origin.remoteAddress?.takeIf { it.isNotBlank() }

private fun ApplicationCall.userAgent(): String? =
    request.header(HttpHeaders.UserAgent)?.take(500)

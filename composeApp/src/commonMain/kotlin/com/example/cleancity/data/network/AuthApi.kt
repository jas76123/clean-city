package com.example.cleancity.data.network

import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.requests.auth.ForgotPasswordRequest
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.RefreshTokenRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.ResendVerificationRequest
import com.example.cleancity.shared.requests.auth.ResetPasswordRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class AuthApi(private val client: HttpClient) {

    suspend fun register(req: RegisterRequest): UserResponse =
        client.post("/auth/register") { setBody(req) }.body()

    suspend fun verifyEmail(req: VerifyEmailRequest): AuthResponse =
        client.post("/auth/verify-email") { setBody(req) }.body()

    suspend fun resendVerification(req: ResendVerificationRequest) {
        client.post("/auth/resend-verification") { setBody(req) }
    }

    suspend fun login(req: LoginRequest): AuthResponse =
        client.post("/auth/login") { setBody(req) }.body()

    suspend fun refresh(req: RefreshTokenRequest): AuthResponse =
        client.post("/auth/refresh") { setBody(req) }.body()

    suspend fun logout() {
        client.post("/auth/logout")
    }

    suspend fun forgotPassword(req: ForgotPasswordRequest) {
        client.post("/auth/forgot-password") { setBody(req) }
    }

    suspend fun resetPassword(req: ResetPasswordRequest) {
        client.post("/auth/reset-password") { setBody(req) }
    }
}

package com.example.cleancity

import com.auth0.jwt.exceptions.JWTVerificationException
import com.example.cleancity.auth.AuthService
import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.auth.authRoutes
import com.example.cleancity.config.configureDatabase
import com.example.cleancity.email.EmailService
import com.example.cleancity.email.LoggingEmailService
import com.example.cleancity.email.SmtpEmailService
import com.example.cleancity.markers.MarkerRepository
import com.example.cleancity.markers.MarkerService
import com.example.cleancity.markers.markerRoutes
import com.example.cleancity.storage.LocalStorageService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureDatabase()

    install(ContentNegotiation) { json() }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
    }

    // Базовые security-заголовки добавляются на reverse-proxy (Caddy/Cloudflare).
    // Если нужно делать на уровне Ktor — добавить ktor-server-default-headers и
    // настроить здесь (см. SPEC § 8.2).

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to (cause.message ?: "Bad request")))
        }
        exception<Exception> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("message" to "Internal server error"))
        }
    }

    val jwtConfig = JwtConfig.fromEnvironment(environment)
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "CleanCity API"
            verifier(jwtConfig.verifier)
            validate { credential ->
                if (credential.payload.subject != null && credential.payload.getClaim("type").asString() == "access") {
                    JWTPrincipal(credential.payload)
                } else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("message" to "Token is not valid or has expired"))
            }
        }
    }

    val storagePath = environment.config.propertyOrNull("storage.path")?.getString()
        ?: System.getenv("STORAGE_PATH")
        ?: "./uploads"
    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
    val baseUrl = environment.config.propertyOrNull("app.base_url")?.getString() ?: "http://localhost:$port"

    val storage = LocalStorageService(storagePath, baseUrl)
    val markerService = MarkerService(MarkerRepository(), storage)

    val emailService = buildEmailService()
    val authService = AuthService(
        users = UserRepository(),
        tokens = TokenRepository(),
        email = emailService,
        jwt = jwtConfig,
        baseUrl = baseUrl
    )

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        authRoutes(authService)
        markerRoutes(markerService, storage)
    }
}

private fun Application.buildEmailService(): EmailService {
    val stage = environment.config.propertyOrNull("app.stage")?.getString()?.uppercase() ?: "DEV"
    val smtpHost = environment.config.propertyOrNull("email.smtp_host")?.getString().orEmpty()
    val smtpUser = environment.config.propertyOrNull("email.smtp_user")?.getString().orEmpty()

    val canUseSmtp = smtpHost.isNotBlank() && smtpUser.isNotBlank()
    if (stage == "DEV" || !canUseSmtp) {
        environment.log.info("EmailService: using LoggingEmailService (stage=$stage, smtp configured=$canUseSmtp)")
        return LoggingEmailService()
    }

    val smtpPort = environment.config.propertyOrNull("email.smtp_port")?.getString()?.toIntOrNull() ?: 465
    val smtpPassword = environment.config.propertyOrNull("email.smtp_password")?.getString().orEmpty()
    val from = environment.config.propertyOrNull("email.from")?.getString() ?: smtpUser

    environment.log.info("EmailService: using SmtpEmailService (host=$smtpHost:$smtpPort, from=$from)")
    return SmtpEmailService(
        host = smtpHost,
        port = smtpPort,
        username = smtpUser,
        password = smtpPassword,
        from = from
    )
}

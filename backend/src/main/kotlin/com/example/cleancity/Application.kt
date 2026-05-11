package com.example.cleancity

import com.auth0.jwt.exceptions.JWTVerificationException
import com.example.cleancity.auth.AuthService
import com.example.cleancity.auth.DbAuditLogger
import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.auth.RateLimiter
import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.TotpService
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.auth.authRoutes
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.complaints.ComplaintService
import com.example.cleancity.complaints.complaintRoutes
import com.example.cleancity.config.configureDatabase
import com.example.cleancity.email.EmailService
import com.example.cleancity.email.LoggingEmailService
import com.example.cleancity.email.SmtpEmailService
import com.example.cleancity.notifications.NoopNotificationService
import com.example.cleancity.storage.LocalStorageService
import com.example.cleancity.storage.S3StorageService
import com.example.cleancity.storage.StorageService
import com.example.cleancity.storage.photoRoutes
import com.example.cleancity.votes.VoteRepository
import com.example.cleancity.votes.VoteService
import com.example.cleancity.votes.voteRoutes
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
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, mapOf("message" to (cause.message ?: "Not found")))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, mapOf("message" to (cause.message ?: "Conflict")))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, mapOf("message" to (cause.message ?: "Forbidden")))
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

    val port = environment.config.propertyOrNull("ktor.deployment.port")?.getString() ?: "8080"
    val baseUrl = environment.config.propertyOrNull("app.base_url")?.getString() ?: "http://localhost:$port"

    val storage = buildStorage(baseUrl)

    val emailService = buildEmailService()
    val termsVersion = environment.config.propertyOrNull("app.terms_version")?.getString() ?: "v1"
    val authService = AuthService(
        users = UserRepository(),
        tokens = TokenRepository(),
        email = emailService,
        jwt = jwtConfig,
        baseUrl = baseUrl,
        termsVersion = termsVersion,
        totp = TotpService(),
        audit = DbAuditLogger()
    )
    val rateLimiter = RateLimiter()

    val complaintRepository = ComplaintRepository()
    val voteRepository = VoteRepository()
    val notificationService = NoopNotificationService()
    val complaintAudit = DbAuditLogger()
    val complaintService = ComplaintService(
        repo = complaintRepository,
        storage = storage,
        voteRepo = voteRepository,
        notifications = notificationService,
        audit = complaintAudit
    )
    val voteService = VoteService(voteRepository, complaintRepository)

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }
        authRoutes(authService, rateLimiter)
        complaintRoutes(complaintService)
        voteRoutes(voteService)
        if (storage is LocalStorageService) {
            photoRoutes(storage)
        }
    }
}

private fun Application.buildStorage(baseUrl: String): StorageService {
    val stage = environment.config.propertyOrNull("app.stage")?.getString()?.uppercase() ?: "DEV"
    if (stage == "PROD") {
        val bucket = environment.config.propertyOrNull("storage.s3.bucket")?.getString().orEmpty()
        val accessKey = environment.config.propertyOrNull("storage.s3.access_key")?.getString().orEmpty()
        val secretKey = environment.config.propertyOrNull("storage.s3.secret_key")?.getString().orEmpty()
        if (bucket.isNotBlank() && accessKey.isNotBlank() && secretKey.isNotBlank()) {
            val region = environment.config.propertyOrNull("storage.s3.region")?.getString() ?: "ru-central1"
            val endpoint = environment.config.propertyOrNull("storage.s3.endpoint")?.getString()
                ?: "https://storage.yandexcloud.net"
            val publicUrlBase = environment.config.propertyOrNull("storage.s3.public_url_base")?.getString()
                ?: "https://$bucket.storage.yandexcloud.net"
            environment.log.info("Storage: S3 (bucket=$bucket, endpoint=$endpoint)")
            return S3StorageService(bucket, region, endpoint, accessKey, secretKey, publicUrlBase)
        }
        environment.log.warn("Stage=PROD but S3 config incomplete — falling back to LocalStorageService")
    }
    val storagePath = environment.config.propertyOrNull("storage.path")?.getString()
        ?: System.getenv("STORAGE_PATH")
        ?: "./uploads"
    environment.log.info("Storage: Local (path=$storagePath)")
    return LocalStorageService(storagePath, baseUrl)
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

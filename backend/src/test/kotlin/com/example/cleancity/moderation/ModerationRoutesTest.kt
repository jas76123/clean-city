package com.example.cleancity.moderation

import com.example.cleancity.auth.DbAuditLogger
import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Users
import com.example.cleancity.auth.validateAccessPrincipal
import com.example.cleancity.notifications.DbNotificationService
import com.example.cleancity.notifications.NotificationRepository
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.testutils.installApiErrorHandling
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class ModerationRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:mod-routes-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, StatusChanges, Complaints, RefreshTokens, EmailTokens, AuditLog, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, Complaints, StatusChanges, Notifications, AuditLog)
        }
    }

    private fun seedUser(
        email: String,
        role: UserRole,
        isActive: Boolean = true,
    ): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "bcrypt-hash"
            it[Users.role] = role.name
            it[Users.fullName] = "Тест Тестов"
            it[Users.emailVerified] = true
            it[Users.isActive] = isActive
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun bearerFor(userId: Long, role: UserRole): String =
        jwtConfig.issueAccessToken(userId = userId, role = role).token

    private fun moderationService() = ModerationService(
        users = UserRepository(),
        complaints = ComplaintRepository(),
        tokens = TokenRepository(),
        notifications = DbNotificationService(NotificationRepository()),
        audit = DbAuditLogger(),
    )

    private fun ApplicationTestBuilder.appWithAuth() {
        val service = moderationService()
        application {
            install(ContentNegotiation) { json() }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { c -> validateAccessPrincipal(c.payload, UserRepository()) }
                }
            }
            installApiErrorHandling()
            routing { moderationRoutes(service) }
        }
    }

    @Test
    fun `resident cannot access moderation summary (403)`() {
        initDb()
        val residentId = seedUser("res@t.local", UserRole.RESIDENT)
        val targetId = seedUser("res2@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/residents/$targetId/moderation") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(residentId, UserRole.RESIDENT)}")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }

    @Test
    fun `operator can fetch moderation summary (200)`() {
        initDb()
        val operatorId = seedUser("op@t.local", UserRole.OPERATOR)
        val targetId = seedUser("res@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/residents/$targetId/moderation") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(operatorId, UserRole.OPERATOR)}")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
        }
    }

    @Test
    fun `banning a non-resident returns 403 MODERATION_NOT_RESIDENT`() {
        initDb()
        val adminId = seedUser("adm@t.local", UserRole.ADMIN)
        val otherOperator = seedUser("op2@t.local", UserRole.OPERATOR)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/residents/$otherOperator/ban") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"x"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }

    @Test
    fun `unauthenticated request returns 401`() {
        initDb()
        val targetId = seedUser("res@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/residents/$targetId/moderation")
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
    }

    @Test
    fun `banned (inactive) staff access token is rejected (401)`() {
        initDb()
        // Сотрудник с валидным по подписи токеном, но деактивированный (бан):
        // доступ должен пропадать немедленно, ещё до проверки роли в requireStaff.
        val bannedAdmin = seedUser("banned-adm@t.local", UserRole.ADMIN, isActive = false)
        val targetId = seedUser("res@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/residents/$targetId/moderation") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(bannedAdmin, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
    }

    @Test
    fun `admin can ban a resident (204)`() {
        initDb()
        val adminId = seedUser("adm@t.local", UserRole.ADMIN)
        val residentId = seedUser("res@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/residents/$residentId/ban") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"Нарушение"}""")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
        }
    }

    @Test
    fun `admin can unban a resident (204)`() {
        initDb()
        val adminId = seedUser("adm@t.local", UserRole.ADMIN)
        val residentId = seedUser("res@t.local", UserRole.RESIDENT, isActive = false)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/residents/$residentId/unban") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
        }
    }

    @Test
    fun `ban with blank reason returns 400 MODERATION_REASON_REQUIRED`() {
        initDb()
        val adminId = seedUser("adm@t.local", UserRole.ADMIN)
        val residentId = seedUser("res@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/residents/$residentId/ban") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"reason":"   "}""")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }
    }

    @Test
    fun `summary returns 404 for non-existent resident`() {
        initDb()
        val adminId = seedUser("adm@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/residents/99999/moderation") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NotFound, resp.status)
        }
    }
}

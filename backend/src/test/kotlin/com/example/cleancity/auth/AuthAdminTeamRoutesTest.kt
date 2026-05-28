package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.email.EmailService
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.responses.admin.AuditLogResponse
import com.example.cleancity.shared.responses.admin.TeamMembersResponse
import com.example.cleancity.testutils.installApiErrorHandling
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthAdminTeamRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )
    private val json = Json { ignoreUnknownKeys = true }

    private class NoopEmail : EmailService {
        override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) = Unit
    }

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:team-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(RefreshTokens, EmailTokens, AuditLog, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, AuditLog)
        }
    }

    private fun seedUser(
        email: String,
        role: UserRole,
        isActive: Boolean = true,
        emailVerified: Boolean = true
    ): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "bcrypt-hash"
            it[Users.role] = role.name
            it[Users.fullName] = "Тест Тестов"
            it[Users.emailVerified] = emailVerified
            it[Users.isActive] = isActive
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun bearerFor(userId: Long, role: UserRole): String =
        jwtConfig.issueAccessToken(userId = userId, role = role).token

    private fun ApplicationTestBuilder.appWithAuth() {
        val service = AuthService(
            users = UserRepository(),
            tokens = TokenRepository(),
            email = NoopEmail(),
            jwt = jwtConfig,
            baseUrl = "http://localhost:8080",
            termsVersion = "test-v1",
            totp = TotpService(),
            audit = DbAuditLogger()
        )
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { c ->
                        if (c.payload.subject != null &&
                            c.payload.getClaim("type").asString() == "access"
                        ) JWTPrincipal(c.payload) else null
                    }
                }
            }
            installApiErrorHandling()
            routing { authRoutes(service, RateLimiter()) }
        }
    }

    @Test
    fun `GET admin users active returns only active staff omitting residents`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        seedUser("op@t.local", UserRole.OPERATOR)
        seedUser("frozen@t.local", UserRole.OPERATOR, isActive = false)
        seedUser("resident@t.local", UserRole.RESIDENT)

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/users?status=ACTIVE") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = json.decodeFromString<TeamMembersResponse>(resp.bodyAsText())
            val emails = body.items.map { it.email }.toSet()
            assertEquals(setOf("admin@t.local", "op@t.local"), emails)
        }
    }

    @Test
    fun `OPERATOR can list team but cannot freeze`() {
        initDb()
        val opId = seedUser("op@t.local", UserRole.OPERATOR)
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val list = client.get("/auth/admin/users") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(opId, UserRole.OPERATOR)}")
            }
            assertEquals(HttpStatusCode.OK, list.status)

            val freeze = client.post("/auth/admin/users/$adminId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(opId, UserRole.OPERATOR)}")
            }
            assertEquals(HttpStatusCode.Forbidden, freeze.status)
        }
    }

    @Test
    fun `freeze revokes all refresh tokens of target`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        seedUser("admin2@t.local", UserRole.ADMIN)  // чтобы I2 не сработало
        val opId = seedUser("op@t.local", UserRole.OPERATOR)

        // выпустить refresh-токен напрямую через TokenRepository
        val rawToken = TokenGenerator.refreshToken()
        TokenRepository().createRefreshToken(
            userId = opId,
            rawToken = rawToken,
            ip = "1.1.1.1",
            userAgent = "test",
            ttlSeconds = 3600
        )

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$opId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)

            // Попытка использовать старый refresh-токен → 401
            val refresh = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$rawToken"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, refresh.status)
        }
    }

    @Test
    fun `freeze self returns 403`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        seedUser("admin2@t.local", UserRole.ADMIN)  // I2 не должно мешать

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$adminId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }

    @Test
    fun `freeze last active admin returns 409`() {
        initDb()
        // adminId — actor (JWT валиден, БД считает его неактивным; JWT не проверяет is_active).
        // targetAdminId — единственный активный ADMIN в БД. Freeze его → 0 активных → I2 → 409.
        val adminId = seedUser("admin@t.local", UserRole.ADMIN, isActive = false)
        val targetAdminId = seedUser("admin2@t.local", UserRole.ADMIN, isActive = true)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$targetAdminId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.Conflict, resp.status)
        }
    }

    @Test
    fun `freeze admin when other admin exists succeeds`() {
        initDb()
        val adminA = seedUser("a@t.local", UserRole.ADMIN)
        val adminB = seedUser("b@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$adminB/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminA, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
        }
    }

    @Test
    fun `unfreeze pending returns 400`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val pendingId = seedUser("pending@t.local", UserRole.OPERATOR, isActive = false, emailVerified = false)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$pendingId/unfreeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }
    }

    @Test
    fun `unfreeze frozen returns 204 and user becomes active`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val frozenId = seedUser("frozen@t.local", UserRole.OPERATOR, isActive = false, emailVerified = true)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/users/$frozenId/unfreeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
            transaction {
                val active = Users.selectAll().where { Users.id eq frozenId }.first()[Users.isActive]
                assertTrue(active)
            }
        }
    }

    @Test
    fun `revoke invitation deletes pending user and invalidates token`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val pendingId = seedUser("pending@t.local", UserRole.OPERATOR, isActive = false, emailVerified = false)
        TokenRepository().createEmailToken(pendingId, EmailTokenPurpose.ADMIN_INVITE, 3600)

        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/admin/invitations/$pendingId") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)
            transaction {
                val gone = Users.selectAll().where { Users.id eq pendingId }.count()
                assertEquals(0L, gone)
                val tokenRows = EmailTokens.selectAll().where { EmailTokens.userId eq pendingId }.toList()
                assertTrue(tokenRows.isEmpty())
            }
        }
    }

    @Test
    fun `revoke invitation on active user returns 400`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val activeId = seedUser("active@t.local", UserRole.OPERATOR)

        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/admin/invitations/$activeId") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }
    }

    @Test
    fun `audit log returns recent events with actor email`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        val opId = seedUser("op@t.local", UserRole.OPERATOR)
        seedUser("admin2@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            client.post("/auth/admin/users/$opId/freeze") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            val resp = client.get("/auth/admin/audit-log") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.OK, resp.status)
            val body = json.decodeFromString<AuditLogResponse>(resp.bodyAsText())
            val frozen = body.items.firstOrNull { it.action == AuditAction.ADMIN_USER_FROZEN.name }
            assertNotNull(frozen)
            assertEquals("admin@t.local", frozen.actorEmail)
        }
    }

    @Test
    fun `audit log limit max 50`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)
        repeat(60) { i ->
            transaction {
                AuditLog.insert {
                    it[AuditLog.actorUserId] = adminId
                    it[AuditLog.action] = AuditAction.LOGIN_SUCCESS.name
                    it[AuditLog.createdAt] = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(i.toLong())
                }
            }
        }

        testApplication {
            appWithAuth()
            val resp = client.get("/auth/admin/audit-log?limit=999") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            val body = json.decodeFromString<AuditLogResponse>(resp.bodyAsText())
            assertEquals(50, body.items.size)
        }
    }

    @Test
    fun `invite role inspector returns 400`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/invite") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"new@t.local","fullName":"X","role":"INSPECTOR"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
        }
    }

    @Test
    fun `operator cannot invite`() {
        initDb()
        val opId = seedUser("op@t.local", UserRole.OPERATOR)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/invite") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(opId, UserRole.OPERATOR)}")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"new@t.local","fullName":"X","role":"OPERATOR"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }

    @Test
    fun `invite with blank fullName returns 400 with VALIDATION_BAD_FIELD`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/invite") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"new@t.local","fullName":"   ","role":"OPERATOR"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            val body = resp.bodyAsText()
            assertTrue(body.contains("VALIDATION_BAD_FIELD"), "expected code VALIDATION_BAD_FIELD, got: $body")
        }
    }

    @Test
    fun `invite with missing fullName field returns 400 with VALIDATION_BAD_FIELD`() {
        initDb()
        val adminId = seedUser("admin@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/invite") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"new@t.local","role":"OPERATOR"}""")
            }
            assertEquals(HttpStatusCode.BadRequest, resp.status)
            val body = resp.bodyAsText()
            assertTrue(body.contains("VALIDATION_BAD_FIELD"), "expected code VALIDATION_BAD_FIELD, got: $body")
        }
    }

    @Test
    fun `invite with fullName creates pending user with that name`() {
        initDb()
        val adminId = seedUser("admin2@t.local", UserRole.ADMIN)

        testApplication {
            appWithAuth()
            val resp = client.post("/auth/admin/invite") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
                contentType(ContentType.Application.Json)
                setBody("""{"email":"named@t.local","fullName":"Пётр Петров","role":"OPERATOR"}""")
            }
            assertEquals(HttpStatusCode.Created, resp.status)
            val invited = transaction {
                Users.selectAll().where { Users.email eq "named@t.local" }.single()
            }
            assertEquals("Пётр Петров", invited[Users.fullName])
        }
    }
}

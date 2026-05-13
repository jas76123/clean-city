package com.example.cleancity.announcements

import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.database.tables.Announcements
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.Users
import com.example.cleancity.notifications.DbNotificationService
import com.example.cleancity.notifications.NotificationRepository
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.shared.models.AnnouncementsListResponse
import com.example.cleancity.shared.models.IconStyle
import com.example.cleancity.shared.models.UserRole
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class AnnouncementRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )
    private val json = Json { ignoreUnknownKeys = true }

    private data class Ctx(val adminId: Long, val residentId: Long)

    private fun initDb(): Ctx {
        Database.connect(
            "jdbc:h2:mem:announce-routes-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        return transaction {
            SchemaUtils.drop(Notifications, Announcements, Users)
            SchemaUtils.create(Users, Announcements, Notifications)
            val admin = Users.insert {
                it[Users.email] = "a@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.ADMIN.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
            val resident = Users.insert {
                it[Users.email] = "r@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.district] = "Центральный"
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
            Ctx(admin, resident)
        }
    }

    private fun bearerFor(userId: Long, role: UserRole): String =
        jwtConfig.issueAccessToken(userId = userId, role = role).token

    private fun ApplicationTestBuilder.appWith(service: AnnouncementService) {
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { c ->
                        if (c.payload.subject != null) JWTPrincipal(c.payload) else null
                    }
                }
            }
            install(StatusPages) {
                exception<com.example.cleancity.ForbiddenException> { call, cause ->
                    call.respond(HttpStatusCode.Forbidden, mapOf("message" to (cause.message ?: "Forbidden")))
                }
                exception<com.example.cleancity.NotFoundException> { call, cause ->
                    call.respond(HttpStatusCode.NotFound, mapOf("message" to (cause.message ?: "Not found")))
                }
                exception<IllegalArgumentException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, mapOf("message" to (cause.message ?: "Bad request")))
                }
            }
            routing { announcementRoutes(service) }
        }
    }

    private fun service(): AnnouncementService {
        val repo = AnnouncementRepository()
        val notifRepo = NotificationRepository()
        return AnnouncementService(repo, DbNotificationService(notifRepo))
    }

    @Test
    fun `GET announcements is open to guests`() = testApplication {
        val ctx = initDb()
        val svc = service()
        svc.create(
            com.example.cleancity.complaints.Viewer.Authenticated(ctx.adminId, UserRole.ADMIN),
            com.example.cleancity.shared.requests.CreateAnnouncementRequest(title = "Hi", body = "Body")
        )
        appWith(svc)

        val resp = client.get("/announcements")
        assertEquals(HttpStatusCode.OK, resp.status)
        val list = json.decodeFromString(AnnouncementsListResponse.serializer(), resp.bodyAsText())
        assertEquals(1, list.total)
        assertEquals("Hi", list.items.single().title)
    }

    @Test
    fun `POST as resident returns 403`() = testApplication {
        val ctx = initDb()
        val svc = service()
        appWith(svc)

        val body = buildJsonObject {
            put("title", "t"); put("body", "b")
        }
        val resp = client.post("/announcements") {
            header("Authorization", "Bearer ${bearerFor(ctx.residentId, UserRole.RESIDENT)}")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `POST as admin creates and triggers push to residents`() = testApplication {
        val ctx = initDb()
        val svc = service()
        appWith(svc)

        val body = buildJsonObject {
            put("title", "Субботник")
            put("body", "21 мая")
            put("iconStyle", "INFO")
            put("districts", buildJsonArray { })
        }
        val resp = client.post("/announcements") {
            header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val created = json.decodeFromString(AnnouncementResponse.serializer(), resp.bodyAsText())
        assertEquals("Субботник", created.title)
        assertEquals(IconStyle.INFO, created.iconStyle)

        val notif = transaction {
            Notifications.selectAll().toList()
        }
        assertEquals(1, notif.size)
        assertEquals(ctx.residentId, notif.single()[Notifications.userId])
    }

    @Test
    fun `PATCH updates and DELETE soft-expires`() = testApplication {
        val ctx = initDb()
        val svc = service()
        appWith(svc)

        val created = svc.create(
            com.example.cleancity.complaints.Viewer.Authenticated(ctx.adminId, UserRole.ADMIN),
            com.example.cleancity.shared.requests.CreateAnnouncementRequest(title = "old", body = "old")
        )

        val patchBody = buildJsonObject { put("title", "new") }
        val patchResp = client.patch("/announcements/${created.id}") {
            header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody(patchBody.toString())
        }
        assertEquals(HttpStatusCode.OK, patchResp.status)
        val updated = json.decodeFromString(AnnouncementResponse.serializer(), patchResp.bodyAsText())
        assertEquals("new", updated.title)

        val delResp = client.delete("/announcements/${created.id}") {
            header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
        }
        assertEquals(HttpStatusCode.NoContent, delResp.status)

        val listResp = client.get("/announcements")
        val list = json.decodeFromString(AnnouncementsListResponse.serializer(), listResp.bodyAsText())
        assertEquals(0, list.total, "expired announcement must not appear")
    }

    @Test
    fun `PATCH of missing id returns 404`() = testApplication {
        val ctx = initDb()
        val svc = service()
        appWith(svc)

        val body = buildJsonObject { put("title", "x") }
        val resp = client.patch("/announcements/9999") {
            header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }
}

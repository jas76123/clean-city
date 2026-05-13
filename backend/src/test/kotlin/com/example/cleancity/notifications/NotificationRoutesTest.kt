package com.example.cleancity.notifications

import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.Users
import com.example.cleancity.testutils.installApiErrorHandling
import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import com.example.cleancity.shared.models.UserRole
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )

    private fun initDb(): Triple<Long, Long, Long> {
        Database.connect(
            "jdbc:h2:mem:routes-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        return transaction {
            SchemaUtils.drop(Notifications, Complaints, Users)
            SchemaUtils.create(Users, Complaints, Notifications)
            val a = Users.insert {
                it[Users.email] = "a@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
            val b = Users.insert {
                it[Users.email] = "b@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
            val cid = Complaints.insert {
                it[Complaints.authorId] = a; it[Complaints.category] = "GARBAGE"
                it[Complaints.title] = "t"; it[Complaints.description] = "d"
                it[Complaints.latitude] = 43.6; it[Complaints.longitude] = 39.7
                it[Complaints.address] = "addr"; it[Complaints.status] = "NEW"
                it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Complaints.id]
            Triple(a, b, cid)
        }
    }

    private fun seedNotification(
        userId: Long,
        complaintId: Long,
        title: String,
        readAt: OffsetDateTime? = null,
        createdAt: OffsetDateTime? = null
    ): Long = transaction {
        Notifications.insert {
            it[Notifications.userId] = userId
            it[Notifications.kind] = "COMPLAINT_STATUS"
            it[Notifications.title] = title; it[Notifications.body] = "x"
            it[Notifications.iconStyle] = "INFO"
            it[Notifications.complaintId] = complaintId
            it[Notifications.createdAt] = createdAt ?: OffsetDateTime.now(ZoneOffset.UTC)
            it[Notifications.readAt] = readAt
        }[Notifications.id]
    }

    private fun bearerFor(userId: Long): String =
        jwtConfig.issueAccessToken(userId = userId, role = UserRole.RESIDENT).token

    private fun ApplicationTestBuilder.appWith(repo: NotificationRepository) {
        application {
            install(ContentNegotiation) { json() }
            installApiErrorHandling()
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { credential ->
                        if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
                    }
                }
            }
            routing { notificationRoutes(repo) }
        }
    }

    @Test
    fun `GET notifications without JWT returns 401`() = testApplication {
        initDb()
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET notifications returns only own items`() = testApplication {
        val (a, b, cid) = initDb()
        repeat(3) { seedNotification(a, cid, "for-a-$it") }
        repeat(2) { seedNotification(b, cid, "for-b-$it") }

        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.decodeFromString(NotificationListResponse.serializer(), resp.bodyAsText())
        assertEquals(3, body.total)
        assertEquals(3, body.items.size)
        body.items.forEach { assertEquals(true, it.title.startsWith("for-a-")) }
    }

    @Test
    fun `GET notifications honors limit and hasMore`() = testApplication {
        val (a, _, cid) = initDb()
        repeat(5) { seedNotification(a, cid, "n$it") }
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications?limit=2") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        val body = Json.decodeFromString(NotificationListResponse.serializer(), resp.bodyAsText())
        assertEquals(5, body.total)
        assertEquals(2, body.items.size)
        assertEquals(true, body.hasMore)

        val resp2 = client.get("/notifications?limit=2&offset=4") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        val body2 = Json.decodeFromString(NotificationListResponse.serializer(), resp2.bodyAsText())
        assertEquals(1, body2.items.size)
        assertEquals(false, body2.hasMore)
    }

    @Test
    fun `GET unread-count counts only unread`() = testApplication {
        val (a, _, cid) = initDb()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        repeat(2) { seedNotification(a, cid, "u$it") }
        seedNotification(a, cid, "read", readAt = now)
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.get("/notifications/unread-count") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        val body = Json.decodeFromString(UnreadCountResponse.serializer(), resp.bodyAsText())
        assertEquals(2L, body.count)
    }

    @Test
    fun `PATCH read own returns 204 and idempotent`() = testApplication {
        val (a, _, cid) = initDb()
        val nid = seedNotification(a, cid, "x")
        val repo = NotificationRepository()
        appWith(repo)

        val r1 = client.patch("/notifications/$nid/read") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.NoContent, r1.status)

        val r2 = client.patch("/notifications/$nid/read") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.NoContent, r2.status)
    }

    @Test
    fun `PATCH read of foreign notification returns 404`() = testApplication {
        val (a, b, cid) = initDb()
        val nid = seedNotification(a, cid, "x")
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.patch("/notifications/$nid/read") {
            header("Authorization", "Bearer ${bearerFor(b)}")
        }
        assertEquals(HttpStatusCode.NotFound, resp.status)
    }

    @Test
    fun `PATCH read-all marks own unread and returns count`() = testApplication {
        val (a, b, cid) = initDb()
        repeat(3) { seedNotification(a, cid, "u$it") }
        seedNotification(a, cid, "alreadyRead", readAt = OffsetDateTime.now(ZoneOffset.UTC))
        seedNotification(b, cid, "for-b")
        val repo = NotificationRepository()
        appWith(repo)

        val resp = client.patch("/notifications/read-all") {
            header("Authorization", "Bearer ${bearerFor(a)}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = Json.decodeFromString(MarkAllReadResponse.serializer(), resp.bodyAsText())
        assertEquals(3, body.markedCount)
    }
}

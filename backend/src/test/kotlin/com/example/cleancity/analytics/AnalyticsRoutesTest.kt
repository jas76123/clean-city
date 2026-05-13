package com.example.cleancity.analytics

import com.example.cleancity.auth.JwtConfig
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Users
import com.example.cleancity.database.tables.Votes
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.testutils.installApiErrorHandling
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.application.*
import io.ktor.server.response.*
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

class AnalyticsRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )
    private val json = Json { ignoreUnknownKeys = true }

    private data class Ctx(val adminId: Long, val residentId: Long)

    private fun initDb(): Ctx {
        Database.connect(
            "jdbc:h2:mem:analytics-routes-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        return transaction {
            SchemaUtils.drop(Votes, Complaints, Users)
            SchemaUtils.create(Users, Complaints, Votes)
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val admin = Users.insert {
                it[Users.email] = "a@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.ADMIN.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = now
                it[Users.passwordChangedAt] = now
            }[Users.id]
            val resident = Users.insert {
                it[Users.email] = "r@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.emailVerified] = true; it[Users.isActive] = true
                it[Users.createdAt] = now
                it[Users.passwordChangedAt] = now
            }[Users.id]
            Ctx(admin, resident)
        }
    }

    private fun bearerFor(userId: Long, role: UserRole): String =
        jwtConfig.issueAccessToken(userId = userId, role = role).token

    private fun ApplicationTestBuilder.appWith() {
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
            installApiErrorHandling()
            routing { analyticsRoutes(AnalyticsService(AnalyticsRepository())) }
        }
    }

    @Test
    fun `guest gets 401 on analytics`() = testApplication {
        initDb()
        appWith()

        val resp = client.get("/analytics/overview")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `resident gets 403 on analytics`() = testApplication {
        val ctx = initDb()
        appWith()

        val resp = client.get("/analytics/overview") {
            header("Authorization", "Bearer ${bearerFor(ctx.residentId, UserRole.RESIDENT)}")
        }
        assertEquals(HttpStatusCode.Forbidden, resp.status)
    }

    @Test
    fun `admin gets 200 on overview`() = testApplication {
        val ctx = initDb()
        appWith()

        val resp = client.get("/analytics/overview") {
            header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertEquals(true, body.contains("\"total\":0"), "Empty DB → total=0; body=$body")
    }

    @Test
    fun `invalid period returns 400`() = testApplication {
        val ctx = initDb()
        appWith()

        val resp = client.get("/analytics/by-category?period=YEAR") {
            header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
        }
        assertEquals(HttpStatusCode.BadRequest, resp.status)
    }

    @Test
    fun `all four parametrized endpoints return 200 for admin`() = testApplication {
        val ctx = initDb()
        appWith()

        listOf("/analytics/by-category", "/analytics/by-district", "/analytics/sla", "/analytics/votes-impact").forEach { path ->
            val resp = client.get("$path?period=week") {
                header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.OK, resp.status, "$path should be 200, was ${resp.status}")
        }
    }
}

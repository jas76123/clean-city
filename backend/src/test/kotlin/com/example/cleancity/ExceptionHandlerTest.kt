package com.example.cleancity

import com.example.cleancity.shared.models.ApiError
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExceptionHandlerTest {

    private fun io.ktor.server.testing.ApplicationTestBuilder.boot(block: io.ktor.server.routing.Route.() -> Unit) {
        application {
            install(ContentNegotiation) { json() }
            install(StatusPages) {
                exception<ApiException> { call, cause ->
                    call.respond(cause.status, ApiError(cause.code, cause.message ?: cause.code))
                }
                exception<IllegalArgumentException> { call, cause ->
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ApiError(ErrorCodes.BAD_REQUEST, cause.message ?: "Bad request")
                    )
                }
                exception<Throwable> { call, _ ->
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiError(ErrorCodes.INTERNAL, "Internal server error")
                    )
                }
            }
            routing(block)
        }
    }

    private fun decode(body: String): ApiError = Json.decodeFromString(ApiError.serializer(), body)

    @Test
    fun `BadRequestException returns 400 with code`() = testApplication {
        boot {
            get("/x") { throw BadRequestException("Email invalid", ErrorCodes.VALIDATION_INVALID_EMAIL) }
        }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val body = decode(resp.bodyAsText())
        assertEquals(ErrorCodes.VALIDATION_INVALID_EMAIL, body.code)
        assertEquals("Email invalid", body.message)
    }

    @Test
    fun `UnauthorizedException returns 401`() = testApplication {
        boot {
            get("/x") { throw UnauthorizedException("Invalid creds", ErrorCodes.AUTH_INVALID_CREDENTIALS) }
        }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
        val body = decode(resp.bodyAsText())
        assertEquals(ErrorCodes.AUTH_INVALID_CREDENTIALS, body.code)
    }

    @Test
    fun `ForbiddenException returns 403`() = testApplication {
        boot { get("/x") { throw ForbiddenException("nope") } }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.Forbidden, resp.status)
        assertEquals(ErrorCodes.FORBIDDEN, decode(resp.bodyAsText()).code)
    }

    @Test
    fun `NotFoundException returns 404`() = testApplication {
        boot { get("/x") { throw NotFoundException("gone") } }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.NotFound, resp.status)
        assertEquals(ErrorCodes.NOT_FOUND, decode(resp.bodyAsText()).code)
    }

    @Test
    fun `ConflictException returns 409`() = testApplication {
        boot { get("/x") { throw ConflictException("dup") } }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.Conflict, resp.status)
        assertEquals(ErrorCodes.CONFLICT, decode(resp.bodyAsText()).code)
    }

    @Test
    fun `RateLimitedException returns 429`() = testApplication {
        boot { get("/x") { throw RateLimitedException() } }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.TooManyRequests, resp.status)
        assertEquals(ErrorCodes.RATE_LIMITED, decode(resp.bodyAsText()).code)
    }

    @Test
    fun `IllegalArgumentException returns 400 BAD_REQUEST`() = testApplication {
        boot { get("/x") { throw IllegalArgumentException("Bad latitude") } }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.BadRequest, resp.status)
        val body = decode(resp.bodyAsText())
        assertEquals(ErrorCodes.BAD_REQUEST, body.code)
        assertEquals("Bad latitude", body.message)
    }

    @Test
    fun `Throwable returns 500 INTERNAL without stacktrace`() = testApplication {
        boot {
            get("/x") { throw RuntimeException("very/internal/path/secret.kt:42 NullPointerException") }
        }
        val resp = client.get("/x")
        assertEquals(HttpStatusCode.InternalServerError, resp.status)
        val raw = resp.bodyAsText()
        val body = decode(raw)
        assertEquals(ErrorCodes.INTERNAL, body.code)
        assertEquals("Internal server error", body.message)
        // No internal details should leak — original RuntimeException message must be masked
        assertFalse(raw.contains("secret.kt"), "Stacktrace/internal path must not leak in error body: $raw")
        assertFalse(raw.contains("NullPointerException"), "Internal class names must not leak: $raw")
    }

    @Test
    fun `error body has only code and message fields`() = testApplication {
        boot { get("/x") { throw NotFoundException("gone") } }
        val resp = client.get("/x")
        val raw = resp.bodyAsText()
        // Round-trip parse: should be exactly the ApiError shape
        val body = decode(raw)
        assertEquals(2, raw.count { it == ':' }, "Expected exactly {code, message} pair, got: $raw")
        assertTrue(body.code.isNotBlank())
        assertTrue(body.message.isNotBlank())
    }
}

package com.example.cleancity.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsApiTest {

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        }
        defaultRequest { url("http://localhost/") }
    }

    @Test
    fun `list passes limit and parses items`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"items":[
                    {"id":1,"kind":"COMPLAINT_STATUS","title":"t","body":"b","complaintId":7,"createdAt":"2026-05-21T10:00:00Z"}
                ],"total":1,"hasMore":false}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = NotificationsApi(httpClient(engine))

        val result = api.list(limit = 50)

        assertTrue(capturedUrl!!.contains("limit=50"))
        assertEquals(1, result.items.size)
        assertEquals(7L, result.items[0].complaintId)
    }

    @Test
    fun `markRead sends PATCH to notification id`() = runTest {
        var method: HttpMethod? = null
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            method = request.method
            capturedUrl = request.url.toString()
            respond("", HttpStatusCode.OK)
        }
        val api = NotificationsApi(httpClient(engine))

        api.markRead(42L)

        assertEquals(HttpMethod.Patch, method)
        assertTrue(capturedUrl!!.contains("/notifications/42/read"))
    }

    @Test
    fun `markAllRead sends PATCH and parses markedCount`() = runTest {
        var method: HttpMethod? = null
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            method = request.method
            capturedUrl = request.url.toString()
            respond(
                content = """{"markedCount":3}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = NotificationsApi(httpClient(engine))

        val result = api.markAllRead()

        assertEquals(HttpMethod.Patch, method)
        assertTrue(capturedUrl!!.contains("/notifications/read-all"))
        assertEquals(3, result.markedCount)
    }
}

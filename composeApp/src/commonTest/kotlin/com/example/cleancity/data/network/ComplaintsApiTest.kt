package com.example.cleancity.data.network

import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplaintsApiTest {

    @Test
    fun `getMapMarkers passes bbox and category to query string`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"markers":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ComplaintsApi(httpClient(engine))

        api.getMapMarkers(43.40, 39.55, 43.75, 40.05, ProblemCategory.GARBAGE)

        assertTrue(capturedUrl!!.contains("swLat=43.4"))
        assertTrue(capturedUrl!!.contains("swLon=39.55"))
        assertTrue(capturedUrl!!.contains("neLat=43.75"))
        assertTrue(capturedUrl!!.contains("neLon=40.05"))
        assertTrue(capturedUrl!!.contains("category=GARBAGE"))
    }

    @Test
    fun `getMapMarkers omits category param when null`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"markers":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ComplaintsApi(httpClient(engine))

        api.getMapMarkers(43.40, 39.55, 43.75, 40.05, category = null)

        assertTrue(!capturedUrl!!.contains("category="))
    }

    @Test
    fun `getMapMarkers parses response`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"markers":[
                    {"id":1,"category":"GARBAGE","status":"NEW","latitude":43.5,"longitude":39.7},
                    {"id":2,"category":"ROADS","status":"IN_PROGRESS","latitude":43.6,"longitude":39.8}
                ]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ComplaintsApi(httpClient(engine))

        val response: MapMarkersResponse =
            api.getMapMarkers(43.40, 39.55, 43.75, 40.05, null)

        assertEquals(2, response.markers.size)
        assertEquals(1L, response.markers[0].id)
        assertEquals(ProblemCategory.GARBAGE, response.markers[0].category)
        assertEquals(ComplaintStatus.NEW, response.markers[0].status)
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        }
        defaultRequest { url("http://localhost/") }
    }
}

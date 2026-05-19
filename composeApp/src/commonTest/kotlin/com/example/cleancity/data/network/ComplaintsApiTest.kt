package com.example.cleancity.data.network

import com.example.cleancity.domain.photo.PhotoBytes
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.requests.CreateComplaintRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `findDuplicates passes lat, lon, category and radius`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"items":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ComplaintsApi(httpClient(engine))

        api.findDuplicates(43.5855, 39.7232, ProblemCategory.GARBAGE, radiusMeters = 100)

        assertContains(capturedUrl!!, "lat=43.5855")
        assertContains(capturedUrl!!, "lon=39.7232")
        assertContains(capturedUrl!!, "category=GARBAGE")
        assertContains(capturedUrl!!, "radius=100")
    }

    @Test
    fun `findDuplicates omits radius when null`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"items":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ComplaintsApi(httpClient(engine))

        api.findDuplicates(43.5, 39.7, ProblemCategory.ROADS, radiusMeters = null)

        assertTrue(!capturedUrl!!.contains("radius="))
    }

    @Test
    fun `create sends multipart with data JSON and photo files`() = runTest {
        var capturedContentType: String? = null
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedContentType = request.body.contentType?.toString()
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"id":42,"authorId":1,"title":"Мусор","category":"GARBAGE","status":"NEW",
                    "latitude":43.5,"longitude":39.7,"address":"ул. Транспортная, 14",
                    "description":"тест","photos":[],"votesCount":1,"userVoted":true,
                    "statusHistory":[],"createdAt":"2026-05-19T10:00:00Z",
                    "updatedAt":"2026-05-19T10:00:00Z"}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ComplaintsApi(httpClient(engine))

        val response = api.create(
            request = CreateComplaintRequest(
                category = ProblemCategory.GARBAGE,
                description = "тест",
                latitude = 43.5,
                longitude = 39.7,
                address = "ул. Транспортная, 14",
                district = null,
            ),
            photos = listOf(
                PhotoBytes(byteArrayOf(1, 2, 3), "p1.jpg"),
                PhotoBytes(byteArrayOf(4, 5, 6), "p2.jpg"),
            ),
        )

        assertEquals(42L, response.id)
        assertContains(capturedContentType!!, "multipart/form-data")
        // "data" form-part with our JSON request
        assertContains(capturedBody!!, "name=data")
        assertContains(capturedBody!!, "\"category\":\"GARBAGE\"")
        assertContains(capturedBody!!, "\"address\":\"ул. Транспортная, 14\"")
        // Two photo parts
        assertContains(capturedBody!!, "name=photo")
        assertContains(capturedBody!!, "filename=\"p1.jpg\"")
        assertContains(capturedBody!!, "filename=\"p2.jpg\"")
    }

    @Test
    fun `create requires at least 1 photo`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val api = ComplaintsApi(httpClient(engine))

        assertFailsWith<IllegalArgumentException> {
            api.create(
                request = CreateComplaintRequest(
                    category = ProblemCategory.GARBAGE,
                    description = "тест",
                    latitude = 43.5,
                    longitude = 39.7,
                    address = "ул. Транспортная",
                ),
                photos = emptyList(),
            )
        }
    }

    @Test
    fun `create rejects more than 5 photos`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        val api = ComplaintsApi(httpClient(engine))

        val six = List(6) { PhotoBytes(byteArrayOf(it.toByte()), "p$it.jpg") }
        assertFailsWith<IllegalArgumentException> {
            api.create(
                request = CreateComplaintRequest(
                    category = ProblemCategory.ROADS,
                    description = "x",
                    latitude = 43.5,
                    longitude = 39.7,
                    address = "ул",
                ),
                photos = six,
            )
        }
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        }
        defaultRequest { url("http://localhost/") }
    }
}

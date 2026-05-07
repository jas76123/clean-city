package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.storage.LocalStorageService
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MarkerRoutesTest {

    private lateinit var storage: LocalStorageService
    private lateinit var service: MarkerService
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.drop(Complaints)
            SchemaUtils.create(Complaints)
        }
        val storagePath = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.currentTimeMillis()}").absolutePath
        storage = LocalStorageService(storagePath, "http://localhost:8080")
        service = MarkerService(MarkerRepository(), storage)
    }

    private fun ApplicationTestBuilder.configureTestApp() {
        install(ContentNegotiation) { json() }
        routing { markerRoutes(service, storage) }
    }

    @Test
    fun `POST complaint returns 201 with response`() = testApplication {
        configureTestApp()

        val response = client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"category":"GARBAGE","description":"Незаконная свалка","latitude":43.585,"longitude":39.723,"address":"ул. Ленина, 42","deviceId":"device-1"}""")
                append("photo", "fake-jpeg".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }
        )

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ComplaintResponse>(response.bodyAsText())
        assertEquals("Незаконная свалка", body.description)
    }

    @Test
    fun `POST complaint without photo returns 400`() = testApplication {
        configureTestApp()

        val response = client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"category":"GARBAGE","description":"desc","latitude":43.0,"longitude":39.0,"address":"addr","deviceId":"d1"}""")
            }
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET markers returns all complaints`() = testApplication {
        configureTestApp()

        client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"category":"ROADS","description":"Pothole","latitude":43.0,"longitude":39.0,"address":"addr","deviceId":"d1"}""")
                append("photo", "jpeg".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"p.jpg\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }
        )

        val response = client.get("/api/markers")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<MapMarkersResponse>(response.bodyAsText())
        assertEquals(1, body.complaints.size)
    }

    @Test
    fun `GET complaint by id returns 200`() = testApplication {
        configureTestApp()

        val createResponse = client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"category":"GARBAGE","description":"desc","latitude":43.0,"longitude":39.0,"address":"addr","deviceId":"d1"}""")
                append("photo", "jpeg".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"p.jpg\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }
        )
        val created = json.decodeFromString<ComplaintResponse>(createResponse.bodyAsText())

        val response = client.get("/api/complaints/${created.id}")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET nonexistent complaint returns 404`() = testApplication {
        configureTestApp()

        val response = client.get("/api/complaints/999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}

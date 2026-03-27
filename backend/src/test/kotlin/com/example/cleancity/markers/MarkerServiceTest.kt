package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Subbotniks
import com.example.cleancity.shared.models.MarkerStatus
import com.example.cleancity.shared.models.ProblemType
import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.shared.requests.CreateSubbotnikRequest
import com.example.cleancity.storage.LocalStorageService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MarkerServiceTest {

    private lateinit var service: MarkerService
    private lateinit var storagePath: String

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.drop(Complaints, Subbotniks)
            SchemaUtils.create(Complaints, Subbotniks)
        }
        storagePath = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.currentTimeMillis()}").absolutePath
        val storage = LocalStorageService(storagePath, "http://localhost:8080")
        service = MarkerService(MarkerRepository(), storage)
    }

    @Test
    fun `createComplaint returns response with photo URL`() {
        val request = CreateComplaintRequest(
            type = ProblemType.DUMP,
            description = "Illegal dump",
            latitude = 43.585,
            longitude = 39.723,
            address = "ул. Ленина, 42",
            deviceId = "device-1"
        )
        val photo = "fake-jpeg-data".toByteArray()

        val response = service.createComplaint(request, photo, "photo.jpg")

        assertNotNull(response)
        assertEquals(ProblemType.DUMP, response.type)
        assertEquals("Illegal dump", response.description)
        assertTrue(response.photoUrl.startsWith("http://localhost:8080/api/photos/"))
        assertEquals(MarkerStatus.NEW, response.status)
    }

    @Test
    fun `createSubbotnik works without photo`() {
        val request = CreateSubbotnikRequest(
            title = "Park cleanup",
            description = "Bring gloves",
            date = "2026-04-01",
            time = "10:00",
            latitude = 43.585,
            longitude = 39.723,
            address = "Сквер Победы",
            deviceId = "device-1"
        )

        val response = service.createSubbotnik(request, null, null)

        assertNotNull(response)
        assertEquals("Park cleanup", response.title)
        assertEquals(null, response.photoUrl)
    }

    @Test
    fun `createComplaint rejects oversized photo`() {
        val request = CreateComplaintRequest(
            type = ProblemType.ROAD,
            description = "Pothole",
            latitude = 43.0,
            longitude = 39.0,
            address = "addr",
            deviceId = "d1"
        )
        val bigPhoto = ByteArray(11 * 1024 * 1024) // 11 MB

        assertFailsWith<IllegalArgumentException> {
            service.createComplaint(request, bigPhoto, "big.jpg")
        }
    }

    @Test
    fun `createComplaint rejects invalid file extension`() {
        val request = CreateComplaintRequest(
            type = ProblemType.ROAD,
            description = "Pothole",
            latitude = 43.0,
            longitude = 39.0,
            address = "addr",
            deviceId = "d1"
        )

        assertFailsWith<IllegalArgumentException> {
            service.createComplaint(request, "data".toByteArray(), "file.gif")
        }
    }

    @Test
    fun `getMarkers returns both types`() {
        val complaint = CreateComplaintRequest(ProblemType.DUMP, "desc", 43.0, 39.0, "addr", "d1")
        service.createComplaint(complaint, "data".toByteArray(), "p.jpg")

        val subbotnik = CreateSubbotnikRequest("Title", "desc", "2026-04-01", "10:00", 43.0, 39.0, "addr", "d1")
        service.createSubbotnik(subbotnik, null, null)

        val markers = service.getAllMarkers()

        assertEquals(1, markers.complaints.size)
        assertEquals(1, markers.subbotniks.size)
    }
}

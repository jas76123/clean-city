package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.requests.CreateComplaintRequest
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
            SchemaUtils.drop(Complaints)
            SchemaUtils.create(Complaints)
        }
        storagePath = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.currentTimeMillis()}").absolutePath
        val storage = LocalStorageService(storagePath, "http://localhost:8080")
        service = MarkerService(MarkerRepository(), storage)
    }

    @Test
    fun `createComplaint returns response with photo URL`() {
        val request = CreateComplaintRequest(
            category = ProblemCategory.GARBAGE,
            description = "Незаконная свалка",
            latitude = 43.585,
            longitude = 39.723,
            address = "ул. Ленина, 42",
            deviceId = "device-1"
        )
        val photo = "fake-jpeg-data".toByteArray()

        val response = service.createComplaint(request, photo, "photo.jpg")

        assertNotNull(response)
        assertEquals(ProblemCategory.GARBAGE, response.category)
        assertEquals("Незаконная свалка", response.description)
        assertTrue(response.photoUrl.startsWith("http://localhost:8080/api/photos/"))
        assertEquals(ComplaintStatus.NEW, response.status)
    }

    @Test
    fun `createComplaint rejects oversized photo`() {
        val request = CreateComplaintRequest(
            category = ProblemCategory.ROADS,
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
            category = ProblemCategory.ROADS,
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
    fun `getMarkers returns all complaints`() {
        val complaint = CreateComplaintRequest(ProblemCategory.GARBAGE, "desc", 43.0, 39.0, "addr", "d1")
        service.createComplaint(complaint, "data".toByteArray(), "p.jpg")

        val markers = service.getAllMarkers()

        assertEquals(1, markers.complaints.size)
    }

    @Test
    fun `createComplaint accepts all 18 categories`() {
        val photo = "x".toByteArray()
        ProblemCategory.entries.forEachIndexed { i, cat ->
            val req = CreateComplaintRequest(
                category = cat,
                description = "desc-$i",
                latitude = 43.0,
                longitude = 39.0,
                address = "addr-$i",
                deviceId = "d-$i"
            )
            val r = service.createComplaint(req, photo, "p.jpg")
            assertEquals(cat, r.category)
        }
    }
}

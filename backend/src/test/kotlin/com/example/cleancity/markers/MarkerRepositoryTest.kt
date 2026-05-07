package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MarkerRepositoryTest {

    private lateinit var repo: MarkerRepository

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.drop(Complaints)
            SchemaUtils.create(Complaints)
        }
        repo = MarkerRepository()
    }

    @Test
    fun `createComplaint inserts and returns complaint`() {
        val result = repo.createComplaint(
            category = "GARBAGE",
            description = "Illegal dump near park",
            photoPath = "abc123.jpg",
            latitude = 43.585,
            longitude = 39.723,
            address = "ул. Ленина, 42",
            deviceId = "device-1"
        )

        assertNotNull(result)
        assertEquals("GARBAGE", result.category)
        assertEquals("Illegal dump near park", result.description)
        assertEquals("abc123.jpg", result.photoPath)
        assertEquals(43.585, result.latitude)
        assertEquals(39.723, result.longitude)
        assertEquals("NEW", result.status)
    }

    @Test
    fun `getAllComplaints returns inserted complaints`() {
        repo.createComplaint("ROADS", "Pothole", "p.jpg", 43.0, 39.0, "addr", "d1")
        repo.createComplaint("LIGHTING", "Dark street", "d.jpg", 43.1, 39.1, "addr2", "d2")

        val all = repo.getAllComplaints()

        assertEquals(2, all.size)
    }

    @Test
    fun `getComplaintById returns correct complaint`() {
        val created = repo.createComplaint("GARBAGE", "desc", "p.jpg", 43.0, 39.0, "addr", "d1")

        val found = repo.getComplaintById(created.id)

        assertNotNull(found)
        assertEquals(created.id, found.id)
    }

    @Test
    fun `getComplaintsInBounds filters correctly`() {
        repo.createComplaint("ROADS", "in", "p.jpg", 43.5, 39.5, "addr", "d1")
        repo.createComplaint("ROADS", "out", "p.jpg", 50.0, 50.0, "addr", "d1")

        val found = repo.getComplaintsInBounds(43.0, 39.0, 44.0, 40.0)

        assertEquals(1, found.size)
        assertEquals("in", found[0].description)
    }
}

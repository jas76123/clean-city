package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Subbotniks
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
            SchemaUtils.drop(Complaints, Subbotniks)
            SchemaUtils.create(Complaints, Subbotniks)
        }
        repo = MarkerRepository()
    }

    @Test
    fun `createComplaint inserts and returns complaint`() {
        val result = repo.createComplaint(
            type = "DUMP",
            description = "Illegal dump near park",
            photoPath = "abc123.jpg",
            latitude = 43.585,
            longitude = 39.723,
            address = "ул. Ленина, 42",
            deviceId = "device-1"
        )

        assertNotNull(result)
        assertEquals("DUMP", result.type)
        assertEquals("Illegal dump near park", result.description)
        assertEquals("abc123.jpg", result.photoPath)
        assertEquals(43.585, result.latitude)
        assertEquals(39.723, result.longitude)
        assertEquals("NEW", result.status)
    }

    @Test
    fun `createSubbotnik inserts and returns subbotnik`() {
        val result = repo.createSubbotnik(
            title = "Park cleanup",
            description = "Bring gloves",
            photoPath = null,
            latitude = 43.585,
            longitude = 39.723,
            address = "Сквер Победы",
            eventDate = "2026-04-01",
            eventTime = "10:00",
            deviceId = "device-1"
        )

        assertNotNull(result)
        assertEquals("Park cleanup", result.title)
        assertEquals("2026-04-01", result.eventDate)
        assertEquals("10:00", result.eventTime)
    }

    @Test
    fun `getAllComplaints returns inserted complaints`() {
        repo.createComplaint("ROAD", "Pothole", "p.jpg", 43.0, 39.0, "addr", "d1")
        repo.createComplaint("LIGHTING", "Dark street", "d.jpg", 43.1, 39.1, "addr2", "d2")

        val all = repo.getAllComplaints()

        assertEquals(2, all.size)
    }

    @Test
    fun `getAllSubbotniks returns inserted subbotniks`() {
        repo.createSubbotnik("Cleanup", "desc", null, 43.0, 39.0, "addr", "2026-04-01", "10:00", "d1")

        val all = repo.getAllSubbotniks()

        assertEquals(1, all.size)
    }

    @Test
    fun `getComplaintById returns correct complaint`() {
        val created = repo.createComplaint("DUMP", "desc", "p.jpg", 43.0, 39.0, "addr", "d1")

        val found = repo.getComplaintById(created.id)

        assertNotNull(found)
        assertEquals(created.id, found.id)
    }

    @Test
    fun `getSubbotnikById returns correct subbotnik`() {
        val created = repo.createSubbotnik("Cleanup", "desc", null, 43.0, 39.0, "addr", "2026-04-01", "10:00", "d1")

        val found = repo.getSubbotnikById(created.id)

        assertNotNull(found)
        assertEquals(created.id, found.id)
    }
}

package com.example.cleancity.announcements

import com.example.cleancity.database.tables.Announcements
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.IconStyle
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnouncementRepositoryTest {
    private lateinit var repo: AnnouncementRepository

    @BeforeTest
    fun setup() {
        Database.connect(
            "jdbc:h2:mem:announce-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Announcements, Users)
            SchemaUtils.create(Users, Announcements)
        }
        repo = AnnouncementRepository()
    }

    private fun seedUser(email: String, district: String? = null, role: UserRole = UserRole.RESIDENT): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "x"
            it[Users.role] = role.name
            it[Users.district] = district
            it[Users.emailVerified] = true
            it[Users.isActive] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    @Test
    fun `insert and findById roundtrip`() {
        val adminId = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val id = transaction {
            repo.insert(
                title = "Субботник",
                body = "21 мая в 10:00",
                iconStyle = IconStyle.INFO,
                category = null,
                districts = listOf("Центральный", "Адлерский"),
                authorId = adminId,
                expiresAt = null
            )
        }
        val row = repo.findById(id)
        assertNotNull(row)
        assertEquals("Субботник", row.title)
        assertEquals(IconStyle.INFO, row.iconStyle)
        assertEquals(listOf("Центральный", "Адлерский"), row.districts)
        assertEquals(adminId, row.authorId)
        assertNull(row.expiresAt)
    }

    @Test
    fun `insert with empty districts stores ALL`() {
        val adminId = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val id = transaction {
            repo.insert("t", "b", IconStyle.INFO, null, emptyList(), adminId, null)
        }
        assertEquals(listOf("ALL"), repo.findById(id)!!.districts)
    }

    @Test
    fun `listActive excludes expired`() {
        val adminId = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        transaction {
            repo.insert("active", "b", IconStyle.INFO, null, emptyList(), adminId, expiresAt = now.plusDays(7))
            repo.insert("forever", "b", IconStyle.INFO, null, emptyList(), adminId, expiresAt = null)
            repo.insert("expired", "b", IconStyle.INFO, null, emptyList(), adminId, expiresAt = now.minusDays(1))
        }
        val (items, total) = repo.listActive(district = null, limit = 50)
        assertEquals(2, total)
        assertEquals(setOf("active", "forever"), items.map { it.title }.toSet())
    }

    @Test
    fun `listActive filter by district matches ALL and CSV`() {
        val adminId = seedUser("admin@x.ru", role = UserRole.ADMIN)
        transaction {
            repo.insert("for-all", "b", IconStyle.INFO, null, emptyList(), adminId, null)
            repo.insert("for-central", "b", IconStyle.INFO, null, listOf("Центральный"), adminId, null)
            repo.insert("for-adler", "b", IconStyle.INFO, null, listOf("Адлерский"), adminId, null)
        }
        val (forCentral, _) = repo.listActive(district = "Центральный", limit = 50)
        assertEquals(setOf("for-all", "for-central"), forCentral.map { it.title }.toSet())

        val (forKhosta, _) = repo.listActive(district = "Хостинский", limit = 50)
        assertEquals(setOf("for-all"), forKhosta.map { it.title }.toSet())
    }

    @Test
    fun `expire sets expires_at and listActive no longer returns it`() {
        val adminId = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val id = transaction {
            repo.insert("t", "b", IconStyle.INFO, null, emptyList(), adminId, null)
        }
        assertTrue(repo.expire(id))
        assertEquals(0, repo.listActive(null, 50).second)
        // Идемпотентность: вторая попытка expire уже-снятого → false
        assertEquals(false, repo.expire(id))
    }

    @Test
    fun `update changes only provided fields`() {
        val adminId = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val id = transaction {
            repo.insert("old-title", "old-body", IconStyle.INFO, null, listOf("Центральный"), adminId, null)
        }
        assertTrue(repo.update(
            id = id, title = "new-title", body = null, iconStyle = IconStyle.WARNING,
            category = null, districts = null, expiresAt = null, clearExpiresAt = false
        ))
        val row = repo.findById(id)!!
        assertEquals("new-title", row.title)
        assertEquals("old-body", row.body)
        assertEquals(IconStyle.WARNING, row.iconStyle)
        assertEquals(listOf("Центральный"), row.districts)
    }

    @Test
    fun `recipientIdsForDistricts ALL returns all active verified residents`() {
        val admin = seedUser("admin@x.ru", role = UserRole.ADMIN)
        val r1 = seedUser("r1@x.ru", district = "Центральный")
        val r2 = seedUser("r2@x.ru", district = "Адлерский")
        val r3 = seedUser("r3@x.ru", district = null)
        transaction {
            val ids = repo.recipientIdsForDistricts(listOf("ALL"))
            assertEquals(setOf(r1, r2, r3), ids.toSet())
            assertTrue(admin !in ids)
        }
    }

    @Test
    fun `recipientIdsForDistricts CSV filters by user district`() {
        val r1 = seedUser("r1@x.ru", district = "Центральный")
        val r2 = seedUser("r2@x.ru", district = "Адлерский")
        seedUser("r3@x.ru", district = "Хостинский")
        seedUser("r4@x.ru", district = null)
        transaction {
            val ids = repo.recipientIdsForDistricts(listOf("Центральный", "Адлерский"))
            assertEquals(setOf(r1, r2), ids.toSet())
        }
    }

    @Test
    fun `recipientIdsForDistricts excludes unverified and inactive`() {
        val ok = seedUser("ok@x.ru", district = "Центральный")
        // unverified
        val u = transaction {
            Users.insert {
                it[Users.email] = "u@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name; it[Users.district] = "Центральный"
                it[Users.emailVerified] = false; it[Users.isActive] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
        }
        // inactive
        val inactive = transaction {
            Users.insert {
                it[Users.email] = "i@x.ru"; it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name; it[Users.district] = "Центральный"
                it[Users.emailVerified] = true; it[Users.isActive] = false
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
        }
        transaction {
            val ids = repo.recipientIdsForDistricts(listOf("Центральный"))
            assertEquals(listOf(ok), ids)
            assertTrue(u !in ids && inactive !in ids)
        }
    }
}

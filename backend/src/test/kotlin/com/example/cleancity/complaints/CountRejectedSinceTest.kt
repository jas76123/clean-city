package com.example.cleancity.complaints

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals

class CountRejectedSinceTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:cnt-rej-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(StatusChanges, Complaints, Users)
            SchemaUtils.create(Users, Complaints, StatusChanges)
        }
    }

    private fun seedResident(): Long = transaction {
        Users.insert {
            it[Users.email] = "r@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.RESIDENT.name
            it[Users.isActive] = true
            it[Users.emailVerified] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedComplaint(authorId: Long): Long = transaction {
        Complaints.insert {
            it[Complaints.authorId] = authorId
            it[Complaints.category] = "GARBAGE"
            it[Complaints.title] = "t"
            it[Complaints.description] = "d"
            it[Complaints.latitude] = 43.6
            it[Complaints.longitude] = 39.7
            it[Complaints.address] = "addr"
            it[Complaints.status] = "NEW"
            it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Complaints.id]
    }

    private fun seedStatusChange(complaintId: Long, to: String, at: OffsetDateTime, actorId: Long) = transaction {
        StatusChanges.insert {
            it[StatusChanges.complaintId] = complaintId
            it[StatusChanges.fromStatus] = "NEW"
            it[StatusChanges.toStatus] = to
            it[StatusChanges.comment] = "c"
            it[StatusChanges.changedById] = actorId
            it[StatusChanges.createdAt] = at
        }
    }

    @Test
    fun `counts only REJECTED, ignores DUPLICATE, dedups per complaint`() {
        initDb()
        val resident = seedResident()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val c1 = seedComplaint(resident)
        val c2 = seedComplaint(resident)
        val c3 = seedComplaint(resident)
        seedStatusChange(c1, "REJECTED", now, resident)
        seedStatusChange(c2, "REJECTED", now, resident)
        seedStatusChange(c2, "REJECTED", now, resident) // дубль перехода той же жалобы
        seedStatusChange(c3, "DUPLICATE", now, resident) // не считается

        val repo = ComplaintRepository()
        assertEquals(2, repo.countRejectedSince(resident, null))
    }

    @Test
    fun `since filter excludes older rejections`() {
        initDb()
        val resident = seedResident()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val c1 = seedComplaint(resident)
        val c2 = seedComplaint(resident)
        seedStatusChange(c1, "REJECTED", now.minusDays(10), resident) // до предупреждения
        seedStatusChange(c2, "REJECTED", now, resident)               // после

        val repo = ComplaintRepository()
        assertEquals(1, repo.countRejectedSince(resident, now.minusDays(1)))
    }

    @Test
    fun `author isolation - does not count other resident rejections`() {
        initDb()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val resident1 = seedResident()
        val resident2 = transaction {
            Users.insert {
                it[Users.email] = "r2@test.local"
                it[Users.passwordHash] = "x"
                it[Users.role] = UserRole.RESIDENT.name
                it[Users.isActive] = true
                it[Users.emailVerified] = true
                it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Users.id]
        }

        val c1 = seedComplaint(resident1)
        val c2 = seedComplaint(resident2)

        seedStatusChange(c1, "REJECTED", now, resident1)
        seedStatusChange(c2, "REJECTED", now, resident2)

        val repo = ComplaintRepository()
        assertEquals(1, repo.countRejectedSince(resident1, null),
            "countRejectedSince should only count resident1's own rejections")
        assertEquals(1, repo.countRejectedSince(resident2, null),
            "countRejectedSince should only count resident2's own rejections")
    }
}

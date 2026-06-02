package com.example.cleancity.moderation

import com.example.cleancity.auth.TokenRepository
import com.example.cleancity.auth.UserRepository
import com.example.cleancity.complaints.ComplaintRepository
import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.Notifications
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Users
import com.example.cleancity.notifications.NotificationRepository
import com.example.cleancity.notifications.DbNotificationService
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModerationServiceTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:mod-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Notifications, StatusChanges, Complaints, RefreshTokens, EmailTokens, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, Complaints, StatusChanges, Notifications)
        }
    }

    private fun service(): ModerationService =
        ModerationService(
            users = UserRepository(),
            complaints = ComplaintRepository(),
            tokens = TokenRepository(),
            notifications = DbNotificationService(NotificationRepository()),
        )

    private fun seedUser(role: UserRole, active: Boolean = true): Long = transaction {
        Users.insert {
            it[Users.email] = "u${System.nanoTime()}@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = role.name
            it[Users.isActive] = active
            it[Users.emailVerified] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedRejectedComplaints(authorId: Long, count: Int, at: OffsetDateTime) = transaction {
        repeat(count) {
            val cid = Complaints.insert {
                it[Complaints.authorId] = authorId
                it[Complaints.category] = "GARBAGE"
                it[Complaints.title] = "t"
                it[Complaints.description] = "d"
                it[Complaints.latitude] = 43.6
                it[Complaints.longitude] = 39.7
                it[Complaints.address] = "addr"
                it[Complaints.status] = "REJECTED"
                it[Complaints.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
                it[Complaints.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }[Complaints.id]
            StatusChanges.insert {
                it[StatusChanges.complaintId] = cid
                it[StatusChanges.fromStatus] = "NEW"
                it[StatusChanges.toStatus] = "REJECTED"
                it[StatusChanges.comment] = "c"
                it[StatusChanges.changedById] = authorId
                it[StatusChanges.createdAt] = at
            }
        }
    }

    @Test
    fun `summary flags resident at threshold of 3`() {
        initDb()
        val svc = service()
        val resident = seedUser(UserRole.RESIDENT)
        seedRejectedComplaints(resident, 3, OffsetDateTime.now(ZoneOffset.UTC))

        val summary = svc.getSummary(resident)
        assertEquals(3, summary.rejectedCountSinceWarning)
        assertTrue(summary.flagged)
        assertFalse(summary.isWarned)
        assertFalse(summary.isBanned)
    }

    @Test
    fun `summary not flagged below threshold`() {
        initDb()
        val svc = service()
        val resident = seedUser(UserRole.RESIDENT)
        seedRejectedComplaints(resident, 2, OffsetDateTime.now(ZoneOffset.UTC))

        val summary = svc.getSummary(resident)
        assertEquals(2, summary.rejectedCountSinceWarning)
        assertFalse(summary.flagged)
    }
}

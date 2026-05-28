package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.AuditLog
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
import kotlin.test.assertNull

class AuditLogRepositoryTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:audit-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(AuditLog, Users)
            SchemaUtils.create(Users, AuditLog)
        }
    }

    private fun seedUser(email: String): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.ADMIN.name
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun seedAudit(actorId: Long?, action: AuditAction, createdAt: OffsetDateTime) = transaction {
        AuditLog.insert {
            it[AuditLog.actorUserId] = actorId
            it[AuditLog.action] = action.name
            it[AuditLog.targetType] = "user"
            it[AuditLog.targetId] = (actorId ?: 0L).toString()
            it[AuditLog.createdAt] = createdAt
        }
    }

    @Test
    fun `findRecent returns entries DESC by createdAt with actorEmail joined`() {
        initDb()
        val adminId = seedUser("admin@test.local")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        seedAudit(adminId, AuditAction.LOGIN_SUCCESS, now.minusMinutes(10))
        seedAudit(adminId, AuditAction.ADMIN_USER_FROZEN, now.minusMinutes(1))
        seedAudit(null, AuditAction.PASSWORD_RESET, now.minusMinutes(5))

        val rows = AuditLogRepository().findRecent(50)
        assertEquals(3, rows.size)
        assertEquals(AuditAction.ADMIN_USER_FROZEN.name, rows[0].action)
        assertEquals("admin@test.local", rows[0].actorEmail)
        assertEquals(AuditAction.PASSWORD_RESET.name, rows[1].action)
        assertNull(rows[1].actorEmail)
    }

    @Test
    fun `findRecent respects limit`() {
        initDb()
        val adminId = seedUser("admin@test.local")
        repeat(60) { i ->
            seedAudit(adminId, AuditAction.LOGIN_SUCCESS, OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(i.toLong()))
        }

        val rows = AuditLogRepository().findRecent(50)
        assertEquals(50, rows.size)
    }
}

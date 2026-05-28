package com.example.cleancity.auth

import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class TokenRepositoryInvalidateInviteTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:tok-inv-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(EmailTokens, Users)
            SchemaUtils.create(Users, EmailTokens)
        }
    }

    private fun seedUser(): Long = transaction {
        Users.insert {
            it[Users.email] = "u@test.local"
            it[Users.passwordHash] = "x"
            it[Users.role] = UserRole.OPERATOR.name
            it[Users.isActive] = false
            it[Users.emailVerified] = false
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    @Test
    fun `invalidateInviteForUser deletes pending ADMIN_INVITE tokens`() {
        initDb()
        val userId = seedUser()
        val repo = TokenRepository()
        repo.createEmailToken(userId, EmailTokenPurpose.ADMIN_INVITE, 3600)
        repo.createEmailToken(userId, EmailTokenPurpose.ADMIN_INVITE, 3600)

        val count = repo.invalidateInviteForUser(userId)
        assertEquals(2, count)

        transaction {
            val rows = EmailTokens.selectAll().where {
                (EmailTokens.userId eq userId) and
                    (EmailTokens.purpose eq EmailTokenPurpose.ADMIN_INVITE.name)
            }.toList()
            assertTrue(rows.isEmpty())
        }
    }

    @Test
    fun `invalidateInviteForUser does not touch other purposes`() {
        initDb()
        val userId = seedUser()
        val repo = TokenRepository()
        repo.createEmailToken(userId, EmailTokenPurpose.RESET_PASSWORD, 3600)

        val count = repo.invalidateInviteForUser(userId)
        assertEquals(0, count)

        transaction {
            val resetTokens = EmailTokens.selectAll().where {
                EmailTokens.purpose eq EmailTokenPurpose.RESET_PASSWORD.name
            }.toList()
            assertEquals(1, resetTokens.size)
            assertFalse(resetTokens[0][EmailTokens.consumed])
        }
    }
}

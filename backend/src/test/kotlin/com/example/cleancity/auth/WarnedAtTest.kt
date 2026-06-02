package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WarnedAtTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:warned-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
        }
    }

    private fun seedUser(): Long = transaction {
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

    @Test
    fun `warned_at is null by default and set after setWarnedAt`() {
        initDb()
        val id = seedUser()
        val repo = UserRepository()
        assertNull(repo.getWarnedAt(id))

        repo.setWarnedAt(id)
        assertNotNull(repo.getWarnedAt(id))
    }
}

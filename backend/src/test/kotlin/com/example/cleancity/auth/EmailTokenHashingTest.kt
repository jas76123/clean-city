package com.example.cleancity.auth

import com.example.cleancity.database.tables.EmailTokenPurpose
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Security: email-токены (verify/reset/invite) — это секреты «на предъявителя».
 * В БД должен лежать SHA-256 hash, а не сырой токен (как у refresh_tokens),
 * чтобы при утечке БД токены нельзя было использовать напрямую.
 */
class EmailTokenHashingTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:tok-hash-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
            it[Users.role] = UserRole.RESIDENT.name
            it[Users.isActive] = true
            it[Users.emailVerified] = false
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    @Test
    fun `stored email token is hashed, not raw`() {
        initDb()
        val userId = seedUser()
        val repo = TokenRepository()

        val rawToken = repo.createEmailToken(userId, EmailTokenPurpose.VERIFY_EMAIL, 3600)

        val stored = transaction {
            EmailTokens.selectAll().where { EmailTokens.userId eq userId }
                .single()[EmailTokens.token]
        }

        // В БД не должен лежать сырой токен — только его SHA-256.
        assertNotEquals(rawToken, stored)
        assertEquals(TokenGenerator.hashSha256(rawToken), stored)
    }

    @Test
    fun `lookup by raw token still works after hashing`() {
        initDb()
        val userId = seedUser()
        val repo = TokenRepository()

        val rawToken = repo.createEmailToken(userId, EmailTokenPurpose.RESET_PASSWORD, 3600)

        val found = repo.findValidEmailToken(rawToken, EmailTokenPurpose.RESET_PASSWORD)
        assertNotNull(found)
        assertEquals(userId, found.userId)
    }

    @Test
    fun `lookup by the stored hash value must NOT match`() {
        initDb()
        val userId = seedUser()
        val repo = TokenRepository()

        val rawToken = repo.createEmailToken(userId, EmailTokenPurpose.VERIFY_EMAIL, 3600)
        val storedHash = TokenGenerator.hashSha256(rawToken)

        // Передача самого хэша (то, что лежит в БД) не должна срабатывать —
        // доказывает, что поиск идёт по hash(input), а БД-значение бесполезно напрямую.
        assertNull(repo.findValidEmailToken(storedHash, EmailTokenPurpose.VERIFY_EMAIL))
    }
}

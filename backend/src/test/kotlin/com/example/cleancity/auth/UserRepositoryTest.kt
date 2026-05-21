package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRepositoryTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:user-repo-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
        }
    }

    @Test
    fun `softDeleteAndAnonymize wipes email password and name`() {
        initDb()
        val repo = UserRepository()
        val user = repo.create(
            email = "victim@cleancity.local",
            passwordHash = "real-bcrypt-hash",
            role = UserRole.RESIDENT,
            fullName = "Иван Петров",
        )

        repo.softDeleteAndAnonymize(user.id)

        val after = repo.findById(user.id)!!
        assertEquals("deleted_${user.id}@cleancity.local", after.email)
        assertEquals("", after.passwordHash)
        assertNull(after.fullName)
        assertFalse(after.isActive)
    }

    @Test
    fun `hasAnyAdmin is false on empty db and with only residents`() {
        initDb()
        val repo = UserRepository()
        assertFalse(repo.hasAnyAdmin())
        repo.create(email = "r@cleancity.local", passwordHash = "h", role = UserRole.RESIDENT)
        assertFalse(repo.hasAnyAdmin())
    }

    @Test
    fun `hasAnyAdmin is true when an admin-role user exists`() {
        initDb()
        val repo = UserRepository()
        repo.create(email = "a@cleancity.local", passwordHash = "h", role = UserRole.ADMIN)
        assertTrue(repo.hasAnyAdmin())
    }

    @Test
    fun `softDeleteAndAnonymize makes original email unfindable`() {
        initDb()
        val repo = UserRepository()
        val user = repo.create(
            email = "victim2@cleancity.local",
            passwordHash = "real-bcrypt-hash",
            role = UserRole.RESIDENT,
            fullName = "Иван Петров",
        )

        repo.softDeleteAndAnonymize(user.id)

        assertNull(repo.findByEmail("victim2@cleancity.local"))
    }
}

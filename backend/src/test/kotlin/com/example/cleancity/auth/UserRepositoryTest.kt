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

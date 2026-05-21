package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminBootstrapTest {

    private val log = LoggerFactory.getLogger(AdminBootstrapTest::class.java)

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:admin-bootstrap-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
        }
    }

    private fun userCount(): Long = transaction { Users.selectAll().count() }

    @Test
    fun `creates admin on empty db in DEV`() {
        initDb()
        val repo = UserRepository()
        bootstrapInitialAdmin(repo, "boot@cleancity.local", "AnyPass123!ok", "DEV", log)
        val admin = repo.findByEmail("boot@cleancity.local")
        assertNotNull(admin)
        assertEquals(UserRole.ADMIN, admin.role)
        assertTrue(admin.emailVerified)
        assertTrue(admin.isActive)
    }

    @Test
    fun `creates admin in DEV even with weak password`() {
        initDb()
        val repo = UserRepository()
        bootstrapInitialAdmin(repo, "boot@cleancity.local", "weak", "DEV", log)
        assertNotNull(repo.findByEmail("boot@cleancity.local"))
    }

    @Test
    fun `throws on weak password in PROD and creates no user`() {
        initDb()
        val repo = UserRepository()
        assertFailsWith<WeakPasswordException> {
            bootstrapInitialAdmin(repo, "boot@cleancity.local", "weak", "PROD", log)
        }
        assertEquals(0L, userCount())
    }

    @Test
    fun `skips when an admin already exists`() {
        initDb()
        val repo = UserRepository()
        repo.create(email = "existing@cleancity.local", passwordHash = "h", role = UserRole.ADMIN)
        bootstrapInitialAdmin(repo, "boot@cleancity.local", "AnyPass123!ok", "DEV", log)
        assertNull(repo.findByEmail("boot@cleancity.local"))
        assertEquals(1L, userCount())
    }

    @Test
    fun `skips when email or password is blank`() {
        initDb()
        val repo = UserRepository()
        bootstrapInitialAdmin(repo, null, "AnyPass123!ok", "DEV", log)
        bootstrapInitialAdmin(repo, "boot@cleancity.local", null, "DEV", log)
        bootstrapInitialAdmin(repo, "", "", "DEV", log)
        assertEquals(0L, userCount())
    }
}

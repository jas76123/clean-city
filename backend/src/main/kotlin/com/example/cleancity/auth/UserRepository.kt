package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class UserRow(
    val id: Long,
    val email: String,
    val passwordHash: String,
    val role: UserRole,
    val fullName: String?,
    val emailVerified: Boolean,
    val isActive: Boolean,
    val createdAt: OffsetDateTime
)

class UserRepository {

    fun findByEmail(email: String): UserRow? = transaction {
        Users.selectAll().where { Users.email eq email.lowercase() }.firstOrNull()?.toUserRow()
    }

    fun findById(id: Long): UserRow? = transaction {
        Users.selectAll().where { Users.id eq id }.firstOrNull()?.toUserRow()
    }

    fun create(
        email: String,
        passwordHash: String,
        role: UserRole = UserRole.RESIDENT,
        fullName: String? = null
    ): UserRow = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Users.insert {
            it[Users.email] = email.lowercase()
            it[Users.passwordHash] = passwordHash
            it[Users.role] = role.name
            it[Users.fullName] = fullName
            it[Users.emailVerified] = false
            it[Users.isActive] = true
            it[Users.passwordChangedAt] = now
            it[Users.createdAt] = now
        }[Users.id]

        UserRow(id, email.lowercase(), passwordHash, role, fullName, false, true, now)
    }

    fun markEmailVerified(userId: Long) = transaction {
        Users.update({ Users.id eq userId }) { it[Users.emailVerified] = true }
    }

    fun updateLastLogin(userId: Long, ip: String?) = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Users.update({ Users.id eq userId }) {
            it[Users.lastLoginAt] = now
            it[Users.lastLoginIp] = ip
            it[Users.failedLoginAttempts] = 0
        }
    }

    fun updatePasswordHash(userId: Long, newHash: String) = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Users.update({ Users.id eq userId }) {
            it[Users.passwordHash] = newHash
            it[Users.passwordChangedAt] = now
            it[Users.mustChangePassword] = false
        }
    }

    private fun ResultRow.toUserRow() = UserRow(
        id = this[Users.id],
        email = this[Users.email],
        passwordHash = this[Users.passwordHash],
        role = UserRole.valueOf(this[Users.role]),
        fullName = this[Users.fullName],
        emailVerified = this[Users.emailVerified],
        isActive = this[Users.isActive],
        createdAt = this[Users.createdAt]
    )
}

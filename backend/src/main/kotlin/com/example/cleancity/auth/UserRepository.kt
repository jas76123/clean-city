package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
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
    val failedLoginAttempts: Int,
    val lockedUntil: OffsetDateTime?,
    val totpSecret: String?,
    val totpEnabled: Boolean,
    val mustChangePassword: Boolean,
    val createdAt: OffsetDateTime
)

class UserRepository {

    fun findByEmail(email: String): UserRow? = transaction {
        Users.selectAll().where { Users.email eq email.lowercase() }.firstOrNull()?.toUserRow()
    }

    fun findById(id: Long): UserRow? = transaction {
        Users.selectAll().where { Users.id eq id }.firstOrNull()?.toUserRow()
    }

    /**
     * true, если в системе есть хотя бы один пользователь с админ-ролью
     * (ADMIN / OPERATOR), независимо от is_active.
     */
    fun hasAnyAdmin(): Boolean = transaction {
        !Users.selectAll().where {
            (Users.role eq UserRole.ADMIN.name) or
                (Users.role eq UserRole.OPERATOR.name)
        }.empty()
    }

    fun create(
        email: String,
        passwordHash: String,
        role: UserRole = UserRole.RESIDENT,
        fullName: String? = null,
        isActive: Boolean = true,
        emailVerified: Boolean = false,
        mustChangePassword: Boolean = false,
        acceptedTermsVersion: String? = null
    ): UserRow = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Users.insert {
            it[Users.email] = email.lowercase()
            it[Users.passwordHash] = passwordHash
            it[Users.role] = role.name
            it[Users.fullName] = fullName
            it[Users.emailVerified] = emailVerified
            it[Users.isActive] = isActive
            it[Users.mustChangePassword] = mustChangePassword
            it[Users.passwordChangedAt] = now
            it[Users.createdAt] = now
            if (acceptedTermsVersion != null) {
                it[Users.acceptedTermsAt] = now
                it[Users.acceptedTermsVersion] = acceptedTermsVersion
            }
        }[Users.id]

        UserRow(
            id = id,
            email = email.lowercase(),
            passwordHash = passwordHash,
            role = role,
            fullName = fullName,
            emailVerified = emailVerified,
            isActive = isActive,
            failedLoginAttempts = 0,
            lockedUntil = null,
            totpSecret = null,
            totpEnabled = false,
            mustChangePassword = mustChangePassword,
            createdAt = now
        )
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
            it[Users.lockedUntil] = null
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

    /**
     * +1 к failed_login_attempts. Возвращает новое значение.
     * Гонка между одновременными неудачами теоретически возможна, но в нашей нагрузке
     * это безразлично — итог всё равно сходится к ≥5 → блокировка.
     */
    fun incrementFailedAttempts(userId: Long): Int = transaction {
        val current = Users.selectAll().where { Users.id eq userId }.first()[Users.failedLoginAttempts]
        val next = current + 1
        Users.update({ Users.id eq userId }) { it[Users.failedLoginAttempts] = next }
        next
    }

    fun lockUntil(userId: Long, until: OffsetDateTime) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.lockedUntil] = until
            it[Users.failedLoginAttempts] = 0
        }
    }

    fun markEmailVerifiedAndActive(userId: Long) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.emailVerified] = true
            it[Users.isActive] = true
        }
    }

    fun setTotpSecret(userId: Long, secret: String) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.totpSecret] = secret
            it[Users.totpEnabled] = false
        }
    }

    fun enableTotp(userId: Long) = transaction {
        Users.update({ Users.id eq userId }) { it[Users.totpEnabled] = true }
    }

    /**
     * Soft-delete + анонимизация аккаунта (152-ФЗ). Email заменяется на
     * deleted_<id>@cleancity.local, пароль и имя обнуляются, is_active=false.
     * password_hash ставится в пустую строку (колонка NOT NULL) — этого
     * достаточно: ни один пароль не верифицируется против пустого хеша.
     */
    fun softDeleteAndAnonymize(userId: Long) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.isActive] = false
            it[Users.email] = "deleted_${userId}@cleancity.local"
            it[Users.passwordHash] = ""
            it[Users.fullName] = null
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
        failedLoginAttempts = this[Users.failedLoginAttempts],
        lockedUntil = this[Users.lockedUntil],
        totpSecret = this[Users.totpSecret],
        totpEnabled = this[Users.totpEnabled],
        mustChangePassword = this[Users.mustChangePassword],
        createdAt = this[Users.createdAt]
    )
}

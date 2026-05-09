package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 20).default("RESIDENT")
    val fullName = varchar("full_name", 200).nullable()
    val district = varchar("district", 100).nullable()
    val emailVerified = bool("email_verified").default(false)
    val isActive = bool("is_active").default(true)
    val failedLoginAttempts = integer("failed_login_attempts").default(0)
    val lockedUntil = timestampWithTimeZone("locked_until").nullable()
    val totpSecret = varchar("totp_secret", 64).nullable()
    val totpEnabled = bool("totp_enabled").default(false)
    val passwordChangedAt = timestampWithTimeZone("password_changed_at")
    val mustChangePassword = bool("must_change_password").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    val lastLoginAt = timestampWithTimeZone("last_login_at").nullable()
    val lastLoginIp = varchar("last_login_ip", 45).nullable()
    val acceptedTermsAt = timestampWithTimeZone("accepted_terms_at").nullable()
    val acceptedTermsVersion = varchar("accepted_terms_version", 10).nullable()

    override val primaryKey = PrimaryKey(id)
}

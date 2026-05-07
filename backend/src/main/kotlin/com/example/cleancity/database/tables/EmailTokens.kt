package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object EmailTokens : Table("email_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val token = varchar("token", 128).uniqueIndex()
    val purpose = varchar("purpose", 20)   // VERIFY_EMAIL, RESET_PASSWORD, ADMIN_INVITE
    val expiresAt = timestampWithTimeZone("expires_at")
    val consumed = bool("consumed").default(false)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

enum class EmailTokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD,
    ADMIN_INVITE
}

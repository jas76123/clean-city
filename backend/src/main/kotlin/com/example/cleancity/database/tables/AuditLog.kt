package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object AuditLog : Table("audit_log") {
    val id = long("id").autoIncrement()
    val actorUserId = long("actor_user_id").references(Users.id).nullable()
    val action = varchar("action", 50)
    val targetType = varchar("target_type", 50).nullable()
    val targetId = varchar("target_id", 100).nullable()
    val ip = varchar("ip", 45).nullable()
    val userAgent = varchar("user_agent", 500).nullable()
    val metadata = text("metadata").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

enum class AuditAction {
    LOGIN_SUCCESS,
    LOGIN_FAIL,
    LOGIN_LOCKED,
    TWOFA_SETUP_STARTED,
    TWOFA_ENABLED,
    TWOFA_FAIL,
    TWOFA_LOGIN_SUCCESS,
    PASSWORD_RESET,
    REFRESH_REVOKED,
    SESSION_REVOKED,
    ADMIN_INVITE_SENT,
    ADMIN_INVITE_ACCEPTED,
    COMPLAINT_STATUS_CHANGE,
    ACCOUNT_DELETED,
    ADMIN_USER_FROZEN,
    ADMIN_USER_UNFROZEN,
    ADMIN_INVITE_REVOKED
}

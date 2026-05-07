package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.database.tables.AuditLog
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset

interface AuditLogger {
    fun log(
        action: AuditAction,
        actorUserId: Long? = null,
        targetType: String? = null,
        targetId: String? = null,
        ip: String? = null,
        userAgent: String? = null,
        metadata: String? = null
    )
}

object NoopAuditLogger : AuditLogger {
    override fun log(
        action: AuditAction,
        actorUserId: Long?,
        targetType: String?,
        targetId: String?,
        ip: String?,
        userAgent: String?,
        metadata: String?
    ) = Unit
}

class DbAuditLogger : AuditLogger {
    override fun log(
        action: AuditAction,
        actorUserId: Long?,
        targetType: String?,
        targetId: String?,
        ip: String?,
        userAgent: String?,
        metadata: String?
    ) {
        transaction {
            AuditLog.insert {
                it[AuditLog.actorUserId] = actorUserId
                it[AuditLog.action] = action.name
                it[AuditLog.targetType] = targetType
                it[AuditLog.targetId] = targetId
                it[AuditLog.ip] = ip
                it[AuditLog.userAgent] = userAgent?.take(500)
                it[AuditLog.metadata] = metadata
                it[AuditLog.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }
        }
    }
}

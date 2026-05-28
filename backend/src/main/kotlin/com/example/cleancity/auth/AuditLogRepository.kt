package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.Users
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

data class AuditEntryRow(
    val id: Long,
    val createdAt: OffsetDateTime,
    val actorEmail: String?,
    val action: String,
    val targetType: String?,
    val targetId: String?,
    val ip: String?,
    val details: String?
)

/**
 * Read-only репозиторий журнала аудита.
 *
 * Отделён от [AuditLogger] (пишущий интерфейс) намеренно: чтение журнала
 * нужно только админу через /admin/audit (Day 17C, задача 7) и не должно
 * соблазнять остальной код «случайно» сделать SELECT по audit_log.
 */
class AuditLogRepository {

    /**
     * Возвращает последние записи аудита, отсортированные по [AuditLog.createdAt]
     * (новые сверху). LEFT JOIN на [Users] сохраняет системные действия
     * (actor_user_id = NULL — например, PASSWORD_RESET по магической ссылке).
     */
    fun findRecent(limit: Int): List<AuditEntryRow> = transaction {
        AuditLog
            .join(Users, JoinType.LEFT, onColumn = AuditLog.actorUserId, otherColumn = Users.id)
            .selectAll()
            .orderBy(AuditLog.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { row ->
                AuditEntryRow(
                    id = row[AuditLog.id],
                    createdAt = row[AuditLog.createdAt],
                    // LEFT JOIN: системные действия (actor_user_id = NULL) дают
                    // отсутствующее значение Users.email. Идиома проекта —
                    // runCatching {…}.getOrNull(), см. ComplaintRepository.
                    actorEmail = runCatching { row[Users.email] }.getOrNull(),
                    action = row[AuditLog.action],
                    targetType = row[AuditLog.targetType],
                    targetId = row[AuditLog.targetId],
                    ip = row[AuditLog.ip],
                    details = row[AuditLog.metadata]
                )
            }
    }
}

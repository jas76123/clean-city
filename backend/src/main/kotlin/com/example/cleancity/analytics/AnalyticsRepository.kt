package com.example.cleancity.analytics

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Votes
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime

/**
 * Аналитика. Стратегия: выгружаем строки и считаем агрегаты в Kotlin.
 * Это безопаснее для H2-PostgreSQL-mode тестов (нет EXTRACT(EPOCH ...), FILTER WHERE
 * и других PostgreSQL-расширений) и достаточно быстро при N жалоб в десятки
 * тысяч (целевой объём пилота — сотни/тысячи). Если объёмы вырастут — переход
 * на materialized views (см. SPEC §5.5).
 */
class AnalyticsRepository {

    data class Row(
        val id: Long,
        val category: ProblemCategory,
        val district: String?,
        val status: ComplaintStatus,
        val createdAt: OffsetDateTime,
        val resolvedAt: OffsetDateTime?
    )

    fun loadComplaints(periodStart: OffsetDateTime?): List<Row> = transaction {
        val query = if (periodStart != null) {
            Complaints.selectAll().where { Complaints.createdAt greaterEq periodStart }
        } else {
            Complaints.selectAll()
        }
        query.map {
            Row(
                id = it[Complaints.id],
                category = parseCategory(it[Complaints.category]),
                district = it[Complaints.district],
                status = parseStatus(it[Complaints.status]),
                createdAt = it[Complaints.createdAt],
                resolvedAt = it[Complaints.resolvedAt]
            )
        }
    }

    /** complaint_id → число `+1` голосов. */
    fun voteCounts(complaintIds: Collection<Long>): Map<Long, Int> {
        if (complaintIds.isEmpty()) return emptyMap()
        return transaction {
            val raw = Votes.selectAll()
                .where { (Votes.complaintId inList complaintIds) and (Votes.value eq 1.toShort()) }
                .map { it[Votes.complaintId] }
            val grouped = raw.groupingBy { it }.eachCount()
            complaintIds.associateWith { grouped[it] ?: 0 }
        }
    }

    private fun parseCategory(raw: String): ProblemCategory =
        runCatching { ProblemCategory.valueOf(raw) }.getOrDefault(ProblemCategory.OTHER)

    private fun parseStatus(raw: String): ComplaintStatus =
        runCatching { ComplaintStatus.valueOf(raw) }.getOrDefault(ComplaintStatus.NEW)
}

package com.example.cleancity.analytics

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.StatusChanges
import com.example.cleancity.database.tables.Votes
import com.example.cleancity.shared.models.CategorySla
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneId

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

    data class OperationalSnapshotRow(
        val backlog: Int,
        val overdueNow: Int,
        val avgDtaHours24h: Double?,
        val createdToday: Int,
        val createdYesterday: Int,
        val statusBreakdown: Map<String, Int>,
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

    fun operationalSnapshot(now: OffsetDateTime): OperationalSnapshotRow = transaction {
        val allRows = loadComplaints(periodStart = null)

        // backlog: status in {NEW, IN_PROGRESS}
        val backlogStatuses = setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS)
        val backlogRows = allRows.filter { it.status in backlogStatuses }
        val backlog = backlogRows.size

        // overdueNow: backlog rows where now > createdAt + SLA hours
        val overdueNow = backlogRows.count { row ->
            val slaHours = CategorySla.hoursFor(row.category).toLong()
            now.isAfter(row.createdAt.plusHours(slaHours))
        }

        // Day boundaries in Europe/Moscow timezone
        val msk = ZoneId.of("Europe/Moscow")
        val todayStart = now.atZoneSameInstant(msk).toLocalDate().atStartOfDay(msk).toOffsetDateTime()
        val yesterdayStart = todayStart.minusDays(1)
        val tomorrowStart = todayStart.plusDays(1)

        val createdToday = allRows.count { row ->
            !row.createdAt.isBefore(todayStart) && row.createdAt.isBefore(tomorrowStart)
        }
        val createdYesterday = allRows.count { row ->
            !row.createdAt.isBefore(yesterdayStart) && row.createdAt.isBefore(todayStart)
        }

        // statusBreakdown: complaints created in last 30 days, grouped by status
        val thirtyDaysAgo = now.minusDays(30)
        val statusBreakdown = allRows
            .filter { !it.createdAt.isBefore(thirtyDaysAgo) }
            .groupingBy { it.status.name }
            .eachCount()
            .filter { it.value > 0 }

        // avgDtaHours24h: average DTA for complaints whose first IN_PROGRESS event is in [now-24h, now)
        val dtaWindowStart = now.minusHours(24)
        // Load all IN_PROGRESS status change events in the 24h window
        val inProgressChanges = StatusChanges.selectAll()
            .where {
                (StatusChanges.toStatus eq "IN_PROGRESS") and
                (StatusChanges.createdAt greaterEq dtaWindowStart) and
                (StatusChanges.createdAt less now)
            }
            .map { row ->
                Pair(row[StatusChanges.complaintId], row[StatusChanges.createdAt])
            }

        val avgDtaHours24h: Double? = if (inProgressChanges.isEmpty()) {
            null
        } else {
            // For each complaint, keep only the earliest IN_PROGRESS event
            val firstInProgressByComplaint = inProgressChanges
                .groupBy { it.first }
                .mapValues { (_, events) -> events.minByOrNull { it.second }!!.second }

            // Look up complaint createdAt for those complaintIds
            val complaintIds = firstInProgressByComplaint.keys
            val complaintCreatedAtMap = Complaints.selectAll()
                .where { Complaints.id inList complaintIds }
                .associate { it[Complaints.id] to it[Complaints.createdAt] }

            // Compute DTA in hours for each complaint
            val dtaValues = firstInProgressByComplaint.mapNotNull { (complaintId, firstIpAt) ->
                val createdAt = complaintCreatedAtMap[complaintId] ?: return@mapNotNull null
                val minutes = java.time.Duration.between(createdAt, firstIpAt).toMinutes()
                minutes / 60.0
            }

            if (dtaValues.isEmpty()) null else dtaValues.average()
        }

        OperationalSnapshotRow(
            backlog = backlog,
            overdueNow = overdueNow,
            avgDtaHours24h = avgDtaHours24h,
            createdToday = createdToday,
            createdYesterday = createdYesterday,
            statusBreakdown = statusBreakdown,
        )
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

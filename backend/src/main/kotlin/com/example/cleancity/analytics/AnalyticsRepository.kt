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
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.pow

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
        val title: String,
        val category: ProblemCategory,
        val district: String?,
        val status: ComplaintStatus,
        val createdAt: OffsetDateTime,
        val resolvedAt: OffsetDateTime?,
        val latitude: Double,
        val longitude: Double,
    )

    data class ReopenStatRow(
        val reopenRate: Double,   // 0..1
        val reopenCount: Int,
        val resolvedCount: Int,
    )

    data class BurningRow(
        val id: Long,
        val title: String,
        val districtCode: String?,
        val category: String,
        val createdAt: Instant,
        val slaDueAt: Instant,
        val secondsToDeadline: Long,
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
                title = it[Complaints.title],
                category = parseCategory(it[Complaints.category]),
                district = it[Complaints.district],
                status = parseStatus(it[Complaints.status]),
                createdAt = it[Complaints.createdAt],
                resolvedAt = it[Complaints.resolvedAt],
                latitude = it[Complaints.latitude],
                longitude = it[Complaints.longitude],
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

        // avgDtaHours24h: average DTA for complaints whose FIRST-EVER IN_PROGRESS event falls in [now-24h, now)
        val dtaWindowStart = now.minusHours(24)
        // Load ALL IN_PROGRESS status change events (no window filter) to find each complaint's global first
        val allInProgressChanges = StatusChanges.selectAll()
            .where { StatusChanges.toStatus eq "IN_PROGRESS" }
            .map { row ->
                Pair(row[StatusChanges.complaintId], row[StatusChanges.createdAt])
            }

        val avgDtaHours24h: Double? = if (allInProgressChanges.isEmpty()) {
            null
        } else {
            // For each complaint, find the global first IN_PROGRESS event (minimum across all time)
            val globalFirstInProgressByComplaint = allInProgressChanges
                .groupBy { it.first }
                .mapValues { (_, events) -> events.minOf { it.second } }

            // Keep only complaints whose global first IN_PROGRESS event falls in [now-24h, now)
            val inWindowFirst = globalFirstInProgressByComplaint
                .filter { (_, firstIpAt) ->
                    !firstIpAt.isBefore(dtaWindowStart) && firstIpAt.isBefore(now)
                }

            if (inWindowFirst.isEmpty()) {
                null
            } else {
                // Look up complaint createdAt in-memory from allRows (already loaded above)
                val createdAtById: Map<Long, OffsetDateTime> = allRows.associate { it.id to it.createdAt }

                // Compute DTA in hours for each complaint
                val dtaValues = inWindowFirst.mapNotNull { (complaintId, firstIpAt) ->
                    val createdAt = createdAtById[complaintId] ?: return@mapNotNull null
                    val minutes = java.time.Duration.between(createdAt, firstIpAt).toMinutes()
                    minutes / 60.0
                }

                if (dtaValues.isEmpty()) null else dtaValues.average()
            }
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

    /** Топ-N жалоб, ближайших к дедлайну SLA (или уже просроченных). */
    fun burningQueue(
        now: OffsetDateTime,
        limit: Int = AnalyticsConfig.BURNING_QUEUE_DEFAULT_LIMIT,
    ): List<BurningRow> {
        val openStatuses = setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS)
        val rows = loadComplaints(periodStart = null).filter { it.status in openStatuses }
        val nowInstant = now.toInstant()
        return rows
            .map { row ->
                val slaHours = CategorySla.hoursFor(row.category).toLong()
                val slaDueAt = row.createdAt.toInstant().plus(Duration.ofHours(slaHours))
                BurningRow(
                    id = row.id,
                    title = row.title,
                    districtCode = row.district,
                    category = row.category.name,
                    createdAt = row.createdAt.toInstant(),
                    slaDueAt = slaDueAt,
                    secondsToDeadline = (slaDueAt.toEpochMilli() - nowInstant.toEpochMilli()) / 1000L,
                )
            }
            .sortedBy { it.slaDueAt }
            .take(limit)
    }

    data class StrategicKpisRow(
        val slaCompliancePct: Double,
        val medianResolutionHours: Double?,
        val p90ResolutionHours: Double?,
        val throughput: Int,
    )

    fun strategicKpis(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): StrategicKpisRow {
        val resolved = loadComplaints(null).filter { row ->
            row.status == ComplaintStatus.RESOLVED &&
                row.resolvedAt != null &&
                !row.resolvedAt.isBefore(periodStart) &&
                row.resolvedAt.isBefore(periodEnd)
        }

        val sortedHours = resolved
            .map { row -> Duration.between(row.createdAt, row.resolvedAt!!).toMinutes() / 60.0 }
            .sorted()

        val throughput = sortedHours.size

        val slaCompliancePct = if (throughput == 0) {
            0.0
        } else {
            val withinSla = resolved.count { row ->
                val hours = Duration.between(row.createdAt, row.resolvedAt!!).toMinutes() / 60.0
                hours <= CategorySla.hoursFor(row.category)
            }
            100.0 * withinSla / throughput
        }

        return StrategicKpisRow(
            slaCompliancePct = slaCompliancePct,
            medianResolutionHours = median(sortedHours),
            p90ResolutionHours = p90(sortedHours),
            throughput = throughput,
        )
    }

    private fun median(sortedHours: List<Double>): Double? {
        if (sortedHours.isEmpty()) return null
        val n = sortedHours.size
        return if (n % 2 == 1) sortedHours[n / 2]
        else (sortedHours[n / 2 - 1] + sortedHours[n / 2]) / 2.0
    }

    private fun p90(sortedHours: List<Double>): Double? {
        if (sortedHours.isEmpty()) return null
        if (sortedHours.size == 1) return sortedHours[0]
        val idx = 0.9 * (sortedHours.size - 1)
        val lo = idx.toInt()
        val hi = (lo + 1).coerceAtMost(sortedHours.size - 1)
        val frac = idx - lo
        return sortedHours[lo] + frac * (sortedHours[hi] - sortedHours[lo])
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

    fun reopenStat(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): ReopenStatRow {
        val allRows = loadComplaints(null)

        // Resolved complaints whose resolvedAt falls in [periodStart, periodEnd)
        val resolved = allRows.filter { row ->
            row.status == ComplaintStatus.RESOLVED &&
                row.resolvedAt != null &&
                !row.resolvedAt.isBefore(periodStart) &&
                row.resolvedAt.isBefore(periodEnd)
        }

        val reopenCount = resolved.count { r ->
            allRows.any { c ->
                c.id != r.id &&
                    c.category == r.category &&
                    c.createdAt.isAfter(r.resolvedAt!!) &&
                    !c.createdAt.isAfter(r.resolvedAt.plusDays(AnalyticsConfig.REOPEN_WINDOW_DAYS.toLong())) &&
                    haversineMeters(r.latitude, r.longitude, c.latitude, c.longitude) <= AnalyticsConfig.REOPEN_RADIUS_METERS
            }
        }

        val resolvedCount = resolved.size
        val reopenRate = if (resolvedCount == 0) 0.0 else reopenCount.toDouble() / resolvedCount

        return ReopenStatRow(
            reopenRate = reopenRate,
            reopenCount = reopenCount,
            resolvedCount = resolvedCount,
        )
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0 // earth radius meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLam = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dPhi / 2).pow(2) + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLam / 2).pow(2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    private fun parseCategory(raw: String): ProblemCategory =
        runCatching { ProblemCategory.valueOf(raw) }.getOrDefault(ProblemCategory.OTHER)

    private fun parseStatus(raw: String): ComplaintStatus =
        runCatching { ComplaintStatus.valueOf(raw) }.getOrDefault(ComplaintStatus.NEW)
}

package com.example.cleancity.analytics

import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.AnalyticsPeriod
import com.example.cleancity.shared.models.BurningComplaintItem
import com.example.cleancity.shared.models.CategorySla
import com.example.cleancity.shared.models.CategoryStat
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.DailyPoint
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.MonthlyKpis
import com.example.cleancity.shared.models.OperationalSnapshot
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.ReopenStat
import com.example.cleancity.shared.models.SlaStat
import com.example.cleancity.shared.models.StrategicKpis
import com.example.cleancity.shared.models.TrendPoint
import com.example.cleancity.shared.models.TrendsResponse
import com.example.cleancity.shared.models.VotesBucket
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.datetime.Instant

class AnalyticsService(private val repo: AnalyticsRepository) {

    /** Срез «сейчас»: счётчики по статусам + today/week + активные SLA-просрочки. */
    fun overview(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): AnalyticsOverview {
        val rows = repo.loadComplaints(periodStart = null)
        val byStatus = rows.groupingBy { it.status }.eachCount()
        val todayStart = now.toLocalDate().atStartOfDay(now.offset).toOffsetDateTime()
        val weekStart = now.minusDays(7)

        val slaBreachCount = rows.count { row ->
            val active = row.status == ComplaintStatus.NEW || row.status == ComplaintStatus.IN_PROGRESS
            if (!active) return@count false
            val ageHours = Duration.between(row.createdAt, now).toHours()
            ageHours > CategorySla.hoursFor(row.category)
        }

        val monthly = monthlyKpis(rows, now)

        return AnalyticsOverview(
            total = rows.size,
            new = byStatus[ComplaintStatus.NEW] ?: 0,
            inProgress = byStatus[ComplaintStatus.IN_PROGRESS] ?: 0,
            resolved = byStatus[ComplaintStatus.RESOLVED] ?: 0,
            rejected = byStatus[ComplaintStatus.REJECTED] ?: 0,
            duplicate = byStatus[ComplaintStatus.DUPLICATE] ?: 0,
            today = rows.count { it.createdAt >= todayStart },
            week = rows.count { it.createdAt >= weekStart },
            slaBreachCount = slaBreachCount,
            monthlyKpis = monthly
        )
    }

    fun byCategory(period: AnalyticsPeriod): List<CategoryStat> {
        val (from, to) = toRange(period)
        val rows = repo.byCategoryExtended(from, to)
        val total = rows.sumOf { it.count }
        return rows.mapNotNull { r ->
            val cat = runCatching { ProblemCategory.valueOf(r.category) }.getOrNull() ?: return@mapNotNull null
            CategoryStat(
                category = cat,
                label = cat.localizedLabel,
                count = r.count,
                sharePct = if (total == 0) 0.0 else round1(r.count * 100.0 / total),
                avgResolutionHours = r.avgResolutionHours,
                medianResolutionHours = r.medianResolutionHours,
                p90ResolutionHours = r.p90ResolutionHours,
                slaCompliancePct = r.slaCompliancePct,
            )
        }
    }

    fun byDistrict(period: AnalyticsPeriod): List<DistrictStat> {
        val (from, to) = toRange(period)
        val rows = repo.byDistrictExtended(from, to)
        return rows.mapNotNull { r ->
            val districtName = r.district ?: return@mapNotNull null
            val d = District.entries.firstOrNull {
                it.localizedLabel.equals(districtName, ignoreCase = true)
            } ?: return@mapNotNull null
            DistrictStat(
                district = d,
                label = d.localizedLabel,
                count = r.count,
                newCount = r.newCount,
                resolvedCount = r.resolvedCount,
                medianResolutionHours = r.medianResolutionHours,
                slaCompliancePct = r.slaCompliancePct,
            )
        }
    }

    fun operational(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): OperationalSnapshot {
        val row = repo.operationalSnapshot(now)
        return OperationalSnapshot(
            backlog = row.backlog,
            overdueNow = row.overdueNow,
            avgDtaHours24h = row.avgDtaHours24h,
            dtaTargetHours = AnalyticsConfig.DTA_TARGET_HOURS,
            createdToday = row.createdToday,
            createdYesterday = row.createdYesterday,
            statusBreakdown = row.statusBreakdown,
        )
    }

    fun burning(
        now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
        limit: Int = AnalyticsConfig.BURNING_QUEUE_DEFAULT_LIMIT,
    ): List<BurningComplaintItem> {
        return repo.burningQueue(now, limit).map { r ->
            BurningComplaintItem(
                id = r.id,
                title = r.title,
                districtCode = r.districtCode,
                category = r.category,
                createdAt = Instant.fromEpochMilliseconds(r.createdAt.toEpochMilli()),
                slaDueAt = Instant.fromEpochMilliseconds(r.slaDueAt.toEpochMilli()),
                secondsToDeadline = r.secondsToDeadline,
            )
        }
    }

    fun strategic(period: AnalyticsPeriod): StrategicKpis {
        val (from, to) = toRange(period)
        val k = repo.strategicKpis(from, to)
        val r = repo.reopenStat(from, to)
        return StrategicKpis(
            slaCompliancePct = k.slaCompliancePct,
            slaTargetPct = AnalyticsConfig.SLA_TARGET_PCT,
            medianResolutionHours = k.medianResolutionHours,
            p90ResolutionHours = k.p90ResolutionHours,
            reopenRate = r.reopenRate,
            reopenTargetPct = AnalyticsConfig.REOPEN_TARGET_PCT,
            throughput = k.throughput,
        )
    }

    fun reopen(period: AnalyticsPeriod): ReopenStat {
        val (from, to) = toRange(period)
        val r = repo.reopenStat(from, to)
        return ReopenStat(reopenRate = r.reopenRate, reopenCount = r.reopenCount, resolvedCount = r.resolvedCount)
    }

    fun trendsRange(period: AnalyticsPeriod, groupBy: String = "day"): TrendsResponse {
        val (from, to) = toRange(period)
        val r = repo.trendsRange(from, to, groupBy)
        return TrendsResponse(
            days = emptyList(),
            createdSeries = r.createdSeries.map { TrendPoint(Instant.fromEpochMilliseconds(it.first.toEpochMilli()), it.second) },
            resolvedSeries = r.resolvedSeries.map { TrendPoint(Instant.fromEpochMilliseconds(it.first.toEpochMilli()), it.second) },
            groupBy = r.groupBy,
        )
    }

    fun sla(period: AnalyticsPeriod): List<SlaStat> {
        val rows = repo.loadComplaints(periodStart(period))
        return ProblemCategory.entries.map { cat ->
            val slaHours = CategorySla.hoursFor(cat)
            val resolved = rows.filter {
                it.category == cat && it.status == ComplaintStatus.RESOLVED && it.resolvedAt != null
            }
            val breach = resolved.count { Duration.between(it.createdAt, it.resolvedAt!!).toHours() > slaHours }
            val breachPct = if (resolved.isEmpty()) 0.0 else round1(breach * 100.0 / resolved.size)
            SlaStat(
                category = cat,
                label = cat.localizedLabel,
                slaHours = slaHours,
                avgResolutionHours = avgResolutionHours(resolved),
                breachPct = breachPct,
                resolvedCount = resolved.size
            )
        }.filter { it.resolvedCount > 0 }
            .sortedBy { it.category.ordinal }
    }

    fun votesImpact(period: AnalyticsPeriod): List<VotesBucket> {
        val resolved = repo.loadComplaints(periodStart(period))
            .filter { it.status == ComplaintStatus.RESOLVED && it.resolvedAt != null }
        if (resolved.isEmpty()) return BUCKET_ORDER.map { VotesBucket(it, 0, null) }

        val votes = repo.voteCounts(resolved.map { it.id })
        val bucketed = resolved.groupBy { row -> bucketFor(votes[row.id] ?: 0) }

        return BUCKET_ORDER.map { label ->
            val rs = bucketed[label].orEmpty()
            VotesBucket(
                bucket = label,
                count = rs.size,
                avgResolutionHours = avgResolutionHours(rs)
            )
        }
    }

    /** Дневной ряд за последние 30 дней: создано/решено по датам (UTC). */
    fun trends(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): TrendsResponse {
        val rows = repo.loadComplaints(periodStart = null)
        val today = now.toLocalDate()
        val days = (29 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DailyPoint(
                date = day.toString(),
                created = rows.count { it.createdAt.toLocalDate() == day },
                resolved = rows.count { it.resolvedAt != null && it.resolvedAt.toLocalDate() == day },
            )
        }
        return TrendsResponse(days)
    }

    private fun periodStart(period: AnalyticsPeriod): OffsetDateTime? {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return when (period) {
            AnalyticsPeriod.WEEK -> now.minusDays(7)
            AnalyticsPeriod.MONTH -> now.minusDays(30)
            AnalyticsPeriod.QUARTER -> now.minusDays(90)
            AnalyticsPeriod.YEAR -> now.minusDays(365)
            AnalyticsPeriod.ALL -> null
        }
    }

    private fun toRange(period: AnalyticsPeriod, now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): Pair<OffsetDateTime, OffsetDateTime> {
        val start = when (period) {
            AnalyticsPeriod.WEEK -> now.minusDays(7)
            AnalyticsPeriod.MONTH -> now.minusDays(30)
            AnalyticsPeriod.QUARTER -> now.minusDays(90)
            AnalyticsPeriod.YEAR -> now.minusDays(365)
            AnalyticsPeriod.ALL -> now.minusYears(10)
        }
        return start to now
    }

    private fun avgResolutionHours(rows: List<AnalyticsRepository.Row>): Double? {
        if (rows.isEmpty()) return null
        val hours = rows.mapNotNull { r ->
            r.resolvedAt?.let { Duration.between(r.createdAt, it).toMinutes() / 60.0 }
        }
        if (hours.isEmpty()) return null
        return round1(hours.average())
    }

    /** KPI за текущие 30 дней и предыдущие 30 дней — для дельт на дашборде. */
    private fun monthlyKpis(rows: List<AnalyticsRepository.Row>, now: OffsetDateTime): MonthlyKpis {
        val curStart = now.minusDays(30)
        val prevStart = now.minusDays(60)

        fun window(from: OffsetDateTime, to: OffsetDateTime): Triple<Int, Double?, Double?> {
            val created = rows.count { it.createdAt >= from && it.createdAt < to }
            val resolvedInWindow = rows.filter {
                it.status == ComplaintStatus.RESOLVED && it.resolvedAt != null &&
                    it.resolvedAt >= from && it.resolvedAt < to
            }
            // Пустой набор resolved — нет базы для процента; возвращаем null,
            // иначе UI отрисует "0% решено", что вводит в заблуждение
            // при отсутствии данных (см. prevAvgResolutionHours рядом).
            val pct = if (resolvedInWindow.isEmpty()) null
            else {
                val within7d = resolvedInWindow.count {
                    Duration.between(it.createdAt, it.resolvedAt!!).toHours() <= 7 * 24
                }
                round1(within7d * 100.0 / resolvedInWindow.size)
            }
            return Triple(created, avgResolutionHours(resolvedInWindow), pct)
        }

        val (curTotal, curAvg, curPct) = window(curStart, now)
        val (prevTotal, prevAvg, prevPct) = window(prevStart, curStart)

        val createdInWindow = rows.filter { it.createdAt >= curStart && it.createdAt < now }
        val byStatus = createdInWindow.groupingBy { it.status }.eachCount()

        return MonthlyKpis(
            total = curTotal,
            prevTotal = prevTotal,
            avgResolutionHours = curAvg,
            prevAvgResolutionHours = prevAvg,
            resolvedWithin7dPct = curPct,
            prevResolvedWithin7dPct = prevPct,
            newCount = byStatus[ComplaintStatus.NEW] ?: 0,
            inProgressCount = byStatus[ComplaintStatus.IN_PROGRESS] ?: 0,
            resolvedCount = byStatus[ComplaintStatus.RESOLVED] ?: 0,
            rejectedCount = byStatus[ComplaintStatus.REJECTED] ?: 0,
            duplicateCount = byStatus[ComplaintStatus.DUPLICATE] ?: 0,
        )
    }

    private fun bucketFor(votes: Int): String = when {
        votes <= 0 -> "0"
        votes <= 9 -> "1-9"
        votes <= 49 -> "10-49"
        else -> "50+"
    }

    private fun round1(d: Double): Double = kotlin.math.round(d * 10) / 10.0

    companion object {
        private val BUCKET_ORDER = listOf("0", "1-9", "10-49", "50+")
    }
}

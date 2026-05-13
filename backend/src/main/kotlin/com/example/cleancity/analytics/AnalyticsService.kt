package com.example.cleancity.analytics

import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.AnalyticsPeriod
import com.example.cleancity.shared.models.CategorySla
import com.example.cleancity.shared.models.CategoryStat
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.SlaStat
import com.example.cleancity.shared.models.VotesBucket
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset

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

        return AnalyticsOverview(
            total = rows.size,
            new = byStatus[ComplaintStatus.NEW] ?: 0,
            inProgress = byStatus[ComplaintStatus.IN_PROGRESS] ?: 0,
            resolved = byStatus[ComplaintStatus.RESOLVED] ?: 0,
            rejected = byStatus[ComplaintStatus.REJECTED] ?: 0,
            duplicate = byStatus[ComplaintStatus.DUPLICATE] ?: 0,
            today = rows.count { it.createdAt >= todayStart },
            week = rows.count { it.createdAt >= weekStart },
            slaBreachCount = slaBreachCount
        )
    }

    fun byCategory(period: AnalyticsPeriod): List<CategoryStat> {
        val rows = repo.loadComplaints(periodStart(period))
        val total = rows.size
        return ProblemCategory.entries.map { cat ->
            val catRows = rows.filter { it.category == cat }
            val resolved = catRows.filter { it.status == ComplaintStatus.RESOLVED && it.resolvedAt != null }
            val avgHours = avgResolutionHours(resolved)
            CategoryStat(
                category = cat,
                label = cat.localizedLabel,
                count = catRows.size,
                sharePct = if (total == 0) 0.0 else round1(catRows.size * 100.0 / total),
                avgResolutionHours = avgHours
            )
        }.filter { it.count > 0 }
            .sortedByDescending { it.count }
    }

    fun byDistrict(period: AnalyticsPeriod): List<DistrictStat> {
        val rows = repo.loadComplaints(periodStart(period))
        return District.entries.map { d ->
            val dRows = rows.filter { row ->
                row.district != null && row.district.equals(d.localizedLabel, ignoreCase = true)
            }
            DistrictStat(
                district = d,
                label = d.localizedLabel,
                count = dRows.size,
                newCount = dRows.count { it.status == ComplaintStatus.NEW },
                resolvedCount = dRows.count { it.status == ComplaintStatus.RESOLVED }
            )
        }.sortedByDescending { it.count }
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

    private fun periodStart(period: AnalyticsPeriod): OffsetDateTime? {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return when (period) {
            AnalyticsPeriod.WEEK -> now.minusDays(7)
            AnalyticsPeriod.MONTH -> now.minusDays(30)
            AnalyticsPeriod.ALL -> null
        }
    }

    private fun avgResolutionHours(rows: List<AnalyticsRepository.Row>): Double? {
        if (rows.isEmpty()) return null
        val hours = rows.mapNotNull { r ->
            r.resolvedAt?.let { Duration.between(r.createdAt, it).toMinutes() / 60.0 }
        }
        if (hours.isEmpty()) return null
        return round1(hours.average())
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

package com.example.cleancity.shared.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class AnalyticsPeriod { WEEK, MONTH, ALL }

@Serializable
data class AnalyticsOverview(
    val total: Int,
    val new: Int,
    val inProgress: Int,
    val resolved: Int,
    val rejected: Int,
    val duplicate: Int,
    val today: Int,
    val week: Int,
    val slaBreachCount: Int,
    val monthlyKpis: MonthlyKpis,
)

@Serializable
data class CategoryStat(
    val category: ProblemCategory,
    val label: String,
    val count: Int,
    val sharePct: Double,
    val avgResolutionHours: Double?,
    val medianResolutionHours: Double? = null,
    val p90ResolutionHours: Double? = null,
    val slaCompliancePct: Double? = null,
)

@Serializable
data class DistrictStat(
    val district: District,
    val label: String,
    val count: Int,
    val newCount: Int,
    val resolvedCount: Int,
    val medianResolutionHours: Double? = null,
    val slaCompliancePct: Double? = null,
)

@Serializable
data class SlaStat(
    val category: ProblemCategory,
    val label: String,
    val slaHours: Int,
    val avgResolutionHours: Double?,
    val breachPct: Double,
    val resolvedCount: Int
)

@Serializable
data class VotesBucket(
    val bucket: String,
    val count: Int,
    val avgResolutionHours: Double?
)

@Deprecated("Используется только в legacy /analytics/overview. Перейти на OperationalSnapshot + StrategicKpis.")
@Serializable
data class MonthlyKpis(
    val total: Int,
    val prevTotal: Int,
    val avgResolutionHours: Double?,
    val prevAvgResolutionHours: Double?,
    val resolvedWithin7dPct: Double?,
    val prevResolvedWithin7dPct: Double?,
    val newCount: Int,
    val inProgressCount: Int,
    val resolvedCount: Int,
    val rejectedCount: Int,
    val duplicateCount: Int,
)

@Serializable
data class DailyPoint(
    val date: String,
    val created: Int,
    val resolved: Int,
)

@Serializable
data class TrendPoint(
    val bucketStart: Instant,
    val value: Int,
)

@Serializable
data class TrendsResponse(
    val days: List<DailyPoint> = emptyList(),
    val createdSeries: List<TrendPoint> = emptyList(),
    val resolvedSeries: List<TrendPoint> = emptyList(),
    val groupBy: String = "day",
)

@Serializable
data class OperationalSnapshot(
    val backlog: Int,                     // count(NEW + IN_PROGRESS) на момент запроса
    val overdueNow: Int,                  // count(open AND now() > slaDueAt)
    val avgDtaHours24h: Double?,          // среднее DTA по жалобам, ack-нутым за последние 24ч
    val dtaTargetHours: Double,           // = AnalyticsConfig.DTA_TARGET_HOURS
    val createdToday: Int,                // count за текущий день в Europe/Moscow
    val createdYesterday: Int,            // для дельты
    val statusBreakdown: Map<String, Int> // NEW/IN_PROGRESS/RESOLVED/REJECTED/DUPLICATE за 30 дней
)

@Serializable
data class BurningComplaintItem(
    val id: Long,
    val title: String,
    val districtCode: String?,
    val category: String,
    val createdAt: Instant,
    val slaDueAt: Instant,
    val secondsToDeadline: Long           // отрицательное = overdue
)

@Serializable
data class StrategicKpis(
    val slaCompliancePct: Double,
    val slaTargetPct: Double,             // = AnalyticsConfig.SLA_TARGET_PCT
    val medianResolutionHours: Double?,
    val p90ResolutionHours: Double?,
    val reopenRate: Double,
    val reopenTargetPct: Double,          // = AnalyticsConfig.REOPEN_TARGET_PCT
    val throughput: Int                   // закрыто за период
)

@Serializable
data class ReopenStat(
    val reopenRate: Double,
    val reopenCount: Int,
    val resolvedCount: Int,
)

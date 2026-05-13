package com.example.cleancity.shared.models

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
    val slaBreachCount: Int
)

@Serializable
data class CategoryStat(
    val category: ProblemCategory,
    val label: String,
    val count: Int,
    val sharePct: Double,
    val avgResolutionHours: Double?
)

@Serializable
data class DistrictStat(
    val district: District,
    val label: String,
    val count: Int,
    val newCount: Int,
    val resolvedCount: Int
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

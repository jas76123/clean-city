package com.example.cleancity.shared.responses.admin

import kotlinx.serialization.Serializable

@Serializable
data class ModerationSummaryResponse(
    val rejectedCountSinceWarning: Int,
    val flagged: Boolean,
    val isWarned: Boolean,
    val isBanned: Boolean,
)

@Serializable
data class WarnResidentRequest(
    val reason: String,
    val complaintId: Long,
)

@Serializable
data class BanResidentRequest(
    val reason: String,
)

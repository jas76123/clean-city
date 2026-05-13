package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class AnnouncementResponse(
    val id: Long,
    val title: String,
    val body: String,
    val iconStyle: IconStyle,
    val category: ProblemCategory? = null,
    val districts: List<String>,
    val authorId: Long,
    val publishedAt: String,
    val expiresAt: String? = null
)

@Serializable
data class AnnouncementsListResponse(
    val items: List<AnnouncementResponse>,
    val total: Long
)

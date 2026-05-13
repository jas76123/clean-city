package com.example.cleancity.shared.requests

import com.example.cleancity.shared.models.IconStyle
import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.serialization.Serializable

@Serializable
data class CreateAnnouncementRequest(
    val title: String,
    val body: String,
    val iconStyle: IconStyle = IconStyle.INFO,
    val category: ProblemCategory? = null,
    val districts: List<String> = emptyList(),
    val expiresAt: String? = null
)

@Serializable
data class UpdateAnnouncementRequest(
    val title: String? = null,
    val body: String? = null,
    val iconStyle: IconStyle? = null,
    val category: ProblemCategory? = null,
    val districts: List<String>? = null,
    val expiresAt: String? = null
)

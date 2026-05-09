package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ComplaintPhotoResponse(
    val id: Long,
    val photoUrl: String,
    val thumbUrl: String,
    val sortOrder: Int
)

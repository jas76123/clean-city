package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ComplaintResponse(
    val id: Long,
    val category: ProblemCategory,
    val description: String,
    val photoUrl: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val status: ComplaintStatus,
    val createdAt: String
)

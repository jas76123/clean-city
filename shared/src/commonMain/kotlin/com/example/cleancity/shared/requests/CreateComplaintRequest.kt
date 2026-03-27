package com.example.cleancity.shared.requests

import com.example.cleancity.shared.models.ProblemType
import kotlinx.serialization.Serializable

@Serializable
data class CreateComplaintRequest(
    val type: ProblemType,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String
)

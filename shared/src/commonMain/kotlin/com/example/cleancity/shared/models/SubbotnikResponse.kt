package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class SubbotnikResponse(
    val id: Long,
    val title: String,
    val description: String,
    val photoUrl: String?,
    val date: String,
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val createdAt: String
)

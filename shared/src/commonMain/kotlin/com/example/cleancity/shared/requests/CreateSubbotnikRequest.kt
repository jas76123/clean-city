package com.example.cleancity.shared.requests

import kotlinx.serialization.Serializable

@Serializable
data class CreateSubbotnikRequest(
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String
)

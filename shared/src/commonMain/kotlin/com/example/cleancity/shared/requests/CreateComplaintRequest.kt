package com.example.cleancity.shared.requests

import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.serialization.Serializable

@Serializable
data class CreateComplaintRequest(
    val category: ProblemCategory,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    /**
     * TODO (Week 1 / Day 2): убрать после внедрения email-auth + JWT.
     * Сейчас оставлено для совместимости с текущим скелетом, но в новой
     * модели `author_id` заполняется из JWT-claims на бэкенде.
     */
    val deviceId: String
)

package com.example.cleancity.shared.requests

import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.serialization.Serializable

/**
 * Тело JSON-части `data` в multipart-запросе `POST /complaints`.
 * `author_id` берётся из JWT-claims, не из тела.
 * `title` авто-генерится сервером как «<Категория> · <улица>».
 */
@Serializable
data class CreateComplaintRequest(
    val category: ProblemCategory,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val district: String? = null
)

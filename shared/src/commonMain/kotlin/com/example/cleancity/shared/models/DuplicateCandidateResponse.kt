package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Облегчённая карточка для GET /complaints/duplicates — предупреждение
 * «Возможно, проблема уже есть» при создании жалобы. Без описания и фото,
 * с расстоянием в метрах от запрошенной точки.
 */
@Serializable
data class DuplicateCandidateResponse(
    val id: Long,
    val title: String,
    val category: ProblemCategory,
    val status: ComplaintStatus,
    val address: String,
    val votesCount: Int,
    val distanceMeters: Int,
    val createdAt: String
)

@Serializable
data class DuplicateCandidatesResponse(
    val items: List<DuplicateCandidateResponse>
)

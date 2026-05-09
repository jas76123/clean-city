package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Жалоба для списка и деталей. В Day 4 голоса и история статусов отсутствуют —
 * `votesCount=0`, `statusHistory=[]`. Day 5 заполнит их.
 */
@Serializable
data class ComplaintResponse(
    val id: Long,
    val authorId: Long,
    val authorName: String? = null,
    val category: ProblemCategory,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val district: String? = null,
    val status: ComplaintStatus,
    val photos: List<ComplaintPhotoResponse> = emptyList(),
    val votesCount: Int = 0,
    val duplicateOfId: Long? = null,
    val createdAt: String,
    val updatedAt: String,
    val resolvedAt: String? = null,
    val statusHistory: List<StatusChangeResponse> = emptyList()
)

@Serializable
data class StatusChangeResponse(
    val fromStatus: ComplaintStatus? = null,
    val toStatus: ComplaintStatus,
    val comment: String? = null,
    val changedByName: String? = null,
    val createdAt: String
)

@Serializable
data class ComplaintListResponse(
    val items: List<ComplaintResponse>,
    val page: Int,
    val size: Int,
    val total: Long
)

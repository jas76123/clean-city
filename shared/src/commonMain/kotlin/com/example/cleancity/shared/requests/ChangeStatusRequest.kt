package com.example.cleancity.shared.requests

import com.example.cleancity.shared.models.ComplaintStatus
import kotlinx.serialization.Serializable

/**
 * Тело PATCH /complaints/{id}/status. SPEC §5.2:
 *  - comment обязателен для любой смены статуса;
 *  - duplicateOfId обязателен только для toStatus=DUPLICATE.
 */
@Serializable
data class ChangeStatusRequest(
    val toStatus: ComplaintStatus,
    val comment: String,
    val duplicateOfId: Long? = null
)

package com.example.cleancity.ui.feature.detail

import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.StatusChangeResponse

/**
 * Комментарий администрации для закрытой жалобы (REJECTED/DUPLICATE) —
 * текст последней записи истории статусов. Для остальных статусов — null.
 */
fun resolutionComment(
    status: ComplaintStatus,
    statusHistory: List<StatusChangeResponse>,
): String? {
    if (status != ComplaintStatus.REJECTED && status != ComplaintStatus.DUPLICATE) return null
    return statusHistory.lastOrNull()?.comment
}

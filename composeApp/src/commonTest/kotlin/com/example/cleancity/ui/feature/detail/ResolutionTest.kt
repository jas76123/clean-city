package com.example.cleancity.ui.feature.detail

import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.StatusChangeResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolutionTest {

    private fun change(to: ComplaintStatus, comment: String) = StatusChangeResponse(
        toStatus = to, comment = comment, createdAt = "2026-05-21T10:00:00Z",
    )

    @Test fun `rejected returns last status change comment`() {
        val history = listOf(
            change(ComplaintStatus.IN_PROGRESS, "взято в работу"),
            change(ComplaintStatus.REJECTED, "вне зоны ответственности города"),
        )
        assertEquals("вне зоны ответственности города", resolutionComment(ComplaintStatus.REJECTED, history))
    }

    @Test fun `duplicate returns last status change comment`() {
        val history = listOf(change(ComplaintStatus.DUPLICATE, "дубликат существующей жалобы"))
        assertEquals("дубликат существующей жалобы", resolutionComment(ComplaintStatus.DUPLICATE, history))
    }

    @Test fun `non-terminal status returns null`() {
        val history = listOf(change(ComplaintStatus.IN_PROGRESS, "взято в работу"))
        assertNull(resolutionComment(ComplaintStatus.IN_PROGRESS, history))
    }

    @Test fun `resolved status returns null`() {
        val history = listOf(change(ComplaintStatus.RESOLVED, "проблема устранена"))
        assertNull(resolutionComment(ComplaintStatus.RESOLVED, history))
    }

    @Test fun `terminal status with empty history returns null`() {
        assertNull(resolutionComment(ComplaintStatus.REJECTED, emptyList()))
    }
}

package com.example.cleancity.domain

import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationEventBusTest {

    private fun ann(id: Long): NotificationResponse = NotificationResponse(
        id = id,
        kind = NotificationKind.ANNOUNCEMENT,
        title = "T-$id",
        body = "B-$id",
        createdAt = "2026-05-28T10:00:00Z",
    )

    @Test
    fun `subscribers receive emitted items`() = runTest(UnconfinedTestDispatcher()) {
        val bus = NotificationEventBus()
        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.take(2).toList(collected) }

        bus.emit(ann(1))
        bus.emit(ann(2))
        job.join()

        assertEquals(listOf(1L, 2L), collected.map { it.id })
    }

    @Test
    fun `late subscriber misses earlier emits (replay=0)`() = runTest(UnconfinedTestDispatcher()) {
        val bus = NotificationEventBus()
        bus.emit(ann(1))   // нет подписчиков — улетает в никуда

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.take(1).toList(collected) }
        bus.emit(ann(2))
        job.join()

        assertEquals(listOf(2L), collected.map { it.id })
    }
}

package com.example.cleancity.domain

import com.example.cleancity.data.local.InMemorySeenNotificationStore
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementSeenFilterTest {

    private fun item(
        id: Long,
        kind: NotificationKind = NotificationKind.ANNOUNCEMENT,
        readAt: String? = null,
    ): NotificationResponse = NotificationResponse(
        id = id,
        kind = kind,
        title = "T-$id",
        body = "B-$id",
        readAt = readAt,
        createdAt = "2026-05-28T10:00:00Z",
    )

    @Test
    fun `first call with lastSeen=0 returns empty and memorizes max`() = runTest {
        val store = InMemorySeenNotificationStore()
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(item(3), item(2), item(1))
        val newOnes = filter.newAnnouncements(userId = 42L, items = items)

        assertTrue(newOnes.isEmpty(), "Первый вызов не должен возвращать ничего")
        assertEquals(3L, store.get(42L), "lastSeen должен стать max(items)=3")
    }

    @Test
    fun `first call with empty items leaves store untouched`() = runTest {
        val store = InMemorySeenNotificationStore()
        val filter = AnnouncementSeenFilter(store)

        val newOnes = filter.newAnnouncements(userId = 42L, items = emptyList())

        assertTrue(newOnes.isEmpty())
        assertEquals(0L, store.get(42L))
    }

    @Test
    fun `subsequent call returns only items newer than lastSeen`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 5L)
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(item(7), item(6), item(5), item(4))
        val newOnes = filter.newAnnouncements(42L, items)

        assertEquals(listOf(7L, 6L), newOnes.map { it.id }.sortedDescending())
        assertEquals(7L, store.get(42L))
    }

    @Test
    fun `filter includes COMPLAINT_STATUS as well as ANNOUNCEMENT`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 5L)
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(
            item(7, kind = NotificationKind.COMPLAINT_STATUS),
            item(6, kind = NotificationKind.ANNOUNCEMENT),
        )
        val newOnes = filter.newAnnouncements(42L, items)

        assertEquals(listOf(7L, 6L), newOnes.map { it.id })
        assertEquals(7L, store.get(42L))
    }

    @Test
    fun `filter excludes already-read announcements`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 5L)
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(
            item(7, readAt = "2026-05-28T10:00:00Z"),
            item(6),
        )
        val newOnes = filter.newAnnouncements(42L, items)

        assertEquals(listOf(6L), newOnes.map { it.id })
    }

    @Test
    fun `lastSeen not updated when no items newer than current`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 10L)
        val filter = AnnouncementSeenFilter(store)

        filter.newAnnouncements(42L, listOf(item(5), item(3)))

        assertEquals(10L, store.get(42L), "lastSeen не должен откатиться назад")
    }

    @Test
    fun `per-user isolation`() = runTest {
        val store = InMemorySeenNotificationStore()
        val filter = AnnouncementSeenFilter(store)

        filter.newAnnouncements(1L, listOf(item(10)))
        val newForUser2 = filter.newAnnouncements(2L, listOf(item(10), item(11)))

        assertTrue(newForUser2.isEmpty())
        assertEquals(10L, store.get(1L))
        assertEquals(11L, store.get(2L))
    }
}

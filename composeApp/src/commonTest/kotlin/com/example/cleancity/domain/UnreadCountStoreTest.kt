package com.example.cleancity.domain

import com.example.cleancity.data.local.InMemorySeenNotificationStore
import com.example.cleancity.data.network.FakeNotificationsApi
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnreadCountStoreTest {

    private val testUser = UserResponse(
        id = 42L,
        email = "u@x",
        role = UserRole.RESIDENT,
        emailVerified = true,
        createdAt = "2026-05-01T00:00:00Z",
    )

    private fun fakeAuth(): TestAuthRepository = TestAuthRepository(
        MutableStateFlow(AuthState.Authenticated(testUser)),
    )

    private fun ann(id: Long, readAt: String? = null) = NotificationResponse(
        id = id,
        kind = NotificationKind.ANNOUNCEMENT,
        title = "T-$id",
        body = "B-$id",
        readAt = readAt,
        createdAt = "2026-05-28T10:00:00Z",
    )

    private fun newStore(
        api: FakeNotificationsApi,
        seen: InMemorySeenNotificationStore = InMemorySeenNotificationStore(),
        bus: NotificationEventBus = NotificationEventBus(),
        auth: TestAuthRepository = fakeAuth(),
        scope: TestScope,
    ): UnreadCountStore = UnreadCountStore(
        api = api,
        seenStore = seen,
        filter = AnnouncementSeenFilter(seen),
        bus = bus,
        authProvider = { (auth.state.value as? AuthState.Authenticated)?.user?.id },
        scope = scope,
        dispatcher = UnconfinedTestDispatcher(scope.testScheduler),
    )

    private fun listResp(items: List<NotificationResponse>) =
        NotificationListResponse(items = items, total = items.size.toLong(), hasMore = false)

    @Test
    fun `state reflects count of unread items from list`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(3), ann(2), ann(1))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()

        assertEquals(3, store.state.value)
        store.stop()
    }

    @Test
    fun `first poll after login does not emit to bus`() = runTest {
        val bus = NotificationEventBus()
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5), ann(4))))
        }
        val store = newStore(api, bus = bus, scope = this)

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.toList(collected) }

        store.start()
        testScheduler.runCurrent()

        assertTrue(collected.isEmpty(), "Первый poll должен только запомнить max, без эмита")
        store.stop()
        job.cancel()
    }

    @Test
    fun `subsequent poll emits new ANNOUNCEMENT to bus`() = runTest {
        val bus = NotificationEventBus()
        val seen = InMemorySeenNotificationStore()
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5))))
        }
        val store = newStore(api, seen = seen, bus = bus, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(5L, seen.get(42L))

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.take(1).toList(collected) }

        // ann(5) уже прочитан — пользователь увидел его до второго poll.
        api.nextListResult = Result.success(
            listResp(listOf(ann(6), ann(5, readAt = "2026-05-28T10:01:00Z")))
        )
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()
        job.join()

        assertEquals(listOf(6L), collected.map { it.id })
        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test
    fun `subsequent poll emits COMPLAINT_STATUS to bus`() = runTest {
        val bus = NotificationEventBus()
        val seen = InMemorySeenNotificationStore()
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5))))
        }
        val store = newStore(api, seen = seen, bus = bus, scope = this)

        store.start()
        testScheduler.runCurrent()

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.take(1).toList(collected) }

        api.nextListResult = Result.success(listResp(listOf(
            NotificationResponse(
                id = 6L,
                kind = NotificationKind.COMPLAINT_STATUS,
                title = "Статус",
                body = "Жалоба обновлена",
                createdAt = "2026-05-28T10:00:00Z",
            ),
            ann(5),
        )))
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()
        job.join()

        assertEquals(listOf(6L), collected.map { it.id })
        store.stop()
    }

    @Test
    fun `api error keeps last state and silently retries`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(1, store.state.value)

        api.nextListResult = Result.failure(RuntimeException("net"))
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()

        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test
    fun `stop cancels loop, restart works`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(9))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(1, store.state.value)

        store.stop()
        assertEquals(0, store.state.value)

        api.nextListResult = Result.success(listResp(listOf(ann(9), ann(10))))
        store.start()
        testScheduler.runCurrent()
        assertEquals(2, store.state.value)
        store.stop()
    }

    @Test
    fun `start is idempotent`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(1))))
        }
        val store = newStore(api, scope = this)

        store.start()
        store.start()
        testScheduler.runCurrent()

        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test
    fun `decrement and increment still work`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(1), ann(2), ann(3), ann(4), ann(5))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(5, store.state.value)

        store.decrement(2)
        assertEquals(3, store.state.value)
        store.decrement(10)
        assertEquals(0, store.state.value)
        store.increment(3)
        assertEquals(3, store.state.value)

        store.stop()
    }

    private class TestAuthRepository(val state: StateFlow<AuthState>)
}

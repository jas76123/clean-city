package com.example.cleancity.ui.feature.notifications

import com.example.cleancity.data.network.FakeNotificationsApi
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsScreenModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun notification(id: Long, read: Boolean = false) = NotificationResponse(
        id = id,
        kind = NotificationKind.COMPLAINT_STATUS,
        title = "Заголовок $id",
        body = "Текст $id",
        complaintId = id,
        readAt = if (read) "2026-05-21T10:00:00Z" else null,
        createdAt = "2026-05-21T09:00:00Z",
    )

    // UnreadCountStore.stop() обнуляет счётчик, поэтому посеять значение можно
    // только через один прогон polling: start() + runCurrent(). Polling-job
    // оставляем активным до конца теста — в самом тесте вызвать store.stop().
    private fun TestScope.seededStore(initial: Int): UnreadCountStore {
        val store = UnreadCountStore(
            api = FakeNotificationsApi().apply { nextCount = initial.toLong() },
            scope = this,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        store.start()
        testScheduler.runCurrent()
        return store
    }

    @Test fun `load with empty list yields Empty`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(NotificationListResponse(emptyList(), 0, false))
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))

        model.load()

        assertEquals(NotificationsState.Empty, model.state.value)
    }

    @Test fun `load with items yields Loaded`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))

        model.load()

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(2, state.items.size)
    }

    @Test fun `load failure yields Error`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.failure(RuntimeException("нет сети"))
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))

        model.load()

        assertTrue(model.state.value is NotificationsState.Error)
    }

    @Test fun `markRead optimistically marks item and decrements badge`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val store = seededStore(2)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markRead(1L)

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(true, state.items.first { it.id == 1L }.readAt != null)
        assertEquals(listOf(1L), api.markReadCalls)
        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test fun `markRead on already-read item does not decrement badge`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1, read = true)), 1, false)
            )
        }
        val store = seededStore(3)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markRead(1L)

        assertEquals(3, store.state.value)
        assertTrue(api.markReadCalls.isEmpty())
        store.stop()
    }

    @Test fun `markRead rolls item back to unread on api failure`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1)), 1, false)
            )
            markReadShouldThrow = true
        }
        val store = seededStore(1)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markRead(1L)

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(null, state.items.first { it.id == 1L }.readAt)  // откат — снова непрочитано
        store.stop()
    }

    @Test fun `markAllRead marks every item and zeroes badge`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val store = seededStore(2)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markAllRead()

        val state = model.state.value as NotificationsState.Loaded
        assertTrue(state.items.all { it.readAt != null })
        assertEquals(1, api.markAllReadCalls)
        assertEquals(0, store.state.value)
        store.stop()
    }

    @Test fun `unreadCount reflects unread items`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(
                    listOf(notification(1), notification(2, read = true), notification(3)), 3, false
                )
            )
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))
        model.load()

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(2, state.unreadCount)
    }
}

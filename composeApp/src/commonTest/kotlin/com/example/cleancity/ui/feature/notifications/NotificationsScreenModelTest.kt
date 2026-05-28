package com.example.cleancity.ui.feature.notifications

import com.example.cleancity.data.local.InMemorySeenNotificationStore
import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeNotificationsApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.data.storage.Tokens
import com.example.cleancity.domain.AnnouncementSeenFilter
import com.example.cleancity.domain.NotificationEventBus
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
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
import kotlin.test.assertNotNull
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

    /** Конструктор UnreadCountStore с разумными дефолтами для тестов screen-model. */
    private fun TestScope.makeStore(
        api: FakeNotificationsApi = FakeNotificationsApi(),
        userId: Long? = 1L,
    ): UnreadCountStore {
        val seen = InMemorySeenNotificationStore()
        return UnreadCountStore(
            api = api,
            seenStore = seen,
            filter = AnnouncementSeenFilter(seen),
            bus = NotificationEventBus(),
            authProvider = { userId },
            scope = this,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    // UnreadCountStore.stop() обнуляет счётчик, поэтому посеять значение можно
    // только через один прогон polling: start() + runCurrent(). Polling-job
    // оставляем активным до конца теста — в самом тесте вызвать store.stop().
    private fun TestScope.seededStore(initial: Int): UnreadCountStore {
        val items = (1..initial).map {
            NotificationResponse(
                id = it.toLong(),
                kind = NotificationKind.ANNOUNCEMENT,
                title = "T",
                body = "B",
                createdAt = "2026-05-21T09:00:00Z",
            )
        }
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(items, items.size.toLong(), false)
            )
        }
        val store = makeStore(api = api)
        store.start()
        testScheduler.runCurrent()
        return store
    }

    private suspend fun authedRepo(): AuthRepository {
        val user = UserResponse(
            id = 1, email = "u@x.com", role = UserRole.RESIDENT,
            fullName = "U", emailVerified = true, createdAt = "2026-05-21T00:00:00Z",
        )
        return AuthRepository(
            FakeAuthApi().asAuthApi(),
            FakeUserApi(meResult = Result.success(user)).asUserApi(),
            FakeTokenStorage().apply { preset(Tokens("acc", "ref")) },
        ).apply { init() }
    }

    private fun guestRepo(): AuthRepository = AuthRepository(
        FakeAuthApi().asAuthApi(),
        FakeUserApi().asUserApi(),
        FakeTokenStorage(),
    ).apply { continueAsGuest() }

    @Test fun `load with empty list yields Empty`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(NotificationListResponse(emptyList(), 0, false))
        }
        val model = NotificationsScreenModel(api, makeStore(), authedRepo(), NotificationEventBus())

        model.load()

        assertEquals(NotificationsState.Empty, model.state.value)
    }

    @Test fun `load with items yields Loaded`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val model = NotificationsScreenModel(api, makeStore(), authedRepo(), NotificationEventBus())

        model.load()

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(2, state.items.size)
    }

    @Test fun `load failure yields Error`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.failure(RuntimeException("нет сети"))
        }
        val model = NotificationsScreenModel(api, makeStore(), authedRepo(), NotificationEventBus())

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
        val model = NotificationsScreenModel(api, store, authedRepo(), NotificationEventBus())
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
        val model = NotificationsScreenModel(api, store, authedRepo(), NotificationEventBus())
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
        val model = NotificationsScreenModel(api, store, authedRepo(), NotificationEventBus())
        model.load()

        model.markRead(1L)

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(null, state.items.first { it.id == 1L }.readAt)  // откат — снова непрочитано
        assertEquals(1, store.state.value)  // бейдж восстановлен: 1 -> 0 -> 1
        assertNotNull(state.transientError)  // snackbar-ошибка выставлена
        store.stop()
    }

    @Test fun `markAllRead marks every item and zeroes badge`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val store = seededStore(2)
        val model = NotificationsScreenModel(api, store, authedRepo(), NotificationEventBus())
        model.load()

        model.markAllRead()

        val state = model.state.value as NotificationsState.Loaded
        assertTrue(state.items.all { it.readAt != null })
        assertEquals(1, api.markAllReadCalls)
        assertEquals(0, store.state.value)
        store.stop()
    }

    @Test fun `markAllRead rolls back on api failure`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
            nextMarkAllResult = Result.failure(RuntimeException("нет сети"))
        }
        val store = seededStore(2)
        val model = NotificationsScreenModel(api, store, authedRepo(), NotificationEventBus())
        model.load()

        model.markAllRead()

        val state = model.state.value as NotificationsState.Loaded
        assertTrue(state.items.all { it.readAt == null })  // откат — все непрочитаны
        assertEquals(2, store.state.value)             // бейдж восстановлен
        assertNotNull(state.transientError)             // snackbar-ошибка выставлена
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
        val model = NotificationsScreenModel(api, makeStore(), authedRepo(), NotificationEventBus())
        model.load()

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(2, state.unreadCount)
    }

    @Test fun `load as guest yields GuestPrompt and never calls api`() = runTest {
        val api = FakeNotificationsListApi()
        val model = NotificationsScreenModel(api, makeStore(), guestRepo(), NotificationEventBus())

        model.load()

        assertEquals(NotificationsState.GuestPrompt, model.state.value)
        assertEquals(0, api.listCalls)
    }

    @Test fun `load as authenticated user calls api`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(NotificationListResponse(emptyList(), 0, false))
        }
        val model = NotificationsScreenModel(api, makeStore(), authedRepo(), NotificationEventBus())

        model.load()

        assertEquals(1, api.listCalls)
        assertEquals(NotificationsState.Empty, model.state.value)
    }
}

package com.example.cleancity.domain

import com.example.cleancity.data.network.FakeNotificationsApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UnreadCountStoreTest {

    private fun newStore(api: FakeNotificationsApi, scope: TestScope): UnreadCountStore =
        UnreadCountStore(api = api, scope = scope, dispatcher = UnconfinedTestDispatcher(scope.testScheduler))

    @Test
    fun `start triggers immediate fetch and updates state`() = runTest {
        val api = FakeNotificationsApi().apply { nextCount = 7L }
        val store = newStore(api, this)

        store.start()
        testScheduler.runCurrent()

        assertEquals(7, store.state.value)
        assertEquals(1, api.callCount)
        store.stop()
    }

    @Test
    fun `polls again after 30 seconds with updated count`() = runTest {
        val api = FakeNotificationsApi().apply { nextCount = 3L }
        val store = newStore(api, this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(3, store.state.value)
        assertEquals(1, api.callCount)

        api.nextCount = 11L
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()

        assertEquals(11, store.state.value)
        assertEquals(2, api.callCount)
        store.stop()
    }

    @Test
    fun `api error keeps last successful state`() = runTest {
        val api = FakeNotificationsApi().apply { nextCount = 5L }
        val store = newStore(api, this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(5, store.state.value)

        api.shouldThrow = true
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()

        assertEquals(5, store.state.value)
        store.stop()
    }

    @Test
    fun `stop cancels loop, resets state, restart works`() = runTest {
        val api = FakeNotificationsApi().apply { nextCount = 9L }
        val store = newStore(api, this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(9, store.state.value)
        val callsAfterStart = api.callCount

        store.stop()
        assertEquals(0, store.state.value)

        testScheduler.advanceTimeBy(60_001)
        testScheduler.runCurrent()
        assertEquals(callsAfterStart, api.callCount)

        api.nextCount = 42L
        store.start()
        testScheduler.runCurrent()
        assertEquals(42, store.state.value)
        store.stop()
    }

    @Test
    fun `start is idempotent — second call does not increase poll rate`() = runTest {
        val api = FakeNotificationsApi().apply { nextCount = 1L }
        val store = newStore(api, this)

        store.start()
        store.start()   // second call must be a no-op
        testScheduler.runCurrent()

        assertEquals(1, api.callCount)  // only one fetch, not two
        store.stop()
    }
}

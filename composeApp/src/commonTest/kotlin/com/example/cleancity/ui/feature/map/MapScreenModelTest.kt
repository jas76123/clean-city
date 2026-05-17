package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.SochiDefaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FakeComplaintsApi
    private lateinit var location: FakeLocationProvider

    @BeforeTest
    fun setup() {
        api = FakeComplaintsApi()
        location = FakeLocationProvider()
    }

    @Test
    fun `init triggers map load with default Sochi bbox`() = runTest(dispatcher) {
        val model = MapScreenModel(api, location, dispatcher)

        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        val call = api.calls.first()
        assertEquals(SochiDefaults.BBOX.swLat, call.swLat)
        assertEquals(SochiDefaults.BBOX.swLon, call.swLon)
        assertEquals(SochiDefaults.BBOX.neLat, call.neLat)
        assertEquals(SochiDefaults.BBOX.neLon, call.neLon)
        assertEquals(null, call.category)
        model.close()
    }

    @Test
    fun `onCameraMoved debounces 500ms`() = runTest(dispatcher) {
        val model = MapScreenModel(api, location, dispatcher)
        advanceUntilIdle()
        api.calls.clear()

        repeat(5) { i ->
            model.onCameraMoved(BoundingBox(43.5 + i * 0.001, 39.5, 43.6, 39.6))
            advanceTimeBy(100)
        }
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        assertTrue(abs(api.calls.first().swLat - 43.504) < 0.0001)
        model.close()
    }
}

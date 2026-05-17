package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.SochiDefaults
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.ProblemCategory
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

    @Test
    fun `mapLatest cancels inflight when new bbox arrives`() = runTest(dispatcher) {
        api.nextDelayMs = 1000
        api.nextResponse = listOf(
            MapMarker(99, ProblemCategory.GARBAGE, ComplaintStatus.NEW, 43.0, 39.0),
        )
        val model = MapScreenModel(api, location, dispatcher)
        advanceUntilIdle()
        api.calls.clear()

        // bbox#1: задержка 1000мс — стартует
        model.onCameraMoved(BoundingBox(43.5, 39.5, 43.6, 39.6))
        advanceTimeBy(500)   // дебаунс прошёл, запрос пошёл, ждёт 1000мс
        advanceTimeBy(400)   // прошло 400мс из задержки

        // bbox#2: отменяет inflight
        api.nextDelayMs = 0
        api.nextResponse = listOf(
            MapMarker(42, ProblemCategory.ROADS, ComplaintStatus.IN_PROGRESS, 43.7, 39.7),
        )
        model.onCameraMoved(BoundingBox(44.0, 40.0, 44.1, 40.1))
        advanceTimeBy(500)
        advanceUntilIdle()

        // В state должен быть результат bbox#2 (id=42), не bbox#1 (id=99)
        assertEquals(listOf(42L), model.state.value.markers.map { it.id })
        model.close()
    }
}

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

    @Test
    fun `selectCategory triggers immediate request without debounce`() = runTest(dispatcher) {
        val model = MapScreenModel(api, location, dispatcher)
        advanceUntilIdle()
        api.calls.clear()

        model.selectCategory(ProblemCategory.GARBAGE)
        advanceTimeBy(50)
        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        assertEquals(ProblemCategory.GARBAGE, api.calls.first().category)
        assertEquals(ProblemCategory.GARBAGE, model.state.value.selectedCategory)
        model.close()
    }

    @Test
    fun `onMarkerClick sets selectedMarkerId`() = runTest(dispatcher) {
        val model = MapScreenModel(api, location, dispatcher)
        advanceUntilIdle()

        model.onMarkerClick(42L)
        assertEquals(42L, model.state.value.selectedMarkerId)
        model.close()
    }

    @Test
    fun `closeMarkerSheet clears selectedMarkerId`() = runTest(dispatcher) {
        val model = MapScreenModel(api, location, dispatcher)
        advanceUntilIdle()
        model.onMarkerClick(42L)

        model.closeMarkerSheet()
        assertEquals(null, model.state.value.selectedMarkerId)
        model.close()
    }

    @Test
    fun `selectCategory of currently selected resets to null`() = runTest(dispatcher) {
        val model = MapScreenModel(api, location, dispatcher)
        advanceUntilIdle()
        model.selectCategory(ProblemCategory.GARBAGE)
        advanceUntilIdle()
        api.calls.clear()

        model.toggleCategory(ProblemCategory.GARBAGE)
        advanceUntilIdle()

        assertEquals(null, model.state.value.selectedCategory)
        assertEquals(1, api.calls.size)
        assertEquals(null, api.calls.first().category)
        model.close()
    }

    @Test
    fun `error preserves previously loaded markers`() = runTest(dispatcher) {
        api.nextResponse = listOf(
            MapMarker(1, ProblemCategory.GARBAGE, ComplaintStatus.NEW, 43.5, 39.5),
            MapMarker(2, ProblemCategory.ROADS, ComplaintStatus.IN_PROGRESS, 43.6, 39.6),
        )
        val model = MapScreenModel(api, location, dispatcher)
        advanceUntilIdle()
        assertEquals(2, model.state.value.markers.size)

        api.nextError = RuntimeException("offline")
        model.onCameraMoved(BoundingBox(43.5, 39.5, 43.6, 39.6))
        advanceTimeBy(600)
        advanceUntilIdle()

        assertEquals(2, model.state.value.markers.size, "markers should NOT be cleared on error")
        assertEquals("offline", model.state.value.error)
        model.close()
    }
}

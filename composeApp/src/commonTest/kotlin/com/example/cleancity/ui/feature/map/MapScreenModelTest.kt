package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.location.PermissionStatus
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
    private lateinit var search: FakeMapSearchProvider

    @BeforeTest
    fun setup() {
        api = FakeComplaintsApi()
        location = FakeLocationProvider()
        search = FakeMapSearchProvider()
    }

    private fun newModel(
        api: FakeComplaintsApi = this.api,
        location: FakeLocationProvider = this.location,
        search: FakeMapSearchProvider = this.search,
    ): MapScreenModel = MapScreenModel(api, location, search, dispatcher)

    @Test
    fun `init triggers map load with default Sochi bbox`() = runTest(dispatcher) {
        val model = newModel()

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
        val model = newModel()
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
        val model = newModel()
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
        val model = newModel()
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
        val model = newModel()
        advanceUntilIdle()

        model.onMarkerClick(42L)
        assertEquals(42L, model.state.value.selectedMarkerId)
        model.close()
    }

    @Test
    fun `closeMarkerSheet clears selectedMarkerId`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()
        model.onMarkerClick(42L)

        model.closeMarkerSheet()
        assertEquals(null, model.state.value.selectedMarkerId)
        model.close()
    }

    @Test
    fun `selectCategory of currently selected resets to null`() = runTest(dispatcher) {
        val model = newModel()
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
        val model = newModel()
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

    @Test
    fun `onLocationFabClicked when granted fetches location and moves camera`() = runTest(dispatcher) {
        val provider = FakeLocationProvider(Result.success(Location(43.6, 39.8)))
        val model = newModel(location = provider)
        advanceUntilIdle()
        var launchedRequest = false

        model.onLocationFabClicked(PermissionStatus.Granted) { launchedRequest = true }
        advanceUntilIdle()

        assertEquals(1, provider.callCount)
        assertEquals(false, launchedRequest)
        assertEquals(43.6, model.state.value.cameraPosition.latitude)
        assertEquals(39.8, model.state.value.cameraPosition.longitude)
        assertEquals(15f, model.state.value.cameraPosition.zoom)
        model.close()
    }

    @Test
    fun `onLocationFabClicked when NotRequested calls launchRequest`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()
        var launched = false

        model.onLocationFabClicked(PermissionStatus.NotRequested) { launched = true }

        assertEquals(true, launched)
        assertEquals(0, location.callCount)
        model.close()
    }

    @Test
    fun `onLocationFabClicked when Denied sets error snackbar`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()

        model.onLocationFabClicked(PermissionStatus.Denied) {}

        assertEquals("Разрешите геолокацию в настройках", model.state.value.error)
        model.close()
    }

    @Test
    fun `onSearchQueryChange debounces 250ms then queries provider`() = runTest(dispatcher) {
        search.nextResult = listOf(
            MapSuggestion("id1", "ул. Ленина", "Сочи", 43.6, 39.7),
        )
        val model = newModel()
        advanceUntilIdle()

        repeat(4) { i ->
            model.onSearchQueryChange("лен" + "и".repeat(i))
            advanceTimeBy(50)
        }
        advanceTimeBy(250)
        advanceUntilIdle()

        assertEquals(1, search.calls.size, "должен быть один вызов после debounce")
        assertEquals("лениии", search.calls.first().query)
        assertEquals(SochiDefaults.BBOX, search.calls.first().region)
        assertEquals(1, model.state.value.searchSuggestions.size)
        assertEquals("ул. Ленина", model.state.value.searchSuggestions.first().title)
        model.close()
    }

    @Test
    fun `onSearchQueryChange shorter than 2 chars skips provider`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()

        model.onSearchQueryChange("л")
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals(0, search.calls.size)
        assertEquals(emptyList(), model.state.value.searchSuggestions)
        model.close()
    }

    @Test
    fun `onSuggestionSelected moves camera and clears suggestions`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()
        val suggestion = MapSuggestion("id1", "ул. Навагинская", "Сочи", 43.583, 39.720)

        model.onSuggestionSelected(suggestion)

        val state = model.state.value
        assertEquals(43.583, state.cameraPosition.latitude)
        assertEquals(39.720, state.cameraPosition.longitude)
        assertEquals(16f, state.cameraPosition.zoom)
        assertEquals(emptyList(), state.searchSuggestions)
        assertEquals("ул. Навагинская", state.searchQuery)
        model.close()
    }

    @Test
    fun `clearSearch resets query and suggestions`() = runTest(dispatcher) {
        search.nextResult = listOf(
            MapSuggestion("id1", "ул. Ленина", null, 43.6, 39.7),
        )
        val model = newModel()
        advanceUntilIdle()
        model.onSearchQueryChange("ленина")
        advanceTimeBy(300)
        advanceUntilIdle()
        assertTrue(model.state.value.searchSuggestions.isNotEmpty())

        model.clearSearch()

        assertEquals("", model.state.value.searchQuery)
        assertEquals(emptyList(), model.state.value.searchSuggestions)
        model.close()
    }
}

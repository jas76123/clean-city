package com.example.cleancity.ui.feature.map.picker

import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.SochiDefaults
import com.example.cleancity.ui.feature.map.FakeMapSearchProvider
import com.example.cleancity.ui.feature.map.ReverseGeocodeResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapPickerScreenModelTest {

    private lateinit var search: FakeMapSearchProvider
    private lateinit var bus: AddressPickerBus

    @BeforeTest fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        search = FakeMapSearchProvider()
        bus = AddressPickerBus()
    }

    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun model(lat: Double? = null, lon: Double? = null) =
        MapPickerScreenModel(lat, lon, search, bus)

    private fun bbox(lat: Double, lon: Double, halfDeg: Double = 0.01) =
        BoundingBox(
            swLat = lat - halfDeg, swLon = lon - halfDeg,
            neLat = lat + halfDeg, neLon = lon + halfDeg,
        )

    @Test fun `initial state uses provided lat-lon`() = runTest {
        val m = model(44.0, 40.0)
        assertEquals(44.0, m.state.value.currentLat)
        assertEquals(40.0, m.state.value.currentLon)
        assertNull(m.state.value.address)
        assertFalse(m.state.value.isResolving)
    }

    @Test fun `initial state defaults to Sochi center when nulls`() = runTest {
        val m = model(null, null)
        assertEquals(SochiDefaults.CENTER.latitude, m.state.value.currentLat)
        assertEquals(SochiDefaults.CENTER.longitude, m.state.value.currentLon)
    }

    @Test fun `onCameraMoved updates currentLat-Lon to bbox centroid and sets isResolving`() = runTest {
        val m = model(43.5, 39.7)
        m.onCameraMoved(bbox(43.6, 39.8))
        assertEquals(43.6, m.state.value.currentLat)
        assertEquals(39.8, m.state.value.currentLon)
        assertTrue(m.state.value.isResolving)
    }

    @Test fun `reverseGeocode fires after debounce and populates address-district`() = runTest {
        search.nextReverseResult = Result.success(ReverseGeocodeResult("ул. Парковая, 7", "Хостинский"))
        val m = model(43.5, 39.7)

        m.onCameraMoved(bbox(43.6, 39.8))
        testScheduler.advanceUntilIdle()

        assertEquals(1, search.reverseCalls.size)
        assertEquals(43.6, search.reverseCalls.first().latitude)
        assertEquals(39.8, search.reverseCalls.first().longitude)
        assertEquals("ул. Парковая, 7", m.state.value.address)
        assertEquals("Хостинский", m.state.value.district)
        assertFalse(m.state.value.isResolving)
    }

    @Test fun `rapid onCameraMoved cancels previous geocode, runs only the last`() = runTest {
        search.nextReverseResult = Result.success(ReverseGeocodeResult("addr", null))
        val m = model(43.5, 39.7)

        m.onCameraMoved(bbox(43.6, 39.8))
        m.onCameraMoved(bbox(43.7, 39.9))
        m.onCameraMoved(bbox(43.8, 40.0))
        testScheduler.advanceUntilIdle()

        assertEquals(1, search.reverseCalls.size)
        assertEquals(43.8, search.reverseCalls.first().latitude)
        assertEquals(40.0, search.reverseCalls.first().longitude)
    }

    @Test fun `failed geocode clears isResolving and sets address null`() = runTest {
        search.nextReverseResult = Result.failure(RuntimeException("timeout"))
        val m = model(43.5, 39.7)

        m.onCameraMoved(bbox(43.6, 39.8))
        testScheduler.advanceUntilIdle()

        assertNull(m.state.value.address)
        assertFalse(m.state.value.isResolving)
    }

    @Test fun `confirm publishes PickedAddress when address present`() = runTest {
        search.nextReverseResult = Result.success(ReverseGeocodeResult("ул. Кирова, 5", "Адлерский"))
        val m = model(43.5, 39.7)
        m.onCameraMoved(bbox(43.42, 39.92))
        testScheduler.advanceUntilIdle()

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            bus.results.first()
        }
        m.confirm()
        val picked = deferred.await()

        assertEquals(43.42, picked.latitude)
        assertEquals(39.92, picked.longitude)
        assertEquals("ул. Кирова, 5", picked.address)
        assertEquals("Адлерский", picked.district)
    }

    @Test fun `confirm does nothing when address null`() = runTest {
        val m = model(43.5, 39.7) // never moved, never resolved
        // Subscribe before confirm to make sure no value ever arrives.
        var received = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.results.collect { received++ }
        }
        m.confirm()
        testScheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(0, received)
    }
}

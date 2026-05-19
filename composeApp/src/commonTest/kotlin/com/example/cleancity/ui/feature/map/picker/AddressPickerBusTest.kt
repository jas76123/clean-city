package com.example.cleancity.ui.feature.map.picker

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AddressPickerBusTest {

    @Test fun `publish emits PickedAddress to results subscriber`() = runTest(UnconfinedTestDispatcher()) {
        val bus = AddressPickerBus()
        val deferred = async { bus.results.first() }
        testScheduler.advanceUntilIdle()

        bus.publish(
            PickedAddress(
                latitude = 43.5,
                longitude = 39.7,
                address = "ул. Несебрская, 1",
                district = "Центральный",
            ),
        )

        val picked = deferred.await()
        assertEquals(43.5, picked.latitude)
        assertEquals(39.7, picked.longitude)
        assertEquals("ул. Несебрская, 1", picked.address)
        assertEquals("Центральный", picked.district)
    }
}

package com.example.cleancity.domain.map

import kotlin.test.Test
import kotlin.test.assertTrue

class BoundingBoxTest {

    @Test
    fun `spanMeters of identical corners is zero`() {
        val bbox = BoundingBox(43.4660, 39.9242, 43.4660, 39.9242)
        assertTrue(bbox.spanMeters() < 0.001, "ожидали ~0, получили ${bbox.spanMeters()}")
    }

    @Test
    fun `spanMeters of 0_001 degree latitude is about 111 meters`() {
        val bbox = BoundingBox(43.0, 39.0, 43.001, 39.0)
        val m = bbox.spanMeters()
        assertTrue(m in 109.0..114.0, "ожидали ~111 м, получили $m")
    }

    @Test
    fun `spanMeters of wide bbox is hundreds of meters or more`() {
        val bbox = BoundingBox(43.40, 39.90, 43.60, 40.10)
        assertTrue(bbox.spanMeters() > 25.0, "широкий bbox должен быть > порога")
    }
}

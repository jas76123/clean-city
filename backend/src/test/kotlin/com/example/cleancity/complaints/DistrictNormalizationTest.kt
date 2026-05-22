package com.example.cleancity.complaints

import com.example.cleancity.shared.models.District
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DistrictNormalizationTest {

    @Test
    fun `fromGeocoderText распознаёт все 4 района по подстроке`() {
        assertEquals(District.CENTRAL, District.fromGeocoderText("Центральный район"))
        assertEquals(District.ADLER, District.fromGeocoderText("Адлерский внутригородской район"))
        assertEquals(District.KHOSTA, District.fromGeocoderText("Хостинский район г. Сочи"))
        assertEquals(District.LAZAREVSKOE, District.fromGeocoderText("Лазаревский район"))
    }

    @Test
    fun `fromGeocoderText регистронезависим`() {
        assertEquals(District.CENTRAL, District.fromGeocoderText("ЦЕНТРАЛЬНЫЙ"))
        assertEquals(District.ADLER, District.fromGeocoderText("адлер"))
    }

    @Test
    fun `fromGeocoderText на нераспознанном и пустом возвращает null`() {
        assertNull(District.fromGeocoderText("Краснодарский край"))
        assertNull(District.fromGeocoderText(""))
        assertNull(District.fromGeocoderText(null))
    }

    @Test
    fun `fromCoordinates определяет район по ближайшему центру`() {
        // Адлер (центр города)
        assertEquals(District.ADLER, District.fromCoordinates(43.43, 39.92))
        // Хоста / Мацеста
        assertEquals(District.KHOSTA, District.fromCoordinates(43.51, 39.86))
        // Центр Сочи
        assertEquals(District.CENTRAL, District.fromCoordinates(43.585, 39.723))
        // Лазаревское
        assertEquals(District.LAZAREVSKOE, District.fromCoordinates(43.91, 39.33))
    }

    @Test
    fun `fromCoordinates всегда возвращает один из 4 районов`() {
        // Любая точка в окрестностях Сочи классифицируется, null невозможен
        val districts = listOf(
            District.fromCoordinates(43.40, 39.95),
            District.fromCoordinates(43.60, 39.73),
            District.fromCoordinates(43.95, 39.32),
            District.fromCoordinates(43.55, 39.80),
        )
        assertEquals(4, districts.size)
        districts.forEach { d -> assertEquals(true, d in District.entries) }
    }
}

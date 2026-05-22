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
}

package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox

interface MapSearchProvider {
    /**
     * Возвращает до ~6 географических suggestions для query, ограниченных регионом.
     * Только те элементы, у которых есть координаты центра — без второго round-trip submit.
     * Если query пустой / короче 2 символов — возвращает emptyList.
     */
    suspend fun suggest(query: String, region: BoundingBox): List<MapSuggestion>

    /**
     * Reverse geocoding: координаты → читаемый адрес. Адрес берётся из первого
     * результата SearchManager.submit с SearchType.GEO. Result.failure при сетевой
     * ошибке / пустом ответе.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): Result<ReverseGeocodeResult>
}

data class MapSuggestion(
    val id: String,
    val title: String,
    val subtitle: String?,
    val latitude: Double,
    val longitude: Double,
)

data class ReverseGeocodeResult(
    val address: String,
    val district: String?,
)

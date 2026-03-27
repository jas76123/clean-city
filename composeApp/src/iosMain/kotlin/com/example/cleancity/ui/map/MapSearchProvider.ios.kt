package com.example.cleancity.ui.map

actual fun createMapSearchProvider(): MapSearchProvider = object : MapSearchProvider {
    override fun suggest(
        query: String,
        centerLat: Double,
        centerLon: Double,
        onResult: (List<SearchSuggestion>) -> Unit,
    ) {
        onResult(emptyList())
    }

    override fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit,
    ) {
        onResult(null)
    }
}

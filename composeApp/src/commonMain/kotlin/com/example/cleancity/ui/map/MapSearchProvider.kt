package com.example.cleancity.ui.map

interface MapSearchProvider {
    fun suggest(
        query: String,
        centerLat: Double,
        centerLon: Double,
        onResult: (List<SearchSuggestion>) -> Unit,
    )

    fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit,
    )
}

expect fun createMapSearchProvider(): MapSearchProvider

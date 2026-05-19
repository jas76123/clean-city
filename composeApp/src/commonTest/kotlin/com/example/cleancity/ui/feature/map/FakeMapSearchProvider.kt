package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox

class FakeMapSearchProvider : MapSearchProvider {
    data class Call(val query: String, val region: BoundingBox)
    data class ReverseCall(val latitude: Double, val longitude: Double)

    val calls = mutableListOf<Call>()
    val reverseCalls = mutableListOf<ReverseCall>()

    var nextResult: List<MapSuggestion> = emptyList()
    var nextReverseResult: Result<ReverseGeocodeResult> =
        Result.failure(IllegalStateException("FakeMapSearchProvider not configured"))

    override suspend fun suggest(query: String, region: BoundingBox): List<MapSuggestion> {
        calls += Call(query, region)
        return nextResult
    }

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double,
    ): Result<ReverseGeocodeResult> {
        reverseCalls += ReverseCall(latitude, longitude)
        return nextReverseResult
    }
}

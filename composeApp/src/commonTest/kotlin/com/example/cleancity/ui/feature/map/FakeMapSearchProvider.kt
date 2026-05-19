package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox

class FakeMapSearchProvider : MapSearchProvider {
    data class Call(val query: String, val region: BoundingBox)

    val calls = mutableListOf<Call>()
    var nextResult: List<MapSuggestion> = emptyList()

    override suspend fun suggest(query: String, region: BoundingBox): List<MapSuggestion> {
        calls += Call(query, region)
        return nextResult
    }
}

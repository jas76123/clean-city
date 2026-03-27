package com.example.cleancity.ui.map

import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.*
import com.yandex.runtime.Error

actual fun createMapSearchProvider(): MapSearchProvider = AndroidMapSearchProvider()

private class AndroidMapSearchProvider : MapSearchProvider {
    private val searchManager: SearchManager =
        SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    private val suggestSession: SuggestSession =
        searchManager.createSuggestSession()

    override fun suggest(
        query: String,
        centerLat: Double,
        centerLon: Double,
        onResult: (List<SearchSuggestion>) -> Unit,
    ) {
        val delta = 0.05
        val window = BoundingBox(
            Point(centerLat - delta, centerLon - delta),
            Point(centerLat + delta, centerLon + delta),
        )
        val options = SuggestOptions().apply {
            suggestTypes = SuggestType.GEO.value or SuggestType.BIZ.value
        }
        suggestSession.suggest(
            query,
            window,
            options,
            object : SuggestSession.SuggestListener {
                override fun onResponse(response: SuggestResponse) {
                    val items = response.items.take(5).mapNotNull { item ->
                        val center = item.center ?: return@mapNotNull null
                        SearchSuggestion(
                            title = item.title?.text?.toString().orEmpty(),
                            subtitle = item.subtitle?.text?.toString(),
                            latitude = center.latitude,
                            longitude = center.longitude,
                        )
                    }
                    onResult(items)
                }
                override fun onError(error: Error) {
                    onResult(emptyList())
                }
            }
        )
    }

    override fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit,
    ) {
        searchManager.submit(
            Point(latitude, longitude),
            16,
            SearchOptions(),
            object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    val meta = response.collection.children.firstOrNull()?.obj
                        ?.metadataContainer
                        ?.getItem(ToponymObjectMetadata::class.java)
                    onResult(meta?.address?.formattedAddress?.toString())
                }
                override fun onSearchError(error: Error) {
                    onResult(null)
                }
            }
        )
    }
}

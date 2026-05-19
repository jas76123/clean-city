package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox
import com.yandex.mapkit.geometry.BoundingBox as YBoundingBox
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.SuggestOptions
import com.yandex.mapkit.search.SuggestResponse
import com.yandex.mapkit.search.SuggestSession
import com.yandex.runtime.Error
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AndroidMapSearchProvider : MapSearchProvider {

    private val searchManager by lazy {
        SearchFactory.getInstance().createSearchManager(SearchManagerType.ONLINE)
    }

    private val suggestSession: SuggestSession by lazy { searchManager.createSuggestSession() }

    private val suggestOptions = SuggestOptions().apply {
        setSuggestTypes(SearchType.GEO.value)
    }

    override suspend fun suggest(query: String, region: BoundingBox): List<MapSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return emptyList()

        val window = YBoundingBox(
            Point(region.swLat, region.swLon),
            Point(region.neLat, region.neLon),
        )

        // MapKit native объекты (SearchManager, SuggestSession) живут на потоке, на котором
        // их создали, и принимают вызовы / отдают коллбеки на том же потоке. Делаем suggest
        // строго на Main — иначе native код роняет процесс через abort().
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SuggestSession.SuggestListener {
                    override fun onResponse(response: SuggestResponse) {
                        val items = response.items.mapNotNull { item ->
                            val center = item.center ?: return@mapNotNull null
                            MapSuggestion(
                                id = "${center.latitude},${center.longitude}|${item.searchText}",
                                title = item.title.text,
                                subtitle = item.subtitle?.text,
                                latitude = center.latitude,
                                longitude = center.longitude,
                            )
                        }.take(6)
                        if (cont.isActive) cont.resume(items)
                    }

                    override fun onError(error: Error) {
                        if (cont.isActive) cont.resume(emptyList())
                    }
                }

                suggestSession.suggest(trimmed, window, suggestOptions, listener)
                cont.invokeOnCancellation { suggestSession.reset() }
            }
        }
    }
}

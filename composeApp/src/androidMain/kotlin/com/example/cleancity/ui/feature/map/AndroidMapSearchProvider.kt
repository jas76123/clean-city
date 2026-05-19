package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox
import com.yandex.mapkit.geometry.BoundingBox as YBoundingBox
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.Address
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchType
import com.yandex.mapkit.search.Session
import com.yandex.mapkit.search.SuggestOptions
import com.yandex.mapkit.search.SuggestResponse
import com.yandex.mapkit.search.SuggestSession
import com.yandex.mapkit.search.ToponymObjectMetadata
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

    private val reverseOptions = SearchOptions().apply {
        searchTypes = SearchType.GEO.value
        resultPageSize = 1
    }

    private companion object {
        const val REVERSE_ZOOM = 17
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

    override suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double,
    ): Result<ReverseGeocodeResult> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val point = Point(latitude, longitude)
            // Локальная переменная — submit возвращает Session, чтобы можно было отменить.
            var session: Session? = null

            val listener = object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    val obj = response.collection.children.firstOrNull()?.obj
                    if (obj == null) {
                        if (cont.isActive) cont.resume(
                            Result.failure(IllegalStateException("Empty geocoder response")),
                        )
                        return
                    }
                    val toponym = obj.metadataContainer.getItem(ToponymObjectMetadata::class.java)
                    val components = toponym?.address?.components.orEmpty()

                    val street = components.firstOrNull { Address.Component.Kind.STREET in it.kinds }?.name
                    val house = components.firstOrNull { Address.Component.Kind.HOUSE in it.kinds }?.name
                    val district = components.firstOrNull { Address.Component.Kind.DISTRICT in it.kinds }?.name
                    val locality = components.firstOrNull { Address.Component.Kind.LOCALITY in it.kinds }?.name

                    // Собираем максимально читаемый адрес: "Сочи, Центральный р-н, ул. Транспортная, 14".
                    // Район отдельно тоже возвращаем — он нужен админке для фильтра/группировки.
                    val parts = buildList {
                        if (locality != null) add(locality)
                        if (district != null) add("$district р-н")
                        if (street != null) add(street)
                        if (house != null) add(house)
                    }
                    val address = if (parts.isNotEmpty()) parts.joinToString(", ")
                        else obj.name ?: toponym?.address?.formattedAddress.orEmpty()

                    if (cont.isActive) cont.resume(
                        Result.success(ReverseGeocodeResult(address = address, district = district)),
                    )
                }

                override fun onSearchError(error: Error) {
                    if (cont.isActive) cont.resume(
                        Result.failure(IllegalStateException("Geocoder error: $error")),
                    )
                }
            }

            session = searchManager.submit(point, REVERSE_ZOOM, reverseOptions, listener)
            cont.invokeOnCancellation { session?.cancel() }
        }
    }
}

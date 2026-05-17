package com.example.cleancity.ui.feature.map

import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class FakeComplaintsApi : ComplaintsApiContract {
    data class Call(
        val swLat: Double, val swLon: Double,
        val neLat: Double, val neLon: Double,
        val category: ProblemCategory?,
    )

    val calls = mutableListOf<Call>()

    var nextResponse: List<MapMarker> = emptyList()
    var nextError: Throwable? = null
    var nextDelayMs: Long = 0

    /** Если задан — функция возвращает Deferred, который тест может resolve в нужный момент. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getMapMarkers(
        swLat: Double, swLon: Double, neLat: Double, neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse {
        calls += Call(swLat, swLon, neLat, neLon, category)
        gate?.await()
        if (nextDelayMs > 0) delay(nextDelayMs)
        nextError?.let { throw it }
        return MapMarkersResponse(markers = nextResponse)
    }
}

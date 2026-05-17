package com.example.cleancity.data.network

import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface ComplaintsApiContract {
    suspend fun getMapMarkers(
        swLat: Double,
        swLon: Double,
        neLat: Double,
        neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse
}

class ComplaintsApi(private val client: HttpClient) : ComplaintsApiContract {

    override suspend fun getMapMarkers(
        swLat: Double,
        swLon: Double,
        neLat: Double,
        neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse = client.get("/complaints/map") {
        parameter("swLat", swLat)
        parameter("swLon", swLon)
        parameter("neLat", neLat)
        parameter("neLon", neLon)
        category?.let { parameter("category", it.name) }
    }.body()
}

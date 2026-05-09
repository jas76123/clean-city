package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Облегчённый маркер для карты — без описания и фото.
 * Возвращается в `GET /complaints/map?swLat=&swLon=&neLat=&neLon=`.
 */
@Serializable
data class MapMarker(
    val id: Long,
    val category: ProblemCategory,
    val status: ComplaintStatus,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class MapMarkersResponse(
    val markers: List<MapMarker>
)

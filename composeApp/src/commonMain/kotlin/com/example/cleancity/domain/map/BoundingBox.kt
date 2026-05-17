package com.example.cleancity.domain.map

data class BoundingBox(
    val swLat: Double,
    val swLon: Double,
    val neLat: Double,
    val neLon: Double,
) {
    fun expandedBy(deltaZoom: Float = 1.5f): BoundingBox {
        val factor = 1.0 / (1 shl deltaZoom.toInt().coerceAtLeast(1))
        val midLat = (swLat + neLat) / 2.0
        val midLon = (swLon + neLon) / 2.0
        val halfLat = (neLat - swLat) / 2.0 * factor
        val halfLon = (neLon - swLon) / 2.0 * factor
        return BoundingBox(
            swLat = midLat - halfLat,
            swLon = midLon - halfLon,
            neLat = midLat + halfLat,
            neLon = midLon + halfLon,
        )
    }
}

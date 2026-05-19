package com.example.cleancity.domain.map

import kotlin.math.pow
import kotlin.math.round

data class BoundingBox(
    val swLat: Double,
    val swLon: Double,
    val neLat: Double,
    val neLon: Double,
) {
    // decimals=4 даёт точность ~11 м — мелкие сдвиги камеры группируются
    // в один запрос маркеров вместо нескольких.
    fun rounded(decimals: Int): BoundingBox {
        val factor = 10.0.pow(decimals)
        fun r(v: Double) = round(v * factor) / factor
        return BoundingBox(r(swLat), r(swLon), r(neLat), r(neLon))
    }

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

    // Zoom, при котором bbox комфортно помещается в кадр Yandex-карты.
    // Эмпирическая таблица для широты Сочи (~43°): не слишком близко даже
    // для крошечных кластеров, чтобы метки не оказались под одной иконкой.
    fun suggestedZoom(): Float {
        val span = maxOf(neLat - swLat, neLon - swLon)
        return when {
            span > 0.5 -> 9f
            span > 0.1 -> 11f
            span > 0.05 -> 12f
            span > 0.02 -> 13f
            span > 0.01 -> 14f
            span > 0.005 -> 15f
            span > 0.002 -> 16f
            else -> 17f
        }
    }
}

package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.BoundingBox

const val SOCHI_CENTER_LAT = 43.5855
const val SOCHI_CENTER_LON = 39.7232

// Bounding box of Sochi для suggest/region — совпадает с SOCHI_MIN/MAX_LAT/LON
// в backend ComplaintService (валидация координат жалоб).
val SOCHI_BBOX = BoundingBox(
    swLat = 43.0,
    swLon = 39.0,
    neLat = 44.0,
    neLon = 41.0,
)

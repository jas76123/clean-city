package com.example.cleancity.domain.map

object SochiDefaults {
    val CENTER = CameraPosition(latitude = 43.5855, longitude = 39.7231, zoom = 12f)

    // Узкий bbox самого города — стартовый кадр карты MapScreen, чтобы маркеры
    // помещались без чрезмерного отдаления.
    val BBOX = BoundingBox(swLat = 43.40, swLon = 39.55, neLat = 43.75, neLon = 40.05)

    // Расширенный регион для address-suggest — совпадает с SOCHI_MIN/MAX_LAT/LON
    // в backend `ComplaintService` (валидация координат жалоб). Включает Адлер
    // и Хосту, поэтому подсказки в этих районах не будут отброшены сервером.
    val SUGGEST_REGION = BoundingBox(swLat = 43.0, swLon = 39.0, neLat = 44.0, neLon = 41.0)
}

package com.example.cleancity.domain.location

interface LocationProvider {
    /**
     * Возвращает последнее известное местоположение. Гарантия наличия permission — на стороне caller.
     * Если location недоступен (никогда не запрашивался / GPS off) — Result.failure.
     */
    suspend fun getLastKnownLocation(): Result<Location>
}

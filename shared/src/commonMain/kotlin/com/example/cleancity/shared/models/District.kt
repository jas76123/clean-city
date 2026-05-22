package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * 4 района Сочи. Источник правды: SPEC.md §4.7.
 *
 * В БД район хранится как varchar (см. `users.district`, `complaints.district`)
 * с русским названием — для обратной совместимости с уже созданными данными.
 * Этот enum используется в API-ответах справочника /districts и в DTO аналитики.
 */
@Serializable
enum class District {
    CENTRAL,
    ADLER,
    KHOSTA,
    LAZAREVSKOE;

    val localizedLabel: String
        get() = when (this) {
            CENTRAL -> "Центральный"
            ADLER -> "Адлерский"
            KHOSTA -> "Хостинский"
            LAZAREVSKOE -> "Лазаревский"
        }

    companion object {
        fun fromLabelOrNull(label: String?): District? {
            if (label.isNullOrBlank()) return null
            return entries.firstOrNull { it.localizedLabel.equals(label.trim(), ignoreCase = true) }
        }

        /**
         * Нормализует свободный текст геокодера к одному из 4 районов по подстроке.
         * Геокодер отдаёт строки вроде «Адлерский внутригородской район» — точное
         * сравнение по label их не ловит, поэтому матчим по корню слова.
         */
        fun fromGeocoderText(raw: String?): District? {
            if (raw.isNullOrBlank()) return null
            val s = raw.lowercase()
            return when {
                "центральн" in s -> CENTRAL
                "адлер" in s -> ADLER
                "хост" in s -> KHOSTA
                "лазаревск" in s -> LAZAREVSKOE
                else -> null
            }
        }

        /**
         * Определяет район по координатам жалобы — ближайший из 4 центров районов
         * (Евклидово расстояние). Всегда возвращает один из 4 районов: используется
         * как запасной вариант, когда [fromGeocoderText] не распознал район в тексте
         * геокодера. Центры — приблизительные, граница между районами размыта.
         */
        fun fromCoordinates(latitude: Double, longitude: Double): District {
            // (район to (центр.широта, центр.долгота))
            val centers = listOf(
                ADLER to (43.46 to 39.95),
                KHOSTA to (43.52 to 39.85),
                CENTRAL to (43.60 to 39.73),
                LAZAREVSKOE to (43.85 to 39.42),
            )
            return centers.minBy { (_, c) ->
                val (cLat, cLon) = c
                val dLat = latitude - cLat
                val dLon = longitude - cLon
                dLat * dLat + dLon * dLon
            }.first
        }
    }
}

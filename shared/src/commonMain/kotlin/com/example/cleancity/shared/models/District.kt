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
    }
}

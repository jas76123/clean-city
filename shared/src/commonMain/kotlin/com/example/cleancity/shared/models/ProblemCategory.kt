package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * 18 категорий жалоб. Источник правды: SPEC.md § 3.1.
 */
@Serializable
enum class ProblemCategory {
    GARBAGE,         // Мусор и санитарное состояние
    ROADS,           // Дороги и ямы
    SIDEWALKS,       // Тротуары и пешеходные зоны
    LIGHTING,        // Уличное освещение
    GREENERY,        // Озеленение и деревья
    LANDSCAPING,     // Благоустройство территорий
    PLAYGROUNDS,     // Детские и спортивные площадки
    PARKS,           // Общественные пространства и парки
    BEACHES,         // Пляжи и зоны отдыха
    SAFETY,          // Безопасность и правонарушения
    VANDALISM,       // Вандализм и повреждение имущества
    WATER_SUPPLY,    // Водоснабжение
    SEWAGE,          // Канализация и ливневые стоки
    ELECTRICITY,     // Электроснабжение
    ECOLOGY,         // Экология и загрязнение окружающей среды
    ACCESSIBILITY,   // Доступная среда для маломобильных граждан
    TRADE,           // Торговля и незаконные объекты
    OTHER;           // Прочее

    val localizedLabel: String
        get() = when (this) {
            GARBAGE -> "Мусор"
            ROADS -> "Дороги"
            SIDEWALKS -> "Тротуары"
            LIGHTING -> "Освещение"
            GREENERY -> "Озеленение"
            LANDSCAPING -> "Благоустройство"
            PLAYGROUNDS -> "Площадки"
            PARKS -> "Парки"
            BEACHES -> "Пляжи"
            SAFETY -> "Безопасность"
            VANDALISM -> "Вандализм"
            WATER_SUPPLY -> "Водоснабжение"
            SEWAGE -> "Канализация"
            ELECTRICITY -> "Электроснабжение"
            ECOLOGY -> "Экология"
            ACCESSIBILITY -> "Доступная среда"
            TRADE -> "Торговля"
            OTHER -> "Прочее"
        }
}

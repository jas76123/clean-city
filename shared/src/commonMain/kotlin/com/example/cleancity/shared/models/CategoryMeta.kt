package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

/**
 * Метаданные категории для справочника /categories.
 * SLA-нормативы — SPEC.md §4.8. Единая точка правды для backend (расчёт breach)
 * и клиентов (отображение в карточках).
 */
@Serializable
data class CategoryMeta(
    val code: ProblemCategory,
    val label: String,
    val slaHours: Int
)

@Serializable
data class DistrictMeta(
    val code: District,
    val label: String
)

object CategorySla {
    private val HOURS_24 = setOf(
        ProblemCategory.GARBAGE,
        ProblemCategory.ECOLOGY,
        ProblemCategory.SAFETY
    )
    private val HOURS_48 = setOf(
        ProblemCategory.LIGHTING,
        ProblemCategory.SEWAGE,
        ProblemCategory.WATER_SUPPLY,
        ProblemCategory.ELECTRICITY
    )
    private val HOURS_72 = setOf(
        ProblemCategory.ROADS,
        ProblemCategory.SIDEWALKS,
        ProblemCategory.GREENERY,
        ProblemCategory.LANDSCAPING,
        ProblemCategory.PLAYGROUNDS,
        ProblemCategory.PARKS,
        ProblemCategory.BEACHES,
        ProblemCategory.ACCESSIBILITY
    )

    fun hoursFor(c: ProblemCategory): Int = when (c) {
        in HOURS_24 -> 24
        in HOURS_48 -> 48
        in HOURS_72 -> 72
        else -> 120
    }
}

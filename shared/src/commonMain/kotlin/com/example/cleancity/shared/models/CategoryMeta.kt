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
        ProblemCategory.SAFETY,
        ProblemCategory.LIGHTING,
        ProblemCategory.SEWAGE,
        ProblemCategory.WATER_SUPPLY,
        ProblemCategory.ELECTRICITY,
    )

    fun hoursFor(c: ProblemCategory): Int = when (c) {
        in HOURS_24 -> 24
        else -> 120
    }
}

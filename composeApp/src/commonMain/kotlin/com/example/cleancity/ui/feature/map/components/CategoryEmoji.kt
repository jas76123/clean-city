package com.example.cleancity.ui.feature.map.components

import com.example.cleancity.shared.models.ProblemCategory

fun ProblemCategory.emoji(): String = when (this) {
    ProblemCategory.GARBAGE -> "🗑"
    ProblemCategory.ROADS -> "🛣"
    ProblemCategory.SIDEWALKS -> "🚶"
    ProblemCategory.LIGHTING -> "💡"
    ProblemCategory.GREENERY -> "🌳"
    ProblemCategory.LANDSCAPING -> "🏗"
    ProblemCategory.PLAYGROUNDS -> "🎠"
    ProblemCategory.PARKS -> "🌲"
    ProblemCategory.BEACHES -> "🏖"
    ProblemCategory.SAFETY -> "🚨"
    ProblemCategory.VANDALISM -> "💥"
    ProblemCategory.WATER_SUPPLY -> "🚰"
    ProblemCategory.SEWAGE -> "🚾"
    ProblemCategory.ELECTRICITY -> "⚡"
    ProblemCategory.ECOLOGY -> "🌱"
    ProblemCategory.ACCESSIBILITY -> "♿"
    ProblemCategory.TRADE -> "🏪"
    ProblemCategory.OTHER -> "…"
}

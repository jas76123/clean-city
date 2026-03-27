package com.example.cleancity.ui.map

import com.example.cleancity.model.ProblemType
import com.example.cleancity.model.ProblemStatus

enum class MapMarkerType { PROBLEM, EVENT, RESOLVED }

enum class MapFilter(val displayName: String) {
    ALL("Все"),
    PROBLEMS("Проблемы"),
    EVENTS("Субботники"),
    RESOLVED("Решённые")
}

data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: MapMarkerType,
    val title: String,
    val problemType: ProblemType? = null,
    val status: ProblemStatus? = null,
)

data class CameraPosition(
    val latitude: Double = 43.585,
    val longitude: Double = 39.723,
    val zoom: Float = 14f,
)

data class SearchSuggestion(
    val title: String,
    val subtitle: String? = null,
    val latitude: Double,
    val longitude: Double,
)

package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class ProblemType {
    DUMP, ROAD, LIGHTING, GREENERY
}

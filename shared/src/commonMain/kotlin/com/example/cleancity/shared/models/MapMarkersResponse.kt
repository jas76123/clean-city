package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class MapMarkersResponse(
    val complaints: List<ComplaintResponse>
)

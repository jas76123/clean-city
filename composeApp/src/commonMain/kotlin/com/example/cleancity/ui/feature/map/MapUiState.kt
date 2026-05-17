package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.domain.map.SochiDefaults
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.ProblemCategory

data class MapUiState(
    val cameraPosition: CameraPosition = SochiDefaults.CENTER,
    val markers: List<MapMarker> = emptyList(),
    val selectedCategory: ProblemCategory? = null,
    val selectedMarkerId: Long? = null,
    val isCategorySheetOpen: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastKnownLocation: Location? = null,
)

package com.example.cleancity.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun YandexMapView(
    modifier: Modifier = Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
)

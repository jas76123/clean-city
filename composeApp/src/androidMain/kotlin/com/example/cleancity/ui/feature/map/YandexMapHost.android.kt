package com.example.cleancity.ui.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.shared.models.MapMarker
import com.yandex.mapkit.Animation
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.Map as YMap
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.map.CameraPosition as YCameraPosition

@Composable
actual fun YandexMapHost(
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onCameraMoved: (BoundingBox) -> Unit,
    onMarkerClick: (markerId: Long) -> Unit,
    onClusterTap: (BoundingBox) -> Unit,
    modifier: Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { view ->
                view.mapWindow.map.move(
                    YCameraPosition(
                        Point(cameraPosition.latitude, cameraPosition.longitude),
                        cameraPosition.zoom, 0f, 0f,
                    ),
                )
                mapViewState.value = view
            }
        },
    )

    DisposableEffect(lifecycleOwner, mapViewState.value) {
        val view = mapViewState.value ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            view.onStop()
        }
    }

    LaunchedEffect(cameraPosition) {
        val view = mapViewState.value ?: return@LaunchedEffect
        view.mapWindow.map.move(
            YCameraPosition(
                Point(cameraPosition.latitude, cameraPosition.longitude),
                cameraPosition.zoom, 0f, 0f,
            ),
            Animation(Animation.Type.SMOOTH, 0.4f),
            null,
        )
    }

    DisposableEffect(mapViewState.value, onCameraMoved) {
        val view = mapViewState.value ?: return@DisposableEffect onDispose { }
        val listener = CameraListener { map: YMap, _, _, finished ->
            if (finished) {
                val region = map.visibleRegion
                val bbox = BoundingBox(
                    swLat = minOf(region.bottomLeft.latitude, region.topRight.latitude),
                    swLon = minOf(region.bottomLeft.longitude, region.topRight.longitude),
                    neLat = maxOf(region.bottomLeft.latitude, region.topRight.latitude),
                    neLon = maxOf(region.bottomLeft.longitude, region.topRight.longitude),
                )
                onCameraMoved(bbox)
            }
        }
        view.mapWindow.map.addCameraListener(listener)
        onDispose { view.mapWindow.map.removeCameraListener(listener) }
    }
}

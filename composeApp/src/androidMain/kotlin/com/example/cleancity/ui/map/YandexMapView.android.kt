package com.example.cleancity.ui.map

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition as YandexCameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

@SuppressLint("MissingPermission")
@Composable
actual fun YandexMapView(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
) {
    val tapListeners = remember { mutableMapOf<String, MapObjectTapListener>() }
    val currentCameraPosition by rememberUpdatedState(cameraPosition)
    val currentMarkers by rememberUpdatedState(markers)
    val currentOnMarkerClick by rememberUpdatedState(onMarkerClick)
    val currentOnMapTap by rememberUpdatedState(onMapTap)

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }

    // Rebuild markers when they change
    LaunchedEffect(currentMarkers) {
        val view = mapViewRef ?: return@LaunchedEffect
        updateMarkers(view, currentMarkers, tapListeners, currentOnMarkerClick)
    }

    // Move camera when position changes
    LaunchedEffect(currentCameraPosition) {
        val view = mapViewRef ?: return@LaunchedEffect
        view.map.move(
            YandexCameraPosition(
                Point(currentCameraPosition.latitude, currentCameraPosition.longitude),
                currentCameraPosition.zoom,
                0.0f,
                0.0f
            )
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                // Use .map directly (same as working project)
                map.move(
                    YandexCameraPosition(
                        Point(cameraPosition.latitude, cameraPosition.longitude),
                        cameraPosition.zoom,
                        0.0f,
                        0.0f
                    )
                )

                map.addInputListener(object : InputListener {
                    override fun onMapTap(map: Map, point: Point) {
                        currentOnMapTap(point.latitude, point.longitude)
                    }
                    override fun onMapLongTap(map: Map, point: Point) {}
                })

                // Start immediately
                onStart()
                mapViewRef = this

                // Add initial markers
                updateMarkers(this, currentMarkers, tapListeners, currentOnMarkerClick)
            }
        },
        onRelease = { view ->
            view.onStop()
        }
    )
}

private fun updateMarkers(
    view: MapView,
    markers: List<MapMarker>,
    tapListeners: MutableMap<String, MapObjectTapListener>,
    onMarkerClick: (MapMarker) -> Unit,
) {
    val map = view.map
    map.mapObjects.clear()
    tapListeners.clear()

    markers.forEach { marker ->
        val point = Point(marker.latitude, marker.longitude)
        val color = when (marker.type) {
            MapMarkerType.RESOLVED -> 0xFF4DAB6E.toInt()
            MapMarkerType.EVENT -> 0xFF8B5CF6.toInt()
            MapMarkerType.PROBLEM -> when (marker.status) {
                com.example.cleancity.model.ProblemStatus.IN_WORK -> 0xFFF59E0B.toInt()
                else -> 0xFFE8453C.toInt()
            }
        }
        val bitmap = createPinBitmap(color)
        val imageProvider = ImageProvider.fromBitmap(bitmap)
        val placemark = map.mapObjects.addPlacemark().apply {
            geometry = point
            setIcon(imageProvider)
        }
        val listener = MapObjectTapListener { _, _ ->
            onMarkerClick(marker)
            true
        }
        tapListeners[marker.id] = listener
        placemark.addTapListener(listener)
    }
}

private fun createPinBitmap(color: Int): Bitmap {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, borderPaint)
    return bitmap
}

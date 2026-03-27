package com.example.cleancity.ui.map

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val tapListeners = remember { mutableMapOf<String, MapObjectTapListener>() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                mapWindow.map.move(
                    YandexCameraPosition(
                        Point(cameraPosition.latitude, cameraPosition.longitude),
                        cameraPosition.zoom,
                        0.0f,
                        0.0f
                    )
                )
                mapWindow.map.addInputListener(object : InputListener {
                    override fun onMapTap(map: Map, point: Point) {
                        onMapTap(point.latitude, point.longitude)
                    }
                    override fun onMapLongTap(map: Map, point: Point) {}
                })
                mapView = this
            }
        },
        update = { view ->
            val map = view.mapWindow.map
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
    )

    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView ?: return@DisposableEffect onDispose {}
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
        }
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

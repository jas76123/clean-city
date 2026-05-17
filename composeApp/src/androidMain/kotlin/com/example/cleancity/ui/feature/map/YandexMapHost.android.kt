package com.example.cleancity.ui.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
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
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarker
import android.graphics.PointF
import com.yandex.mapkit.Animation
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.ClusterListener
import com.yandex.mapkit.map.ClusterTapListener
import com.yandex.mapkit.map.ClusterizedPlacemarkCollection
import com.yandex.mapkit.map.Map as YMap
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.map.CameraPosition as YCameraPosition
import com.yandex.runtime.image.ImageProvider

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

    DisposableEffect(mapViewState.value, markers, onMarkerClick, onClusterTap) {
        val view = mapViewState.value ?: return@DisposableEffect onDispose { }
        val tapListeners = mutableListOf<Pair<PlacemarkMapObject, MapObjectTapListener>>()

        val clusterListener = ClusterListener { cluster ->
            cluster.appearance.setIcon(
                ImageProvider.fromBitmap(createClusterBitmap(cluster.size)),
            )
            cluster.addClusterTapListener(
                ClusterTapListener { c ->
                    val placemarks = c.placemarks
                    if (placemarks.isEmpty()) return@ClusterTapListener true
                    var minLat = Double.MAX_VALUE
                    var maxLat = -Double.MAX_VALUE
                    var minLon = Double.MAX_VALUE
                    var maxLon = -Double.MAX_VALUE
                    placemarks.forEach { p ->
                        val pt = p.geometry
                        if (pt.latitude < minLat) minLat = pt.latitude
                        if (pt.latitude > maxLat) maxLat = pt.latitude
                        if (pt.longitude < minLon) minLon = pt.longitude
                        if (pt.longitude > maxLon) maxLon = pt.longitude
                    }
                    onClusterTap(BoundingBox(minLat, minLon, maxLat, maxLon))
                    true
                },
            )
        }

        val collection: ClusterizedPlacemarkCollection =
            view.mapWindow.map.mapObjects.addClusterizedPlacemarkCollection(clusterListener)

        val pinIconStyle = IconStyle().setAnchor(PointF(0.5f, 1f))

        markers.forEach { marker ->
            val placemark = collection.addPlacemark().apply {
                geometry = Point(marker.latitude, marker.longitude)
                setIcon(
                    ImageProvider.fromBitmap(createPinBitmap(statusColor(marker.status))),
                    pinIconStyle,
                )
            }
            val listener = MapObjectTapListener { _, _ ->
                onMarkerClick(marker.id)
                true
            }
            placemark.addTapListener(listener)
            tapListeners.add(placemark to listener)
        }
        collection.clusterPlacemarks(60.0, 15)

        onDispose {
            tapListeners.forEach { (p, l) -> p.removeTapListener(l) }
            view.mapWindow.map.mapObjects.remove(collection)
        }
    }
}

private fun statusColor(status: ComplaintStatus): Int = when (status) {
    ComplaintStatus.NEW -> 0xFFF59E0B.toInt()
    ComplaintStatus.IN_PROGRESS -> 0xFF3B82F6.toInt()
    ComplaintStatus.RESOLVED -> 0xFF10B981.toInt()
    ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> 0xFF9CA3AF.toInt()
}

private fun createPinBitmap(color: Int, widthPx: Int = 56, heightPx: Int = 72): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Drop-pin силуэт: круг сверху + треугольный «хвост» вниз
    val cx = widthPx / 2f
    val headRadius = widthPx / 2f - 4f
    val headCy = headRadius + 4f
    val tipY = heightPx - 4f
    val tailHalfWidth = headRadius * 0.55f

    val path = Path().apply {
        moveTo(cx - tailHalfWidth, headCy + headRadius * 0.55f)
        lineTo(cx, tipY)
        lineTo(cx + tailHalfWidth, headCy + headRadius * 0.55f)
        close()
    }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val centerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    // Сначала «хвост» как продолжение фона, потом голова поверх — даёт классическую каплю с белым контуром
    canvas.drawPath(path, fill)
    canvas.drawCircle(cx, headCy, headRadius, fill)
    canvas.drawPath(path, stroke)
    canvas.drawCircle(cx, headCy, headRadius, stroke)
    canvas.drawCircle(cx, headCy, headRadius * 0.32f, centerDot)
    return bitmap
}

private fun createClusterBitmap(count: Int, sizePx: Int = 80): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF374151.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111827.toInt()
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.4f
        isFakeBoldText = true
    }
    val r = sizePx / 2f - 4f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, fill)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, stroke)
    canvas.drawText(count.toString(), sizePx / 2f, sizePx / 2f + text.textSize / 3f, text)
    return bitmap
}

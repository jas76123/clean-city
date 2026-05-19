package com.example.cleancity.ui.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cleancity.R
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarker
import android.graphics.PointF
import androidx.compose.ui.graphics.toArgb
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.AccentDark
import com.example.cleancity.ui.theme.Amber
import com.example.cleancity.ui.theme.Blue
import com.example.cleancity.ui.theme.Gray400
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green900
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
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
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val clusterTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.unbounded_semibold) ?: Typeface.DEFAULT_BOLD
    }

    // MapKit native code держит weak reference на UserLocationObjectListener — без strong
    // reference в Kotlin/Java объект уходит в GC, и callback'и (onObjectAdded и др.) теряются
    // (в logcat это видно как «yandex.maps.runtime: Java object is already finalized»).
    // remember удерживает decorator на всё время жизни composable.
    val userLocationDecorator = remember { UserLocationDecorator(Accent.toArgb()) }

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
                // Стандартный «синий пульсирующий маркер» юзера. MapKit подписывается на
                // FusedLocationProvider сам и тихо ждёт ACCESS_FINE_LOCATION. createUserLocationLayer
                // допустим ровно один раз на mapWindow, поэтому делаем его здесь, вместе с MapView.
                // setObjectListener должен быть зарегистрирован ДО isVisible — MapKit вызывает
                // onObjectAdded один раз при первом включении видимости layer'а.
                MapKitFactory.getInstance()
                    .createUserLocationLayer(view.mapWindow)
                    .apply {
                        setObjectListener(userLocationDecorator)
                        isVisible = true
                    }
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
                ImageProvider.fromBitmap(
                    createClusterBitmap(cluster.size, density, clusterTypeface),
                ),
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
                    ImageProvider.fromBitmap(createPinBitmap(statusColor(marker.status), density)),
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
    ComplaintStatus.NEW -> Amber.toArgb()
    ComplaintStatus.IN_PROGRESS -> Blue.toArgb()
    ComplaintStatus.RESOLVED -> AccentDark.toArgb()
    ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> Gray400.toArgb()
}

private fun createPinBitmap(
    color: Int,
    density: Float,
    widthDp: Float = 36f,
    heightDp: Float = 46f,
): Bitmap {
    val widthPx = (widthDp * density).toInt()
    val heightPx = (heightDp * density).toInt()
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val pad = 3f * density
    val cx = widthPx / 2f
    val headRadius = widthPx / 2f - pad
    val headCy = headRadius + pad
    val tipY = heightPx - pad
    val tailHalfWidth = headRadius * 0.55f

    // Силуэт «капля» — единый Path (UNION круга и треугольника). Сплошной контур без
    // внутренней дуги на стыке: stroke идёт ровно по внешней границе.
    val circlePath = Path().apply { addCircle(cx, headCy, headRadius, Path.Direction.CW) }
    val tailPath = Path().apply {
        moveTo(cx - tailHalfWidth, headCy + headRadius * 0.55f)
        lineTo(cx, tipY)
        lineTo(cx + tailHalfWidth, headCy + headRadius * 0.55f)
        close()
    }
    val pinPath = Path().apply { op(circlePath, tailPath, Path.Op.UNION) }

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    val centerDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }

    canvas.drawPath(pinPath, fill)
    canvas.drawPath(pinPath, stroke)
    canvas.drawCircle(cx, headCy, headRadius * 0.32f, centerDot)
    return bitmap
}

private fun createClusterBitmap(
    count: Int,
    density: Float,
    typeface: Typeface,
): Bitmap {
    val sizePx = (36f * density).toInt()         // 36dp
    val strokePx = 2f * density                  // 2dp
    val padPx = strokePx / 2f + 1f
    val radius = sizePx / 2f - padPx

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Green700.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Green900.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.34f
        this.typeface = typeface
    }

    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, fill)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, stroke)
    canvas.drawText(
        count.toString(),
        sizePx / 2f,
        sizePx / 2f - (text.descent() + text.ascent()) / 2f,
        text,
    )
    return bitmap
}

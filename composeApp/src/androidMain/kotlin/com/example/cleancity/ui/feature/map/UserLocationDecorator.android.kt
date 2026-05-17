package com.example.cleancity.ui.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider

/**
 * Брендирует слой пользователя Yandex MapKit:
 *   - подменяет иконку user-location на брендовую Accent-точку с белой обводкой,
 *   - перекрашивает accuracy-circle в Accent.
 *
 * MapKit показывает либо `arrow` (когда у локации есть валидный heading), либо `pin`
 * (когда без heading). На эмуляторе FusedLocation отдаёт bearing=0 с малой accuracy,
 * поэтому всегда активен arrow-режим, и подмена только pin'а не дала бы видимой точки.
 * Поэтому одну и ту же брендовую иконку ставим на оба placemark'а.
 *
 * Один экземпляр на MapView. Регистрируется через UserLocationLayer.setObjectListener и
 * ОБЯЗАТЕЛЬНО должен удерживаться сильной ссылкой (например, через `remember` в Composable):
 * MapKit держит на listener weak reference, без strong ref GC уничтожит decorator и callback'и
 * не придут (в logcat — «yandex.maps.runtime: Java object is already finalized»).
 */
class UserLocationDecorator(
    private val accentArgb: Int,
) : UserLocationObjectListener {

    private val pinIcon: ImageProvider by lazy {
        ImageProvider.fromBitmap(createUserPin(accentArgb))
    }

    override fun onObjectAdded(userLocationView: UserLocationView) {
        userLocationView.arrow.setIcon(pinIcon)
        userLocationView.pin.setIcon(pinIcon)
        userLocationView.accuracyCircle.fillColor = withAlpha(accentArgb, 0.10f)
        userLocationView.accuracyCircle.strokeColor = withAlpha(accentArgb, 0.40f)
        userLocationView.accuracyCircle.strokeWidth = 1f
    }

    override fun onObjectRemoved(userLocationView: UserLocationView) { /* no-op */ }

    override fun onObjectUpdated(
        userLocationView: UserLocationView,
        event: com.yandex.mapkit.layers.ObjectEvent,
    ) {
        // MapKit на каждом fix'е может вернуть default-иконки — ставим наши повторно.
        // Accuracy circle перекрашивать не нужно: setFillColor персистентен.
        userLocationView.arrow.setIcon(pinIcon)
        userLocationView.pin.setIcon(pinIcon)
    }

    private companion object {
        fun withAlpha(argb: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt() and 0xFF
            return (argb and 0x00FFFFFF) or (a shl 24)
        }

        fun createUserPin(accentArgb: Int, sizePx: Int = 48): Bitmap {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = sizePx / 2f
            val radius = sizePx / 2f - 4f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentArgb
                style = Paint.Style.FILL
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawCircle(cx, cx, radius, fill)
            canvas.drawCircle(cx, cx, radius, stroke)
            return bitmap
        }
    }
}

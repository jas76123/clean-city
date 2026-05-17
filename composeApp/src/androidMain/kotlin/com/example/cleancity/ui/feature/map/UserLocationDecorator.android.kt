package com.example.cleancity.ui.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider

/**
 * Замещает дефолтную чёрно-оранжевую стрелку Yandex MapKit на брендовую точку:
 *   - сплошная Accent-точка 16dp с белой обводкой 3dp,
 *   - мягкий пульсирующий ring (накачка от 1.0 до 1.6 по scale, fade-out по alpha),
 *   - accuracy-circle перекрашен в Accent.
 *
 * Один экземпляр на MapView. Регистрируется через UserLocationLayer.setObjectListener.
 * Жизненный цикл пульсации: запускается в onObjectAdded, останавливается в onObjectRemoved.
 */
class UserLocationDecorator(
    private val density: Float,
    private val accentArgb: Int,
) : UserLocationObjectListener {

    private val handler = Handler(Looper.getMainLooper())
    private var view: UserLocationView? = null
    private var startedAtMs = 0L

    private val dotBitmap: Bitmap = createDot(accentArgb, density)
    private val transparent1x1: Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun onObjectAdded(userLocationView: UserLocationView) {
        view = userLocationView
        userLocationView.arrow.setIcon(ImageProvider.fromBitmap(transparent1x1))
        userLocationView.accuracyCircle.fillColor = withAlpha(accentArgb, 0.10f)
        userLocationView.accuracyCircle.strokeColor = withAlpha(accentArgb, 0.40f)
        userLocationView.accuracyCircle.strokeWidth = 1f

        val composite = userLocationView.pin.useCompositeIcon()
        composite.setIcon("dot", ImageProvider.fromBitmap(dotBitmap), IconStyle())
        composite.setIcon(
            "ring",
            ImageProvider.fromBitmap(createRing(accentArgb, density, alpha = 0.6f)),
            IconStyle(),
        )

        startedAtMs = System.currentTimeMillis()
        handler.post(pulseTick)
    }

    override fun onObjectRemoved(userLocationView: UserLocationView) {
        handler.removeCallbacks(pulseTick)
        view = null
    }

    override fun onObjectUpdated(
        userLocationView: UserLocationView,
        event: com.yandex.mapkit.layers.ObjectEvent,
    ) {
        // нечего обновлять: пульсация привязана к таймеру, локация — к MapKit
    }

    private val pulseTick = object : Runnable {
        override fun run() {
            val v = view ?: return
            val t = ((System.currentTimeMillis() - startedAtMs) % PULSE_PERIOD_MS).toFloat() /
                PULSE_PERIOD_MS
            val alpha = (1f - t) * 0.6f
            val newRing = createRing(accentArgb, density * (1f + 0.6f * t), alpha)
            v.pin.useCompositeIcon().setIcon(
                "ring",
                ImageProvider.fromBitmap(newRing),
                IconStyle(),
            )
            handler.postDelayed(this, PULSE_FRAME_MS)
        }
    }

    companion object {
        private const val PULSE_PERIOD_MS = 1200L
        private const val PULSE_FRAME_MS = 80L

        private fun withAlpha(argb: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt() and 0xFF
            return (argb and 0x00FFFFFF) or (a shl 24)
        }

        private fun createDot(accentArgb: Int, density: Float): Bitmap {
            val sizePx = (16f * density).toInt().coerceAtLeast(24)
            val strokePx = 3f * density
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = sizePx / 2f
            val r = sizePx / 2f - strokePx / 2f - 1f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentArgb
                style = Paint.Style.FILL
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = strokePx
            }
            canvas.drawCircle(cx, cx, r, fill)
            canvas.drawCircle(cx, cx, r, stroke)
            return bitmap
        }

        /**
         * @param scaledDensity density × pulse-scale; ring растёт со временем
         * @param alpha       0..1, наружный fade
         */
        private fun createRing(accentArgb: Int, scaledDensity: Float, alpha: Float): Bitmap {
            val sizePx = (32f * scaledDensity).toInt().coerceAtLeast(32)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = sizePx / 2f
            val r = sizePx / 2f - 1f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accentArgb, alpha * 0.4f)
                style = Paint.Style.FILL
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accentArgb, alpha)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawCircle(cx, cx, r, fill)
            canvas.drawCircle(cx, cx, r, stroke)
            return bitmap
        }
    }
}

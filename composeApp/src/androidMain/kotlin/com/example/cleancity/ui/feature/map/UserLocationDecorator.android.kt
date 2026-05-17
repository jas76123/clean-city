package com.example.cleancity.ui.feature.map

import android.graphics.Bitmap
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider

/**
 * Брендирует слой пользователя Yandex MapKit:
 *   - скрывает чужеродную чёрно-оранжевую стрелку heading'а,
 *   - перекрашивает accuracy-circle в Accent.
 *
 * Замена самого pin'а (синяя default-точка → зелёная Accent-точка с пульсирующим ring'ом)
 * не реализована в этой итерации: setIcon на `view.pin` в MapKit 4.25 не подменяет
 * системную иконку, useCompositeIcon добавляет наш layer поверх default'а вместо замены.
 * Чтобы получить полностью кастомный pin, нужно завести отдельный PlacemarkMapObject на
 * map.mapObjects и двигать его в onObjectUpdated. Запланировано отдельным шагом.
 *
 * Один экземпляр на MapView. Регистрируется через UserLocationLayer.setObjectListener и
 * ОБЯЗАТЕЛЬНО должен удерживаться сильной ссылкой (например, через `remember` в Composable):
 * MapKit держит на listener weak reference, без strong ref GC уничтожит decorator и callback'и
 * не придут (в logcat — «yandex.maps.runtime: Java object is already finalized»).
 */
class UserLocationDecorator(
    private val accentArgb: Int,
) : UserLocationObjectListener {

    private val transparent1x1: Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun onObjectAdded(userLocationView: UserLocationView) {
        userLocationView.arrow.setIcon(ImageProvider.fromBitmap(transparent1x1))
        userLocationView.accuracyCircle.fillColor = withAlpha(accentArgb, 0.10f)
        userLocationView.accuracyCircle.strokeColor = withAlpha(accentArgb, 0.40f)
        userLocationView.accuracyCircle.strokeWidth = 1f
    }

    override fun onObjectRemoved(userLocationView: UserLocationView) { /* no-op */ }

    override fun onObjectUpdated(
        userLocationView: UserLocationView,
        event: com.yandex.mapkit.layers.ObjectEvent,
    ) {
        // MapKit может перерисовывать arrow на каждом fix'е — на всякий случай ставим прозрачную
        // иконку повторно. Accuracy circle перекрашивать не нужно: setFillColor персистентен.
        userLocationView.arrow.setIcon(ImageProvider.fromBitmap(transparent1x1))
    }

    private companion object {
        fun withAlpha(argb: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt() and 0xFF
            return (argb and 0x00FFFFFF) or (a shl 24)
        }
    }
}

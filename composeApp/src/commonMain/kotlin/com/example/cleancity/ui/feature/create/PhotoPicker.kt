package com.example.cleancity.ui.feature.create

import androidx.compose.runtime.Composable
import com.example.cleancity.domain.photo.PhotoBytes

/**
 * Source выбора фото: камера или галерея. UI бэкап-листа на стороне commonMain.
 */
enum class PhotoSource { CAMERA, GALLERY }

/**
 * Платформо-зависимый launcher для фото-пикера. Common-сторона дёргает [launch]
 * с указанием источника и оставшегося лимита, реализация в androidMain открывает
 * соответствующий ActivityResultContract и через callback возвращает PhotoBytes.
 */
interface PhotoPickerLauncher {
    fun launch(source: PhotoSource, remainingSlots: Int)
}

@Composable
expect fun rememberPhotoPickerLauncher(
    onPhotosPicked: (List<PhotoBytes>) -> Unit,
    onCameraPermissionDenied: () -> Unit = {},
    onPhotoTooLarge: (sizeMb: Int) -> Unit = {},
): PhotoPickerLauncher

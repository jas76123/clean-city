package com.example.cleancity.ui.feature.create

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.cleancity.domain.photo.PhotoBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Backend ImageProcessor: 10 MB hard cap. */
private const val MAX_PHOTO_BYTES = 10L * 1024 * 1024

@Composable
actual fun rememberPhotoPickerLauncher(
    onPhotosPicked: (List<PhotoBytes>) -> Unit,
    onCameraPermissionDenied: () -> Unit,
): PhotoPickerLauncher {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDenied = rememberUpdatedState(onCameraPermissionDenied)

    // URI выходного файла камеры между launch и onResult. На камере мы знаем URI заранее
    // (TakePicture пишет в указанный), поэтому держим его в state.
    val pendingCameraUri = remember { mutableStateOf<Uri?>(null) }
    // Сколько слотов было запрошено при последнем launch — используем для trim.
    val pendingSlots = remember { mutableStateOf(5) }
    // Флаг: после grant CAMERA permission'а сразу запустить TakePicture.
    val pendingCameraLaunch = remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val limit = pendingSlots.value.coerceAtLeast(0)
            val photos = uris.take(limit).mapNotNull { uri ->
                runCatching { readPhotoFromUri(context, uri) }.getOrNull()
            }
            if (photos.isNotEmpty()) {
                withContext(Dispatchers.Main) { onPhotosPicked(photos) }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri.value
        pendingCameraUri.value = null
        if (!success || uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val photo = runCatching { readPhotoFromUri(context, uri) }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) { onPhotosPicked(listOf(photo)) }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingCameraLaunch.value) {
            pendingCameraLaunch.value = false
            launchCamera(context, cameraLauncher, pendingCameraUri)
        } else if (!granted) {
            pendingCameraLaunch.value = false
            currentDenied.value()
        }
    }

    return remember(galleryLauncher, cameraLauncher, cameraPermissionLauncher) {
        object : PhotoPickerLauncher {
            override fun launch(source: PhotoSource, remainingSlots: Int) {
                if (remainingSlots <= 0) return
                pendingSlots.value = remainingSlots
                when (source) {
                    PhotoSource.GALLERY -> {
                        // PickMultipleVisualMedia использует системный Photo Picker —
                        // runtime-permission не требуется ни на одной версии Android.
                        val request = PickVisualMediaRequest(
                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly,
                        )
                        galleryLauncher.launch(request)
                    }
                    PhotoSource.CAMERA -> {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            launchCamera(context, cameraLauncher, pendingCameraUri)
                        } else {
                            pendingCameraLaunch.value = true
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
        }
    }
}

private fun launchCamera(
    context: Context,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>,
    pendingUri: androidx.compose.runtime.MutableState<Uri?>,
) {
    val uri = createCameraOutputUri(context)
    pendingUri.value = uri
    cameraLauncher.launch(uri)
}

private fun createCameraOutputUri(context: Context): Uri {
    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
    val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
}

private fun readPhotoFromUri(context: Context, uri: Uri): PhotoBytes? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return null
    require(bytes.size <= MAX_PHOTO_BYTES) {
        "Photo exceeds 10 MB (${bytes.size / 1024 / 1024} MB)"
    }
    val filename = queryFilename(context, uri) ?: "photo_${System.currentTimeMillis()}.jpg"
    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
    return PhotoBytes(bytes = bytes, filename = filename, mimeType = mime)
}

private fun queryFilename(context: Context, uri: Uri): String? {
    return context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}

package com.example.cleancity.domain.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberLocationPermission(): LocationPermissionController {
    val context = LocalContext.current
    var status by remember {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) PermissionStatus.Granted else PermissionStatus.NotRequested)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        status = if (isGranted) PermissionStatus.Granted else PermissionStatus.Denied
    }
    return LocationPermissionController(
        status = status,
        launchRequest = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )
}

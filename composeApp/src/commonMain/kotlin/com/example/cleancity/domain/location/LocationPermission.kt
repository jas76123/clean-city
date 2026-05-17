package com.example.cleancity.domain.location

import androidx.compose.runtime.Composable

enum class PermissionStatus { Granted, Denied, NotRequested }

class LocationPermissionController(
    val status: PermissionStatus,
    val launchRequest: () -> Unit,
)

@Composable
expect fun rememberLocationPermission(): LocationPermissionController

package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ACCENT = Color(0xFF5DDE8A)
private val ACCENT_ON = Color(0xFF0D2B1A)

@Composable
fun MapFabGroup(
    onLocationClick: () -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FloatingActionButton(
            onClick = onLocationClick,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Моё местоположение")
        }
        ExtendedFloatingActionButton(
            onClick = onCreateClick,
            containerColor = ACCENT,
            contentColor = ACCENT_ON,
            text = { Text("Сообщить") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }
}

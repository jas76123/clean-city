package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ACCENT = Color(0xFF5DDE8A)
private val ACCENT_ON = Color(0xFF0D2B1A)
private val SURFACE = Color(0xFFFFFFFF)
private val ICON_TINT = Color(0xFF1F5233) // green-700, matches theme primary

@Composable
fun MapFabGroup(
    onLocationClick: () -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocating: Boolean = false,
) {
    Column(
        modifier = modifier.padding(
            PaddingValues(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // Мишень — квадрат 52dp, прижата к правому краю
        Box(
            modifier = Modifier.align(Alignment.End),
        ) {
            FloatingActionButton(
                onClick = onLocationClick,
                shape = RoundedCornerShape(16.dp),
                containerColor = SURFACE,
                contentColor = ICON_TINT,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(52.dp),
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = ICON_TINT,
                    )
                } else {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Моё местоположение",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreateClick,
            containerColor = ACCENT,
            contentColor = ACCENT_ON,
            shape = RoundedCornerShape(16.dp),
            text = { Text("Сообщить") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }
}

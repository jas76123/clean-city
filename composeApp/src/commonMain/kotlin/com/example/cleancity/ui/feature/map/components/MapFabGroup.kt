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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MapFabGroup(
    onLocationClick: () -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocating: Boolean = false,
) {
    Column(
        modifier = modifier.padding(
            PaddingValues(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 40.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Box(modifier = Modifier.align(Alignment.End)) {
            FloatingActionButton(
                onClick = onLocationClick,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(52.dp),
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondary,
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
        FloatingActionButton(
            onClick = onCreateClick,
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Сообщить о проблеме",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

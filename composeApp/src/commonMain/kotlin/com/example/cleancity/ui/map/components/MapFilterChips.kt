package com.example.cleancity.ui.map.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.map.MapFilter
import com.example.cleancity.ui.theme.*

@Composable
fun MapFilterChips(
    activeFilter: MapFilter,
    onFilterClick: (MapFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emojis = mapOf(
        MapFilter.ALL to "🗺",
        MapFilter.PROBLEMS to "🗑️",
        MapFilter.EVENTS to "🤝",
        MapFilter.RESOLVED to "✅",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapFilter.entries.forEach { filter ->
            val isActive = filter == activeFilter
            Surface(
                onClick = { onFilterClick(filter) },
                shape = CircleShape,
                color = if (isActive) Accent else Color.White.copy(alpha = 0.15f),
                contentColor = if (isActive) Green900 else Color.White.copy(alpha = 0.8f),
            ) {
                Text(
                    text = "${emojis[filter] ?: ""} ${filter.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

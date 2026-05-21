package com.example.cleancity.ui.feature.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.feature.feed.FeedMode
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray200
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Green900

@Composable
fun FeedToggle(
    mode: FeedMode,
    mineCount: Int,
    showMineCount: Boolean,
    isGuest: Boolean,
    onModeChange: (FeedMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToggleChip(
            text = "Все жалобы",
            selected = mode == FeedMode.ALL,
            enabled = true,
            onClick = { onModeChange(FeedMode.ALL) },
        )
        val mineLabel = when {
            isGuest -> "Мои"
            showMineCount && mineCount > 0 -> "Мои ($mineCount)"
            else -> "Мои"
        }
        ToggleChip(
            text = mineLabel,
            selected = mode == FeedMode.MINE,
            enabled = !isGuest,
            onClick = { onModeChange(FeedMode.MINE) },
        )
    }
}

@Composable
private fun ToggleChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        !enabled -> Gray200
        selected -> Accent
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        !enabled -> Gray500
        selected -> Green900
        else -> Gray500
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(background)
            .border(
                width = 1.dp,
                color = if (selected) Accent else Gray200,
                shape = RoundedCornerShape(100.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            ),
            color = textColor,
        )
    }
}

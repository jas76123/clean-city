package com.example.cleancity.ui.feature.feed.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.shared.models.AnnouncementResponse
import com.example.cleancity.shared.models.IconStyle
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Amber
import com.example.cleancity.ui.theme.Blue
import com.example.cleancity.ui.theme.Gray200
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green900

@Composable
fun AnnouncementCard(
    announcement: AnnouncementResponse,
    modifier: Modifier = Modifier,
) {
    val isPositive = announcement.iconStyle == IconStyle.SUCCESS
    val isWarning = announcement.iconStyle == IconStyle.WARNING
    val cardBackground: Brush = if (isPositive) {
        Brush.linearGradient(listOf(Green700, Green900))
    } else {
        Brush.linearGradient(listOf(Color.White, Color.White))
    }
    val titleColor = if (isPositive) Color.White else Gray900
    val bodyColor = if (isPositive) Color.White.copy(alpha = 0.7f) else Gray600
    val borderColor = if (isPositive) Color.Transparent else Gray200

    Column(
        modifier = modifier
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(cardBackground)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val labelColor = when {
            isPositive -> Accent
            isWarning -> Amber
            else -> Blue
        }
        val labelText = when {
            isPositive -> "✓ ХОРОШИЕ НОВОСТИ"
            isWarning -> "⚠ ВНИМАНИЕ"
            else -> "ⓘ ИНФО"
        }
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = labelColor,
        )
        Text(
            text = announcement.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = titleColor,
            maxLines = 2,
        )
        Text(
            text = announcement.body,
            style = MaterialTheme.typography.bodySmall,
            color = bodyColor,
            maxLines = 2,
        )
    }
}

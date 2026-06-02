package com.example.cleancity.ui.feature.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray100
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green50
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.util.relativeTime

@Composable
fun NotificationCard(
    notification: NotificationResponse,
    modifier: Modifier = Modifier,
) {
    val isUnread = notification.readAt == null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isUnread) Green50 else MaterialTheme.colorScheme.surface)
            .border(1.dp, Gray100, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Green50),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (notification.kind) {
                NotificationKind.COMPLAINT_STATUS -> Icons.Default.NotificationsActive
                NotificationKind.ANNOUNCEMENT -> Icons.Default.Campaign
                NotificationKind.MODERATION_WARNING -> Icons.Default.Warning
            }
            Icon(icon, contentDescription = null, tint = Green600, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                notification.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Gray900,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                notification.body,
                style = MaterialTheme.typography.bodySmall,
                color = Gray500,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                relativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = Gray500,
            )
        }
        if (isUnread) {
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Accent),
            )
        }
    }
}

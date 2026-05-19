package com.example.cleancity.ui.feature.feed.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cleancity.composeapp.generated.resources.Res
import cleancity.composeapp.generated.resources.app_logo
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Green400
import org.jetbrains.compose.resources.painterResource

@Composable
fun FeedTopBar(
    modifier: Modifier = Modifier,
    unreadCount: Int,
    onBellClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(Res.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "Чистый Город",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Gray700,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        BadgedBox(
            badge = {
                if (unreadCount > 0) {
                    Badge { Text(if (unreadCount > 99) "99+" else unreadCount.toString()) }
                }
            },
        ) {
            IconButton(onClick = onBellClick) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "Уведомления",
                    tint = Gray700,
                )
            }
        }
    }
}

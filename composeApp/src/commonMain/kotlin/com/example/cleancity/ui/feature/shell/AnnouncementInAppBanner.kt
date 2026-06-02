package com.example.cleancity.ui.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green900
import kotlin.math.roundToInt

/**
 * In-app баннер нового уведомления. Рендерится глобальным оверлеем в App.kt.
 *
 * Свайп вниз больше порога -> [onDismiss]. Кнопка «Посмотреть» -> [onAction].
 */
@Composable
fun AnnouncementInAppBanner(
    title: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 80.dp.toPx() }
    // key to title so a new banner doesn't inherit a previous swipe offset
    var offsetY by remember(title) { mutableStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Green900)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        // только вниз: не даём уехать выше исходной позиции
                        offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (offsetY > dismissThresholdPx) onDismiss() else offsetY = 0f
                    },
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Campaign,
                contentDescription = null,
                tint = Green600,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Новое уведомление",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
            )
        }
        TextButton(onClick = onAction) {
            Text("Посмотреть", color = Accent)
        }
    }
}

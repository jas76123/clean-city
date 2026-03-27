package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.model.CleanupEvent
import com.example.cleancity.ui.theme.*

@Composable
fun EventBottomSheet(
    event: CleanupEvent,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 20.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gray200)
            )
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "🤝 ${event.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900,
                )
                Spacer(Modifier.height(6.dp))
                Text("📍 ${event.location}", style = MaterialTheme.typography.bodySmall, color = Gray500)
                Spacer(Modifier.height(4.dp))
                Text("👥 ${event.participants.size} участников", style = MaterialTheme.typography.bodySmall, color = Gray500)
                Spacer(Modifier.height(10.dp))
                Text(event.description, style = MaterialTheme.typography.bodyMedium, color = Gray600)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text("Присоединиться", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

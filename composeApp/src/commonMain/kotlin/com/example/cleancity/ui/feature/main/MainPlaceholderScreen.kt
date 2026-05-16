package com.example.cleancity.ui.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green900

class MainPlaceholderScreen(
    private val isGuest: Boolean,
    private val onPrimaryAction: () -> Unit,
) : Screen {
    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Accent, Green400))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🛡", style = MaterialTheme.typography.titleLarge, color = Green900)
                }
                Spacer(Modifier.height(16.dp))
                Text("Чистый Город", style = MaterialTheme.typography.headlineSmall, color = Gray900)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Главный экран появится Day 9",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray500,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = if (isGuest) "Войти / Регистрация" else "Выйти",
                onClick = onPrimaryAction,
                backgroundColor = Green700,
                contentColor = Color.White,
            )
        }
    }
}

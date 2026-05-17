package com.example.cleancity.ui.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Green900

class AboutScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("О приложении") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Accent, Green400))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🛡", style = MaterialTheme.typography.displayMedium, color = Green900)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Чистый Город",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Gray900,
                )
                Text(
                    "Версия 1.0 · MVP для Сочи",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Гражданская платформа для подачи и подтверждения " +
                        "проблем городской среды: мусор, дороги, освещение, " +
                        "пляжи. Голоса жителей формируют приоритет в работе " +
                        "администрации.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray700,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                InfoRow(label = "Дипломный проект", value = "ИТ-факультет, 2026")
                InfoRow(label = "Backend", value = "Kotlin · Ktor · PostgreSQL + PostGIS")
                InfoRow(label = "Mobile", value = "Compose Multiplatform · Yandex MapKit")
                InfoRow(label = "Поддержка", value = "support@cleancity.local")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = Gray500,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Gray700)
    }
}

package com.example.cleancity.ui.feature.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cleancity.composeapp.generated.resources.Res
import cleancity.composeapp.generated.resources.app_logo
import com.example.cleancity.ui.components.SectionTopBar
import com.example.cleancity.ui.feature.auth.LegalKind
import com.example.cleancity.ui.feature.auth.LegalScreen
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green600
import org.jetbrains.compose.resources.painterResource

class AboutScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            SectionTopBar(title = "О приложении", onBack = { navigator.pop() })
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(Res.drawable.app_logo),
                    contentDescription = "Чистый Город",
                    modifier = Modifier.size(160.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Чистый Город",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Gray900,
                )
                Text(
                    "Версия 1.0 · MVP для города",
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
                InfoRow(label = "Дипломный проект", value = "Сочи, 2026")
                InfoRow(label = "Backend", value = "Kotlin · Ktor · PostgreSQL + PostGIS")
                InfoRow(label = "Mobile", value = "Compose Multiplatform · Yandex MapKit")
                InfoRow(label = "Поддержка", value = "a.ja5m@yandex.ru")
                Spacer(Modifier.height(24.dp))
                LegalLinkRow(
                    text = "Политика обработки данных",
                    onClick = { navigator.push(LegalScreen(LegalKind.Privacy)) },
                )
                LegalLinkRow(
                    text = "Условия использования",
                    onClick = { navigator.push(LegalScreen(LegalKind.Terms)) },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Карты и геокодирование © Яндекс. Использование сервиса " +
                        "регулируется условиями Яндекс Карт.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                    textAlign = TextAlign.Center,
                )
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

@Composable
private fun LegalLinkRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Green600,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}

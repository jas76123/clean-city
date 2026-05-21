package com.example.cleancity.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray900

/** Спиннер по центру. Вызывающий задаёт размер через [modifier] (обычно Modifier.fillMaxSize()). */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Состояние ошибки с кнопкой повтора. [message] — готовый текст (см. listErrorMessage). */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("⚠️", fontSize = 40.sp)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Gray600,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}

/** Пустое состояние списка: эмодзи-иллюстрация, заголовок, опциональный подзаголовок и действие. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(emoji, fontSize = 48.sp)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Gray900,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray500,
                    textAlign = TextAlign.Center,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

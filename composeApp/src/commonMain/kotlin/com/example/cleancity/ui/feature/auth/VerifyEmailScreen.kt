package com.example.cleancity.ui.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import com.example.cleancity.ui.components.AuthScaffold
import com.example.cleancity.ui.components.AuthSub
import com.example.cleancity.ui.components.AuthTag
import com.example.cleancity.ui.components.AuthTitle
import com.example.cleancity.ui.components.SecondaryButton
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Red
import org.koin.core.parameter.parametersOf

class VerifyEmailScreen(private val email: String) : Screen {
    @Composable
    override fun Content() {
        val model: VerifyEmailScreenModel = koinScreenModel { parametersOf(email) }
        val state by model.state.collectAsState()

        AuthScaffold(onBack = null) {
            AuthTag("Подтверждение")
            AuthTitle("Проверьте почту")
            AuthSub("Мы отправили письмо на ${state.email}. Откройте письмо и нажмите кнопку подтверждения.\n\nЕсли письма нет, проверьте папку «Спам».")
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                when (val s = state.status) {
                    VerifyStatus.Waiting -> Text("Откройте письмо на почте", color = Gray500, style = MaterialTheme.typography.bodyMedium)
                    VerifyStatus.Verifying -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Проверяем токен...", style = MaterialTheme.typography.bodyMedium)
                    }
                    is VerifyStatus.Error -> Text(s.message, color = Red, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SecondaryButton(
                text = if (state.cooldownSec > 0) "Повторно через ${state.cooldownSec} с" else "Отправить повторно",
                onClick = model::resend,
                enabled = state.cooldownSec == 0,
                contentColor = androidx.compose.ui.graphics.Color.Black,
                borderColor = Gray500,
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
            )
            TextButton(
                onClick = model::changeEmail,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            ) {
                Text("Изменить email", color = Gray500, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

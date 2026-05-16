package com.example.cleancity.ui.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.ui.components.AuthScaffold
import com.example.cleancity.ui.components.AuthSub
import com.example.cleancity.ui.components.AuthTag
import com.example.cleancity.ui.components.AuthTitle
import com.example.cleancity.ui.components.FormField
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.theme.Green700

class ForgotPasswordScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val model: ForgotPasswordScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }

        LaunchedEffect(state.snackbar) {
            state.snackbar?.let { snackbarHost.showSnackbar(it); model.dismissSnackbar() }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AuthScaffold(onBack = { nav.pop() }) {
                if (!state.sent) {
                    AuthTag("Сброс пароля")
                    AuthTitle("Забыли пароль?")
                    AuthSub("Введите email — мы отправим ссылку для сброса.")
                    FormField("EMAIL", state.email, model::setEmail, keyboardType = KeyboardType.Email, error = state.emailError)
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = "Прислать ссылку",
                        onClick = model::submit,
                        enabled = state.canSubmit,
                        loading = state.loading,
                        backgroundColor = Green700,
                        contentColor = Color.White,
                    )
                } else {
                    AuthTag("Сброс пароля")
                    AuthTitle("Письмо отправлено")
                    Text(
                        text = "Если такой email зарегистрирован, мы прислали на него ссылку для сброса. Проверьте почту.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                    PrimaryButton(
                        text = "Вернуться к входу",
                        onClick = { nav.pop() },
                        backgroundColor = Green700,
                        contentColor = Color.White,
                    )
                }
            }
            SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter)) { Snackbar(it) }
        }
    }
}

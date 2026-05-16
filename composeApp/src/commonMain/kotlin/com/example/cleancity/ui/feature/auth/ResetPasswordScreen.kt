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
import com.example.cleancity.ui.theme.Red
import org.koin.core.parameter.parametersOf

class ResetPasswordScreen(private val token: String) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val model: ResetPasswordScreenModel = koinScreenModel { parametersOf(token) }
        val state by model.state.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }

        LaunchedEffect(state.success) {
            if (state.success) {
                snackbarHost.showSnackbar("Пароль обновлён")
                nav.replaceAll(LoginScreen())
            }
        }
        LaunchedEffect(state.snackbar) {
            state.snackbar?.let { snackbarHost.showSnackbar(it); model.dismissSnackbar() }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AuthScaffold(onBack = null) {
                state.fullScreenError?.let { msg ->
                    AuthTag("Ошибка")
                    AuthTitle("Ссылка устарела")
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = Red, modifier = Modifier.padding(bottom = 24.dp))
                    PrimaryButton(
                        text = "Запросить новую",
                        onClick = { nav.replaceAll(ForgotPasswordScreen()) },
                        backgroundColor = Green700,
                        contentColor = Color.White,
                    )
                    return@AuthScaffold
                }
                AuthTag("Новый пароль")
                AuthTitle("Создайте новый пароль")
                AuthSub("Минимум 8 символов.")
                FormField(
                    label = "НОВЫЙ ПАРОЛЬ",
                    value = state.newPassword,
                    onValueChange = model::setNewPassword,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    error = state.newPasswordError,
                )
                FormField(
                    label = "ПОВТОРИТЕ ПАРОЛЬ",
                    value = state.confirm,
                    onValueChange = model::setConfirm,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    error = state.confirmError,
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Установить пароль",
                    onClick = model::submit,
                    enabled = state.canSubmit,
                    loading = state.loading,
                    backgroundColor = Green700,
                    contentColor = Color.White,
                )
            }
            SnackbarHost(snackbarHost, modifier = Modifier.align(Alignment.BottomCenter)) { Snackbar(it) }
        }
    }
}

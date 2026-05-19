package com.example.cleancity.ui.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.ui.components.AuthLinkRow
import com.example.cleancity.ui.components.AuthScaffold
import com.example.cleancity.ui.components.AuthSub
import com.example.cleancity.ui.components.AuthTag
import com.example.cleancity.ui.components.AuthTitle
import com.example.cleancity.ui.components.FormField
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green700
import org.koin.compose.koinInject

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val model: LoginScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }
        val authRepo: AuthRepository = koinInject()
        val authState by authRepo.state.collectAsState()

        LaunchedEffect(authState) {
            if (authState is AuthState.Authenticated) {
                nav.pop()
            }
        }

        LaunchedEffect(state.snackbar) {
            state.snackbar?.let {
                snackbarHost.showSnackbar(it)
                model.dismissSnackbar()
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AuthScaffold(onBack = { nav.pop() }) {
                AuthTag("Вход")
                AuthTitle("С возвращением!")
                AuthSub("Войдите, чтобы продолжить.")
                FormField(
                    label = "EMAIL",
                    value = state.email,
                    onValueChange = model::setEmail,
                    keyboardType = KeyboardType.Email,
                    error = state.emailError,
                )
                FormField(
                    label = "ПАРОЛЬ",
                    value = state.password,
                    onValueChange = model::setPassword,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    error = state.passwordError,
                )
                state.emailNotVerifiedFor?.let { email ->
                    Text(
                        text = "Подтвердите email. Письмо отправлено на $email.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { model.resendVerification(email) }) {
                        Text("Прислать ещё раз")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { nav.push(ForgotPasswordScreen()) }) {
                        Text(
                            "Забыли пароль?",
                            color = Green600,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Войти",
                    onClick = model::submit,
                    enabled = state.canSubmit,
                    loading = state.loading,
                    backgroundColor = Green700,
                    contentColor = Color.White,
                )
                AuthLinkRow(
                    prefix = "Нет аккаунта?",
                    linkText = "Регистрация",
                    onClick = { nav.replace(RegisterScreen()) },
                )
            }
            SnackbarHost(snackbarHost, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                Snackbar(it)
            }
        }
    }
}

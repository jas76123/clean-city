package com.example.cleancity.ui.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.ui.components.AuthLinkRow
import com.example.cleancity.ui.components.AuthScaffold
import com.example.cleancity.ui.components.AuthSub
import com.example.cleancity.ui.components.AuthTag
import com.example.cleancity.ui.components.AuthTitle
import com.example.cleancity.ui.components.ConsentRow
import com.example.cleancity.ui.components.FormField
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.theme.Green700

class RegisterScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val model: RegisterScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }

        LaunchedEffect(state.snackbar) {
            state.snackbar?.let { snackbarHost.showSnackbar(it); model.dismissSnackbar() }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AuthScaffold(onBack = { nav.pop() }) {
                AuthTag("Создание аккаунта")
                AuthTitle("Присоединяйтесь к\nчистому городу")
                AuthSub("За 30 секунд — и вы можете влиять на состояние Сочи.")
                FormField("ИМЯ", state.fullName, model::setFullName, error = state.nameError)
                FormField("EMAIL", state.email, model::setEmail, keyboardType = KeyboardType.Email, error = state.emailError)
                FormField(
                    label = "ПАРОЛЬ",
                    value = state.password,
                    onValueChange = model::setPassword,
                    keyboardType = KeyboardType.Password,
                    isPassword = true,
                    hint = "Минимум 8 символов",
                    error = state.passwordError,
                )
                ConsentRow(
                    checked = state.consent,
                    onCheckedChange = model::setConsent,
                    onTermsClick = { nav.push(LegalScreen(LegalKind.Terms)) },
                    onPrivacyClick = { nav.push(LegalScreen(LegalKind.Privacy)) },
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton(
                    text = "Зарегистрироваться",
                    onClick = model::submit,
                    enabled = state.canSubmit,
                    loading = state.loading,
                    backgroundColor = Green700,
                    contentColor = Color.White,
                )
                AuthLinkRow("Уже есть аккаунт?", "Войти", onClick = { nav.replace(LoginScreen()) })
            }
            SnackbarHost(snackbarHost, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) { Snackbar(it) }
        }
    }
}

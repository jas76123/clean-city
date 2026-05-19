package com.example.cleancity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.navigator.Navigator
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.DeepLink
import com.example.cleancity.domain.DeepLinkBus
import com.example.cleancity.ui.feature.auth.LoginScreen
import com.example.cleancity.ui.feature.auth.ResetPasswordScreen
import com.example.cleancity.ui.feature.auth.VerifyEmailScreen
import com.example.cleancity.ui.feature.shell.MainShellScreen
import com.example.cleancity.ui.feature.splash.SplashLoaderScreen
import com.example.cleancity.ui.feature.splash.SplashScreen
import com.example.cleancity.ui.theme.CleanCityTheme
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject
import cafe.adriel.voyager.core.screen.Screen

@Composable
fun App() {
    CleanCityTheme {
        val authRepo: AuthRepository = koinInject()
        val authState by authRepo.state.collectAsState()

        // First-time-only init
        LaunchedEffect(Unit) { authRepo.init() }

        val initial: Screen = remember(authState) {
            when (val s = authState) {
                AuthState.Loading -> SplashLoaderScreen()
                AuthState.Anonymous -> SplashScreen()
                AuthState.Guest -> MainShellScreen()
                is AuthState.NeedsVerification -> VerifyEmailScreen(email = s.email)
                is AuthState.Authenticated -> MainShellScreen()
            }
        }

        // Уникальный ключ сессии: включает user.id, чтобы смена аккаунта
        // гарантированно ребилдила стек, даже если корневой класс тот же
        // (Authenticated A → Authenticated B = оба MainShellScreen).
        val sessionKey: String = when (val s = authState) {
            AuthState.Loading -> "loading"
            AuthState.Anonymous -> "anonymous"
            AuthState.Guest -> "guest"
            is AuthState.NeedsVerification -> "needs-verify:${s.email}"
            is AuthState.Authenticated -> "auth:${s.user.id}"
        }

        // Process-уникальный seed: переживает рекомпозицию, НЕ переживает
        // process death. Включаем в ключ key(...), чтобы при cold start
        // savedInstanceState не восстановил backstack Voyager-а.
        val processSeed: String = remember { kotlin.random.Random.nextLong().toString(36) }

        // key(...) пересоздаёт всю поддерево при смене session или процесса:
        // root Navigator, MainShellScreen, TabNavigator, вложенные
        // Navigator(FeedScreen()/MapScreen()/ProfileScreen()/...) в табах.
        // Без key() менялся бы только ключ root rememberSaveable, а
        // позиционные ключи вложенных Navigator-ов оставались бы прежними
        // и Voyager-saver восстанавливал бы старый backstack.
        key(processSeed, sessionKey) {
        Navigator(initial) { navigator ->
            // Re-route across major sections when AuthState changes — пересоздаём
            // root всякий раз при смене sessionKey, чтобы back stack/ScreenModel'ы
            // предыдущего пользователя не дожили до сессии нового.
            var lastSessionKey by remember { mutableStateOf(sessionKey) }
            LaunchedEffect(sessionKey) {
                if (sessionKey == lastSessionKey) return@LaunchedEffect
                lastSessionKey = sessionKey
                val newRoot: Screen = when (val s = authState) {
                    AuthState.Loading -> SplashLoaderScreen()
                    AuthState.Anonymous -> SplashScreen()
                    AuthState.Guest -> MainShellScreen()
                    is AuthState.NeedsVerification -> VerifyEmailScreen(email = s.email)
                    is AuthState.Authenticated -> MainShellScreen()
                }
                navigator.replaceAll(newRoot)
            }

            // Reset deep-link → push ResetPasswordScreen overriding current
            LaunchedEffect(Unit) {
                DeepLinkBus.pending
                    .filterNotNull()
                    .filterIsInstance<DeepLink.Reset>()
                    .collect { link ->
                        navigator.replaceAll(ResetPasswordScreen(link.token))
                        DeepLinkBus.consume(link)
                    }
            }

            cafe.adriel.voyager.navigator.CurrentScreen()
        }
        }
    }
}

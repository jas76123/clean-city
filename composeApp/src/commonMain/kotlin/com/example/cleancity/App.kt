package com.example.cleancity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
                AuthState.Anonymous -> SplashScreen(onContinueAsGuest = { authRepo.continueAsGuest() })
                AuthState.Guest -> MainShellScreen()
                is AuthState.NeedsVerification -> VerifyEmailScreen(email = s.email)
                is AuthState.Authenticated -> MainShellScreen()
            }
        }

        Navigator(initial) { navigator ->
            // Re-route across major sections when AuthState changes
            LaunchedEffect(authState) {
                val newRoot: Screen? = when (val s = authState) {
                    AuthState.Loading -> SplashLoaderScreen()
                    AuthState.Anonymous -> SplashScreen(onContinueAsGuest = { authRepo.continueAsGuest() })
                    AuthState.Guest -> MainShellScreen()
                    is AuthState.NeedsVerification -> VerifyEmailScreen(email = s.email)
                    is AuthState.Authenticated -> MainShellScreen()
                }
                if (newRoot != null && navigator.lastItem::class != newRoot::class) {
                    navigator.replaceAll(newRoot)
                }
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

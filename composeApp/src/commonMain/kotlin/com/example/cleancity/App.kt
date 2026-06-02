package com.example.cleancity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.DeepLink
import com.example.cleancity.domain.DeepLinkBus
import com.example.cleancity.domain.NotificationEventBus
import com.example.cleancity.domain.NotificationTapBus
import com.example.cleancity.ui.feature.auth.ResetPasswordScreen
import com.example.cleancity.ui.feature.auth.VerifyEmailScreen
import com.example.cleancity.ui.feature.shell.AnnouncementInAppBanner
import com.example.cleancity.ui.feature.shell.BannerController
import com.example.cleancity.ui.feature.shell.BannerData
import com.example.cleancity.ui.feature.shell.MainShellScreen
import com.example.cleancity.ui.feature.splash.SplashLoaderScreen
import com.example.cleancity.ui.feature.splash.SplashScreen
import com.example.cleancity.ui.theme.CleanCityTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject

private const val BANNER_AUTO_DISMISS_MS = 9_000L

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
        key(processSeed, sessionKey) {
        Box(Modifier.fillMaxSize()) {
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

            GlobalNotificationBanner(authState = authState)
        }
        }
    }
}

/**
 * Глобальный оверлей in-app баннера. Живёт поверх корневого Navigator,
 * поэтому виден на любом экране. Показывается только для Authenticated/Guest.
 * Встаёт над нижней панелью вкладок (высоту публикует MainShellScreen),
 * на экранах без панели — над системной навигационной полосой.
 */
@Composable
private fun BoxScope.GlobalNotificationBanner(authState: AuthState) {
    val bus: NotificationEventBus = koinInject()
    val controller: BannerController = koinInject()

    val banner by controller.current.collectAsState()
    val barHeight by controller.bottomBarHeight.collectAsState()
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val showAllowed = authState is AuthState.Authenticated || authState is AuthState.Guest

    // Подписка на новые объявления → показать баннер.
    LaunchedEffect(Unit) {
        bus.newAnnouncements.collect { n ->
            controller.show(BannerData(title = n.title, notificationId = n.id))
        }
    }

    // Скрыть баннер, если ушли в неавторизованную зону (logout и т.п.).
    LaunchedEffect(showAllowed) {
        if (!showAllowed) controller.dismiss()
    }

    // Автоскрытие по таймеру (перезапускается на каждый новый баннер).
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(BANNER_AUTO_DISMISS_MS)
            controller.dismiss()
        }
    }

    val bottomPadding = if (barHeight > 0.dp) barHeight else systemBottom

    AnimatedVisibility(
        visible = banner != null && showAllowed,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomPadding),
    ) {
        val data = banner
        if (data != null) {
            AnnouncementInAppBanner(
                title = data.title,
                onAction = {
                    data.notificationId?.let { NotificationTapBus.emit(it) }
                    controller.dismiss()
                },
                onDismiss = { controller.dismiss() },
            )
        }
    }
}

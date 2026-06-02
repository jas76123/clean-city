# Глобальный баннер уведомлений со свайпом — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Поднять in-app баннер новых уведомлений с уровня `MainShellScreen` на уровень `App.kt`, чтобы он показывался на любом экране авторизованной части и закрывался свайпом вниз.

**Architecture:** Новый синглтон `BannerController` (StateFlow с данными баннера + высотой нижней панели) заменяет прямое использование `SnackbarHostState`. `App.kt` подписывается на `NotificationEventBus`, держит таймер автоскрытия и рендерит баннер как оверлей поверх корневого `Navigator`. `MainShellScreen` публикует фактическую высоту нижней панели вкладок, чтобы оверлей вставал над ней. Навигация по «Посмотреть» переиспользует существующий `NotificationTapBus`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager (навигация), Koin (DI), kotlinx.coroutines (StateFlow), kotlin.test (тесты).

---

## Структура файлов

- **Создать** `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/BannerController.kt` — синглтон-координатор: `current: StateFlow<BannerData?>`, `bottomBarHeight: StateFlow<Dp>`, методы `show/dismiss/setBottomBarHeight`. Лежит в `ui/feature/shell`, а не в `domain`, потому что хранит UI-тип `Dp`.
- **Создать** `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/shell/BannerControllerTest.kt` — unit-тесты на `show/dismiss/replace`.
- **Изменить** `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt` — регистрация `BannerController` в Koin.
- **Изменить** `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt` — новый API (`title` + колбэки `onAction`/`onDismiss` вместо `SnackbarData`) и жест свайпа вниз.
- **Изменить** `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt` — Box-обёртка над `Navigator`, подписка на `NotificationEventBus` с гейтом по `authState`, таймер автоскрытия, оверлей-баннер.
- **Изменить** `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt` — убрать `SnackbarHost` и подписку на баннер; публиковать высоту нижней панели в `BannerController`.

---

## Task 1: BannerController + данные баннера

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/BannerController.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/shell/BannerControllerTest.kt`

- [ ] **Step 1: Написать падающий тест**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/shell/BannerControllerTest.kt`:

```kotlin
package com.example.cleancity.ui.feature.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BannerControllerTest {

    @Test
    fun show_setsCurrentBanner() {
        val controller = BannerController()
        controller.show(BannerData(title = "Привет", notificationId = 42L))
        assertEquals("Привет", controller.current.value?.title)
        assertEquals(42L, controller.current.value?.notificationId)
    }

    @Test
    fun dismiss_clearsCurrentBanner() {
        val controller = BannerController()
        controller.show(BannerData(title = "Привет", notificationId = 42L))
        controller.dismiss()
        assertNull(controller.current.value)
    }

    @Test
    fun show_replacesPreviousBanner() {
        val controller = BannerController()
        controller.show(BannerData(title = "Первый", notificationId = 1L))
        controller.show(BannerData(title = "Второй", notificationId = 2L))
        assertEquals("Второй", controller.current.value?.title)
        assertEquals(2L, controller.current.value?.notificationId)
    }

    @Test
    fun bottomBarHeight_defaultsToZero_andUpdates() {
        val controller = BannerController()
        assertEquals(0.dp, controller.bottomBarHeight.value)
        controller.setBottomBarHeight(80.dp)
        assertEquals(80.dp, controller.bottomBarHeight.value)
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что не компилируется/падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.shell.BannerControllerTest"`
Expected: FAIL — `Unresolved reference: BannerController` / `BannerData`.

- [ ] **Step 3: Реализовать `BannerController`**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/BannerController.kt`:

```kotlin
package com.example.cleancity.ui.feature.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Данные одного in-app баннера уведомления. */
data class BannerData(
    val title: String,
    val notificationId: Long?,
)

/**
 * Координатор глобального in-app баннера. Синглтон в Koin.
 *
 * Показывается по одному баннеру за раз — новый вытесняет предыдущий.
 * Также хранит высоту нижней панели вкладок ([bottomBarHeight]), которую
 * публикует MainShellScreen, чтобы оверлей в App.kt вставал НАД панелью,
 * а не перекрывал её кнопки.
 */
class BannerController {
    private val _current = MutableStateFlow<BannerData?>(null)
    val current: StateFlow<BannerData?> = _current.asStateFlow()

    private val _bottomBarHeight = MutableStateFlow(0.dp)
    val bottomBarHeight: StateFlow<Dp> = _bottomBarHeight.asStateFlow()

    fun show(data: BannerData) { _current.value = data }

    fun dismiss() { _current.value = null }

    fun setBottomBarHeight(height: Dp) { _bottomBarHeight.value = height }
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.shell.BannerControllerTest"`
Expected: PASS (4 теста).

- [ ] **Step 5: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/BannerController.kt composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/shell/BannerControllerTest.kt
git commit -m "feat: BannerController для глобального баннера уведомлений"
```

---

## Task 2: Зарегистрировать BannerController в Koin

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt:78`

- [ ] **Step 1: Добавить регистрацию синглтона**

В `AppModule.kt` найти строку 78:

```kotlin
    single { NotificationEventBus() }
```

Заменить на:

```kotlin
    single { NotificationEventBus() }
    single { com.example.cleancity.ui.feature.shell.BannerController() }
```

- [ ] **Step 2: Сборка — проверить, что компилируется**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat: зарегистрировать BannerController в Koin"
```

---

## Task 3: Новый API баннера + свайп вниз

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt` (полная замена)

Баннер перестаёт зависеть от `SnackbarData`. Теперь принимает `title` и колбэки, и сам обрабатывает вертикальный свайп: сдвиг вниз больше порога вызывает `onDismiss()`.

- [ ] **Step 1: Заменить файл целиком**

Полностью заменить содержимое `AnnouncementInAppBanner.kt` на:

```kotlin
package com.example.cleancity.ui.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green900
import kotlin.math.roundToInt

/**
 * In-app баннер нового уведомления. Рендерится глобальным оверлеем в App.kt.
 *
 * Свайп вниз больше порога -> [onDismiss]. Кнопка «Посмотреть» -> [onAction].
 */
@Composable
fun AnnouncementInAppBanner(
    title: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 80.dp.toPx() }
    var offsetY by remember { mutableStateOf(0f) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Green900)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        // только вниз: не даём уехать выше исходной позиции
                        offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (offsetY > dismissThresholdPx) onDismiss() else offsetY = 0f
                    },
                )
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Campaign,
                contentDescription = null,
                tint = Green600,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Новое уведомление",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
            )
        }
        TextButton(onClick = onAction) {
            Text("Посмотреть", color = Accent)
        }
    }
}
```

- [ ] **Step 2: Сборка — ожидается ОШИБКА в MainShellScreen**

Run: `./gradlew :composeApp:assembleDebug`
Expected: FAIL — `MainShellScreen.kt` всё ещё вызывает старую сигнатуру `AnnouncementInAppBanner(data)`. Это нормально, чиним в Task 5. (Если хочется зелёную сборку на каждом шаге — выполнять Task 4 и Task 5 до сборки; но коммитим этот файл сейчас.)

- [ ] **Step 3: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt
git commit -m "feat: новый API баннера со свайпом вниз"
```

---

## Task 4: Глобальный оверлей-баннер в App.kt

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt` (полная замена)

Оборачиваем корневой `Navigator` в `Box`, добавляем приватный composable `GlobalNotificationBanner`, который подписывается на `NotificationEventBus`, держит таймер автоскрытия и рендерит баннер. Гейт по `authState`: баннер виден только для `Authenticated`/`Guest` (на Splash/Login/Verify скрыт).

- [ ] **Step 1: Заменить файл целиком**

Полностью заменить содержимое `App.kt` на:

```kotlin
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
```

Примечание: импорт `LoginScreen` из старого `App.kt` не используется (initial не ссылается на него), поэтому он убран — это устраняет неиспользуемый импорт.

- [ ] **Step 2: Сборка — ожидается ОШИБКА в MainShellScreen**

Run: `./gradlew :composeApp:assembleDebug`
Expected: FAIL — `MainShellScreen.kt` всё ещё вызывает старую сигнатуру `AnnouncementInAppBanner(data)`. Чиним в Task 5.

- [ ] **Step 3: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt
git commit -m "feat: глобальный оверлей баннера уведомлений в App.kt"
```

---

## Task 5: Убрать баннер из MainShellScreen + публиковать высоту панели

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt`

Убираем `SnackbarHostState`, подписку на `NotificationEventBus` и `SnackbarHost` из `Scaffold`. Вместо этого инжектим `BannerController` и публикуем измеренную высоту нижней панели; на dispose сбрасываем в `0.dp`. Тап по системному push (`NotificationTapBus`) остаётся без изменений.

- [ ] **Step 1: Обновить импорты**

В шапке файла удалить больше не нужные импорты:

```kotlin
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.example.cleancity.domain.NotificationEventBus
```

И добавить новые:

```kotlin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
```

(Оставить как есть: `DisposableEffect`, `Modifier`, `dp`, `Color`, `Scaffold` — они ещё используются.)

- [ ] **Step 2: Обновить тело `Content()`**

Заменить блок инъекций и эффектов (текущие строки 51–64):

```kotlin
        val store: UnreadCountStore = koinInject()
        val authRepo: AuthRepository = koinInject()
        val bus: NotificationEventBus = koinInject()
        val authState by authRepo.state.collectAsState()
        val unreadCount by store.state.collectAsState()
        val pendingTap by NotificationTapBus.pending.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }

        LaunchedEffect(authState) {
            if (authState is AuthState.Authenticated) store.start() else store.stop()
        }
        DisposableEffect(Unit) {
            onDispose { store.stop() }
        }
```

на:

```kotlin
        val store: UnreadCountStore = koinInject()
        val authRepo: AuthRepository = koinInject()
        val bannerController: BannerController = koinInject()
        val authState by authRepo.state.collectAsState()
        val unreadCount by store.state.collectAsState()
        val pendingTap by NotificationTapBus.pending.collectAsState()
        val density = LocalDensity.current

        LaunchedEffect(authState) {
            if (authState is AuthState.Authenticated) store.start() else store.stop()
        }
        DisposableEffect(Unit) {
            onDispose {
                store.stop()
                bannerController.setBottomBarHeight(0.dp)
            }
        }
```

`BannerController` лежит в том же пакете `com.example.cleancity.ui.feature.shell`, поэтому дополнительный импорт не нужен.

- [ ] **Step 3: Удалить подписку на in-app баннер**

Удалить целиком блок (текущие строки 71–83):

```kotlin
            // 1. In-app banner подписка
            LaunchedEffect(Unit) {
                bus.newAnnouncements.collect { n ->
                    val result = snackbarHost.showSnackbar(
                        message = n.title,
                        actionLabel = "Посмотреть",
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        tabNavigator.current = NotificationsTab
                    }
                }
            }

```

(Блок «2. Тап по системному push» с `LaunchedEffect(pendingTap)` оставить без изменений.)

- [ ] **Step 4: Убрать `snackbarHost` из Scaffold и измерять высоту панели**

Заменить вызов `Scaffold(...)` (текущие строки 93–114):

```kotlin
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                snackbarHost = {
                    SnackbarHost(snackbarHost) { data -> AnnouncementInAppBanner(data) }
                },
                content = { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        CurrentTab()
                    }
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        TabNavigationItem(FeedTab)
                        TabNavigationItem(MapTab)
                        TabNavigationItem(NotificationsTab, badgeCount = unreadCount)
                        TabNavigationItem(ProfileTab)
                    }
                },
            )
```

на:

```kotlin
            Scaffold(
                contentWindowInsets = WindowInsets(0),
                content = { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        CurrentTab()
                    }
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.onSizeChanged { size ->
                            bannerController.setBottomBarHeight(
                                with(density) { size.height.toDp() },
                            )
                        },
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                        TabNavigationItem(FeedTab)
                        TabNavigationItem(MapTab)
                        TabNavigationItem(NotificationsTab, badgeCount = unreadCount)
                        TabNavigationItem(ProfileTab)
                    }
                },
            )
```

- [ ] **Step 5: Сборка — теперь ЗЕЛЁНАЯ**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL. Если есть предупреждения о неиспользуемых импортах (`Box`/`AnnouncementInAppBanner` больше не нужны в MainShellScreen) — удалить и эти импорты:
- `AnnouncementInAppBanner` импортируется неявно (один пакет), отдельного импорта не было — ничего не делать.
- `Box` всё ещё используется в `content = { ... Box(...) }` — оставить.

- [ ] **Step 6: Прогнать unit-тесты — убедиться, что ничего не сломалось**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS (включая `BannerControllerTest` и существующие тесты).

- [ ] **Step 7: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt
git commit -m "refactor: убрать локальный баннер из MainShellScreen, публиковать высоту панели"
```

---

## Task 6: Ручной smoke на устройстве (Samsung A33)

Жесты и композицию в эмуляторе/девайсе автоматизировать не будем — проверяем руками. APK ставится через `~/Library/Android/sdk/platform-tools/adb install -r <apk>` (обновление с сохранением данных). Для работы с локальным backend — `adb reverse tcp:8081` и `API_BASE_URL=http://10.0.2.2:8081` (для эмулятора), либо боевой backend.

- [ ] **Step 1: Собрать debug-APK**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL, APK в `composeApp/build/outputs/apk/debug/`.

- [ ] **Step 2: Установить и пройти сценарии**

Триггерить новое объявление (через polling — создать ANNOUNCEMENT на backend, дождаться ≤30 сек) и проверить:

- [ ] Баннер появляется, когда открыта **лента**.
- [ ] Баннер появляется, когда открыт **экран деталей жалобы**.
- [ ] Баннер появляется на **других вкладках** (Карта, Профиль, Уведомления).
- [ ] **Свайп вниз** закрывает баннер.
- [ ] Если не трогать — баннер **сам исчезает** примерно через 9 секунд.
- [ ] Тап **«Посмотреть»** открывает вкладку «Уведомления» и закрывает баннер.
- [ ] На экранах с вкладками баннер висит **над** нижней панелью навигации, не перекрывая её кнопки.
- [ ] На splash/логине/верификации email баннер **не показывается**.

- [ ] **Step 3: Финальная проверка перед завершением**

Использовать superpowers:verification-before-completion: подтвердить, что `./gradlew :composeApp:testDebugUnitTest` и `:composeApp:assembleDebug` зелёные, и все пункты smoke отмечены. Только после этого считать работу выполненной.

---

## Self-Review

**Spec coverage:**
- «Баннер на любом экране авторизованной части» → Task 4 (оверлей в App.kt поверх Navigator) + гейт `showAllowed`. ✓
- «Свайп вниз закрывает» → Task 3 (`detectVerticalDragGestures` + порог). ✓
- «Автоскрытие как запасной вариант» → Task 4 (`BANNER_AUTO_DISMISS_MS` таймер). ✓
- «Не показывать на Splash/Login/Verify» → Task 4 (`showAllowed = Authenticated || Guest`). ✓
- «Отступ над нижней панелью (вариант A)» → Task 1 (`bottomBarHeight`) + Task 5 (`onSizeChanged` публикует высоту) + Task 4 (`bottomPadding`). ✓
- «BannerController через общий StateFlow, не CompositionLocal» → Task 1. ✓
- «Не трогать системную шторку / NotificationTapBus как канал» → не меняем; переиспользуем `NotificationTapBus.emit` только для навигации «Посмотреть». ✓
- Unit-тесты на `show/dismiss/replace` → Task 1. ✓

**Placeholder scan:** плейсхолдеров нет — везде полный код и точные команды. ✓

**Type consistency:**
- `BannerData(title: String, notificationId: Long?)` — одинаково в Task 1, 4. ✓
- `BannerController.show/dismiss/current/bottomBarHeight/setBottomBarHeight` — согласованы между Task 1, 2, 4, 5. ✓
- `AnnouncementInAppBanner(title, onAction, onDismiss, modifier)` — одинаковая сигнатура в Task 3 и вызов в Task 4. ✓
- `NotificationResponse.id: Long`, `.title: String` — подтверждено по модели; используется в Task 4. ✓
- `NotificationTapBus.emit(Long)` — существующая сигнатура; потребитель в MainShellScreen не тронут. ✓

**Известное ограничение (приемлемо):** если баннер показан на экране, открытом поверх `MainShellScreen` на КОРНЕВОМ Navigator (вне shell), тап «Посмотреть» через `NotificationTapBus` сработает только когда shell снова окажется в композиции (не делает авто-pop). Основное требование — видимость баннера + свайп — выполняется на всех экранах.

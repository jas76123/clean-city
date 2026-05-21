# Day 13 — Mobile полировка + release-сборка — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Привести мобильное приложение «Чистый Город» к release-готовности: единые состояния списков, единообразные иконки категорий, свой launcher-icon, R8-минификация и подписанный release-APK.

**Architecture:** Полировка в `composeApp` (Compose Multiplatform commonMain) — выносим дублированные UI-состояния в общие composable, добавляем компонент иконки категории. Release-часть — Android-конфигурация в `composeApp/build.gradle.kts` + ресурсы в `androidMain`. Legal-часть — обработка ошибок в `LegalWebView` и ссылки в `AboutScreen`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager (навигация), Koin (DI), Ktor client, kotlinx-serialization, Yandex MapKit, Android Gradle Plugin (R8).

**Соглашение по коммитам:** каждый коммит заканчивается trailer-строкой:
```
Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
```
В примерах ниже она опущена для краткости — добавляй её в каждый коммит.

**Базовая ветка:** репозиторий ведётся на `main` (солопроект, история — коммиты прямо в `main`). Работаем в `main`, как и предыдущие дни.

**Спека:** `docs/superpowers/specs/2026-05-21-day13-polish-release-design.md`

---

## File Structure

**Создаются:**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/ScreenState.kt` — общие `LoadingState`, `ErrorState`, `EmptyState`.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/util/ListErrorMessage.kt` — чистая функция `listErrorMessage(Throwable): String`.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/util/ListErrorMessageTest.kt` — тесты функции.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/CategoryIcon.kt` — иконка категории в круглом контейнере.
- `composeApp/src/androidMain/res/drawable/ic_launcher_logo.xml` — копия логотипа как Android vector drawable.
- `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml` — foreground adaptive-иконки (inset вокруг логотипа).
- `composeApp/src/androidMain/res/values/colors.xml` — цвета фона иконки и сплэша.
- `composeApp/src/androidMain/res/values/themes.xml` — тема приложения с `windowBackground`.
- `composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml` и `ic_launcher_round.xml` — adaptive-иконки.
- `composeApp/proguard-rules.pro` — правила R8.
- `keystore.properties` — пути и пароли keystore (НЕ коммитится, в `.gitignore`).

**Перемещается:**
- `CategoryEmoji.kt` из `ui/feature/map/components/` в `ui/components/` (общий компонент, не привязан к карте).

**Модифицируются:**
- `composeApp/src/commonMain/.../ui/feature/feed/FeedScreen.kt`, `.../feed/FeedScreenModel.kt`
- `composeApp/src/commonMain/.../ui/feature/notifications/NotificationsScreen.kt`, `.../NotificationsScreenModel.kt`
- `composeApp/src/commonMain/.../ui/feature/mycomplaints/MyComplaintsScreen.kt`, `.../MyComplaintsScreenModel.kt`
- `composeApp/src/commonMain/.../ui/feature/feed/components/ComplaintCard.kt`
- `composeApp/src/commonMain/.../ui/feature/detail/ComplaintDetailScreen.kt`
- `composeApp/src/commonMain/.../ui/feature/create/CreateComplaintScreen.kt`
- `composeApp/src/commonMain/.../ui/feature/map/components/CategorySheet.kt`, `CategoryFilterChips.kt` (только import-строки после переноса `CategoryEmoji.kt`)
- `composeApp/src/commonMain/.../ui/feature/profile/AboutScreen.kt`
- `composeApp/src/androidMain/.../ui/feature/auth/LegalWebView.android.kt`
- `composeApp/src/androidMain/AndroidManifest.xml`
- `composeApp/build.gradle.kts`
- `.gitignore`

---

## Task 1: Общие state-компоненты

Выносим продублированные `LoadingState`/`ErrorState`/`EmptyState` из трёх экранов в один файл. Компоненты не привязаны к размеру — сайзинг задаёт вызывающий код через `modifier`. Это чистый UI без бизнес-логики — unit-тестов нет (UI-тесты — backlog проекта).

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/ScreenState.kt`

- [ ] **Step 1: Создать файл `ScreenState.kt`**

```kotlin
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
```

- [ ] **Step 2: Скомпилировать**

Run: `./gradlew composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/ScreenState.kt
git commit -m "feat(ui): общие state-компоненты LoadingState/ErrorState/EmptyState"
```

---

## Task 2: Функция `listErrorMessage`

Чистая функция, превращающая `Throwable` в человекочитаемый текст для `ErrorState` на списках. Тестируемая — пишем по TDD.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/util/ListErrorMessage.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/util/ListErrorMessageTest.kt`

- [ ] **Step 1: Написать падающий тест**

```kotlin
package com.example.cleancity.ui.util

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListErrorMessageTest {

    @Test fun `network error gives connection message`() {
        val msg = listErrorMessage(IOException("no route to host"))
        assertEquals("Нет соединения. Проверьте интернет и попробуйте позже.", msg)
    }

    @Test fun `server 5xx gives server-unavailable message`() {
        val err = ApiException(ApiError("INTERNAL", "boom"), httpStatus = 503)
        assertEquals("Сервер временно недоступен. Попробуйте позже.", listErrorMessage(err))
    }

    @Test fun `client 4xx falls back to generic message`() {
        val err = ApiException(ApiError("BAD_REQUEST", "bad"), httpStatus = 400)
        assertEquals("Что-то пошло не так. Попробуйте ещё раз.", listErrorMessage(err))
    }

    @Test fun `unknown throwable falls back to generic message`() {
        assertEquals(
            "Что-то пошло не так. Попробуйте ещё раз.",
            listErrorMessage(RuntimeException("???")),
        )
    }

    @Test fun `message never leaks raw exception text`() {
        val msg = listErrorMessage(RuntimeException("kotlin.NullPointerException at line 42"))
        assertTrue("line 42" !in msg)
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.util.ListErrorMessageTest"`
Expected: FAIL — `unresolved reference: listErrorMessage`.

- [ ] **Step 3: Реализовать функцию**

`HttpRequestTimeoutException` в Ktor наследует `IOException`, поэтому таймауты попадают в ветку сети — отдельная ветка не нужна.

```kotlin
package com.example.cleancity.ui.util

import com.example.cleancity.data.network.ApiException
import kotlinx.io.IOException

private const val NETWORK = "Нет соединения. Проверьте интернет и попробуйте позже."
private const val SERVER = "Сервер временно недоступен. Попробуйте позже."
private const val GENERIC = "Что-то пошло не так. Попробуйте ещё раз."

/**
 * Человекочитаемый текст ошибки для ErrorState на экранах-списках.
 * Никогда не показывает сырой текст исключения пользователю.
 */
fun listErrorMessage(t: Throwable): String = when {
    t is IOException -> NETWORK
    t is ApiException && t.httpStatus in 500..599 -> SERVER
    else -> GENERIC
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.util.ListErrorMessageTest"`
Expected: PASS, 5 тестов зелёные.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/util/ListErrorMessage.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/util/ListErrorMessageTest.kt
git commit -m "feat(ui): listErrorMessage — текст ошибки для списков + тесты"
```

---

## Task 3: Миграция трёх экранов на общие компоненты

Заменяем приватные `LoadingState`/`ErrorState`/`EmptyState`/`CenteredSpinner`/`EmptyFeedRow` на общие компоненты из Task 1. Экраны-модели начинают строить текст ошибки через `listErrorMessage`.

Неиспользуемые импорты после удаления приватных функций компилятор пометит warning'ами (не ошибками) — убери их.

**Files:**
- Modify: `composeApp/src/commonMain/.../ui/feature/feed/FeedScreen.kt`, `FeedScreenModel.kt`
- Modify: `composeApp/src/commonMain/.../ui/feature/notifications/NotificationsScreen.kt`, `NotificationsScreenModel.kt`
- Modify: `composeApp/src/commonMain/.../ui/feature/mycomplaints/MyComplaintsScreen.kt`, `MyComplaintsScreenModel.kt`

- [ ] **Step 1: `FeedScreenModel.kt` — текст ошибки через `listErrorMessage`**

Найди (строка ~86):
```kotlin
            } catch (e: Throwable) {
                _state.value = FeedState.Error(e.message ?: "Не удалось загрузить ленту")
```
Замени на:
```kotlin
            } catch (e: Throwable) {
                _state.value = FeedState.Error(listErrorMessage(e))
```
Добавь импорт: `import com.example.cleancity.ui.util.listErrorMessage`.

- [ ] **Step 2: `NotificationsScreenModel.kt` — текст ошибки через `listErrorMessage`**

Найди (строка ~55):
```kotlin
                        _state.value = NotificationsState.Error(
                            e.message ?: "Не удалось загрузить уведомления"
                        )
```
Замени на:
```kotlin
                        _state.value = NotificationsState.Error(listErrorMessage(e))
```
Добавь импорт: `import com.example.cleancity.ui.util.listErrorMessage`.

- [ ] **Step 3: `MyComplaintsScreenModel.kt` — текст ошибки через `listErrorMessage`**

Найди (строка ~53):
```kotlin
                    _state.value = MyComplaintsState.Error(
                        e.message ?: "Не удалось загрузить ваши жалобы"
                    )
```
Замени на:
```kotlin
                    _state.value = MyComplaintsState.Error(listErrorMessage(e))
```
Добавь импорт: `import com.example.cleancity.ui.util.listErrorMessage`.

- [ ] **Step 4: `FeedScreen.kt` — использовать общие компоненты**

В блоке `when` Content() замени ветки Initial/Loading и Error:
```kotlin
            when (val s = state) {
                FeedState.Initial, FeedState.Loading -> LoadingState(Modifier.fillMaxSize())
                is FeedState.Error -> ErrorState(
                    message = s.message,
                    onRetry = { model.loadInitial() },
                    modifier = Modifier.fillMaxSize(),
                )
                is FeedState.Loaded -> FeedLoadedContent(
```

В `FeedLoadedContent`, ветка пустого списка — замени `EmptyFeedRow(...)` на:
```kotlin
            if (loaded.complaints.isEmpty()) {
                item {
                    val title = when {
                        loaded.mode == FeedMode.MINE && loaded.isGuest -> "Войдите в аккаунт"
                        loaded.mode == FeedMode.MINE -> "У вас пока нет жалоб"
                        else -> "Пока нет жалоб поблизости"
                    }
                    val subtitle = when {
                        loaded.mode == FeedMode.MINE && loaded.isGuest ->
                            "Чтобы видеть свои жалобы, войдите в аккаунт."
                        loaded.mode == FeedMode.MINE ->
                            "Создайте первую — нажмите ➕ на карте."
                        else -> "Станьте первым — нажмите ➕ на карте."
                    }
                    EmptyState(
                        emoji = "📋",
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    )
                }
            } else {
```

Удали приватные функции `LoadingState`, `ErrorState`, `EmptyFeedRow` (строки ~199-242). Функция `AnnouncementsSection` и остальное остаются.

Добавь импорты:
```kotlin
import com.example.cleancity.ui.components.EmptyState
import com.example.cleancity.ui.components.ErrorState
import com.example.cleancity.ui.components.LoadingState
```
Убери ставшие неиспользуемыми импорты (компилятор подскажет): `androidx.compose.material3.Button`, `androidx.compose.material3.OutlinedButton`, `androidx.compose.ui.text.style.TextAlign` — проверь, что они больше нигде в файле не нужны, прежде чем удалять.

- [ ] **Step 5: `NotificationsScreen.kt` — использовать общие компоненты**

В блоке `when` замени три ветки:
```kotlin
                when (val s = state) {
                    NotificationsState.Initial, NotificationsState.Loading ->
                        LoadingState(Modifier.fillMaxSize())
                    NotificationsState.Empty ->
                        EmptyState(
                            emoji = "🔔",
                            title = "Пока нет уведомлений",
                            subtitle = "Здесь появятся ответы администрации и объявления.",
                            modifier = Modifier.fillMaxSize(),
                        )
                    is NotificationsState.Error ->
                        ErrorState(s.message, onRetry = { model.load() }, modifier = Modifier.fillMaxSize())
                    is NotificationsState.Loaded ->
                        NotificationsList(
                            loaded = s,
                            onRefresh = model::refresh,
                            onItemClick = ::onClick,
                        )
                }
```

Удали приватные функции `CenteredSpinner`, `EmptyState`, `ErrorState` (строки ~160-190). Функция `NotificationsTopBar` и `NotificationsList` остаются.

Добавь импорты:
```kotlin
import com.example.cleancity.ui.components.EmptyState
import com.example.cleancity.ui.components.ErrorState
import com.example.cleancity.ui.components.LoadingState
```
Убери неиспользуемые импорты по подсказке компилятора.

- [ ] **Step 6: `MyComplaintsScreen.kt` — использовать общие компоненты**

В блоке `when` замени три ветки:
```kotlin
            when (val s = state) {
                MyComplaintsState.Initial, MyComplaintsState.Loading ->
                    LoadingState(Modifier.fillMaxSize())
                MyComplaintsState.Empty ->
                    EmptyState(
                        emoji = "📋",
                        title = "Вы пока не создавали жалоб",
                        subtitle = "Заметили проблему — нажмите ➕ на карте.",
                        modifier = Modifier.fillMaxSize(),
                    )
                is MyComplaintsState.Error ->
                    ErrorState(s.message, onRetry = { model.load() }, modifier = Modifier.fillMaxSize())
                is MyComplaintsState.Loaded -> LoadedList(
                    loaded = s,
                    onRefresh = model::refresh,
                    onLoadMore = model::loadNextPage,
                    onComplaintClick = { id -> navigator.push(ComplaintDetailScreen(id)) },
                )
            }
```

Удали приватные функции `CenteredSpinner`, `EmptyState`, `ErrorState` (строки ~153-183). `TopBar` и `LoadedList` остаются.

Добавь импорты:
```kotlin
import com.example.cleancity.ui.components.EmptyState
import com.example.cleancity.ui.components.ErrorState
import com.example.cleancity.ui.components.LoadingState
```
Убери неиспользуемые импорты по подсказке компилятора.

- [ ] **Step 7: Прогнать весь unit-suite**

Run: `./gradlew composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные (новые из Task 2 + существующие).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/feed \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints
git commit -m "refactor(ui): три экрана-списка на общие state-компоненты"
```

---

## Task 4: Иконки категорий — аудит эмодзи + `CategoryIcon`

Чиним слабый эмодзи у `OTHER`, переносим `CategoryEmoji.kt` в общий пакет `ui/components`, добавляем `CategoryIcon` — эмодзи в едином круглом контейнере — и переводим на него точки, где эмодзи стоит отдельной иконкой (карточка жалобы, детали, создание). Inline-подписи вида `"эмодзи Название"` в `CategorySheet`/`CategoryFilterChips` остаются текстом — там эмодзи и подпись уже консистентны как единая строка.

**Files:**
- Move: `ui/feature/map/components/CategoryEmoji.kt` → `ui/components/CategoryEmoji.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/CategoryIcon.kt`
- Modify: `ComplaintCard.kt`, `ComplaintDetailScreen.kt`, `CreateComplaintScreen.kt`, `CategorySheet.kt`, `CategoryFilterChips.kt`

- [ ] **Step 1: Перенести `CategoryEmoji.kt` в `ui/components` и починить `OTHER`**

```bash
git mv composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategoryEmoji.kt \
       composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/CategoryEmoji.kt
```
В перенесённом файле поменяй первую строку пакета:
```kotlin
package com.example.cleancity.ui.components
```
и почини эмодзи `OTHER` — замени строку:
```kotlin
    ProblemCategory.OTHER -> "…"
```
на:
```kotlin
    ProblemCategory.OTHER -> "📌"
```

- [ ] **Step 2: Обновить импорты `emoji()` в 5 файлах**

В каждом из файлов ниже найди импорт
`import com.example.cleancity.ui.feature.map.components.emoji`
и замени на
`import com.example.cleancity.ui.components.emoji`:
- `ui/feature/feed/components/ComplaintCard.kt`
- `ui/feature/detail/ComplaintDetailScreen.kt`
- `ui/feature/map/components/CategorySheet.kt`
- `ui/feature/map/components/CategoryFilterChips.kt`
- `ui/feature/create/CreateComplaintScreen.kt`

Если в каком-то файле импорт был неявным (тот же пакет `map.components`) — добавь явный `import com.example.cleancity.ui.components.emoji`. После шага скомпилируй: `./gradlew composeApp:compileDebugKotlinAndroid` → `BUILD SUCCESSFUL`.

- [ ] **Step 3: Создать `CategoryIcon.kt`**

```kotlin
package com.example.cleancity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.ui.theme.Green50

/** Эмодзи категории в едином круглом контейнере. Размер эмодзи = половина диаметра. */
@Composable
fun CategoryIcon(
    category: ProblemCategory,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(Green50),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = category.emoji(),
            style = TextStyle(fontSize = (size.value * 0.5f).sp),
        )
    }
}
```

- [ ] **Step 4: `ComplaintCard.kt` — отдельную эмодзи-иконку через `CategoryIcon`**

Открой файл, найди строку ~94 (`text = complaint.category.emoji()`). Это `Text` с эмодзи в роли иконки. Замени весь этот `Text(...)` композабл на:
```kotlin
                CategoryIcon(category = complaint.category, size = 40.dp)
```
Если эмодзи там обёрнут в `Box`/контейнер с фоном — убери внешний контейнер, `CategoryIcon` уже рисует круг. Строку ~111 (`"${complaint.category.emoji()} ${complaint.category.localizedLabel}"`) НЕ трогай — это текстовая подпись.
Добавь импорт `import com.example.cleancity.ui.components.CategoryIcon`.

- [ ] **Step 5: `ComplaintDetailScreen.kt` — отдельную эмодзи-иконку через `CategoryIcon`**

Найди строку ~256 (`text = complaint.category.emoji()`). Замени этот `Text(...)` (и его контейнер-кружок, если есть) на:
```kotlin
                        CategoryIcon(category = complaint.category, size = 44.dp)
```
Строку ~289 (`"${c.category.emoji()} ${c.category.localizedLabel}"`) НЕ трогай.
Добавь импорт `import com.example.cleancity.ui.components.CategoryIcon`.

- [ ] **Step 6: `CreateComplaintScreen.kt` — две отдельные эмодзи-иконки через `CategoryIcon`**

Найди строку ~506: `Text(item.category.emoji(), fontSize = 22.sp)` → замени на:
```kotlin
                CategoryIcon(category = item.category, size = 40.dp)
```
Найди строку ~640: `Text(category.emoji(), fontSize = 22.sp)` → замени на:
```kotlin
            CategoryIcon(category = category, size = 40.dp)
```
Добавь импорт `import com.example.cleancity.ui.components.CategoryIcon`.

- [ ] **Step 7: Скомпилировать и прогнать тесты**

Run: `./gradlew composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные.

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui
git commit -m "feat(ui): CategoryIcon — единая иконка категории, починен эмодзи OTHER"
```

---

## Task 5: Launcher-иконка приложения + устранение белой вспышки сплэша

Сейчас у приложения нет своей иконки (дефолтная Android-иконка) и при холодном старте мелькает белый экран. `minSdk = 26` — adaptive-иконки поддерживаются на всех устройствах, legacy-PNG не нужны.

**Files:**
- Create: `composeApp/src/androidMain/res/drawable/ic_launcher_logo.xml`
- Create: `composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml`
- Create: `composeApp/src/androidMain/res/values/colors.xml`
- Create: `composeApp/src/androidMain/res/values/themes.xml`
- Create: `composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml`
- Create: `composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Скопировать логотип в Android-ресурсы**

`composeResources/drawable/app_logo.xml` уже в формате Android `<vector>`, но composeResources недоступны из `R.drawable`. Копируем в `androidMain/res`:
```bash
mkdir -p composeApp/src/androidMain/res/drawable composeApp/src/androidMain/res/mipmap-anydpi-v26
cp composeApp/src/commonMain/composeResources/drawable/app_logo.xml \
   composeApp/src/androidMain/res/drawable/ic_launcher_logo.xml
```

- [ ] **Step 2: Создать foreground adaptive-иконки**

`composeApp/src/androidMain/res/drawable/ic_launcher_foreground.xml` — логотип с отступом под safe-zone adaptive-иконки (внешние ~16% маскируются системой):
```xml
<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/ic_launcher_logo"
    android:inset="18%" />
```

- [ ] **Step 3: Создать `colors.xml`**

`composeApp/src/androidMain/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Green900 — фон adaptive-иконки и сплэш-окна -->
    <color name="ic_launcher_background">#0D2B1A</color>
    <color name="splash_background">#0D2B1A</color>
</resources>
```

- [ ] **Step 4: Создать adaptive-иконки**

`composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```
`composeApp/src/androidMain/res/mipmap-anydpi-v26/ic_launcher_round.xml` — идентичное содержимое (тот же XML).

- [ ] **Step 5: Создать тему со сплэш-фоном**

`composeApp/src/androidMain/res/values/themes.xml` — тема с тёмным `windowBackground`, чтобы при холодном старте вместо белого экрана был фон в цвет сплэша:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.CleanCity" parent="@android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@color/splash_background</item>
        <item name="android:statusBarColor">@color/splash_background</item>
        <item name="android:windowLightStatusBar">false</item>
    </style>
</resources>
```

- [ ] **Step 6: Подключить иконку и тему в манифесте**

В `composeApp/src/androidMain/AndroidManifest.xml`:

В теге `<application>` добавь атрибуты `android:icon` и `android:roundIcon` и поменяй `android:theme`:
```xml
    <application
        android:name=".CleanCityApplication"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.CleanCity">
```
В теге `<activity android:name=".MainActivity" ...>` поменяй `android:theme` с
`@android:style/Theme.Material.Light.NoActionBar` на `@style/Theme.CleanCity`.

- [ ] **Step 7: Собрать debug-APK и проверить иконку**

Run: `./gradlew composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`.
Затем установи на устройство/эмулятор и убедись: на рабочем столе — иконка-логотип «Чистый Город» (не дефолтная Android), при запуске нет белой вспышки (тёмный фон до отрисовки Compose).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/androidMain/res composeApp/src/androidMain/AndroidManifest.xml
git commit -m "feat(android): adaptive launcher-иконка + тёмный сплэш-фон"
```

---

## Task 6: ProGuard / R8 + подписанный release-keystore

Включаем R8 без обфускации (`-dontobfuscate`) и настраиваем подпись release-сборки. `keystore.jks` хранится вне репозитория, пароли — в `keystore.properties` (в `.gitignore`).

**Files:**
- Create: `composeApp/proguard-rules.pro`
- Create: `keystore.properties` (НЕ коммитится)
- Modify: `composeApp/build.gradle.kts`
- Modify: `.gitignore`

- [ ] **Step 1: Создать `proguard-rules.pro`**

`composeApp/proguard-rules.pro`:
```proguard
# Day 13: R8 без обфускации — читаемые стек-трейсы, безопасная рефлексия.
-dontobfuscate

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.cleancity.**$$serializer { *; }
-keep class com.example.cleancity.shared.** { *; }

# --- Ktor client ---
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**

# --- Koin ---
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- Yandex MapKit ---
-keep class com.yandex.** { *; }
-dontwarn com.yandex.**

# --- Voyager ---
-keep class cafe.adriel.voyager.** { *; }
```

- [ ] **Step 2: Сгенерировать release-keystore**

Выполни локально (пароли придумай и сохрани — они понадобятся в Step 4 и для всех будущих обновлений приложения):
```bash
mkdir -p ~/keys/cleancity
keytool -genkeypair -v \
  -keystore ~/keys/cleancity/keystore.jks \
  -alias cleancity \
  -keyalg RSA -keysize 2048 -validity 10000
```
keytool спросит пароль хранилища, имя/организацию и пароль ключа. Запиши оба пароля.

- [ ] **Step 3: Добавить `keystore.properties` в `.gitignore`**

В конец `.gitignore` добавь строку:
```
keystore.properties
```

- [ ] **Step 4: Создать `keystore.properties`**

Файл `keystore.properties` в корне репозитория (рядом с `local.properties`). НЕ коммитится:
```properties
storeFile=/Users/jasminagababyan/keys/cleancity/keystore.jks
storePassword=ПАРОЛЬ_ХРАНИЛИЩА_ИЗ_STEP_2
keyAlias=cleancity
keyPassword=ПАРОЛЬ_КЛЮЧА_ИЗ_STEP_2
```

- [ ] **Step 5: Настроить подпись и R8 в `build.gradle.kts`**

В `composeApp/build.gradle.kts`, после блока чтения `secrets` (после строки с `apiBaseUrl`), добавь чтение keystore:
```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
```

Внутри блока `android { ... }`, перед блоком `buildTypes`, добавь `signingConfigs`:
```kotlin
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
```

Замени блок `release { ... }` в `buildTypes` на:
```kotlin
        release {
            buildConfigField("boolean", "IS_DEBUG", "false")
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
        }
```

- [ ] **Step 6: Собрать подписанный release-APK**

Run: `./gradlew composeApp:assembleRelease`
Expected: `BUILD SUCCESSFUL`. APK появится в `composeApp/build/outputs/apk/release/composeApp-release.apk`.

Если сборка падает на R8 с `Missing class ...` — добавь соответствующий `-dontwarn` в `proguard-rules.pro` и повтори.

- [ ] **Step 7: Проверить подпись APK**

Run: `~/Library/Android/sdk/build-tools/35.0.0/apksigner verify --verbose composeApp/build/outputs/apk/release/composeApp-release.apk`
Expected: `Verified using v2 scheme: true` (или v3), без ошибок.
Если каталога `35.0.0` нет — подставь имеющуюся версию из `~/Library/Android/sdk/build-tools/`.

- [ ] **Step 8: Резервная копия keystore**

Скопируй `~/keys/cleancity/keystore.jks` и `keystore.properties` на USB-флешку. Без этих файлов обновить приложение в RuStore будет невозможно.

- [ ] **Step 9: Commit**

```bash
git add composeApp/proguard-rules.pro composeApp/build.gradle.kts .gitignore
git status --short   # убедись, что keystore.properties НЕ в индексе
git commit -m "build(android): R8 без обфускации + подпись release-keystore"
```

---

## Task 7: Legal — устойчивость WebView + ссылки в AboutScreen + атрибуция Yandex

Делаем legal-документы доступными из `AboutScreen` (не только при регистрации), показываем понятную ошибку при недоступном бэкенде и сверяем требования Yandex MapKit.

**Files:**
- Modify: `composeApp/src/androidMain/.../ui/feature/auth/LegalWebView.android.kt`
- Modify: `composeApp/src/commonMain/.../ui/feature/profile/AboutScreen.kt`

- [ ] **Step 1: `LegalWebView` — понятная ошибка вместо белого экрана**

В `LegalWebView.android.kt`, внутри `object : WebViewClient()`, добавь обработчик `onReceivedError` рядом с `shouldOverrideUrlLoading`:
```kotlin
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        view?.loadData(
                            """
                            <html><body style="font-family:sans-serif;padding:32px;
                            color:#4A6055;text-align:center">
                            <h3>Документ временно недоступен</h3>
                            <p>Не удалось загрузить страницу. Проверьте интернет-соединение
                            и попробуйте позже.</p>
                            </body></html>
                            """.trimIndent(),
                            "text/html; charset=utf-8",
                            "UTF-8",
                        )
                    }
                }
```

- [ ] **Step 2: Скомпилировать**

Run: `./gradlew composeApp:compileDebugKotlinAndroid`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: `AboutScreen` — ссылки на legal-документы и атрибуция Yandex**

В `AboutScreen.kt`, внутри внутреннего `Column` после последнего `InfoRow` (`InfoRow(label = "Поддержка", ...)`), добавь блок:
```kotlin
                Spacer(Modifier.height(24.dp))
                LegalLinkRow(
                    text = "Политика обработки данных",
                    onClick = { navigator.push(LegalScreen(LegalKind.Privacy)) },
                )
                LegalLinkRow(
                    text = "Условия использования",
                    onClick = { navigator.push(LegalScreen(LegalKind.Terms)) },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Карты и геокодирование © Яндекс. Использование сервиса " +
                        "регулируется условиями Яндекс Карт.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                    textAlign = TextAlign.Center,
                )
```

Добавь приватный composable `LegalLinkRow` после `InfoRow`:
```kotlin
@Composable
private fun LegalLinkRow(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = Green400,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        textAlign = TextAlign.Center,
    )
}
```

Добавь импорты:
```kotlin
import androidx.compose.foundation.clickable
import com.example.cleancity.ui.feature.auth.LegalKind
import com.example.cleancity.ui.feature.auth.LegalScreen
```
(`Green400`, `Gray500`, `FontWeight`, `Modifier`, `Spacer`, `height`, `fillMaxWidth`, `padding` уже импортированы в файле.)

- [ ] **Step 4: Сверить требования Yandex MapKit**

Прочитай актуальные условия: WebFetch `https://yandex.ru/legal/mapkit_termsofuse/` с вопросом «какие требования к атрибуции и упоминанию условий Яндекса предъявляются к приложению, использующему MapKit».
Затем проверь в коде, что копирайт/логотип Яндекса на карте не скрывается:
```bash
grep -rn -i "logo\|copyright" composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map composeApp/src/androidMain
```
Expected: нет вызовов, скрывающих логотип/копирайт MapKit (например `.logo` с `isVisible = false` или `setAlignment` за пределы экрана). Если найдены — убери их (атрибуция Яндекса обязательна). Если WebFetch выявит дополнительное обязательное требование (например прямую ссылку на условия) — добавь его в текст атрибуции из Step 3.

- [ ] **Step 5: Прогнать тесты и собрать debug**

Run: `./gradlew composeApp:testDebugUnitTest composeApp:assembleDebug`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/auth/LegalWebView.android.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/AboutScreen.kt
git commit -m "feat(legal): ссылки на политику/условия в AboutScreen, fallback WebView, атрибуция Yandex"
```

---

## Task 8: Финальная верификация

Сквозная проверка release-готовности на реальном устройстве.

**Files:** нет изменений кода — только проверка.

- [ ] **Step 1: Полный unit-suite**

Run: `./gradlew composeApp:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, все тесты зелёные.

- [ ] **Step 2: Сборка подписанного release-APK**

Run: `./gradlew composeApp:assembleRelease`
Expected: `BUILD SUCCESSFUL`, APK в `composeApp/build/outputs/apk/release/composeApp-release.apk`.

- [ ] **Step 3: Установка на Samsung A33 5G**

Подключи телефон по USB, запусти backend локально, выполни:
```bash
~/Library/Android/sdk/platform-tools/adb reverse tcp:8081 tcp:8081
~/Library/Android/sdk/platform-tools/adb install -r composeApp/build/outputs/apk/release/composeApp-release.apk
```
Expected: `Success`, без предупреждений о debug-сборке.

- [ ] **Step 4: Ручной happy-path smoke**

На устройстве пройди сценарий и убедись:
- Иконка приложения на рабочем столе — логотип «Чистый Город», при старте нет белой вспышки.
- Регистрация → подтверждение email → вход.
- Лента, «Мои жалобы», «Уведомления»: пустые/loading/error состояния выглядят единообразно (эмодзи + заголовок + подзаголовок).
- Создание жалобы: иконки категорий — в единых круглых контейнерах.
- Профиль → «О приложении»: видны ссылки «Политика обработки данных» и «Условия использования», открываются; есть строка атрибуции Яндекс.
- Отключи интернет, открой legal-документ — показывается сообщение «Документ временно недоступен», не белый экран.

- [ ] **Step 5: Финальный коммит-отметка**

Обнови чек-лист Day 13 в `docs/PLAN.md` (отметь выполненные пункты галочками `[x]`: empty/loading/error states, иконки категорий, app icon + splash, ProGuard/R8, release-keystore, release-сборка APK; пункты про RuStore оставь незакрытыми — они в отдельном чеклисте). Добавь строку «**День 13 закрыт <дата>**» с кратким итогом, по образцу записей Day 8/Day 9/Day 12.
```bash
git add docs/PLAN.md
git commit -m "docs: Day 13 закрыт — полировка + подписанный release-APK"
```

---

## Follow-ups (вне этого плана)

- **Контакт поддержки.** `AboutScreen` указывает `a.ja5m@yandex.ru`, legal-документы — `info@cleancity.ru`/`support@…`/`privacy@…`. Нужно решение, какой адрес канонический, и привести к единому — правка 1 строки, но требует выбора пользователя. Не блокирует Day 13.
- **RuStore-подача** — отдельный ops-чеклист после готовности веб-админки.
- **QR-код на APK** — генерируется после того, как подписанный APK где-то захостен (Yandex Object Storage или статика бэкенда); сохраняется в `docs/marketing/`. Зависит от выбора хостинга — отдельный шаг.
- **FCM SDK + системные push** — Day 14-буфер (уже записано в `PLAN.md`).
- **Skeleton-загрузка, `shrinkResources`** — backlog.

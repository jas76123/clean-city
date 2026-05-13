# Day 8 Mobile setup + auth-экраны — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** На Android-эмуляторе работает полный auth-флоу (splash → register → email-link verify → login → main; forgot/reset password; гостевой режим), поверх боевого backend `/auth/*` + новый `GET /users/me`.

**Architecture:** KMP Compose Multiplatform (Android-only на Week 2). Чистая зачистка common-кода, дизайн-система 1:1 с `mobile-mockup-v3.html`. Ktor Client + Koin DI + EncryptedSharedPreferences. Voyager навигация реактивна к `AuthRepository.state: StateFlow<AuthState>`. Deep-link `cleancity://verify` и `cleancity://reset` через `MainActivity.onNewIntent` + `DeepLinkBus` (StateFlow с cold-start replay).

**Tech Stack:** Kotlin 2.0.21, Compose Multiplatform 1.7.3, Voyager 1.1.0-beta03, Ktor Client 3.0.3, Koin 3.5.6, AndroidX Security 1.1.0-alpha06. Backend — Ktor Server 3.0.3 (уже стоит). Шрифты Unbounded + Golos Text bundled через Compose Resources.

**Spec:** `docs/superpowers/specs/2026-05-13-week2-day8-mobile-auth-design.md`

---

## Phase 0 — Зачистка фундамента

### Task 0.1: Создать ветку и проверить baseline

**Files:** — (git only)

- [ ] **Step 1: Создать ветку**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git checkout main && git pull --ff-only
git checkout -b day8-mobile-auth
```

- [ ] **Step 2: Проверить, что текущий main собирается**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL. Если падает — остановиться, починить main раньше, чем удалять старый код.

- [ ] **Step 3: Зафиксировать baseline-коммит (пустой)**

```bash
git commit --allow-empty -m "chore(day8): start Week 2 Day 8 — Mobile setup + auth"
```

---

### Task 0.2: Удалить старую common-логику composeApp

**Files:**
- Delete: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/InMemoryRepository.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/SampleData.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/example/cleancity/model/Models.kt` (вся папка `model/`)
- Delete: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/` (всё содержимое)
- Delete: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/navigation/MainTabScreen.kt`

- [ ] **Step 1: Удалить файлы**

```bash
rm -rf composeApp/src/commonMain/kotlin/com/example/cleancity/data
rm -rf composeApp/src/commonMain/kotlin/com/example/cleancity/model
rm -rf composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map
rm composeApp/src/commonMain/kotlin/com/example/cleancity/ui/navigation/MainTabScreen.kt
rmdir composeApp/src/commonMain/kotlin/com/example/cleancity/ui/navigation 2>/dev/null || true
```

- [ ] **Step 2: Заменить App.kt на пустую заглушку (билд должен оставаться зелёным)**

Перезаписать `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt`:

```kotlin
package com.example.cleancity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.cleancity.ui.theme.CleanCityTheme

@Composable
fun App() {
    CleanCityTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("CleanCity — Day 8 setup in progress")
        }
    }
}
```

- [ ] **Step 3: Проверить сборку**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(composeApp): wipe legacy common code (4-cat model, chats, events, in-memory)"
```

---

### Task 0.3: Перенести Yandex platform-actuals в legacy/

**Files:**
- Move: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/YandexMapView.android.kt` → `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/YandexMapView.android.kt`
- Move: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/MapSearchProvider.android.kt` → `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/MapSearchProvider.android.kt`

- [ ] **Step 1: Создать папку legacy и переместить файлы**

```bash
mkdir -p composeApp/src/androidMain/kotlin/com/example/cleancity/legacy
git mv composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/YandexMapView.android.kt \
       composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/YandexMapView.android.kt
git mv composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/MapSearchProvider.android.kt \
       composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/MapSearchProvider.android.kt
rmdir composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map 2>/dev/null || true
```

- [ ] **Step 2: Обновить package и закомментировать тело (нет common-аналогов сейчас)**

Замени `package com.example.cleancity.ui.map` → `package com.example.cleancity.legacy` в обоих файлах. Заверни весь содержательный код в `/* TODO Day 9 — обернуть в новый expect */ ... */` ИЛИ просто оставь `// File parked — Day 9 will rewire`. Цель: файлы есть в git-истории, но не ломают сборку.

Безопаснее всего — закомментировать всё кроме `package` и `import`-ов:

```kotlin
package com.example.cleancity.legacy

/*
 * Day 8: parked здесь до Day 9, когда будем переписывать MapScreen под backend.
 * Оригинал содержал @Composable expect-actual для Yandex Maps SDK.
 *
 * <тело файла как было>
 */
```

- [ ] **Step 3: Проверить сборку**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(composeApp): park Yandex platform actuals to legacy/ for Day 9 rewire"
```

---

### Task 0.4: Дропнуть iOS из composeApp/build.gradle.kts

**Files:**
- Modify: `composeApp/build.gradle.kts` — удалить iOS-блок
- Delete: `composeApp/src/iosMain/` (целиком)

- [ ] **Step 1: Удалить iosMain**

```bash
rm -rf composeApp/src/iosMain
```

- [ ] **Step 2: Удалить iOS-блок из gradle**

В `composeApp/build.gradle.kts` найти и удалить блок:

```kotlin
listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
        baseName = "ComposeApp"
        isStatic = true
    }
}
```

- [ ] **Step 3: Sync + проверка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL. iOS targets больше не упоминаются.

- [ ] **Step 4: Commit**

```bash
git add composeApp/build.gradle.kts composeApp/src/iosMain 2>/dev/null
git add -A
git commit -m "build(composeApp): drop iOS targets for Week 2 (Android-only focus)"
```

---

## Phase 1 — Backend: GET /users/me

### Task 1.1: Добавить публичный метод AuthService.getUserById

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`

- [ ] **Step 1: Открыть AuthService.kt, найти private fun UserRow.toResponse() (~line 381)**

Над `private fun UserRow.toResponse()` добавить публичный метод (вписать в class AuthService body):

```kotlin
suspend fun getUserById(userId: Long): UserResponse? = withContext(Dispatchers.IO) {
    transaction {
        UsersTable.select { UsersTable.id eq userId }
            .map { it.toUserRow().toResponse() }
            .firstOrNull()
    }
}
```

Если уже есть похожий метод (`findById`, `loadUser`) — используем его, новый не добавляем.

**Проверь имена**: `UsersTable`, `toUserRow` — точные имена в AuthService.kt могут отличаться. Открой файл и используй те же helpers, что и в `register()` / `login()`.

- [ ] **Step 2: Проверить компиляцию backend**

```bash
./gradlew :backend:compileKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit (промежуточный, без routes)**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt
git commit -m "feat(backend/auth): expose getUserById for /users/me endpoint"
```

---

### Task 1.2: Написать failing test для GET /users/me

**Files:**
- Create: `backend/src/test/kotlin/com/example/cleancity/users/UserRoutesTest.kt`

- [ ] **Step 1: Изучить существующий тестовый аппарат**

```bash
ls backend/src/test/kotlin/com/example/cleancity/auth/
cat backend/src/test/kotlin/com/example/cleancity/auth/AuthSecurityTest.kt | head -40
```

Используй тот же helper-builder для тестового Application (обычно `testApplication { ... }` + `installModule` или `helper`-функция типа `runAuthTestApp`). Узнай имя и переиспользуй.

- [ ] **Step 2: Создать UserRoutesTest.kt**

```kotlin
package com.example.cleancity.users

import com.example.cleancity.shared.models.UserResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class UserRoutesTest {

    @Test
    fun `GET users me without token returns 401`() = runAuthTestApp { client ->
        val resp = client.get("/users/me")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users me with invalid token returns 401`() = runAuthTestApp { client ->
        val resp = client.get("/users/me") {
            header(HttpHeaders.Authorization, "Bearer not.a.real.jwt")
        }
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }

    @Test
    fun `GET users me with valid token returns 200 and user`() = runAuthTestApp { client ->
        val tokens = registerAndVerify(client, email = "u1@cleancity.local", password = "Password123")
        val resp = client.get("/users/me") {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val user: UserResponse = resp.body()
        assertEquals("u1@cleancity.local", user.email)
        assertNotNull(user.role)
        assertEquals(true, user.emailVerified)
    }
}
```

`runAuthTestApp` и `registerAndVerify` — переиспользуй helpers из существующих тестов; если их нет в shared utils, скопируй паттерн из `AuthSecurityTest`.

- [ ] **Step 3: Запустить тест — ожидаем FAIL**

```bash
./gradlew :backend:test --tests "com.example.cleancity.users.UserRoutesTest"
```
Expected: FAIL — endpoint ещё не существует, ответы будут 404.

---

### Task 1.3: Создать UserRoutes.kt и подключить

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/users/UserRoutes.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/Application.kt` — добавить `userRoutes(authService)`

- [ ] **Step 1: Создать UserRoutes.kt**

```kotlin
package com.example.cleancity.users

import com.example.cleancity.auth.AuthService
import com.example.cleancity.auth.requireUserId
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.userRoutes(authService: AuthService) {
    route("/users") {
        authenticate("auth-jwt") {
            get("/me") {
                val userId = call.requireUserId()
                val user = authService.getUserById(userId)
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(HttpStatusCode.OK, user)
            }
        }
    }
}
```

- [ ] **Step 2: Подключить в Application.kt**

В `backend/src/main/kotlin/com/example/cleancity/Application.kt`:

Добавить import рядом с другими:
```kotlin
import com.example.cleancity.users.userRoutes
```

В `routing { }` блоке (после `authRoutes(authService, rateLimiter)`, ~line 170) добавить:
```kotlin
userRoutes(authService)
```

- [ ] **Step 3: Запустить тест — ожидаем PASS**

```bash
./gradlew :backend:test --tests "com.example.cleancity.users.UserRoutesTest"
```
Expected: PASS — все 3 кейса зелёные.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/users/UserRoutes.kt \
        backend/src/main/kotlin/com/example/cleancity/Application.kt \
        backend/src/test/kotlin/com/example/cleancity/users/UserRoutesTest.kt
git commit -m "feat(backend/users): GET /users/me — current user from JWT"
```

---

### Task 1.4: Обновить SPEC.md §4.7

**Files:**
- Modify: `docs/SPEC.md`

- [ ] **Step 1: Открыть SPEC.md, найти §4.7 «Прочее»**

В таблицу эндпоинтов §4.7 добавить строку (перед `/users/me/push-token`):

```
| `GET` | `/users/me` | Резидент+ | Текущий пользователь по JWT. 200: UserResponse. 401 если токен невалиден/истёк. |
```

- [ ] **Step 2: Обновить openapi.yaml (если он handwritten)**

Если в `docs/api/openapi.yaml` руками поддерживается дока — добавить путь `/users/me` по аналогии с другими GET-эндпоинтами. Если автогенеренный — пропустить.

- [ ] **Step 3: Commit**

```bash
git add docs/SPEC.md docs/api/openapi.yaml 2>/dev/null
git commit -m "docs(spec): document GET /users/me in §4.7"
```

---

## Phase 2 — Зависимости + BuildConfig + шрифты

### Task 2.1: Добавить новые версии и библиотеки в libs.versions.toml

**Files:**
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: В блок `[versions]` добавить (рядом с уже существующими)**

```toml
koin = "3.5.6"
androidx-security = "1.1.0-alpha06"
ktor-client-okhttp-android = "3.0.3"
```

Версию `ktor` (3.0.3) переиспользуем — она уже есть.

- [ ] **Step 2: В блок `[libraries]` добавить**

```toml
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "androidx-security" }
```

`ktor-serialization-kotlinx-json` уже есть в существующих библиотеках — переиспользуем.

- [ ] **Step 3: Sync gradle**

```bash
./gradlew :composeApp:dependencies > /tmp/deps.txt
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml
git commit -m "build: add Ktor Client, Koin, AndroidX Security to version catalog"
```

---

### Task 2.2: Обновить composeApp/build.gradle.kts (deps + BuildConfig)

**Files:**
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Добавить чтение API_BASE_URL в шапке файла рядом с yandexMapsApiKey**

```kotlin
val apiBaseUrl: String =
    secrets.getProperty("API_BASE_URL")
        ?: System.getenv("API_BASE_URL")
        ?: "http://10.0.2.2:8080"
```

- [ ] **Step 2: Добавить buildConfigField в `android { defaultConfig { ... } }`**

```kotlin
buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
buildConfigField("boolean", "IS_DEBUG", "true")
```

И в `android { buildTypes { ... } }` (если такой блок отсутствует — создать):

```kotlin
buildTypes {
    debug {
        buildConfigField("boolean", "IS_DEBUG", "true")
    }
    release {
        buildConfigField("boolean", "IS_DEBUG", "false")
        isMinifyEnabled = false  // Day 13 включит proguard
    }
}
```

Замени общий `buildConfigField("boolean", "IS_DEBUG", "true")` из defaultConfig (он перекрывается buildType-ами).

- [ ] **Step 3: Расширить sourceSets.commonMain.dependencies**

В блок `commonMain.dependencies { }`:

```kotlin
implementation(libs.ktor.client.core)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.client.auth)
implementation(libs.ktor.client.logging)
implementation(libs.ktor.serialization.kotlinx.json)
implementation(libs.koin.core)
implementation(libs.koin.compose)
```

- [ ] **Step 4: Расширить sourceSets.androidMain.dependencies**

```kotlin
implementation(libs.ktor.client.okhttp)
implementation(libs.koin.android)
implementation(libs.androidx.security.crypto)
```

- [ ] **Step 5: Добавить sourceSets.commonTest.dependencies**

После `commonMain` блока:

```kotlin
commonTest.dependencies {
    implementation(kotlin("test"))
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.ktor.client.mock)
}
```

Версию `kotlinx-coroutines-test` — добавить в libs.versions.toml если её нет: `kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }`.

- [ ] **Step 6: Sync + сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/build.gradle.kts gradle/libs.versions.toml
git commit -m "build(composeApp): wire Ktor Client / Koin / EncryptedSharedPreferences + BuildConfig.API_BASE_URL"
```

---

### Task 2.3: Положить .ttf-шрифты в composeResources

**Files:**
- Create: `composeApp/src/commonMain/composeResources/font/unbounded_regular.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/unbounded_semibold.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/unbounded_bold.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/golos_text_regular.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/golos_text_medium.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/golos_text_semibold.ttf`
- Create: `composeApp/src/commonMain/composeResources/font/OFL.txt`

- [ ] **Step 1: Скачать шрифты**

С google fonts (downloadable пакетами):
- https://fonts.google.com/specimen/Unbounded → Download family. Из ZIP'а взять `static/Unbounded-Regular.ttf`, `Unbounded-SemiBold.ttf`, `Unbounded-Bold.ttf`.
- https://fonts.google.com/specimen/Golos+Text → Download family. Взять `static/GolosText-Regular.ttf`, `GolosText-Medium.ttf`, `GolosText-SemiBold.ttf`.

```bash
mkdir -p composeApp/src/commonMain/composeResources/font
```

Скопировать и переименовать (lowercase + underscore — Compose Resources требование):
- `Unbounded-Regular.ttf` → `unbounded_regular.ttf`
- `Unbounded-SemiBold.ttf` → `unbounded_semibold.ttf`
- `Unbounded-Bold.ttf` → `unbounded_bold.ttf`
- `GolosText-Regular.ttf` → `golos_text_regular.ttf`
- `GolosText-Medium.ttf` → `golos_text_medium.ttf`
- `GolosText-SemiBold.ttf` → `golos_text_semibold.ttf`

- [ ] **Step 2: Положить OFL.txt (one license file для обоих шрифтов)**

Содержимое — стандартный OFL-1.1 текст (из ZIP'а любого шрифта, файл `OFL.txt`).

- [ ] **Step 3: Проверить Compose Resources регистрацию**

```bash
./gradlew :composeApp:generateComposeResClass
```
Expected: BUILD SUCCESSFUL. Сгенерируется `Res.font.unbounded_regular` etc — можно проверить в `composeApp/build/generated/compose/resourceGenerator/`.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/font/
git commit -m "feat(composeApp): bundle Unbounded + Golos Text fonts (OFL-1.1)"
```

---

## Phase 3 — Theme

### Task 3.1: Создать Shapes.kt + Dimens.kt

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Shapes.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Dimens.kt`

- [ ] **Step 1: Создать Shapes.kt**

```kotlin
package com.example.cleancity.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CleanCityShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
```

- [ ] **Step 2: Создать Dimens.kt**

```kotlin
package com.example.cleancity.ui.theme

import androidx.compose.ui.unit.dp

object Dimens {
    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 16.dp
    val spaceLg = 24.dp
    val spaceXl = 32.dp
    val spaceXxl = 40.dp
}
```

- [ ] **Step 3: Сборка**

```bash
./gradlew :composeApp:compileKotlinMetadata
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Shapes.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Dimens.kt
git commit -m "feat(theme): add Shapes (10/16/24/32 dp) and Dimens tokens"
```

---

### Task 3.2: Обновить Type.kt с Unbounded + Golos Text

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Type.kt`

- [ ] **Step 1: Перезаписать Type.kt**

```kotlin
package com.example.cleancity.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cleancity.composeapp.generated.resources.Res
import cleancity.composeapp.generated.resources.golos_text_medium
import cleancity.composeapp.generated.resources.golos_text_regular
import cleancity.composeapp.generated.resources.golos_text_semibold
import cleancity.composeapp.generated.resources.unbounded_bold
import cleancity.composeapp.generated.resources.unbounded_regular
import cleancity.composeapp.generated.resources.unbounded_semibold
import org.jetbrains.compose.resources.Font

@Composable
fun fontDisplay(): FontFamily = FontFamily(
    Font(Res.font.unbounded_regular, FontWeight.Normal),
    Font(Res.font.unbounded_semibold, FontWeight.SemiBold),
    Font(Res.font.unbounded_bold, FontWeight.Bold),
)

@Composable
fun fontBody(): FontFamily = FontFamily(
    Font(Res.font.golos_text_regular, FontWeight.Normal),
    Font(Res.font.golos_text_medium, FontWeight.Medium),
    Font(Res.font.golos_text_semibold, FontWeight.SemiBold),
)

@Composable
fun cleanCityTypography(): Typography {
    val display = fontDisplay()
    val body = fontBody()
    return Typography(
        displayMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 31.sp),
        headlineSmall = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        titleLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.65.sp),
        labelMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
        labelSmall = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.55.sp),
    )
}
```

`cleancity.composeapp.generated.resources` — package, который Compose Resources генерирует автоматически. Если он не находится — проверить namespace в `composeApp/build.gradle.kts` (`android.namespace = "com.example.cleancity"` → `compose.resources { packageOfResClass = "..." }`).

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Type.kt
git commit -m "feat(theme): wire Unbounded + Golos Text via Compose Resources"
```

---

### Task 3.3: Обновить Theme.kt — colorScheme + typography + shapes

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Theme.kt`

- [ ] **Step 1: Перезаписать Theme.kt**

```kotlin
package com.example.cleancity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun CleanCityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green700,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = Accent,
            onSecondary = Green900,
            background = Gray50,
            onBackground = Gray900,
            surface = androidx.compose.ui.graphics.Color.White,
            onSurface = Gray900,
            surfaceVariant = Gray100,
            onSurfaceVariant = Gray600,
            outline = Gray300,
            outlineVariant = Gray200,
            error = Red,
            onError = androidx.compose.ui.graphics.Color.White,
        ),
        typography = cleanCityTypography(),
        shapes = CleanCityShapes,
        content = content,
    )
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL. Запуск приложения на эмуляторе — отображается placeholder text с новым шрифтом Golos Text.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Theme.kt
git commit -m "feat(theme): wire lightColorScheme + Typography + Shapes into CleanCityTheme"
```

---

## Phase 4 — Domain layer

### Task 4.1: AuthState

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/AuthState.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.domain

import com.example.cleancity.shared.models.UserResponse

sealed interface AuthState {
    data object Loading : AuthState
    data object Anonymous : AuthState
    data object Guest : AuthState
    data class NeedsVerification(val email: String) : AuthState
    data class Authenticated(val user: UserResponse) : AuthState
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/AuthState.kt
git commit -m "feat(domain): add AuthState sealed interface"
```

---

### Task 4.2: Validation — TDD

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/Validation.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/ValidationTest.kt`

- [ ] **Step 1: Написать failing test**

```kotlin
package com.example.cleancity.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationTest {

    @Test fun `emailFormat accepts valid emails`() {
        assertTrue(Validation.emailFormat("user@example.com"))
        assertTrue(Validation.emailFormat("a.b+tag@sub.example.co"))
    }

    @Test fun `emailFormat rejects missing at`() {
        assertFalse(Validation.emailFormat("userexample.com"))
    }

    @Test fun `emailFormat rejects missing dot`() {
        assertFalse(Validation.emailFormat("user@example"))
    }

    @Test fun `emailFormat rejects too short`() {
        assertFalse(Validation.emailFormat("a@b"))
    }

    @Test fun `emailFormat rejects empty`() {
        assertFalse(Validation.emailFormat(""))
    }

    @Test fun `passwordStrength accepts 8 chars`() {
        assertTrue(Validation.passwordStrength("12345678"))
    }

    @Test fun `passwordStrength rejects 7 chars`() {
        assertFalse(Validation.passwordStrength("1234567"))
    }

    @Test fun `passwordStrength rejects over 100 chars`() {
        assertFalse(Validation.passwordStrength("a".repeat(101)))
    }

    @Test fun `fullNameNonBlank rejects whitespace only`() {
        assertFalse(Validation.fullNameNonBlank("   "))
    }

    @Test fun `fullNameNonBlank accepts trimmed name`() {
        assertTrue(Validation.fullNameNonBlank("Иван Иванов"))
    }
}
```

- [ ] **Step 2: Запустить — ожидаем FAIL (Validation не существует)**

```bash
./gradlew :composeApp:commonTest --tests "com.example.cleancity.domain.ValidationTest"
```
Expected: COMPILE FAIL — unresolved reference Validation.

- [ ] **Step 3: Создать Validation.kt**

```kotlin
package com.example.cleancity.domain

object Validation {
    fun emailFormat(email: String): Boolean {
        val e = email.trim()
        if (e.length !in 5..255) return false
        val at = e.indexOf('@')
        if (at <= 0 || at == e.lastIndex) return false
        return e.substring(at + 1).contains('.')
    }

    fun passwordStrength(p: String): Boolean = p.length in 8..100

    fun fullNameNonBlank(n: String): Boolean = n.trim().isNotEmpty()
}
```

- [ ] **Step 4: Запустить — ожидаем PASS**

```bash
./gradlew :composeApp:commonTest --tests "com.example.cleancity.domain.ValidationTest"
```
Expected: PASS (10 кейсов).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/Validation.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/domain/ValidationTest.kt
git commit -m "feat(domain): Validation (email/password/fullName) + tests"
```

---

### Task 4.3: DeepLinkBus

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/DeepLinkBus.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface DeepLink {
    data class Verify(val token: String) : DeepLink
    data class Reset(val token: String) : DeepLink
}

object DeepLinkBus {
    private val _pending = MutableStateFlow<DeepLink?>(null)
    val pending: StateFlow<DeepLink?> = _pending.asStateFlow()

    fun emit(link: DeepLink) { _pending.value = link }

    fun consume(link: DeepLink) {
        if (_pending.value == link) _pending.value = null
    }
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/DeepLinkBus.kt
git commit -m "feat(domain): DeepLinkBus (StateFlow with consume() for cold-start replay)"
```

---

### Task 4.4: ApiError + ApiException

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ApiError.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: JsonObject? = null,
)

class ApiException(
    val error: ApiError,
    val httpStatus: Int,
) : RuntimeException(error.message)
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ApiError.kt
git commit -m "feat(data/network): ApiError + ApiException"
```

---

## Phase 5 — Storage layer

### Task 5.1: TokenStorage — expect + Tokens

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/storage/TokenStorage.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.data.storage

data class Tokens(val access: String, val refresh: String)

interface TokenStorage {
    suspend fun read(): Tokens?
    suspend fun write(access: String, refresh: String)
    suspend fun clear()
}

expect class TokenStorageFactory {
    fun create(): TokenStorage
}
```

- [ ] **Step 2: Сборка (ожидаем actual missing)**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: COMPILE FAIL — `expect class TokenStorageFactory` has no actual declaration. Это норм, починим в Step 5.2.

- [ ] **Step 3: НЕ КОММИТИТЬ — Phase сборка зелёная только после 5.2**

---

### Task 5.2: AndroidTokenStorage — actual

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/data/storage/TokenStorage.android.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidTokenStorage(private val context: Context) : TokenStorage {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cleancity_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun read(): Tokens? = withContext(Dispatchers.IO) {
        val a = prefs.getString(KEY_ACCESS, null) ?: return@withContext null
        val r = prefs.getString(KEY_REFRESH, null) ?: return@withContext null
        Tokens(a, r)
    }

    override suspend fun write(access: String, refresh: String) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_ACCESS, access)
                .putString(KEY_REFRESH, refresh)
                .apply()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit().clear().apply()
        }
    }

    companion object {
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
    }
}

actual class TokenStorageFactory(private val context: Context) {
    actual fun create(): TokenStorage = AndroidTokenStorage(context)
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit обоих файлов**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/storage/TokenStorage.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/data/storage/TokenStorage.android.kt
git commit -m "feat(storage): TokenStorage expect/actual via EncryptedSharedPreferences"
```

---

## Phase 6 — Networking

### Task 6.1: AuthApi — interface + implementation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/AuthApi.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.requests.auth.ForgotPasswordRequest
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.RefreshTokenRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.ResendVerificationRequest
import com.example.cleancity.shared.requests.auth.ResetPasswordRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class AuthApi(private val client: HttpClient) {

    suspend fun register(req: RegisterRequest): UserResponse =
        client.post("/auth/register") { setBody(req) }.body()

    suspend fun verifyEmail(req: VerifyEmailRequest): AuthResponse =
        client.post("/auth/verify-email") { setBody(req) }.body()

    suspend fun resendVerification(req: ResendVerificationRequest) {
        client.post("/auth/resend-verification") { setBody(req) }
    }

    suspend fun login(req: LoginRequest): AuthResponse =
        client.post("/auth/login") { setBody(req) }.body()

    suspend fun refresh(req: RefreshTokenRequest): AuthResponse =
        client.post("/auth/refresh") { setBody(req) }.body()

    suspend fun logout() {
        client.post("/auth/logout")
    }

    suspend fun forgotPassword(req: ForgotPasswordRequest) {
        client.post("/auth/forgot-password") { setBody(req) }
    }

    suspend fun resetPassword(req: ResetPasswordRequest) {
        client.post("/auth/reset-password") { setBody(req) }
    }
}
```

**Проверь поля `AuthResponse`** — в `shared/models/AuthResponse.kt`. Если field называется `accessToken`, `refreshToken`, `user` — используем как есть. Если что-то отличается — поправь импорты/доступ ниже по плану.

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/AuthApi.kt
git commit -m "feat(data/network): AuthApi — calls to /auth/*"
```

---

### Task 6.2: UserApi.me()

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/UserApi.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.UserResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class UserApi(private val client: HttpClient) {
    suspend fun me(): UserResponse =
        client.get("/users/me").body()
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/UserApi.kt
git commit -m "feat(data/network): UserApi — GET /users/me"
```

---

### Task 6.3: ApiClient — HttpClient с Auth + Logging + ResponseValidator

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ApiClient.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.data.storage.TokenStorage
import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.requests.auth.RefreshTokenRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

interface AuthFailureHandler {
    fun onAuthFailure()
}

private val refreshMutex = Mutex()

fun createHttpClient(
    engine: HttpClientEngine,
    baseUrl: String,
    isDebug: Boolean,
    tokenStorage: TokenStorage,
    onAuthFailure: AuthFailureHandler,
): HttpClient = HttpClient(engine) {
    expectSuccess = true

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        })
    }

    install(Logging) {
        level = if (isDebug) LogLevel.HEADERS else LogLevel.NONE
        logger = Logger.SIMPLE
        sanitizeHeader { name -> name == HttpHeaders.Authorization }
        filter { req -> !req.url.encodedPath.startsWith("/auth/") }
    }

    defaultRequest {
        url(baseUrl)
        contentType(ContentType.Application.Json)
        headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 5_000
    }

    install(Auth) {
        bearer {
            loadTokens {
                tokenStorage.read()?.let { BearerTokens(it.access, it.refresh) }
            }
            refreshTokens {
                refreshMutex.withLock {
                    val current = tokenStorage.read()
                    if (current == null) {
                        onAuthFailure.onAuthFailure()
                        return@refreshTokens null
                    }
                    try {
                        val refreshed: AuthResponse = client.post("/auth/refresh") {
                            markAsRefreshTokenRequest()
                            setBody(RefreshTokenRequest(current.refresh))
                        }.body()
                        tokenStorage.write(refreshed.accessToken, refreshed.refreshToken)
                        BearerTokens(refreshed.accessToken, refreshed.refreshToken)
                    } catch (t: Throwable) {
                        tokenStorage.clear()
                        onAuthFailure.onAuthFailure()
                        null
                    }
                }
            }
        }
    }

    HttpResponseValidator {
        validateResponse { response: HttpResponse ->
            if (!response.status.isSuccess()) {
                val err = runCatching { response.body<ApiError>() }
                    .getOrDefault(ApiError("UNKNOWN", "HTTP ${response.status.value}"))
                throw ApiException(err, response.status.value)
            }
        }
    }
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ApiClient.kt
git commit -m "feat(data/network): HttpClient factory with bearer auth + refresh + response validator"
```

---

## Phase 7 — Repository + tests

### Task 7.1: Fake helpers для тестов

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/storage/FakeTokenStorage.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeAuthApi.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeUserApi.kt`

- [ ] **Step 1: FakeTokenStorage.kt**

```kotlin
package com.example.cleancity.data.storage

class FakeTokenStorage : TokenStorage {
    private var tokens: Tokens? = null
    val writes = mutableListOf<Tokens>()
    var clearCount = 0
        private set

    fun preset(tokens: Tokens?) { this.tokens = tokens }

    override suspend fun read(): Tokens? = tokens
    override suspend fun write(access: String, refresh: String) {
        tokens = Tokens(access, refresh)
        writes += tokens!!
    }
    override suspend fun clear() {
        tokens = null
        clearCount += 1
    }
}
```

- [ ] **Step 2: FakeAuthApi.kt**

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.requests.auth.*

class FakeAuthApi(
    var registerResult: Result<UserResponse>? = null,
    var verifyResult: Result<AuthResponse>? = null,
    var loginResult: Result<AuthResponse>? = null,
    var refreshResult: Result<AuthResponse>? = null,
) {
    val logoutCalls = mutableListOf<Unit>()
    val resendCalls = mutableListOf<String>()
    val forgotCalls = mutableListOf<String>()
    val resetCalls = mutableListOf<Pair<String, String>>()

    fun asAuthApi(): AuthApiContract = object : AuthApiContract {
        override suspend fun register(req: RegisterRequest): UserResponse =
            requireNotNull(registerResult).getOrThrow()
        override suspend fun verifyEmail(req: VerifyEmailRequest): AuthResponse =
            requireNotNull(verifyResult).getOrThrow()
        override suspend fun resendVerification(req: ResendVerificationRequest) {
            resendCalls += req.email
        }
        override suspend fun login(req: LoginRequest): AuthResponse =
            requireNotNull(loginResult).getOrThrow()
        override suspend fun refresh(req: RefreshTokenRequest): AuthResponse =
            requireNotNull(refreshResult).getOrThrow()
        override suspend fun logout() { logoutCalls += Unit }
        override suspend fun forgotPassword(req: ForgotPasswordRequest) { forgotCalls += req.email }
        override suspend fun resetPassword(req: ResetPasswordRequest) {
            resetCalls += req.token to req.newPassword
        }
    }
}

interface AuthApiContract {
    suspend fun register(req: RegisterRequest): UserResponse
    suspend fun verifyEmail(req: VerifyEmailRequest): AuthResponse
    suspend fun resendVerification(req: ResendVerificationRequest)
    suspend fun login(req: LoginRequest): AuthResponse
    suspend fun refresh(req: RefreshTokenRequest): AuthResponse
    suspend fun logout()
    suspend fun forgotPassword(req: ForgotPasswordRequest)
    suspend fun resetPassword(req: ResetPasswordRequest)
}
```

Это требует ввести интерфейс `AuthApiContract` в основном коде и сделать `AuthApi` его реализацией — иначе нельзя замокать. Допиши в `AuthApi.kt` строку `: AuthApiContract` (или вынеси контракт отдельным файлом и реализуй его в `AuthApi`).

Если предпочитаешь без контракта — используй `MockEngine` от Ktor вместо Fake. Любой из подходов ок, но Fake чище для unit-тестов repository.

- [ ] **Step 3: FakeUserApi.kt**

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.UserResponse

class FakeUserApi(var meResult: Result<UserResponse>? = null) {
    fun asUserApi(): UserApiContract = object : UserApiContract {
        override suspend fun me(): UserResponse = requireNotNull(meResult).getOrThrow()
    }
}

interface UserApiContract {
    suspend fun me(): UserResponse
}
```

То же — добавить `: UserApiContract` к `UserApi` в `UserApi.kt`.

- [ ] **Step 4: Refactor `AuthApi`/`UserApi` под контракты**

В `AuthApi.kt` изменить заголовок:
```kotlin
class AuthApi(private val client: HttpClient) : AuthApiContract {
```
Все методы уже совпадают с интерфейсом — overrideбудет автоматически (но `override` modifier надо добавить перед каждым `suspend fun`).

Аналогично в `UserApi.kt`:
```kotlin
class UserApi(private val client: HttpClient) : UserApiContract {
    override suspend fun me(): UserResponse = client.get("/users/me").body()
}
```

- [ ] **Step 5: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin :composeApp:compileTestKotlinAndroid
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/AuthApi.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/UserApi.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/data/
git commit -m "test(data): Fake API helpers + AuthApiContract / UserApiContract interfaces"
```

---

### Task 7.2: AuthRepository — TDD

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/repository/AuthRepository.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/repository/AuthRepositoryTest.kt`

- [ ] **Step 1: Написать failing-тест**

```kotlin
package com.example.cleancity.data.repository

import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.data.storage.Tokens
import com.example.cleancity.domain.AuthState
import com.example.cleancity.shared.models.AuthResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private val sampleUser = UserResponse(
        id = 1, email = "u@x.com", role = UserRole.RESIDENT,
        fullName = "User", emailVerified = true, createdAt = "2026-05-13T00:00:00Z"
    )

    @Test fun `init with no tokens yields Anonymous`() = runTest {
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        repo.init()
        assertEquals(AuthState.Anonymous, repo.state.value)
    }

    @Test fun `init with valid tokens fetches me and yields Authenticated`() = runTest {
        val storage = FakeTokenStorage().apply { preset(Tokens("acc", "ref")) }
        val userApi = FakeUserApi(meResult = Result.success(sampleUser))
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), userApi.asUserApi(), storage)
        repo.init()
        assertEquals(AuthState.Authenticated(sampleUser), repo.state.value)
    }

    @Test fun `init with invalid tokens clears storage and yields Anonymous`() = runTest {
        val storage = FakeTokenStorage().apply { preset(Tokens("bad", "bad")) }
        val userApi = FakeUserApi(meResult = Result.failure(RuntimeException("401")))
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), userApi.asUserApi(), storage)
        repo.init()
        assertEquals(AuthState.Anonymous, repo.state.value)
        assertEquals(1, storage.clearCount)
    }

    @Test fun `register success yields NeedsVerification`() = runTest {
        val authApi = FakeAuthApi(registerResult = Result.success(sampleUser.copy(emailVerified = false)))
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        val r = repo.register("u@x.com", "Password1", "Full Name")
        assertTrue(r.isSuccess)
        assertEquals(AuthState.NeedsVerification("u@x.com"), repo.state.value)
    }

    @Test fun `verifyEmail success writes tokens and yields Authenticated`() = runTest {
        val storage = FakeTokenStorage()
        val authApi = FakeAuthApi(verifyResult = Result.success(
            AuthResponse(accessToken = "acc", refreshToken = "ref", user = sampleUser)
        ))
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), storage)
        val r = repo.verifyEmail("token-xyz")
        assertTrue(r.isSuccess)
        assertEquals(AuthState.Authenticated(sampleUser), repo.state.value)
        assertEquals(Tokens("acc", "ref"), storage.read())
    }

    @Test fun `login success writes tokens`() = runTest {
        val storage = FakeTokenStorage()
        val authApi = FakeAuthApi(loginResult = Result.success(
            AuthResponse(accessToken = "a", refreshToken = "r", user = sampleUser)
        ))
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), storage)
        val r = repo.login("u@x.com", "Password1")
        assertTrue(r.isSuccess)
        assertIs<AuthState.Authenticated>(repo.state.value)
        assertEquals(Tokens("a", "r"), storage.read())
    }

    @Test fun `logout clears storage and yields Anonymous`() = runTest {
        val storage = FakeTokenStorage().apply { preset(Tokens("a", "r")) }
        val repo = AuthRepository(FakeAuthApi().asAuthApi(), FakeUserApi().asUserApi(), storage)
        repo.logout()
        assertEquals(AuthState.Anonymous, repo.state.value)
        assertEquals(1, storage.clearCount)
    }
}
```

- [ ] **Step 2: Запустить тест — ожидаем FAIL**

```bash
./gradlew :composeApp:commonTest --tests "com.example.cleancity.data.repository.AuthRepositoryTest"
```
Expected: COMPILE FAIL — AuthRepository не существует.

- [ ] **Step 3: Создать AuthRepository.kt**

```kotlin
package com.example.cleancity.data.repository

import com.example.cleancity.data.network.AuthApiContract
import com.example.cleancity.data.network.UserApiContract
import com.example.cleancity.data.storage.TokenStorage
import com.example.cleancity.domain.AuthState
import com.example.cleancity.shared.requests.auth.ForgotPasswordRequest
import com.example.cleancity.shared.requests.auth.LoginRequest
import com.example.cleancity.shared.requests.auth.RegisterRequest
import com.example.cleancity.shared.requests.auth.ResendVerificationRequest
import com.example.cleancity.shared.requests.auth.ResetPasswordRequest
import com.example.cleancity.shared.requests.auth.VerifyEmailRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val authApi: AuthApiContract,
    private val userApi: UserApiContract,
    private val storage: TokenStorage,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    suspend fun init() {
        val tokens = storage.read()
        if (tokens == null) {
            _state.value = AuthState.Anonymous
            return
        }
        runCatching { userApi.me() }
            .onSuccess { _state.value = AuthState.Authenticated(it) }
            .onFailure {
                storage.clear()
                _state.value = AuthState.Anonymous
            }
    }

    suspend fun register(email: String, password: String, fullName: String): Result<Unit> = runCatching {
        authApi.register(RegisterRequest(
            email = email.trim(),
            password = password,
            fullName = fullName.trim(),
            acceptedTerms = true,
        ))
        _state.value = AuthState.NeedsVerification(email.trim())
    }

    suspend fun verifyEmail(token: String): Result<Unit> = runCatching {
        val resp = authApi.verifyEmail(VerifyEmailRequest(token))
        storage.write(resp.accessToken, resp.refreshToken)
        _state.value = AuthState.Authenticated(resp.user)
    }

    suspend fun resendVerification(email: String): Result<Unit> = runCatching {
        authApi.resendVerification(ResendVerificationRequest(email))
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val resp = authApi.login(LoginRequest(email.trim(), password))
        storage.write(resp.accessToken, resp.refreshToken)
        _state.value = AuthState.Authenticated(resp.user)
    }

    suspend fun forgotPassword(email: String): Result<Unit> = runCatching {
        authApi.forgotPassword(ForgotPasswordRequest(email.trim()))
    }

    suspend fun resetPassword(token: String, newPassword: String): Result<Unit> = runCatching {
        authApi.resetPassword(ResetPasswordRequest(token, newPassword))
    }

    fun continueAsGuest() { _state.value = AuthState.Guest }
    fun toAnonymous() { _state.value = AuthState.Anonymous }

    suspend fun logout() {
        runCatching { authApi.logout() }
        storage.clear()
        _state.value = AuthState.Anonymous
    }

    internal fun forceAnonymous() {
        _state.value = AuthState.Anonymous
    }
}
```

- [ ] **Step 4: Запустить тесты — ожидаем PASS**

```bash
./gradlew :composeApp:commonTest --tests "com.example.cleancity.data.repository.AuthRepositoryTest"
```
Expected: PASS (7 кейсов).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/repository/AuthRepository.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/data/repository/AuthRepositoryTest.kt
git commit -m "feat(data/repository): AuthRepository + 7 unit tests (state transitions, storage)"
```

---

## Phase 8 — DI (Koin)

### Task 8.1: AppModule (common)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.di

import com.example.cleancity.data.network.AuthApi
import com.example.cleancity.data.network.AuthApiContract
import com.example.cleancity.data.network.UserApi
import com.example.cleancity.data.network.UserApiContract
import com.example.cleancity.data.network.AuthFailureHandler
import com.example.cleancity.data.network.createHttpClient
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.TokenStorage
import com.example.cleancity.data.storage.TokenStorageFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.module

data class NetworkConfig(val baseUrl: String, val isDebug: Boolean)

fun appModule(): Module = module {
    single { get<TokenStorageFactory>().create() } bind TokenStorage::class

    single {
        val cfg = get<NetworkConfig>()
        val storage: TokenStorage = get()
        // AuthFailureHandler — late-bound через AuthRepository
        val handler = object : AuthFailureHandler {
            override fun onAuthFailure() {
                get<AuthRepository>().forceAnonymous()
            }
        }
        createHttpClient(
            engine = get<HttpClientEngine>(),
            baseUrl = cfg.baseUrl,
            isDebug = cfg.isDebug,
            tokenStorage = storage,
            onAuthFailure = handler,
        )
    }

    single<AuthApiContract> { AuthApi(get<HttpClient>()) }
    single<UserApiContract> { UserApi(get<HttpClient>()) }

    single { AuthRepository(get(), get(), get()) }
}
```

**Note**: импорт `bind` из `org.koin.dsl.bind` — добавь импорт `import org.koin.dsl.bind` если IDE не подскажет.

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat(di): Koin module — HttpClient, AuthRepository, AuthApi, UserApi"
```

---

### Task 8.2: AndroidModule + CleanCityApplication

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt`

- [ ] **Step 1: AndroidModule.kt**

```kotlin
package com.example.cleancity.di

import com.example.cleancity.BuildConfig
import com.example.cleancity.data.storage.TokenStorageFactory
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidModule(): Module = module {
    single { NetworkConfig(baseUrl = BuildConfig.API_BASE_URL, isDebug = BuildConfig.IS_DEBUG) }
    single { TokenStorageFactory(androidContext()) }
    single<HttpClientEngine> { OkHttp.create() }
}
```

- [ ] **Step 2: Перезаписать CleanCityApplication.kt**

```kotlin
package com.example.cleancity

import android.app.Application
import com.example.cleancity.di.androidModule
import com.example.cleancity.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CleanCityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.IS_DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@CleanCityApplication)
            modules(androidModule(), appModule())
        }
    }
}
```

- [ ] **Step 3: Проверить AndroidManifest.xml**

`composeApp/src/androidMain/AndroidManifest.xml` должен содержать `android:name=".CleanCityApplication"` в теге `<application>`. Если нет — добавить:

```xml
<application
    android:name=".CleanCityApplication"
    android:label="@string/app_name"
    ...>
```

- [ ] **Step 4: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt \
        composeApp/src/androidMain/AndroidManifest.xml
git commit -m "feat(android/di): wire Koin in CleanCityApplication + OkHttp engine + EncryptedSharedPreferences"
```

---

## Phase 9 — UI components (mockup grammar)

### Task 9.1: AuthScaffold

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/AuthScaffold.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Dimens
import com.example.cleancity.ui.theme.Gray100
import com.example.cleancity.ui.theme.Gray700
import androidx.compose.ui.graphics.Color

@Composable
fun AuthScaffold(
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(start = 28.dp, end = 28.dp, top = Dimens.spaceXxl, bottom = Dimens.spaceXxl)
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gray100),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Gray700)
            }
            Spacer(Modifier.height(28.dp))
        }
        content()
    }
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/AuthScaffold.kt
git commit -m "feat(ui/components): AuthScaffold — white bg, padding 40/28/40, optional back button"
```

---

### Task 9.2: AuthTag / AuthTitle / AuthSub

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/AuthTexts.kt`

- [ ] **Step 1: Создать файл (все три в одном файле — связаны логически)**

```kotlin
package com.example.cleancity.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Green200
import com.example.cleancity.ui.theme.Green50
import com.example.cleancity.ui.theme.Green500
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray900

@Composable
fun AuthTag(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Green50)
            .border(1.dp, Green200, RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Green500))
        Spacer(Modifier.width(6.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Green700,
        )
    }
}

@Composable
fun AuthTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        color = Gray900,
        modifier = modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
fun AuthSub(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Gray500,
        modifier = modifier.padding(bottom = 32.dp),
    )
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/AuthTexts.kt
git commit -m "feat(ui/components): AuthTag + AuthTitle + AuthSub (mockup grammar)"
```

---

### Task 9.3: FormField

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/FormField.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Gray200
import com.example.cleancity.ui.theme.Gray300
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Red

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    hint: String? = null,
    error: String? = null,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.padding(bottom = 16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Gray600,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Green400,
                unfocusedBorderColor = Gray200,
                errorBorderColor = Red,
                focusedTextColor = Gray900,
                unfocusedTextColor = Gray900,
                cursorColor = Green400,
                focusedPlaceholderColor = Gray300,
                unfocusedPlaceholderColor = Gray300,
            ),
            isError = error != null,
        )
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = error, style = MaterialTheme.typography.bodySmall, color = Red)
        } else if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = Gray500)
        }
    }
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/FormField.kt
git commit -m "feat(ui/components): FormField — label + OutlinedTextField + error/hint"
```

---

### Task 9.4: PrimaryButton + SecondaryButton

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/Buttons.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Green900

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    backgroundColor: Color = Accent,
    contentColor: Color = Green900,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.6f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = Color.White,
    borderColor: Color = Color.White.copy(alpha = 0.15f),
    backgroundColor: Color = Color.White.copy(alpha = 0.08f),
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = backgroundColor,
            contentColor = contentColor,
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/Buttons.kt
git commit -m "feat(ui/components): PrimaryButton (loading) + SecondaryButton"
```

---

### Task 9.5: AuthLinkRow + ConsentRow

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/AuthLinkRow.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/ConsentRow.kt`

- [ ] **Step 1: AuthLinkRow.kt**

```kotlin
package com.example.cleancity.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Green600

@Composable
fun AuthLinkRow(
    prefix: String,
    linkText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "$prefix ", style = MaterialTheme.typography.bodyMedium, color = Gray500)
        Text(
            text = linkText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Green600,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}
```

- [ ] **Step 2: ConsentRow.kt**

```kotlin
package com.example.cleancity.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green700

private const val TAG_TERMS = "TERMS"
private const val TAG_PRIVACY = "PRIVACY"

@Composable
fun ConsentRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = buildAnnotatedString {
        append("Я принимаю ")
        pushStringAnnotation(tag = TAG_TERMS, annotation = "terms")
        withStyle(SpanStyle(color = Green600, fontWeight = FontWeight.SemiBold)) {
            append("Условия")
        }
        pop()
        append(" и ")
        pushStringAnnotation(tag = TAG_PRIVACY, annotation = "privacy")
        withStyle(SpanStyle(color = Green600, fontWeight = FontWeight.SemiBold)) {
            append("Политику обработки данных")
        }
        pop()
    }
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Green700,
                uncheckedColor = Gray600,
                checkmarkColor = Accent,
            ),
        )
        ClickableText(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(color = Gray600),
            onClick = { offset ->
                text.getStringAnnotations(TAG_TERMS, offset, offset).firstOrNull()?.let { onTermsClick() }
                text.getStringAnnotations(TAG_PRIVACY, offset, offset).firstOrNull()?.let { onPrivacyClick() }
            },
            modifier = Modifier.padding(top = 12.dp, start = 4.dp),
        )
    }
}
```

- [ ] **Step 3: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/AuthLinkRow.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/components/ConsentRow.kt
git commit -m "feat(ui/components): AuthLinkRow + ConsentRow (Checkbox + annotated text spans)"
```

---

## Phase 10 — Auth screens

### Task 10.1: ErrorMapper utility

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ErrorMapper.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.ui.feature.auth

import com.example.cleancity.data.network.ApiException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.io.IOException

sealed interface UiErrorPlacement {
    data class InlineEmail(val message: String) : UiErrorPlacement
    data class InlinePassword(val message: String) : UiErrorPlacement
    data class Snackbar(val message: String) : UiErrorPlacement
    data class FullScreen(val message: String) : UiErrorPlacement
    data class EmailNotVerified(val email: String) : UiErrorPlacement
}

object ErrorMapper {
    private const val GENERIC = "Что-то пошло не так. Попробуйте ещё раз."
    private const val NETWORK = "Нет соединения с интернетом"
    private const val TIMEOUT = "Сервер не отвечает. Попробуйте позже."

    fun map(t: Throwable, fallbackEmail: String = ""): UiErrorPlacement = when (t) {
        is ApiException -> when (t.error.code) {
            "AUTH_EMAIL_NOT_VERIFIED", "EMAIL_NOT_VERIFIED" -> UiErrorPlacement.EmailNotVerified(fallbackEmail)
            "AUTH_INVALID_CREDENTIALS", "INVALID_CREDENTIALS" -> UiErrorPlacement.InlinePassword("Неверный email или пароль")
            "AUTH_EMAIL_TAKEN", "EMAIL_ALREADY_EXISTS" -> UiErrorPlacement.InlineEmail("Этот email уже зарегистрирован")
            "VALIDATION_INVALID_EMAIL", "EMAIL_INVALID_FORMAT" -> UiErrorPlacement.InlineEmail("Неверный формат email")
            "VALIDATION_WEAK_PASSWORD", "WEAK_PASSWORD" -> UiErrorPlacement.InlinePassword(t.error.message)
            "RATE_LIMITED", "AUTH_RATE_LIMITED" -> UiErrorPlacement.Snackbar("Слишком много попыток. Попробуйте через минуту.")
            "AUTH_ACCOUNT_FROZEN", "ACCOUNT_FROZEN" -> UiErrorPlacement.Snackbar("Аккаунт временно заблокирован. Свяжитесь с поддержкой.")
            "EMAIL_TOKEN_EXPIRED", "INVITE_TOKEN_INVALID" ->
                UiErrorPlacement.FullScreen("Ссылка устарела. Запросите новую.")
            else -> UiErrorPlacement.Snackbar(GENERIC)
        }
        is HttpRequestTimeoutException -> UiErrorPlacement.Snackbar(TIMEOUT)
        is IOException -> UiErrorPlacement.Snackbar(NETWORK)
        else -> UiErrorPlacement.Snackbar(GENERIC)
    }
}
```

**Note про error codes:** в backend используются формы вроде `AUTH_INVALID_CREDENTIALS` (см. `ErrorCodes.kt`). Проверь точные строки в `backend/src/main/kotlin/com/example/cleancity/auth/ErrorCodes.kt` и при разногласии заменяй на актуальные значения. Маппинг учитывает оба варианта формулировок (с префиксом и без) — защита от опечаток.

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ErrorMapper.kt
git commit -m "feat(ui/auth): ErrorMapper — ApiException + network → UiErrorPlacement"
```

---

### Task 10.2: SplashScreen + SplashLoaderScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/splash/SplashScreen.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.ui.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.components.SecondaryButton
import com.example.cleancity.ui.feature.auth.LoginScreen
import com.example.cleancity.ui.feature.auth.RegisterScreen
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Green800
import com.example.cleancity.ui.theme.Green900

class SplashScreen(
    private val onContinueAsGuest: () -> Unit,
) : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        SplashContent(
            onLoginClick = { nav.push(LoginScreen()) },
            onRegisterClick = { nav.push(RegisterScreen()) },
            onGuestClick = onContinueAsGuest,
        )
    }
}

class SplashLoaderScreen : Screen {
    @Composable
    override fun Content() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Green800, Green900))),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Accent)
        }
    }
}

@Composable
private fun SplashContent(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onGuestClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Green800, Green900)))
            .padding(start = 32.dp, end = 32.dp, top = 60.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        // Logo
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(Accent, Green400))),
                contentAlignment = Alignment.Center,
            ) {
                Text("🛡", style = MaterialTheme.typography.headlineMedium, color = Green900)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = buildAnnotatedString {
                    append("Чистый ")
                    withStyle(SpanStyle(color = Accent)) { append("Город") }
                },
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сообщайте о проблемах\nВлияйте на свой город",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
        // Actions
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = "Войти", onClick = onLoginClick, backgroundColor = Accent, contentColor = Green900)
            SecondaryButton(text = "Регистрация", onClick = onRegisterClick)
            Text(
                text = "Зайти как гость",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .let { mod -> mod }
            )
        }
    }
}
```

**Note**: «Зайти как гость» в коде выше — это `Text` без кликабельности. Замени на:

```kotlin
import androidx.compose.foundation.clickable
// ...
Text(
    text = "Зайти как гость",
    style = MaterialTheme.typography.bodyMedium,
    color = Color.White.copy(alpha = 0.55f),
    modifier = Modifier
        .align(Alignment.CenterHorizontally)
        .padding(top = 8.dp)
        .clickable(onClick = onGuestClick)
        .padding(horizontal = 16.dp, vertical = 8.dp),
)
```

Disclaimer-текст «Продолжая, вы соглашаетесь…» добавь под кнопками как `Text(...)` 11sp `Color.White.copy(alpha = 0.3f)` (mockup строка 363). Кликабельные spans «Условиями» / «Политикой данных» — через `ClickableText` (паттерн как в `ConsentRow`).

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/splash/SplashScreen.kt
git commit -m "feat(ui/splash): SplashScreen with 3 actions + SplashLoaderScreen"
```

---

### Task 10.3: LoginScreen + LoginScreenModel + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LoginScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LoginScreenModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/auth/LoginScreenModelTest.kt`

- [ ] **Step 1: LoginScreenModel.kt**

```kotlin
package com.example.cleancity.ui.feature.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val emailNotVerifiedFor: String? = null,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= 8 && !loading
}

class LoginScreenModel(private val authRepo: AuthRepository) : ScreenModel {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun setEmail(s: String) = _state.update { it.copy(email = s, emailError = null, snackbar = null) }
    fun setPassword(s: String) = _state.update { it.copy(password = s, passwordError = null, snackbar = null) }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }
    fun dismissEmailNotVerified() = _state.update { it.copy(emailNotVerifiedFor = null) }

    fun submit() {
        val s = _state.value
        if (!Validation.emailFormat(s.email)) {
            _state.update { it.copy(emailError = "Неверный формат email") }
            return
        }
        screenModelScope.launch {
            _state.update { it.copy(loading = true, emailError = null, passwordError = null, snackbar = null, emailNotVerifiedFor = null) }
            authRepo.login(s.email, s.password).fold(
                onSuccess = { /* AuthState changes → App routes away */ },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e, fallbackEmail = s.email)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlineEmail -> st.copy(loading = false, emailError = placement.message)
                            is UiErrorPlacement.InlinePassword -> st.copy(loading = false, passwordError = placement.message)
                            is UiErrorPlacement.EmailNotVerified -> st.copy(loading = false, emailNotVerifiedFor = placement.email)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            is UiErrorPlacement.FullScreen -> st.copy(loading = false, snackbar = placement.message)
                        }
                    }
                },
            )
        }
    }

    fun resendVerification(email: String) {
        screenModelScope.launch {
            authRepo.resendVerification(email)
        }
    }
}
```

- [ ] **Step 2: LoginScreenModelTest.kt**

```kotlin
package com.example.cleancity.ui.feature.auth

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginScreenModelTest {

    private fun buildModel(authApi: FakeAuthApi): Pair<LoginScreenModel, AuthRepository> {
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        return LoginScreenModel(repo) to repo
    }

    @Test fun `submit with invalid credentials sets inline password error`() = runTest {
        val authApi = FakeAuthApi(loginResult = Result.failure(ApiException(ApiError("AUTH_INVALID_CREDENTIALS", "bad"), 401)))
        val (model, _) = buildModel(authApi)
        model.setEmail("u@x.com"); model.setPassword("Password1")
        model.submit()
        // wait propagation
        kotlinx.coroutines.test.testScheduler.advanceUntilIdle()
        assertEquals("Неверный email или пароль", model.state.value.passwordError)
        assertNull(model.state.value.emailError)
    }

    @Test fun `submit with email not verified surfaces resend state`() = runTest {
        val authApi = FakeAuthApi(loginResult = Result.failure(ApiException(ApiError("AUTH_EMAIL_NOT_VERIFIED", "verify"), 403)))
        val (model, _) = buildModel(authApi)
        model.setEmail("u@x.com"); model.setPassword("Password1")
        model.submit()
        kotlinx.coroutines.test.testScheduler.advanceUntilIdle()
        assertEquals("u@x.com", model.state.value.emailNotVerifiedFor)
    }

    @Test fun `submit with network error sets snackbar`() = runTest {
        val authApi = FakeAuthApi(loginResult = Result.failure(kotlinx.io.IOException("no net")))
        val (model, _) = buildModel(authApi)
        model.setEmail("u@x.com"); model.setPassword("Password1")
        model.submit()
        kotlinx.coroutines.test.testScheduler.advanceUntilIdle()
        assertNotNull(model.state.value.snackbar)
    }
}
```

**Note про `testScheduler.advanceUntilIdle()`** — обращение к scheduler внутри `runTest` идёт через `coroutineContext[TestCoroutineScheduler.Key]`. Если IDE не вытянет — используй вариант `TestScope().runTest { ... }` или `runTest(StandardTestDispatcher()) { ... advanceUntilIdle() }`. Точная форма зависит от версии `kotlinx-coroutines-test`.

- [ ] **Step 3: Запустить — ожидаем FAIL (LoginScreenModel пока нет компайла без LoginScreen)**

Compile-only — модель уже определена. Запусти:
```bash
./gradlew :composeApp:commonTest --tests "com.example.cleancity.ui.feature.auth.LoginScreenModelTest"
```
Expected: PASS (3 кейса), если ErrorMapper и AuthRepository корректно интегрированы.

- [ ] **Step 4: LoginScreen.kt**

```kotlin
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
import androidx.compose.ui.text.font.FontWeight
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
import com.example.cleancity.ui.components.FormField
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green700
import androidx.compose.ui.graphics.Color

class LoginScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val model: LoginScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHost = remember { SnackbarHostState() }

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
                    linkText = "Зарегистрироваться",
                    onClick = { nav.replace(RegisterScreen()) },
                )
            }
            SnackbarHost(snackbarHost, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                Snackbar(it)
            }
        }
    }
}
```

- [ ] **Step 5: Добавить LoginScreenModel в Koin (AppModule.kt)**

В `appModule()` добавить:
```kotlin
factory { LoginScreenModel(get()) }
```

- [ ] **Step 6: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LoginScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LoginScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/auth/LoginScreenModelTest.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat(ui/auth): LoginScreen + LoginScreenModel + tests; Koin factory"
```

---

### Task 10.4: RegisterScreen + RegisterScreenModel + tests

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreenModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreenModelTest.kt`

- [ ] **Step 1: RegisterScreenModel.kt**

```kotlin
package com.example.cleancity.ui.feature.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val consent: Boolean = false,
    val loading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val nameError: String? = null,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = Validation.emailFormat(email) &&
                Validation.passwordStrength(password) &&
                Validation.fullNameNonBlank(fullName) &&
                consent &&
                !loading
}

class RegisterScreenModel(private val authRepo: AuthRepository) : ScreenModel {

    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    fun setFullName(s: String) = _state.update { it.copy(fullName = s, nameError = null) }
    fun setEmail(s: String) = _state.update { it.copy(email = s, emailError = null) }
    fun setPassword(s: String) = _state.update { it.copy(password = s, passwordError = null) }
    fun setConsent(b: Boolean) = _state.update { it.copy(consent = b) }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun submit() {
        val s = _state.value
        screenModelScope.launch {
            _state.update { it.copy(loading = true, emailError = null, passwordError = null, nameError = null, snackbar = null) }
            authRepo.register(s.email, s.password, s.fullName).fold(
                onSuccess = { /* App.kt routes to VerifyEmailScreen via AuthState */ },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e, fallbackEmail = s.email)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlineEmail -> st.copy(loading = false, emailError = placement.message)
                            is UiErrorPlacement.InlinePassword -> st.copy(loading = false, passwordError = placement.message)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            else -> st.copy(loading = false, snackbar = "Что-то пошло не так. Попробуйте ещё раз.")
                        }
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 2: RegisterScreenModelTest.kt**

```kotlin
package com.example.cleancity.ui.feature.auth

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RegisterScreenModelTest {

    private fun newModel(authApi: FakeAuthApi = FakeAuthApi()) = LoginScreenModel(
        AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
    ).let { RegisterScreenModel(AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())) }

    @Test fun `canSubmit false when fields empty and consent off`() {
        val model = newModel()
        assertFalse(model.state.value.canSubmit)
    }

    @Test fun `canSubmit false when consent off but fields filled`() {
        val model = newModel()
        model.setFullName("Test"); model.setEmail("u@x.com"); model.setPassword("Password1")
        assertFalse(model.state.value.canSubmit)
    }

    @Test fun `canSubmit true when all filled and consent on`() {
        val model = newModel()
        model.setFullName("Test"); model.setEmail("u@x.com"); model.setPassword("Password1")
        model.setConsent(true)
        assertTrue(model.state.value.canSubmit)
    }

    @Test fun `submit on EMAIL_ALREADY_EXISTS sets inline email error`() = runTest {
        val authApi = FakeAuthApi(registerResult = Result.failure(ApiException(ApiError("AUTH_EMAIL_TAKEN", "taken"), 409)))
        val model = newModel(authApi)
        model.setFullName("Test"); model.setEmail("u@x.com"); model.setPassword("Password1"); model.setConsent(true)
        model.submit()
        testScheduler.advanceUntilIdle()
        assertEquals("Этот email уже зарегистрирован", model.state.value.emailError)
    }
}
```

- [ ] **Step 3: RegisterScreen.kt**

```kotlin
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
```

- [ ] **Step 4: Koin factory в AppModule**

Добавить в `appModule()`:
```kotlin
factory { RegisterScreenModel(get()) }
```

- [ ] **Step 5: Запустить тесты**

```bash
./gradlew :composeApp:commonTest --tests "com.example.cleancity.ui.feature.auth.RegisterScreenModelTest"
```
Expected: PASS (4 кейса).

- [ ] **Step 6: Сборка APK**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreenModelTest.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat(ui/auth): RegisterScreen + RegisterScreenModel + tests; ConsentRow gates submit"
```

---

### Task 10.5: VerifyEmailScreen + VerifyEmailScreenModel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/VerifyEmailScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/VerifyEmailScreenModel.kt`

- [ ] **Step 1: VerifyEmailScreenModel.kt**

```kotlin
package com.example.cleancity.ui.feature.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.DeepLink
import com.example.cleancity.domain.DeepLinkBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VerifyStatus {
    data object Waiting : VerifyStatus
    data object Verifying : VerifyStatus
    data class Error(val message: String) : VerifyStatus
}

data class VerifyEmailState(
    val email: String,
    val status: VerifyStatus = VerifyStatus.Waiting,
    val cooldownSec: Int = 0,
)

class VerifyEmailScreenModel(
    email: String,
    private val authRepo: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(VerifyEmailState(email = email))
    val state: StateFlow<VerifyEmailState> = _state.asStateFlow()

    init {
        screenModelScope.launch {
            DeepLinkBus.pending
                .filterNotNull()
                .filterIsInstance<DeepLink.Verify>()
                .collect { link ->
                    _state.update { it.copy(status = VerifyStatus.Verifying) }
                    authRepo.verifyEmail(link.token).fold(
                        onSuccess = { /* App routes via AuthState */ },
                        onFailure = { e ->
                            val msg = when (val p = ErrorMapper.map(e)) {
                                is UiErrorPlacement.FullScreen -> p.message
                                is UiErrorPlacement.Snackbar -> p.message
                                else -> "Не удалось подтвердить email"
                            }
                            _state.update { it.copy(status = VerifyStatus.Error(msg)) }
                        }
                    )
                    DeepLinkBus.consume(link)
                }
        }
    }

    fun resend() {
        screenModelScope.launch {
            authRepo.resendVerification(_state.value.email)
            _state.update { it.copy(cooldownSec = 300) }
            launch {
                while (_state.value.cooldownSec > 0) {
                    delay(1000)
                    _state.update { it.copy(cooldownSec = it.cooldownSec - 1) }
                }
            }
        }
    }

    fun changeEmail() {
        screenModelScope.launch { authRepo.logout() }
    }
}
```

- [ ] **Step 2: VerifyEmailScreen.kt**

```kotlin
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
                text = if (state.cooldownSec > 0) "Отправить повторно через ${state.cooldownSec} с" else "Отправить повторно",
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
```

- [ ] **Step 3: Koin factory с параметром**

В `appModule()` добавить:
```kotlin
factory { (email: String) -> VerifyEmailScreenModel(email, get()) }
```

- [ ] **Step 4: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/VerifyEmailScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/VerifyEmailScreenModel.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat(ui/auth): VerifyEmailScreen with DeepLinkBus listener + resend cooldown"
```

---

### Task 10.6: ForgotPasswordScreen + ResetPasswordScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ForgotPasswordScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ForgotPasswordScreenModel.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ResetPasswordScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ResetPasswordScreenModel.kt`

- [ ] **Step 1: ForgotPasswordScreenModel.kt**

```kotlin
package com.example.cleancity.ui.feature.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ForgotState(
    val email: String = "",
    val loading: Boolean = false,
    val emailError: String? = null,
    val sent: Boolean = false,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = Validation.emailFormat(email) && !loading && !sent
}

class ForgotPasswordScreenModel(private val authRepo: AuthRepository) : ScreenModel {
    private val _state = MutableStateFlow(ForgotState())
    val state: StateFlow<ForgotState> = _state.asStateFlow()

    fun setEmail(s: String) = _state.update { it.copy(email = s, emailError = null) }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun submit() {
        screenModelScope.launch {
            _state.update { it.copy(loading = true, snackbar = null, emailError = null) }
            authRepo.forgotPassword(_state.value.email).fold(
                onSuccess = { _state.update { it.copy(loading = false, sent = true) } },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlineEmail -> st.copy(loading = false, emailError = placement.message)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            else -> st.copy(loading = false, snackbar = "Что-то пошло не так. Попробуйте ещё раз.")
                        }
                    }
                }
            )
        }
    }
}
```

- [ ] **Step 2: ForgotPasswordScreen.kt**

```kotlin
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
```

- [ ] **Step 3: ResetPasswordScreenModel.kt**

```kotlin
package com.example.cleancity.ui.feature.auth

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResetState(
    val token: String,
    val newPassword: String = "",
    val confirm: String = "",
    val loading: Boolean = false,
    val newPasswordError: String? = null,
    val confirmError: String? = null,
    val fullScreenError: String? = null,
    val success: Boolean = false,
    val snackbar: String? = null,
) {
    val canSubmit: Boolean
        get() = Validation.passwordStrength(newPassword) && newPassword == confirm && !loading
}

class ResetPasswordScreenModel(
    token: String,
    private val authRepo: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(ResetState(token = token))
    val state: StateFlow<ResetState> = _state.asStateFlow()

    fun setNewPassword(s: String) = _state.update {
        it.copy(newPassword = s, newPasswordError = null, confirmError = null)
    }
    fun setConfirm(s: String) = _state.update {
        val err = if (s.isNotEmpty() && s != it.newPassword) "Пароли не совпадают" else null
        it.copy(confirm = s, confirmError = err)
    }
    fun dismissSnackbar() = _state.update { it.copy(snackbar = null) }

    fun submit() {
        val s = _state.value
        screenModelScope.launch {
            _state.update { it.copy(loading = true, snackbar = null, newPasswordError = null, confirmError = null) }
            authRepo.resetPassword(s.token, s.newPassword).fold(
                onSuccess = { _state.update { it.copy(loading = false, success = true) } },
                onFailure = { e ->
                    val placement = ErrorMapper.map(e)
                    _state.update { st ->
                        when (placement) {
                            is UiErrorPlacement.InlinePassword -> st.copy(loading = false, newPasswordError = placement.message)
                            is UiErrorPlacement.FullScreen -> st.copy(loading = false, fullScreenError = placement.message)
                            is UiErrorPlacement.Snackbar -> st.copy(loading = false, snackbar = placement.message)
                            else -> st.copy(loading = false, snackbar = "Что-то пошло не так. Попробуйте ещё раз.")
                        }
                    }
                }
            )
        }
    }
}
```

- [ ] **Step 4: ResetPasswordScreen.kt**

```kotlin
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
```

- [ ] **Step 5: Koin-factory обоих моделей**

В `appModule()`:
```kotlin
factory { ForgotPasswordScreenModel(get()) }
factory { (token: String) -> ResetPasswordScreenModel(token, get()) }
```

- [ ] **Step 6: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ForgotPasswordScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ForgotPasswordScreenModel.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ResetPasswordScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/ResetPasswordScreenModel.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat(ui/auth): ForgotPasswordScreen + ResetPasswordScreen (token via Koin parametersOf)"
```

---

### Task 10.7: LegalScreen + LegalWebView (expect/actual)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LegalScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LegalWebView.kt`
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/auth/LegalWebView.android.kt`

- [ ] **Step 1: LegalScreen.kt (common)**

```kotlin
package com.example.cleancity.ui.feature.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

enum class LegalKind(val path: String, val title: String) {
    Privacy("/legal/privacy", "Политика данных"),
    Terms("/legal/terms", "Условия использования"),
}

class LegalScreen(private val kind: LegalKind) : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(kind.title) },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                LegalWebView(url = currentApiBase() + kind.path)
            }
        }
    }
}
```

- [ ] **Step 2: LegalWebView.kt (common — expect)**

```kotlin
package com.example.cleancity.ui.feature.auth

import androidx.compose.runtime.Composable

@Composable
expect fun LegalWebView(url: String)

@Composable
expect fun currentApiBase(): String
```

- [ ] **Step 3: LegalWebView.android.kt (actual)**

```kotlin
package com.example.cleancity.ui.feature.auth

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.viewinterop.AndroidView
import com.example.cleancity.BuildConfig

@Composable
actual fun LegalWebView(url: String) {
    AndroidView(factory = { ctx ->
        WebView(ctx).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            val allowedHost = Uri.parse(BuildConfig.API_BASE_URL).host
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val host = request?.url?.host ?: return true
                    return host != allowedHost
                }
            }
            loadUrl(url)
        }
    })
}

@Composable
actual fun currentApiBase(): String = BuildConfig.API_BASE_URL
```

- [ ] **Step 4: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LegalScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/LegalWebView.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/auth/LegalWebView.android.kt
git commit -m "feat(ui/auth): LegalScreen with WebView (expect/actual, sandbox restricted)"
```

---

### Task 10.8: MainPlaceholderScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/main/MainPlaceholderScreen.kt`

- [ ] **Step 1: Создать файл**

```kotlin
package com.example.cleancity.ui.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green900

class MainPlaceholderScreen(
    private val isGuest: Boolean,
    private val onPrimaryAction: () -> Unit,
) : Screen {
    @Composable
    override fun Content() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Brush.linearGradient(listOf(Accent, Green400))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🛡", style = MaterialTheme.typography.titleLarge, color = Green900)
                }
                Spacer(Modifier.height(16.dp))
                Text("Чистый Город", style = MaterialTheme.typography.headlineSmall, color = Gray900)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Главный экран появится Day 9",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray500,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                text = if (isGuest) "Войти / Регистрация" else "Выйти",
                onClick = onPrimaryAction,
                backgroundColor = Green700,
                contentColor = Color.White,
            )
        }
    }
}
```

- [ ] **Step 2: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/main/MainPlaceholderScreen.kt
git commit -m "feat(ui/main): MainPlaceholderScreen — stub до Day 9, logout / exit-guest action"
```

---

## Phase 11 — Wiring (App.kt + MainActivity + Manifest)

### Task 11.1: App.kt — реактивная навигация на AuthState

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt`

- [ ] **Step 1: Перезаписать App.kt**

```kotlin
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
import com.example.cleancity.ui.feature.main.MainPlaceholderScreen
import com.example.cleancity.ui.feature.splash.SplashLoaderScreen
import com.example.cleancity.ui.feature.splash.SplashScreen
import com.example.cleancity.ui.theme.CleanCityTheme
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.screen.Screen

@Composable
fun App() {
    CleanCityTheme {
        val authRepo: AuthRepository = koinInject()
        val authState by authRepo.state.collectAsState()
        val coroutineScope = rememberCoroutineScope()

        // First-time-only init
        LaunchedEffect(Unit) { authRepo.init() }

        val initial: Screen = remember(authState) {
            when (val s = authState) {
                AuthState.Loading -> SplashLoaderScreen()
                AuthState.Anonymous -> SplashScreen(onContinueAsGuest = { authRepo.continueAsGuest() })
                AuthState.Guest -> MainPlaceholderScreen(isGuest = true, onPrimaryAction = { authRepo.toAnonymous() })
                is AuthState.NeedsVerification -> VerifyEmailScreen(email = s.email)
                is AuthState.Authenticated -> MainPlaceholderScreen(
                    isGuest = false,
                    onPrimaryAction = { coroutineScope.launch { authRepo.logout() } },
                )
            }
        }

        Navigator(initial) { navigator ->
            // Re-route across major sections when AuthState changes
            LaunchedEffect(authState) {
                val newRoot: Screen? = when (val s = authState) {
                    AuthState.Loading -> SplashLoaderScreen()
                    AuthState.Anonymous -> SplashScreen(onContinueAsGuest = { authRepo.continueAsGuest() })
                    AuthState.Guest -> MainPlaceholderScreen(isGuest = true, onPrimaryAction = { authRepo.toAnonymous() })
                    is AuthState.NeedsVerification -> VerifyEmailScreen(email = s.email)
                    is AuthState.Authenticated -> MainPlaceholderScreen(
                        isGuest = false,
                        onPrimaryAction = { coroutineScope.launch { authRepo.logout() } },
                    )
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
```

**Note:** `koinInject()` для повторного использования singleton `AuthRepository`. `navigator.lastItem::class != newRoot::class` — слабая проверка «уже на нужной секции», она достаточна потому что между секциями экраны разных типов.

- [ ] **Step 2: Добавить Voyager-Koin зависимость**

В `gradle/libs.versions.toml`:
```toml
voyager-koin = { module = "cafe.adriel.voyager:voyager-koin", version.ref = "voyager" }
```

В `composeApp/build.gradle.kts` commonMain:
```kotlin
implementation(libs.voyager.koin)
```

- [ ] **Step 3: Сборка**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt \
        gradle/libs.versions.toml \
        composeApp/build.gradle.kts
git commit -m "feat(app): reactive Navigator routing on AuthState + DeepLink listener for Reset"
```

---

### Task 11.2: MainActivity + AndroidManifest deep-link intent-filters

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Перезаписать MainActivity.kt**

```kotlin
package com.example.cleancity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.cleancity.domain.DeepLink
import com.example.cleancity.domain.DeepLinkBus

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "cleancity") return
        val token = uri.getQueryParameter("token") ?: return
        when (uri.host) {
            "verify" -> DeepLinkBus.emit(DeepLink.Verify(token))
            "reset" -> DeepLinkBus.emit(DeepLink.Reset(token))
        }
    }
}
```

- [ ] **Step 2: Обновить AndroidManifest.xml**

Открыть `composeApp/src/androidMain/AndroidManifest.xml`. Заменить `<activity>` блок для `MainActivity` на:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:label="@string/app_name"
    android:theme="@android:style/Theme.Material.Light.NoActionBar">
    <intent-filter>
        <action android:name="android.intent.action.MAIN"/>
        <category android:name="android.intent.category.LAUNCHER"/>
    </intent-filter>
    <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="cleancity" android:host="verify"/>
    </intent-filter>
    <intent-filter android:autoVerify="false">
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="cleancity" android:host="reset"/>
    </intent-filter>
</activity>
```

Также убедись что `<application android:name=".CleanCityApplication" ...>` в манифесте есть (из Task 8.2).

Добавь `<string name="app_name">Чистый Город</string>` в `composeApp/src/androidMain/res/values/strings.xml` если ещё нет.

- [ ] **Step 3: Сборка APK**

```bash
./gradlew :composeApp:assembleDebug
```
Expected: BUILD SUCCESSFUL. `composeApp/build/outputs/apk/debug/composeApp-debug.apk` существует.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt \
        composeApp/src/androidMain/AndroidManifest.xml \
        composeApp/src/androidMain/res/values/strings.xml
git commit -m "feat(android): MainActivity deep-link handler + manifest intent-filters (verify, reset)"
```

---

## Phase 12 — Manual checkpoint + закрытие Day 8

### Task 12.1: Поднять backend локально и установить APK на эмулятор

**Files:** — (runtime only)

- [ ] **Step 1: Запустить backend**

В отдельном терминале:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
./gradlew :backend:run
```
Дождаться `Application started in X seconds`. Backend слушает `:8080`.

- [ ] **Step 2: Запустить Android-эмулятор**

Через Android Studio AVD Manager или CLI:
```bash
emulator -avd Pixel_API_34   # имя AVD из локальной конфигурации
```
Дождаться boot.

- [ ] **Step 3: Установить APK**

```bash
./gradlew :composeApp:installDebug
```
Expected: BUILD SUCCESSFUL → приложение появилось в списке.

- [ ] **Step 4: Открыть приложение, проверить что SplashScreen рендерится**

Должны быть видны: лого + «Чистый **Город**» + 3 кнопки + disclaimer внизу. Шрифты Unbounded/Golos применены.

---

### Task 12.2: Прогнать Сценарий 1 — Happy path (register → verify → login → logout)

- [ ] **Step 1: Регистрация**

1. Splash → Tap «Регистрация» → открывается RegisterScreen
2. Поля: «Тестовый Пользователь» / `test1@cleancity.local` / `Password123` (без consent — кнопка disabled)
3. Tap consent → кнопка enabled → «Зарегистрироваться» → loading

Ожидаемо: переход на VerifyEmailScreen с email `test1@cleancity.local`.

- [ ] **Step 2: Получить verify-token**

В терминале backend смотришь stdout — найди строку email-templated «Verify your email» с токеном или скопируй токен прямо из DB:
```bash
docker exec -it cleancity_postgres_dev psql -U cleancity -d cleancity \
  -c "SELECT token FROM email_tokens WHERE purpose='VERIFY_EMAIL' ORDER BY created_at DESC LIMIT 1;"
```
Сохрани его в переменную (например `TOKEN=abc123...`).

- [ ] **Step 3: Открыть deep-link через adb**

```bash
adb shell am start -W -a android.intent.action.VIEW -d "cleancity://verify?token=$TOKEN" com.example.cleancity
```
Ожидаемо: приложение в фокусе → VerifyEmailScreen → Verifying → переход на MainPlaceholderScreen.

- [ ] **Step 4: Logout → Login**

1. Tap «Выйти» → SplashScreen
2. Tap «Войти» → ввести `test1@cleancity.local` / `Password123` → Войти → MainPlaceholderScreen

✅ Сценарий 1 пройден.

---

### Task 12.3: Сценарий 2 — Гостевой режим

- [ ] **Step 1: Сценарий**

1. SplashScreen → tap «Зайти как гость»
2. Ожидаемо: MainPlaceholderScreen, внизу кнопка «Войти / Регистрация»
3. Tap → SplashScreen

✅ Сценарий 2 пройден.

---

### Task 12.4: Сценарий 3 — Forgot/Reset password

- [ ] **Step 1: Запросить reset-ссылку**

1. SplashScreen → Войти → LoginScreen
2. Tap «Забыли пароль?» → ForgotPasswordScreen
3. Ввести `test1@cleancity.local` → «Прислать ссылку» → success state с текстом про «если email зарегистрирован»

- [ ] **Step 2: Получить reset-token + открыть deep-link**

```bash
TOKEN=$(docker exec -it cleancity_postgres_dev psql -U cleancity -d cleancity -t \
  -c "SELECT token FROM email_tokens WHERE purpose='RESET_PASSWORD' ORDER BY created_at DESC LIMIT 1;" | tr -d ' \n')
adb shell am start -W -a android.intent.action.VIEW -d "cleancity://reset?token=$TOKEN" com.example.cleancity
```

Ожидаемо: ResetPasswordScreen → ввести `NewPassword456` дважды → «Установить пароль» → snackbar «Пароль обновлён» → LoginScreen.

- [ ] **Step 3: Войти с новым паролем**

LoginScreen → `test1@cleancity.local` / `NewPassword456` → MainPlaceholderScreen.

✅ Сценарий 3 пройден.

---

### Task 12.5: Сценарий 4 — Граничные случаи

- [ ] **Step 1: Login с неверным паролем**

LoginScreen → `test1@cleancity.local` / `WrongPass` → ожидание: inline под полем «Неверный email или пароль», loading исчез.

- [ ] **Step 2: Register на занятый email**

SplashScreen → Регистрация → имя + `test1@cleancity.local` + любой пароль + consent → ожидание: inline под email «Этот email уже зарегистрирован».

- [ ] **Step 3: Resend cooldown**

После регистрации второго аккаунта на VerifyEmailScreen → tap «Отправить повторно» → ожидание: кнопка стала «Отправить повторно через 300 с» disabled, обратный отсчёт.

- [ ] **Step 4: No network**

В Android-эмуляторе → Settings → Network → выключить wifi/cellular. SplashScreen → Войти → submit → ожидание: snackbar «Нет соединения с интернетом».

- [ ] **Step 5: Перезапуск приложения после login**

С активной сессией: закрыть приложение (force stop через `adb shell am force-stop com.example.cleancity`), открыть заново. Ожидание: SplashLoaderScreen → `/users/me` → MainPlaceholderScreen (не SplashScreen!).

- [ ] **Step 6: Имитация expired refresh**

Подключи debug-приложение к Android Studio → Device Explorer → `/data/data/com.example.cleancity/shared_prefs/cleancity_secure.xml` → испортить значение `refresh`. Force-stop, открыть заново. Ожидание: SplashLoaderScreen → `/users/me` 401 → SplashScreen.

✅ Сценарий 4 пройден.

---

### Task 12.6: Final commit + PLAN.md update

**Files:**
- Modify: `docs/PLAN.md` — расставить галочки на Day 8 строках 189–203

- [ ] **Step 1: Открыть PLAN.md, найти секцию «День 8 (15.05) — Mobile setup + auth-экраны»**

Расставить `[x]` на всех пунктах строк 189–203 (включая checkpoint). Если что-то не сделано — оставить `[ ]` и зафиксировать в commit message как known issue.

- [ ] **Step 2: Прогнать все тесты ещё раз**

```bash
./gradlew :composeApp:commonTest :backend:test
```
Expected: BUILD SUCCESSFUL, все тесты зелёные.

- [ ] **Step 3: Final commit + tag**

```bash
git add docs/PLAN.md
git commit -m "docs(plan): close Day 8 — Mobile setup + auth flow + forgot/reset"
git tag -a day8-mobile-auth-complete -m "Day 8 — Mobile auth foundation"
```

- [ ] **Step 4: Merge в main (или PR)**

```bash
git checkout main
git merge --no-ff day8-mobile-auth -m "Day 8 — Mobile setup + auth (Compose Multiplatform → Android)"
```

✅ Day 8 закрыт.

---

## Self-review против спеки

**Spec coverage check:**

| Spec section | Task(s) |
|---|---|
| §1 цель и границы | 12.x checkpoints |
| §2.1–2.8 решения | Phase 0–1 (cleanup, iOS drop, backend endpoint), Phase 4–7 (auth foundation), 10.6 (forgot/reset), 10.7 (Legal) |
| §3 структура файлов | каждая Task создаёт указанные файлы по §3 |
| §4 дизайн-система | Phase 3 (Shapes/Dimens/Type/Theme) + Task 2.3 (шрифты) |
| §5.1 AuthState | Task 4.1 |
| §5.2 ApiClient | Task 6.3 |
| §5.3 API_BASE_URL | Task 2.2 |
| §5.4 TokenStorage | Tasks 5.1/5.2 |
| §5.5 AuthRepository | Task 7.2 |
| §5.6 DeepLinkBus | Task 4.3 |
| §5.7 Validation | Task 4.2 |
| §6.1–6.8 экраны | Tasks 10.2–10.8 |
| §7 error handling | Task 10.1 (ErrorMapper) + интеграция в каждом ScreenModel |
| §8 deep-linking | Task 11.2 |
| §9 backend /users/me | Tasks 1.1–1.4 |
| §10 deps | Tasks 2.1–2.3 |
| §11 тесты | Tasks 1.2/4.2/7.2 + LoginTest/RegisterTest в 10.3/10.4 |
| §11.4 manual DoD | Tasks 12.2–12.5 |
| §12 constraints | Phase 6 (logging filter), Phase 5 (encrypted storage), Phase 11 (no token logs) |
| §13 риски | покрыты митигациями в реализации |
| §14 DoD | Task 12.6 |

**Placeholder scan:** прошёл. Все шаги содержат точные команды, код, или action items с явным «Note:» где нужна проверка по месту.

**Type consistency:**
- `AuthApiContract` / `UserApiContract` — введены в Task 7.1, использованы в 7.2/8.1
- `AuthState` — Task 4.1, 7.2, 11.1
- `DeepLinkBus.emit` / `consume` — Task 4.3, 10.5, 11.1, 11.2
- `Tokens(access, refresh)` — Task 5.1, 5.2, 7.1, 7.2
- `Validation.emailFormat/passwordStrength/fullNameNonBlank` — Task 4.2, 10.3, 10.4, 10.6
- `BuildConfig.API_BASE_URL` / `BuildConfig.IS_DEBUG` — Task 2.2, 6.3, 8.2, 10.7

Внутренне согласовано.

**Готовность плана:** план полный, ~50+ дискретных задач, каждая 2–10 минут работы. Total estimate Day 8 — 10–14 часов чистого времени.

---

Plan complete and saved to `docs/superpowers/plans/2026-05-13-week2-day8-mobile-auth.md`.

# Spec — Week 2 Day 8: Mobile setup + auth-экраны

**Дата:** 2026-05-13
**Целевой день в PLAN.md:** Day 8 (15.05) — Mobile setup + auth screens
**Связанные спеки:** —
**Переопределяет PLAN.md:** строка 196 (LoginScreen) расширяется до 7 экранов с forgot/reset-password; строка 189 уточняется (полная зачистка common-кода composeApp)
**Дополняет SPEC.md:** §4.7 — добавляется `GET /users/me`

---

## 1. Цель и границы

### Цель
Заложить фундамент mobile-приложения CleanCity (KMP / Compose Multiplatform → Android-only на Week 2): чистый стартовый каркас, дизайн-систему по мокапу `mobile-mockup-v3.html`, и полный auth-флоу (splash → register → verify → login → guest-mode + forgot/reset password) поверх боевого backend `/auth/*`.

К концу Day 8 на Android-эмуляторе проходит manual checkpoint: register → email-link → verify → login → main → logout → splash. Гостевой режим работает. Forgot/reset password работает. Все ошибки backend пользователю видны как inline / snackbar / full-screen state (не как crash).

### Что входит
- Полная зачистка старого common-кода `composeApp/` (4-категорная модель, chats, cleanup events)
- Зависимости: Ktor Client (Core/CIO-Android/Auth/Logging/ContentNegotiation), Koin, EncryptedSharedPreferences, Compose Resources (шрифты)
- Bundled-шрифты: Unbounded + Golos Text (`.ttf` в `composeResources/font/`)
- `Theme.kt`/`Color.kt`/`Type.kt`/`Shapes.kt`/`Dimens.kt` — токены 1:1 с CSS-переменными мокапа
- `ApiClient` (Ktor + bearer-auth + auto-refresh с Mutex)
- `TokenStorage` (expect/actual, Android = EncryptedSharedPreferences)
- `AuthRepository` + `AuthState` (Loading/Anonymous/Guest/NeedsVerification/Authenticated)
- 7 экранов: SplashScreen, LoginScreen, RegisterScreen, VerifyEmailScreen, ForgotPasswordScreen, ResetPasswordScreen, LegalScreen, плюс `MainPlaceholderScreen` (stub для Day 9)
- Переиспользуемые компоненты: AuthScaffold, AuthTag, AuthTitle, AuthSub, FormField, PrimaryButton, SecondaryButton, AuthLinkRow, ConsentRow
- Deep-link: 2 intent-filter'а (`cleancity://verify`, `cleancity://reset`) + `DeepLinkBus`
- Унифицированная обработка ApiError по таблице кодов (см. §6)
- `GET /users/me` endpoint в backend + `UserRoutes.kt` + тест
- Unit-тесты: Validation, AuthRepository, LoginScreenModel, RegisterScreenModel
- Compose @Preview для всех экранов и базовых компонентов (Android only)

### Что НЕ входит
- ❌ iOS-сборка (Q4 — iOS дропается на Week 2, `iosMain/` и iOS-блок в gradle удаляются)
- ❌ MapScreen / FeedScreen / CreateComplaintScreen / NotificationsScreen — Day 9–12
- ❌ FCM / push-уведомления — Day 12
- ❌ Compose UI-тесты (`androidTest/`) — Day 14 buffer / backlog
- ❌ Screenshot/snapshot тесты — backlog
- ❌ Dark mode — Day 14 buffer
- ❌ Accessibility / TalkBack — backlog после защиты
- ❌ 2FA admin-флоу — это web-admin (Week 3)

---

## 2. Зафиксированные решения (через брейншторм)

### 2.1 Мокап vs PLAN.md (Q1)
**Решение:** мокап-v3 — стайл-гайд (цвета, типографика, радиусы, паддинги, структура `.auth-screen`). PLAN.md — фича-спека. Где мокап не покрывает (гостевая кнопка на splash, consent-чекбокс на register, VerifyEmailScreen, LegalScreen, ссылка «Забыли пароль?») — рисуем в той же визуальной грамматике.

### 2.2 Зачистка существующего composeApp (Q2)
**Решение:** полная зачистка common-кода. Удаляем `data/InMemoryRepository.kt`, `data/SampleData.kt`, `model/Models.kt`, весь `ui/map/` (common-часть), `ui/navigation/MainTabScreen.kt`. Сохраняем `androidMain/.../ui/map/YandexMapView.android.kt` и `MapSearchProvider.android.kt` в `androidMain/.../legacy/` — Day 9 их вытащит и обернёт в новые `expect`.

### 2.3 Backend (Q3)
**Решение:** реальный backend сразу. `Ktor Client` с `bearer-auth` + `auto-refresh` interceptor. `BuildConfig.API_BASE_URL` с override через `local.properties`. Никакого in-memory fake.

### 2.4 iOS (Q4)
**Решение:** дропаем iOS из composeApp на Week 2. iOS-блок в `kotlin {}` удаляется, `iosMain/` переезжает в `archive/` (или удаляется). Все зависимости — `androidMain` или чистый `commonMain`.

### 2.5 LegalScreen (Q5)
**Решение:** WebView + backend `/legal/privacy` и `/legal/terms` (markdown→HTML на бэке). Реализация через `expect/actual` — Android `AndroidView(WebView)`.

### 2.6 Deep-link схема (Q6)
**Решение:** custom URI scheme `cleancity://verify?token=XYZ` и `cleancity://reset?token=XYZ`. Без HTTPS App Links на Day 8 — переход на них опционально в Day 13 после генерации release-keystore.

### 2.7 Forgot/reset password (доп. вопрос)
**Решение:** включаем в Day 8 — ссылка «Забыли пароль?» на LoginScreen → ForgotPasswordScreen → email → SuccessState. Deep-link `cleancity://reset?token=...` → ResetPasswordScreen → новый пароль 2 раза → POST `/auth/reset-password` → LoginScreen + snackbar.

### 2.8 Endpoint `/users/me` (после ревью §4)
**Решение:** добавляем `GET /users/me` в backend. Используется на `AuthRepository.init()` для валидации сохранённых токенов при старте приложения. Заодно — фундамент для `POST /users/me/push-token` (Day 12).

---

## 3. Структура файлов

```
composeApp/src/commonMain/kotlin/com/example/cleancity/
  App.kt                                    — root @Composable, подписан на AuthState, выбирает стартовый экран
  di/
    AppModule.kt                            — Koin-модуль: HttpClient, TokenStorage (expect-factory), AuthRepository, ScreenModels
  data/
    network/
      ApiClient.kt                          — HttpClient(CIO|OkHttp) + ContentNegotiation + Auth(bearer) + Logging + DefaultRequest + HttpTimeout + HttpResponseValidator
      AuthApi.kt                            — register / verifyEmail / resendVerification / login / refresh / logout / forgotPassword / resetPassword
      UserApi.kt                            — me() (GET /users/me)
      LegalApi.kt                           — privacy() / terms() (для опционального fallback, основной канал — WebView)
      ApiError.kt                           — @Serializable + ApiException + responseValidator helper
    storage/
      TokenStorage.kt                       — expect interface + Tokens data class
    repository/
      AuthRepository.kt                     — init / register / login / verifyEmail / resendVerification / forgotPassword / resetPassword / continueAsGuest / logout; StateFlow<AuthState>
  domain/
    AuthState.kt                            — sealed interface
    Validation.kt                           — emailFormat / passwordStrength / fullNameNonBlank — pure functions
    DeepLinkBus.kt                          — object с MutableSharedFlow<DeepLink>, два типа: Verify(token) / Reset(token)
  ui/
    theme/
      Color.kt                              — БЕЗ ИЗМЕНЕНИЙ (уже 1:1 с мокапом)
      Type.kt                               — Unbounded + Golos Text через Compose Resources
      Shapes.kt                             — radius_sm/md/lg/xl (10/16/24/32 dp)
      Dimens.kt                             — spaceXs/sm/md/lg/xl/xxl (4/8/16/24/32/40 dp)
      Theme.kt                              — MaterialTheme wrapper (lightColorScheme + typography + shapes)
    components/
      AuthScaffold.kt                       — белый фон, padding 40/28/40, optional back-кнопка 40dp, scrollable
      AuthTag.kt                            — green-pill с точкой слева
      AuthTitle.kt + AuthSub.kt             — типографика
      FormField.kt                          — label + OutlinedTextField + error/hint
      PrimaryButton.kt                      — 54dp, radius 16, optional loading-spinner
      SecondaryButton.kt                    — 54dp, transparent + border
      AuthLinkRow.kt                        — center-aligned prefix + clickable link
      ConsentRow.kt                         — Checkbox + AnnotatedString с двумя ClickableText span
    feature/
      splash/
        SplashScreen.kt                     — Voyager Screen
      auth/
        LoginScreen.kt + LoginScreenModel.kt
        RegisterScreen.kt + RegisterScreenModel.kt
        VerifyEmailScreen.kt + VerifyEmailScreenModel.kt
        ForgotPasswordScreen.kt + ForgotPasswordScreenModel.kt
        ResetPasswordScreen.kt + ResetPasswordScreenModel.kt
        LegalScreen.kt + LegalWebView.kt (expect Composable)
      main/
        MainPlaceholderScreen.kt            — stub до Day 9

composeApp/src/androidMain/kotlin/com/example/cleancity/
  CleanCityApplication.kt                   — startKoin(modules = [appModule, androidModule])
  MainActivity.kt                           — setContent { App() } + onNewIntent → DeepLinkBus
  di/
    AndroidModule.kt                        — Context, TokenStorage актуал, OkHttp engine config
  data/storage/
    TokenStorage.android.kt                 — actual class AndroidTokenStorage(context) : TokenStorage
  ui/feature/auth/
    LegalWebView.android.kt                 — actual @Composable LegalWebView(url) = AndroidView { WebView(...) }
  legacy/                                   — переносим существующие YandexMapView.android.kt + MapSearchProvider.android.kt сюда (Day 9 их вытащит)

composeApp/src/androidMain/res/
  values/strings.xml                        — без изменений (или добавить "app_name" если нет)
  drawable/                                 — app icon (можно дефолт пока)

composeApp/src/commonMain/composeResources/font/
  unbounded_regular.ttf / _semibold.ttf / _bold.ttf
  golos_text_regular.ttf / _medium.ttf / _semibold.ttf

composeApp/src/commonTest/kotlin/com/example/cleancity/
  domain/ValidationTest.kt
  data/repository/AuthRepositoryTest.kt + FakeAuthApi.kt + FakeTokenStorage.kt
  ui/feature/auth/RegisterScreenModelTest.kt + LoginScreenModelTest.kt

backend/src/main/kotlin/com/example/cleancity/users/
  UserRoutes.kt                             — НОВЫЙ FILE: route("/users") { authenticate("auth-jwt") { get("/me") {...} } }
  
backend/src/test/kotlin/com/example/cleancity/users/
  UserRoutesTest.kt                         — НОВЫЙ FILE: 401 без токена / 200 с валидным / 401 с истёкшим

backend/src/main/kotlin/com/example/cleancity/Application.kt
  — добавить вызов userRoutes() в module()

docs/SPEC.md
  — §4.7 строка `GET /users/me — текущий пользователь по JWT. 200: UserResponse.`
```

Удаляются (полная зачистка common):
- `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt` (переписывается)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/data/InMemoryRepository.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/data/SampleData.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/model/Models.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/**` (всё common)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/navigation/MainTabScreen.kt`

Дропаются (iOS):
- Весь `composeApp/src/iosMain/`
- iOS-блок в `composeApp/build.gradle.kts:25-31`

---

## 4. Дизайн-система

### 4.1 Цвета — БЕЗ ИЗМЕНЕНИЙ
`Color.kt` уже 1:1 с CSS-переменными мокапа (`Green900..Green50`, `Accent/AccentDark`, `Red/Blue/Amber/Purple`, `Gray50..Gray900`).

### 4.2 Шрифты
Bundled через Compose Resources (`compose.components.resources` уже в deps).

| Файл | Использование |
|---|---|
| `unbounded_regular.ttf` | редко |
| `unbounded_semibold.ttf` | `btn-primary` текст, `auth-tag` |
| `unbounded_bold.ttf` | `auth-title`, `splash-title` |
| `golos_text_regular.ttf` | `auth-sub`, `form-input`, body везде |
| `golos_text_medium.ttf` | secondary nav links |
| `golos_text_semibold.ttf` | `form-label`, link text |

Шрифты — Google Fonts, OFL-1.1 (legal-safe). Скачиваются один раз при разработке, дальше живут в APK. Никаких runtime-запросов к Google Fonts CDN (важно для российской аудитории).

### 4.3 Типографические токены
`CleanCityTypography` (Material3 `Typography`):

| Material slot | size / lineHeight | weight | family | letterSpacing |
|---|---|---|---|---|
| `displayMedium` | 28 / 34 | 700 | Unbounded | 0 |
| `headlineMedium` | 26 / 31 | 700 | Unbounded | 0 |
| `headlineSmall` | 18 / 24 | 600 | Unbounded | 0 |
| `titleLarge` | 16 / 22 | 600 | Unbounded | 0 |
| `bodyLarge` | 15 / 22 | 400 | Golos | 0 |
| `bodyMedium` | 14 / 21 | 400 | Golos | 0 |
| `bodySmall` | 12 / 18 | 400 | Golos | 0 |
| `labelLarge` | 13 / 18 | 600 | Unbounded | 0.65 |
| `labelMedium` | 12 / 16 | 600 | Golos | 0.6 |
| `labelSmall` | 11 / 14 | 600 | Golos | 0.55 |

### 4.4 Shapes / Dimens
`Shapes(small=10dp, medium=16dp, large=24dp, extraLarge=32dp)`.
`Dimens(xs=4, sm=8, md=16, lg=24, xl=32, xxl=40 dp)`.

### 4.5 Theme
```kotlin
@Composable
fun CleanCityTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = lightColorScheme(
      primary = Green700, onPrimary = White,
      secondary = Accent, onSecondary = Green900,
      background = Gray50, onBackground = Gray900,
      surface = White, onSurface = Gray900,
      error = Red, onError = White,
    ),
    typography = CleanCityTypography,
    shapes = CleanCityShapes,
    content = content,
  )
}
```

---

## 5. Архитектура data/domain

### 5.1 AuthState
```kotlin
sealed interface AuthState {
  data object Loading : AuthState
  data object Anonymous : AuthState
  data object Guest : AuthState
  data class NeedsVerification(val email: String) : AuthState
  data class Authenticated(val user: UserResponse) : AuthState
}
```

`App.kt` подписан на `AuthRepository.state: StateFlow<AuthState>` через `collectAsState()` и выбирает стартовый Voyager Screen:

| State | Стартовый Screen |
|---|---|
| `Loading` | `SplashLoaderScreen` (splash без кнопок, индикатор) |
| `Anonymous` | `SplashScreen` (3 кнопки) |
| `Guest` | `MainPlaceholderScreen` |
| `NeedsVerification(email)` | `VerifyEmailScreen(email)` |
| `Authenticated(user)` | `MainPlaceholderScreen` |

**Реактивная навигация при смене state.** `App.kt` строит один `Navigator` со стартовым экраном по текущему `authState` при первой композиции. Дальше при смене state, через `LaunchedEffect(authState)`, делает `navigator.replaceAll(...)` для **межсекционных** переходов (Anonymous ↔ Guest ↔ Authenticated ↔ NeedsVerification). Внутри одной секции (например, Splash → Login → ForgotPassword) — обычные `nav.push/pop`, состояние стека сохраняется. Это даёт автоматический ре-роутинг при `authRepo.logout()` / `authRepo.continueAsGuest()` / успешном login без явных nav-вызовов из ScreenModel.

### 5.2 ApiClient
```kotlin
fun createHttpClient(tokenStorage: TokenStorage, onAuthFailure: () -> Unit): HttpClient =
  HttpClient(OkHttp) {
    expectSuccess = true
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    install(Logging) {
      level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
      sanitizeHeader { it == HttpHeaders.Authorization }
      filter { req -> !req.url.encodedPath.startsWith("/auth/") }  // не логируем auth-body
    }
    install(DefaultRequest) {
      url(BuildConfig.API_BASE_URL)
      contentType(ContentType.Application.Json)
    }
    install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 5_000 }
    install(Auth) {
      bearer {
        loadTokens { tokenStorage.read()?.let { BearerTokens(it.access, it.refresh) } }
        refreshTokens {
          refreshMutex.withLock {
            val current = tokenStorage.read() ?: run { onAuthFailure(); return@refreshTokens null }
            try {
              val resp = client.post("/auth/refresh") {
                markAsRefreshTokenRequest()
                setBody(RefreshTokenRequest(current.refresh))
              }.body<AuthResponse>()
              tokenStorage.write(resp.accessToken, resp.refreshToken)
              BearerTokens(resp.accessToken, resp.refreshToken)
            } catch (e: Throwable) {
              tokenStorage.clear()
              onAuthFailure()
              null
            }
          }
        }
      }
    }
    HttpResponseValidator {
      validateResponse { resp ->
        if (!resp.status.isSuccess()) {
          val err = runCatching { resp.body<ApiError>() }
            .getOrDefault(ApiError("UNKNOWN", "HTTP ${resp.status.value}"))
          throw ApiException(err, resp.status.value)
        }
      }
    }
  }
```

`onAuthFailure` — callback из Koin: `{ authRepository._state.value = AuthState.Anonymous }`. Это разрывает циклическую зависимость HttpClient ↔ AuthRepository.

### 5.3 API_BASE_URL
В `composeApp/build.gradle.kts` добавляем:
```kotlin
val apiBaseUrl: String =
  secrets.getProperty("API_BASE_URL")
    ?: System.getenv("API_BASE_URL")
    ?: "http://10.0.2.2:8080"  // Android emulator default
buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
buildConfigField("boolean", "DEBUG", "${!isReleaseBuild}")
```

Для реального устройства Жасмин кладёт IP в `local.properties`:
```
API_BASE_URL=http://192.168.1.42:8080
```

### 5.4 TokenStorage
```kotlin
// commonMain
interface TokenStorage {
  suspend fun read(): Tokens?
  suspend fun write(access: String, refresh: String)
  suspend fun clear()
}
data class Tokens(val access: String, val refresh: String)
expect fun createTokenStorage(): TokenStorage  // factory из Koin

// androidMain
class AndroidTokenStorage(context: Context) : TokenStorage {
  private val prefs by lazy {
    EncryptedSharedPreferences.create(
      context, "cleancity_secure",
      MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }
  override suspend fun read(): Tokens? = withContext(Dispatchers.IO) {
    val a = prefs.getString("access", null) ?: return@withContext null
    val r = prefs.getString("refresh", null) ?: return@withContext null
    Tokens(a, r)
  }
  override suspend fun write(access: String, refresh: String) = withContext(Dispatchers.IO) {
    prefs.edit().putString("access", access).putString("refresh", refresh).apply()
  }
  override suspend fun clear() = withContext(Dispatchers.IO) {
    prefs.edit().clear().apply()
  }
}
```

Никаких `Log.d`/`println` с содержимым в `read/write/clear`.

### 5.5 AuthRepository
```kotlin
class AuthRepository(
  private val api: AuthApi,
  private val userApi: UserApi,
  private val storage: TokenStorage,
) {
  private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
  val state: StateFlow<AuthState> = _state.asStateFlow()

  suspend fun init() {
    val tokens = storage.read()
    if (tokens == null) { _state.value = AuthState.Anonymous; return }
    runCatching { userApi.me() }
      .onSuccess { _state.value = AuthState.Authenticated(it) }
      .onFailure { storage.clear(); _state.value = AuthState.Anonymous }
  }

  suspend fun register(email, password, fullName): Result<Unit> = runCatching {
    api.register(RegisterRequest(email.trim(), password, fullName.trim(), acceptedTerms = true))
    _state.value = AuthState.NeedsVerification(email.trim())
  }

  suspend fun verifyEmail(token: String): Result<Unit> = runCatching {
    val resp = api.verifyEmail(VerifyEmailRequest(token))
    storage.write(resp.accessToken, resp.refreshToken)
    _state.value = AuthState.Authenticated(resp.user)
  }

  suspend fun resendVerification(email: String): Result<Unit> = runCatching {
    api.resendVerification(ResendVerificationRequest(email))
  }

  suspend fun login(email, password): Result<Unit> = runCatching {
    val resp = api.login(LoginRequest(email.trim(), password))
    storage.write(resp.accessToken, resp.refreshToken)
    _state.value = AuthState.Authenticated(resp.user)
  }

  suspend fun forgotPassword(email: String): Result<Unit> = runCatching {
    api.forgotPassword(ForgotPasswordRequest(email.trim()))  // 200 всегда
  }

  suspend fun resetPassword(token, newPassword): Result<Unit> = runCatching {
    api.resetPassword(ResetPasswordRequest(token, newPassword))
  }

  fun continueAsGuest() { _state.value = AuthState.Guest }
  fun toAnonymous() { _state.value = AuthState.Anonymous }

  suspend fun logout() {
    runCatching { api.logout() }  // лучшее усилие; даже при сетевой ошибке чистим локально
    storage.clear()
    _state.value = AuthState.Anonymous
  }
}
```

### 5.6 DeepLinkBus
```kotlin
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

`StateFlow<DeepLink?>` (а не `SharedFlow`) — чтобы **переживать cold-start**: если пользователь тапнул `cleancity://verify?token=...` при выключенном приложении, `MainActivity.onCreate` вызывает `handleIntent` ДО того, как Compose успел подписаться. `StateFlow` хранит последнее значение — `App.kt` / `VerifyEmailScreenModel` подхватят его при первой подписке. После обработки подписчик вызывает `DeepLinkBus.consume(link)` — чтобы повторное подключение наблюдателей не сработало ещё раз.

`MainActivity.onNewIntent(intent)` парсит `intent.data` (scheme=cleancity, host=verify|reset) и вызывает `DeepLinkBus.emit(...)`. `VerifyEmailScreenModel` и root `App.kt` (для Reset) подписаны на `DeepLinkBus.pending`.

### 5.7 Validation (pure functions)
```kotlin
object Validation {
  fun emailFormat(email: String): Boolean =
    email.contains('@') && email.contains('.') && email.length in 5..255
  fun passwordStrength(p: String): Boolean = p.length >= 8 && p.length <= 100
  fun fullNameNonBlank(n: String): Boolean = n.trim().isNotEmpty()
}
```

---

## 6. UI — экраны (детали)

### 6.1 SplashScreen
Тёмный градиент `Green800→Green900`, два декоративных круга (`Accent` @ alpha 0.08, blur), центральная иконка-щит в Accent-квадрате 88dp radius 28dp, заголовок `Чистый Город` (span «Город» в `Accent`), подзаголовок 14sp white@55.

Кнопки (Bottom Column gap 12dp):
- `PrimaryButton("Войти", bg=Accent, textColor=Green900)` → `nav.push(LoginScreen)`
- `SecondaryButton("Регистрация")` → `nav.push(RegisterScreen)`
- `TextButton("Зайти как гость", color=White@55)` → `authRepo.continueAsGuest()`

Под кнопками — disclaimer 11sp white@30: «Продолжая, вы соглашаетесь с **Условиями** и **Политикой данных**». Span-ы кликабельны → `nav.push(LegalScreen(Terms|Privacy))`.

Padding: `60dp / 32dp / 48dp`.

### 6.2 LoginScreen
`AuthScaffold` (back → SplashScreen):
- `AuthTag("Вход")`
- `AuthTitle("С возвращением!")`
- `AuthSub("Войдите, чтобы продолжить.")`
- `FormField("EMAIL", state.email, keyboardType=Email, autofill=EmailAddress)`
- `FormField("ПАРОЛЬ", state.password, keyboardType=Password, autofill=Password, error=state.errors.password)`
- Row(Arrangement.End) + TextButton(«Забыли пароль?», color=Green600, 12sp, weight=500) → `nav.push(ForgotPasswordScreen)`
- `PrimaryButton("Войти", bg=Green700, textColor=White, shadow=Green900@30, enabled=canSubmit && !loading, loading=state.loading)` → `vm.submit()`
- `AuthLinkRow("Нет аккаунта?", "Зарегистрироваться")` → `nav.replace(RegisterScreen)`

`LoginScreenModel.canSubmit = email.isNotBlank() && password.length >= 8`.

### 6.3 RegisterScreen
`AuthScaffold` (back → SplashScreen):
- `AuthTag("Создание аккаунта")`
- `AuthTitle("Присоединяйтесь к\nчистому городу")`
- `AuthSub("За 30 секунд — и вы можете влиять на состояние Сочи.")`
- `FormField("ИМЯ", state.fullName, autofill=PersonFullName)`
- `FormField("EMAIL", state.email, keyboardType=Email, autofill=EmailAddress)`
- `FormField("ПАРОЛЬ", state.password, keyboardType=Password, hint="Минимум 8 символов")`
- `ConsentRow(checked=state.consent, text=AnnotatedString("Я принимаю Условия и Политику обработки данных"))` — span-ы «Условия» и «Политику обработки данных» открывают LegalScreen(Terms) / LegalScreen(Privacy)
- `PrimaryButton("Зарегистрироваться", bg=Green700, textColor=White, enabled=canSubmit)` → `vm.submit()`
- `AuthLinkRow("Уже есть аккаунт?", "Войти")` → `nav.replace(LoginScreen)`

`canSubmit = Validation.emailFormat(email) && Validation.passwordStrength(password) && Validation.fullNameNonBlank(fullName) && consent`.

После успеха → `AuthState.NeedsVerification(email)` → `App.kt` сменит на VerifyEmailScreen.

### 6.4 VerifyEmailScreen(email)
`AuthScaffold(onBack=null)` — back-кнопка скрыта.
- `AuthTag("Подтверждение")`
- `AuthTitle("Проверьте почту")`
- `AuthSub("Мы отправили письмо на $email. Откройте письмо и нажмите кнопку.")`
- Зона состояния:
  - `Waiting`: иконка-letter + текст «Откройте письмо на почте»
  - `Verifying`: `CircularProgressIndicator` + «Проверяем токен...»
  - `Error(message)`: иконка-крест + текст + кнопка «Запросить новую ссылку» → resend + reset state в Waiting
- `SecondaryButton` text:
  - `cooldownSec > 0` → «Отправить повторно через $X с», disabled
  - `cooldownSec == 0` → «Отправить повторно», enabled → `vm.resend()` → cooldown 300с
- `TextButton("Изменить email", color=Gray500)` → `authRepo.logout()` + `nav.popAll()` + `nav.push(SplashScreen)`

`VerifyEmailScreenModel` слушает `DeepLinkBus.events`:
```kotlin
init {
  screenModelScope.launch {
    DeepLinkBus.events.collect { link ->
      if (link is DeepLink.Verify) {
        state.update { it.copy(status = Verifying) }
        authRepo.verifyEmail(link.token).fold(
          onSuccess = { /* App.kt → MainPlaceholderScreen */ },
          onFailure = { e -> state.update { it.copy(status = Error(mapErrorMessage(e))) } }
        )
      }
    }
  }
}
```

### 6.5 ForgotPasswordScreen
`AuthScaffold` (back → LoginScreen):
- `AuthTag("Сброс пароля")`
- `AuthTitle("Забыли пароль?")`
- `AuthSub("Введите email — мы отправим ссылку для сброса.")`
- `FormField("EMAIL", state.email, keyboardType=Email)`
- `PrimaryButton("Прислать ссылку", bg=Green700, enabled=canSubmit, loading=state.loading)` → `vm.submit()`

После 200 экран меняет содержимое:
- иконка letter в Green100-квадрате
- title «Письмо отправлено»
- sub «Если такой email зарегистрирован, мы прислали на него ссылку для сброса. Проверьте почту.» (текст обязателен — user-enumeration protection)
- `PrimaryButton("Вернуться к входу")` → `nav.popUntil(LoginScreen::class)`

### 6.6 ResetPasswordScreen(token)
Открывается только через `DeepLinkBus.events` (`DeepLink.Reset`). `App.kt` обрабатывает: если на любом экране пришёл Reset — `navigator.replaceAll(ResetPasswordScreen(token))`.

`AuthScaffold(onBack=null)`:
- `AuthTag("Новый пароль")`
- `AuthTitle("Создайте новый пароль")`
- `AuthSub("Минимум 8 символов.")`
- `FormField("НОВЫЙ ПАРОЛЬ", state.newPassword, keyboardType=Password)`
- `FormField("ПОВТОРИТЕ ПАРОЛЬ", state.confirm, keyboardType=Password, error=if (mismatch) "Пароли не совпадают" else null)`
- `PrimaryButton("Установить пароль", bg=Green700, enabled=canSubmit)` → `vm.submit()`

После успеха: snackbar «Пароль обновлён» + `nav.replaceAll(LoginScreen)`. На `EMAIL_TOKEN_EXPIRED`/`INVITE_TOKEN_INVALID` — full-screen error «Ссылка устарела. Запросите новую.» + кнопка «Запросить новую» → `nav.replaceAll(ForgotPasswordScreen)`.

### 6.7 LegalScreen(kind)
```kotlin
enum class LegalKind(val path: String, val title: String) {
  Privacy("/legal/privacy", "Политика данных"),
  Terms("/legal/terms", "Условия использования"),
}
```
- `TopAppBar(title=kind.title, navIcon=back)` → `nav.pop()`
- `LegalWebView(url = BuildConfig.API_BASE_URL + kind.path)` — expect/actual

`LegalWebView.android.kt`:
```kotlin
@Composable actual fun LegalWebView(url: String) {
  AndroidView(factory = { ctx ->
    WebView(ctx).apply {
      settings.javaScriptEnabled = false
      settings.allowFileAccess = false
      settings.allowContentAccess = false
      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
          val host = request?.url?.host ?: return true
          return host != Uri.parse(BuildConfig.API_BASE_URL).host  // только наш домен
        }
      }
      loadUrl(url)
    }
  })
}
```

### 6.8 MainPlaceholderScreen
- Центр: иконка-щит 64dp + «Чистый Город» Unbounded 22sp Green900 + «Главный экран появится Day 9» 14sp Gray500
- Bottom: `PrimaryButton(if (Guest) "Войти / Регистрация" else "Выйти")` → `authRepo.toAnonymous()` или `authRepo.logout()`

---

## 7. Error handling

### 7.1 ApiError модель
```kotlin
@Serializable data class ApiError(
  val code: String,
  val message: String,
  val details: JsonObject? = null,
)
class ApiException(val error: ApiError, val httpStatus: Int) : RuntimeException(error.message)
```

Парсинг — через `HttpResponseValidator.validateResponse` (см. §5.2). Невалидный JSON ответа → fallback `ApiError("UNKNOWN", "HTTP $code")`.

### 7.2 Маппинг error → UI

| `code` | Где | Текст |
|---|---|---|
| `EMAIL_NOT_VERIFIED` | inline на LoginScreen (под полем пароля) | «Подтвердите email. Письмо отправлено на $email.» + inline TextButton «Прислать ещё раз» — дёргает resendVerification + запускает cooldown |
| `INVALID_CREDENTIALS` | inline под полем пароля LoginScreen | «Неверный email или пароль» |
| `EMAIL_ALREADY_EXISTS` | inline под полем email RegisterScreen | «Этот email уже зарегистрирован.» + TextButton «Войти» → nav.replace(LoginScreen) |
| `EMAIL_INVALID_FORMAT` | inline под полем email | «Неверный формат email» |
| `WEAK_PASSWORD` | inline под полем пароля | server-message (бэк сам формулирует) |
| `RATE_LIMITED` | Snackbar | «Слишком много попыток. Попробуйте через минуту.» |
| `ACCOUNT_FROZEN` | Snackbar после login | «Аккаунт временно заблокирован. Свяжитесь с поддержкой.» |
| `EMAIL_TOKEN_EXPIRED` / `INVITE_TOKEN_INVALID` | full-screen error state на VerifyEmail/Reset | «Ссылка устарела. Запросите новую.» + кнопка действия |
| `UNKNOWN` или любой другой | Snackbar | **«Что-то пошло не так. Попробуйте ещё раз.»** (НЕ показываем server-message — может содержать тех-детали) |

### 7.3 Network ошибки (до ApiException)

| Исключение | UI |
|---|---|
| `IOException`, `ConnectException`, `UnknownHostException` | Snackbar «Нет соединения с интернетом» |
| `HttpRequestTimeoutException`, `SocketTimeoutException` | Snackbar «Сервер не отвечает. Попробуйте позже.» |
| `SerializationException` или любой `Throwable` без ApiException | Snackbar «Что-то пошло не так. Попробуйте ещё раз.» |

### 7.4 Loading state на формах
- `PrimaryButton(loading=true)` → текст заменяется на `CircularProgressIndicator(color=textColor, size=18dp)`, кнопка disabled
- Поля не блокируются (юзер может править)
- Повторный submit заблокирован, пока `loading=true`

### 7.5 Resend cooldown
- 300 секунд (`/auth/resend-verification` rate-limit 1/5min из SPEC §4.1)
- Локальный таймер в `VerifyEmailScreenModel` (decrement каждую секунду через `delay(1000)`)
- Cooldown сбрасывается при перезапуске приложения (хранение в памяти ScreenModel)

### 7.6 Refresh-логика — защита от бесконечного цикла
1. `markAsRefreshTokenRequest()` — Ktor не ретраит `/auth/refresh`
2. `Mutex` вокруг `refreshTokens {}` — параллельные 401 ждут одного refresh
3. Provider при провале возвращает `null` → Ktor пробрасывает оригинальную 401 → `AuthRepository._state.value = Anonymous` через `onAuthFailure` callback → UI ловит и переключается на SplashScreen

### 7.7 Логирование (release vs debug)
- **debug** (`BuildConfig.DEBUG = true`): Ktor `LogLevel.HEADERS`, `e.printStackTrace()` в catch-блоках разрешён. Auth-body фильтруется (`filter { req -> !req.url.encodedPath.startsWith("/auth/") }`). Header `Authorization` всегда sanitized.
- **release**: Ktor `LogLevel.NONE`. В catch-блоках — короткий безопасный лог через `println` или `Log.w("AuthRepo", "register failed: code=${e.error.code}")` (только `code` и `httpStatus`, без `message` если может содержать персональные данные, без stacktrace, без request/response body, без токенов/паролей).

### 7.8 TokenStorage logging
В `AndroidTokenStorage.read/write/clear` — **никаких** `Log.*`, `println`, `System.out.println` с содержимым. Допустим `Log.d("AuthRepo", "Tokens written")` без значений.

---

## 8. Deep-linking

### 8.1 AndroidManifest.xml
```xml
<activity android:name=".MainActivity"
          android:launchMode="singleTask"
          android:exported="true">
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

### 8.2 MainActivity handler
```kotlin
override fun onCreate(...) {
  setContent { App() }
  handleIntent(intent)
}
override fun onNewIntent(intent: Intent) {
  super.onNewIntent(intent)
  setIntent(intent)
  handleIntent(intent)
}
private fun handleIntent(intent: Intent) {
  val uri = intent.data ?: return
  if (uri.scheme != "cleancity") return
  val token = uri.getQueryParameter("token") ?: return
  lifecycleScope.launch {
    when (uri.host) {
      "verify" -> DeepLinkBus.emit(DeepLink.Verify(token))
      "reset" -> DeepLinkBus.emit(DeepLink.Reset(token))
    }
  }
}
```

`App.kt` (root) тоже слушает `DeepLinkBus.events`:
- Любой `Reset(token)` на любом экране → `navigator.replaceAll(ResetPasswordScreen(token))`
- `Verify(token)` обрабатывается только в `VerifyEmailScreenModel` (если приложение не на этом экране — игнорируется, пользователь должен быть в правильном flow)

### 8.3 Тестирование deep-link
Через `adb`:
```
adb shell am start -W -a android.intent.action.VIEW -d "cleancity://verify?token=ABC123" com.example.cleancity
adb shell am start -W -a android.intent.action.VIEW -d "cleancity://reset?token=XYZ789" com.example.cleancity
```

Токены берутся из stdout backend (на dev — email отправляется в лог).

---

## 9. Backend изменения

### 9.1 `backend/src/main/kotlin/com/example/cleancity/users/UserRoutes.kt` (НОВЫЙ)
```kotlin
package com.example.cleancity.users

import com.example.cleancity.auth.user
import com.example.cleancity.shared.models.UserResponse
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.userRoutes() {
  routing {
    route("/users") {
      authenticate("auth-jwt") {
        get("/me") {
          val user = call.user()  // существующий extension — извлекает текущего юзера из JWT
          call.respond(user.toResponse())
        }
      }
    }
  }
}
```

Если `user.toResponse()` не существует — берём поля из `User` доменной модели и собираем `UserResponse(id, email, fullName, role, district, emailVerified, ...)` (модель уже описана в `shared/models/UserResponse.kt`).

### 9.2 `backend/src/main/kotlin/com/example/cleancity/Application.kt` — добавить
```kotlin
fun Application.module() {
  // ... existing ...
  userRoutes()
}
```

### 9.3 `backend/src/test/kotlin/com/example/cleancity/users/UserRoutesTest.kt` (НОВЫЙ)
3 кейса:
- `GET /users/me` без `Authorization` → 401
- `GET /users/me` с истёкшим/невалидным JWT → 401
- `GET /users/me` с валидным JWT (resident) → 200 + правильный email/role
- `GET /users/me` для admin → правильная роль

Использует существующий тестовый аппарат `AuthTestSupport` (из `AuthSecurityTest` / `AuthServiceTest`).

### 9.4 `docs/SPEC.md` — добавить строку в §4.7
В таблицу «4.7 Прочее» (или новую секцию «4.7a Users»):
```
| `GET` | `/users/me` | Резидент+ | Текущий пользователь по JWT. 200: UserResponse. 401 если токен невалиден/истёк. |
```

---

## 10. Зависимости (build)

### 10.1 `gradle/libs.versions.toml` — добавить
```toml
[versions]
ktor = "2.3.12"
koin = "3.5.6"
androidx-security = "1.1.0-alpha06"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-client-logging = { module = "io.ktor:ktor-client-logging", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }

koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }

androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "androidx-security" }
```

Если какие-то версии уже там — переиспользуем; не дублируем.

### 10.2 `composeApp/build.gradle.kts`
- Удалить весь iOS-блок и `iosMain.dependencies`
- В `commonMain.dependencies`: ktor-client-core, content-negotiation, auth, logging, serialization-kotlinx-json, koin-core, koin-compose
- В `androidMain.dependencies`: ktor-client-okhttp, koin-android, androidx-security-crypto, **(оставляем mapkit-android, activity-compose)**
- В `commonTest.dependencies`: kotlin-test, kotlinx-coroutines-test, ktor-client-mock
- Добавить `buildConfigField("String", "API_BASE_URL", ...)` — см. §5.3

### 10.3 Шрифты
Положить 6 `.ttf` файлов в `composeApp/src/commonMain/composeResources/font/`. Источники:
- Unbounded: https://fonts.google.com/specimen/Unbounded — Regular 400, SemiBold 600, Bold 700
- Golos Text: https://fonts.google.com/specimen/Golos+Text — Regular 400, Medium 500, SemiBold 600

Оба под OFL — legal-safe для bundling в APK.

---

## 11. Тестирование

### 11.1 Unit-тесты (`composeApp/src/commonTest/`)

**`domain/ValidationTest.kt`** (~10 кейсов):
- email с/без `@`, без `.`, слишком короткий, слишком длинный, валидный
- password ≥8 / <8 / >100
- fullName blank / whitespace-only / valid
- password-confirm match / mismatch

**`data/repository/AuthRepositoryTest.kt`** (~7 кейсов):
- `init()` без токенов → `Anonymous`
- `init()` с валидными токенами → `me()` 200 → `Authenticated`
- `init()` с просроченными токенами → `me()` 401 → `Anonymous` + storage cleared
- `register()` 200 → `NeedsVerification(email)`
- `verifyEmail()` 200 → `Authenticated` + tokens written
- `login()` 200 → `Authenticated` + tokens written
- `logout()` → `Anonymous` + storage cleared (даже при сетевой ошибке `/auth/logout`)

Используем `FakeAuthApi` (in-memory с настраиваемыми ответами) + `FakeTokenStorage` (HashMap).

**`ui/feature/auth/RegisterScreenModelTest.kt`** (~4 кейса):
- `canSubmit = false` если email blank / password<8 / fullName blank / consent=false
- `canSubmit = true` если все 4 заполнены и consent=true
- `submit()` дёргает `authRepo.register(...)` с правильным `RegisterRequest`
- `submit()` failure (`EMAIL_ALREADY_EXISTS`) → state.errors.email = «Этот email уже зарегистрирован»

**`ui/feature/auth/LoginScreenModelTest.kt`** (~3 кейса):
- `submit()` failure (`INVALID_CREDENTIALS`) → state.errors.password
- `submit()` failure (`EMAIL_NOT_VERIFIED`) → специальный state с возможностью resend
- network failure → state.snackbar = «Нет соединения»

### 11.2 Backend test (`backend/src/test/.../users/UserRoutesTest.kt`)
См. §9.3 — 4 кейса.

### 11.3 Compose @Preview (Android only)
Для каждого экрана + ключевых компонентов: Empty/Filled/Error/Loading. Только визуальный self-check в Android Studio. Не входит в CI.

### 11.4 Manual checkpoint (DoD)

**Сценарий 1 — Happy path:**
1. Splash (3 кнопки) → Регистрация → Register (кнопка disabled пока нет данных)
2. Ввести email/password/fullName → кнопка всё ещё disabled
3. Tap consent checkbox → кнопка enabled
4. «Зарегистрироваться» → loading → VerifyEmailScreen
5. Скопировать verify-token из backend stdout → `adb shell am start -W -d "cleancity://verify?token=XYZ"`
6. Приложение фокус → Verifying → MainPlaceholderScreen
7. «Выйти» → SplashScreen
8. «Войти» → email+password → MainPlaceholderScreen

**Сценарий 2 — Гость:**
1. Splash → «Зайти как гость» → MainPlaceholderScreen («Войти / Регистрация» внизу)
2. Tap → SplashScreen

**Сценарий 3 — Forgot/Reset:**
1. Splash → Войти → «Забыли пароль?» → ForgotPasswordScreen
2. Email → «Прислать ссылку» → SuccessState
3. Скопировать reset-token → `adb shell am start -W -d "cleancity://reset?token=XYZ"`
4. ResetPasswordScreen → новый пароль ×2 → «Установить пароль» → LoginScreen + snackbar
5. Войти с новым паролем → MainPlaceholderScreen

**Сценарий 4 — Граничные случаи:**
1. Login с неверным паролем → inline «Неверный email или пароль»
2. Register с существующим email → inline «Этот email уже зарегистрирован» + ссылка «Войти»
3. Resend verification → cooldown 300с в кнопке
4. Wifi выключен → login → snackbar «Нет соединения с интернетом»
5. Register без consent → кнопка disabled
6. Перезапуск после login → сразу MainPlaceholderScreen (токены прочитаны, `/users/me` валидирует)
7. Имитация expired refresh (испортить значение в EncryptedSharedPreferences через debug-tool) → SplashScreen после холодного старта

**Pass-критерий:** все сценарии без crash, без зависаний >5с (кроме реальной сетевой задержки), все ошибки в правильном месте (inline/snackbar/full-screen) и с правильным текстом.

---

## 12. Соблюдение constraints от ревью §4

| Constraint | Где зафиксировано |
|---|---|
| API_BASE_URL override через local.properties | §5.3 |
| Токены через EncryptedSharedPreferences | §5.4 |
| access/refresh не логируются | §7.7, §7.8 |
| Release: без подробного логирования | §7.7 (LogLevel.NONE) |
| Refresh 401 → clear + Anonymous | §5.2, §5.5 onAuthFailure |
| Нет бесконечного refresh-цикла | §7.6 (markAsRefreshTokenRequest + Mutex) |
| GET /users/me в backend | §9.1 |
| UI показывает ApiError без crash | §7.1, §7.2 (mapping table) |
| UNKNOWN errors показываются как generic | §7.2 |
| Deep-link для verify и reset | §8 |
| e.printStackTrace только в debug | §7.7 |

---

## 13. Риски и митигации

| Риск | Митигация |
|---|---|
| WebView в LegalScreen не показывает контент (нет сети при первом запуске) | Fallback-текст «Документ также доступен на cleancity.ru/legal/...» в error-состоянии WebView |
| Deep-link не открывает приложение если оно в background | `launchMode="singleTask"` + `onNewIntent` обрабатывает intent — стандартный паттерн |
| `cleancity://` scheme конфликтует с другим установленным приложением | Маловероятно для домашних имён; на демо это не проблема. Day 13 — переезд на App Links |
| Backend `/auth/refresh` не возвращает user в `AuthResponse` | Проверить `shared/models/AuthResponse.kt` на этапе имплементации; если нет — добавить или дёргать `/users/me` сразу после refresh |
| EncryptedSharedPreferences медленная при первом доступе | Прогреваем в `Application.onCreate()` через `lifecycleScope.launch { tokenStorage.read() }` |
| Шрифты OFL-1.1 — нужно прикрепить копию лицензии в `composeResources/font/` | Создаём `OFL.txt` рядом с .ttf — стандартная практика |
| RegisterRequest.acceptedTerms — backend проверяет что `true`? | Если нет — нужно либо добавить валидацию на бэке, либо клиент обязан передавать `true` (он и так передаёт — кнопка disabled без галочки) |

---

## 14. Definition of Done

✅ Все 8 экранов запускаются на Android-эмуляторе без crash
✅ Полный flow register → verify → login → main проходит за <2 минуты
✅ Forgot/reset password flow проходит
✅ Гостевой режим работает
✅ Без consent checkbox-а — кнопка Register disabled
✅ Перезапуск приложения сохраняет авторизацию (токены валидируются через `/users/me`)
✅ Любая backend-ошибка видна в UI (inline / snackbar / full-screen), нет crash dialog
✅ Refresh-токен 401 → пользователь возвращается на SplashScreen, локальные токены очищены
✅ `composeApp:assembleDebug` собирается без warnings (кроме deprecated, если есть в Yandex SDK)
✅ Unit-тесты `composeApp:commonTest` зелёные (ValidationTest, AuthRepositoryTest, RegisterScreenModelTest, LoginScreenModelTest)
✅ `backend:test --tests "*UserRoutesTest*"` зелёный (3+ кейса)
✅ В release-build `LogLevel.NONE`, в коде нет `println(token)` / `Log.d(... password ...)`
✅ PLAN.md строка 187–203 — все галочки расставлены
✅ Git: feature-ветка смерджена в `main`, тег `day8-mobile-auth-complete` или просто финальный commit

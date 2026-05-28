# Day 17B хвост — Push при публикации объявления (Local notification + in-app banner)

**Дата:** 2026-05-28
**Контекст:** Day 17B (Объявления) закрыт по части CRUD/web-админки. Открытый пункт `[ ] При публикации → push на mobile` закрываем без Firebase/FCM — гибрид Android local notification (WorkManager + NotificationManager) и in-app баннера в `MainShellScreen`.
**Связанные спеки:**
- `2026-05-11-notifications-infrastructure-design.md` (polling-канал, NotificationService)
- `2026-05-24-day17b-announcements-design.md` (бэкенд-триггер `notify(kind=ANNOUNCEMENT)`)
**Переопределяет:** ничего. Полностью совместимо с уже работающим polling-каналом (Day 12).

---

## 1. Проблема

Backend (`AnnouncementService.create`) уже вставляет записи `notifications` с `kind=ANNOUNCEMENT` при публикации. Mobile уже опрашивает `/notifications/unread-count` каждые 30 сек и обновляет бейдж на табе. Список объявлений рендерится с иконкой Campaign.

Чего не хватает:
1. **Когда приложение закрыто или в фоне** — житель ничего не видит до следующего открытия. Push в системной шторке нужен, чтобы доставка работала без открытого приложения.
2. **Когда приложение открыто на другом табе** (Лента/Карта/Профиль) — бейдж тихо растёт, но житель не получает явный сигнал «вот новое прямо сейчас».

Цель — не подключать Firebase (откладывалось решением 2026-05-11 как post-defense). Используем штатный Android-стек (`WorkManager` + `NotificationManagerCompat`) и in-app баннер на общем event bus.

## 2. Решение — гибрид A+C

```
[Web admin: POST /announcements]
        ↓
[Backend AnnouncementService.create]  — уже работает
        ↓
[notifications.notify(kind=ANNOUNCEMENT, ...)]  — уже работает
        ↓
   ┌────┴────────────────────────────────┐
   ↓                                     ↓
[A] WorkManager periodic 15 min     [C] UnreadCountStore (уже poll 30 сек)
       ↓                                  ↓
[NotificationManagerCompat.notify]   [NotificationEventBus.emit(new)]
системная шторка                          ↓
(app killed / background)            [AnnouncementInAppBanner
                                      в MainShellScreen]
```

Точка дедупликации — общий `lastSeenNotificationId: Long` в DataStore (per user). Оба пути сравнивают `id > lastSeen`. Кто первый записал — второй пропускает.

### Принципы

- **Никакого Firebase/Google Play Services.** Только Android платформенные API.
- **iOS-actual пустой** (no-op). Push на iOS отложен.
- **Скоуп — только `kind=ANNOUNCEMENT`.** COMPLAINT_STATUS остаётся как сейчас (бейдж + список).
- **Никаких изменений в бэкенде.** Все требования покрываются существующим API.

## 3. Архитектура

### 3.1. Common (commonMain)

**Новый файл:** `domain/NotificationEventBus.kt`

```kotlin
class NotificationEventBus {
    private val _newAnnouncements = MutableSharedFlow<NotificationResponse>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val newAnnouncements: SharedFlow<NotificationResponse> = _newAnnouncements
    suspend fun emit(notification: NotificationResponse) = _newAnnouncements.emit(notification)
}
```

Singleton через Koin. Подписчики: `MainShellScreen` (in-app banner).

**Новый файл:** `data/local/SeenNotificationStore.kt` (expect/actual)

Контракт:
- `suspend fun get(userId: Long): Long` — последний известный max id (0 если пустой)
- `suspend fun set(userId: Long, id: Long)` — обновляет
- `suspend fun clear(userId: Long)` — сбрасывает (вызывается при logout)

Android-actual: `androidx.datastore.preferences.core.Preferences` с ключом `lastSeen:$userId`.
iOS-actual: `NSUserDefaults` (минимальная реализация; iOS push в скоупе не активируется).

**Изменения в `UnreadCountStore`:**

Текущий `start()` крутит цикл и пишет `count` в StateFlow. Расширяю:

```kotlin
class UnreadCountStore(
    private val api: NotificationsApiContract,
    private val seenStore: SeenNotificationStore,
    private val bus: NotificationEventBus,
    private val authRepo: AuthRepository,
    private val scope: CoroutineScope = ...,
    private val intervalMillis: Long = 30_000L,
) {
    // existing _state и job

    private suspend fun pollOnce() {
        val userId = (authRepo.state.value as? AuthState.Authenticated)?.user?.id ?: return
        val resp = runCatching { api.list(limit = 50) }.getOrNull() ?: return
        val lastSeen = seenStore.get(userId)
        val maxId = resp.items.maxOfOrNull { it.id } ?: 0L
        if (lastSeen == 0L) {
            // первый poll после login — молчим, только запоминаем
            if (maxId > 0) seenStore.set(userId, maxId)
        } else {
            resp.items
                .filter { it.kind == NotificationKind.ANNOUNCEMENT && it.readAt == null && it.id > lastSeen }
                .forEach { bus.emit(it) }
            if (maxId > lastSeen) seenStore.set(userId, maxId)
        }
        _state.value = resp.items.count { it.readAt == null }
    }
}
```

`api.unreadCount()` больше не используется (заменён на полный fetch + локальный подсчёт). Это даёт нам данные для дедупликации без дополнительного запроса.

**Изменения в Koin-модуле:** добавить `NotificationEventBus`, `SeenNotificationStore`, прокинуть их + `AuthRepository` в `UnreadCountStore`.

### 3.2. Android-only (androidMain)

**Новый файл:** `notifications/AnnouncementCheckWorker.kt`

```kotlin
class AnnouncementCheckWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params), KoinComponent {

    private val api: NotificationsApiContract by inject()
    private val seenStore: SeenNotificationStore by inject()
    private val authRepo: AuthRepository by inject()
    private val dispatcher: SystemNotificationDispatcher by inject()

    override suspend fun doWork(): Result {
        val auth = authRepo.state.value as? AuthState.Authenticated
            ?: return Result.success()  // не залогинен — тихо выходим
        val userId = auth.user.id
        val resp = runCatching { api.list(limit = 50) }.getOrNull()
            ?: return Result.retry()
        val lastSeen = seenStore.get(userId)
        val maxId = resp.items.maxOfOrNull { it.id } ?: 0L
        if (lastSeen == 0L) {
            if (maxId > 0) seenStore.set(userId, maxId)
            return Result.success()
        }
        resp.items
            .filter { it.kind == NotificationKind.ANNOUNCEMENT && it.readAt == null && it.id > lastSeen }
            .forEach { dispatcher.notify(it) }
        if (maxId > lastSeen) seenStore.set(userId, maxId)
        return Result.success()
    }
}
```

**Schedule:** в `CleanCityApplication.onCreate()`:
```kotlin
val req = PeriodicWorkRequestBuilder<AnnouncementCheckWorker>(15, TimeUnit.MINUTES)
    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .build()
WorkManager.getInstance(this).enqueueUniquePeriodicWork(
    "announcement-check",
    ExistingPeriodicWorkPolicy.KEEP,
    req,
)
```

Перепланирование при login/logout — через `WorkManager.getInstance(ctx).cancelUniqueWork("announcement-check")` / `enqueueUniquePeriodicWork(...)`.

**Новый файл:** `notifications/SystemNotificationDispatcher.kt`

```kotlin
class SystemNotificationDispatcher(private val ctx: Context) {
    companion object {
        const val CHANNEL_ID = "cleancity_announcements"
        const val CHANNEL_NAME = "Объявления"
    }

    init {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Объявления от администрации города"
            enableVibration(true)
        }
        NotificationManagerCompat.from(ctx).createNotificationChannel(channel)
    }

    fun notify(n: NotificationResponse) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, n.id)
            putExtra(EXTRA_OPEN_TAB, "notifications")
        }
        val pending = PendingIntent.getActivity(
            ctx, n.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.body.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(ctx).notify(n.id.toInt(), notif)
    }
}
```

Иконка `ic_notification` — добавляется как vector drawable (белый bell или Campaign-style на прозрачном фоне; для Android system notification mono).

**iOS:** `SystemNotificationDispatcher` — Android-only класс (НЕ expect/actual). На iOS его просто нет в DI; `AnnouncementBusBridge` тоже не создаётся. iOS получает только бейдж + список (как сейчас).

### 3.2.bis. Android — bridge между bus и SystemNotificationDispatcher

UnreadCountStore (common) опрашивает каждые 30 сек и эмитит в `NotificationEventBus`. Когда **приложение foreground** — `AnnouncementInAppBanner` в `MainShellScreen` это видит и показывает Snackbar. Когда **приложение в фоне, но процесс жив** — Snackbar не показывается (UI не активен), и без bridge юзер ничего не увидит до 15-минутного срабатывания Worker.

Чтобы 30-секундный канал работал и в фоне, добавляю на Android `AnnouncementBusBridge` (singleton, инициализируется в `CleanCityApplication.onCreate()`):

```kotlin
class AnnouncementBusBridge(
    private val bus: NotificationEventBus,
    private val dispatcher: SystemNotificationDispatcher,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            bus.newAnnouncements.collect { n ->
                if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    dispatcher.notify(n)
                }
            }
        }
    }
}
```

`ProcessLifecycleOwner.STATE >= STARTED` означает «есть видимая активность» (foreground). Если foreground — баннер уже показал Snackbar, system notification не нужен. Иначе — пушим в шторку.

Dedup со встроенным `seenStore` остаётся актуальным: даже если оба пути (Worker и bridge) увидят одну запись, второй пройти фильтр `id > lastSeen` не сможет.

### 3.3. UI hookup (commonMain)

**Новый composable:** `ui/feature/shell/AnnouncementInAppBanner.kt`

Material 3 Snackbar с кастомным контентом — иконка `Campaign`, заголовок и body (две строки), кнопка «Посмотреть». Длительность `SnackbarDuration.Long` (~10 сек).

**Изменения в `MainShellScreen`:**

```kotlin
val bus: NotificationEventBus = koinInject()
val tabNav = remember { ... }
val snackbarHost = remember { SnackbarHostState() }

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

Scaffold(
    snackbarHost = { SnackbarHost(snackbarHost) { AnnouncementInAppBanner(it) } },
    bottomBar = { ... },
    content = ...,
)
```

### 3.4. Permission flow (Android 13+)

- `AndroidManifest.xml` — добавить `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.
- Флаг в DataStore: `notif_permission_asked: Boolean` (default false). Меняется в `true` ОДИН раз — после первого диалога (независимо от ответа).
- Триггер показа: в `MainShellScreen` `LaunchedEffect(authState)` — когда `authState is AuthState.Authenticated` И `Build.VERSION.SDK_INT >= 33` И `ContextCompat.checkSelfPermission(...) != GRANTED` И `notif_permission_asked == false`:
  - Показать **rationale-диалог** «Чтобы вы не пропустили важные объявления от администрации»
  - По «Разрешить» — запустить `ActivityResultContracts.RequestPermission` через `rememberLauncherForActivityResult`
  - В любом случае (deny/grant/dismiss) — выставить `notif_permission_asked = true`
- На устройствах < Android 13 — флаг не проверяется, диалог не показываем, permission даётся автоматически.
- В Settings → «Уведомления» (если хотим UX полировки) можно добавить ссылку на системные настройки приложения. Не входит в скоуп этой спеки.

### 3.5. Тап по системному уведомлению

`MainActivity.onNewIntent(intent)` (уже есть logic для deep-link `cleancity://verify` и `cleancity://reset`):

- Если `intent.hasExtra(EXTRA_NOTIFICATION_ID)`:
  - Найти `notification_id`
  - В `MainShellScreen` через side-channel (например, MutableSharedFlow<Long> в DI) — переключить tabNavigator на `NotificationsTab`
  - Вызвать `notificationsScreenModel.markRead(notification_id)`
  - `setAutoCancel(true)` в notification builder убирает запись из шторки автоматически

### 3.6. Logout / смена юзера

В `AuthRepository.logout()` (или централизованной точке выхода):
- `WorkManager.getInstance(ctx).cancelUniqueWork("announcement-check")` (через expect/actual hook или Application-level listener)
- `seenStore.clear(currentUserId)` через тот же hook
- `BearerAuthProvider.clearToken()` уже вызывается (memory `[[ktor_bearer_cache]]`)

При следующем `login()` — заново enqueue worker, заново стартует `UnreadCountStore`. Первый poll увидит `lastSeen == 0` и сработает «молчим, только запоминаем».

## 4. Edge cases

| Сценарий | Поведение |
|----------|-----------|
| Первый poll после login, 5 непрочитанных | seenStore выставлен в max, баннеров/шторки нет. Юзер видит бейдж и может зайти в список. |
| Worker и foreground polling видят одну запись | `seenStore.get` атомарен на уровне DataStore; первый записал — второй не пройдёт фильтр `id > lastSeen`. В худшем случае `NotificationManager.notify(id, ...)` дважды — Android заменяет по id, дубликата в шторке не будет. |
| Permission denied | Worker всё равно зовёт `notify(...)` — Android молча игнорирует. Логика не падает. In-app banner работает. |
| Сеть упала во время `doWork()` | `Result.retry()` → WorkManager переплнирует с exponential backoff. |
| Объявление с пустыми districts | Бэкенд `recipientIdsForDistricts(emptyList())` — рассылка всем (это уже работает). Push доходит. |
| Объявление в конкретный district | Бэкенд фильтрует получателей. Юзер не из этого района записи в `/notifications` не получит — push не сработает. |
| Logout с pending worker | `cancelUniqueWork` останавливает следующий запуск; уже выполняющийся завершится `Result.success()` (auth-проверка в начале `doWork`). |
| Reboot | WorkManager переживает reboot, periodic work возобновится. |

## 5. Что НЕ делаем

- Firebase Cloud Messaging — отложено решением 2026-05-11.
- iOS push — `actual` пустой, в скоупе не активируется.
- Push для `kind=COMPLAINT_STATUS` — остаётся как сейчас (бейдж + список).
- Server-side `since_id` параметр — клиентская фильтрация по `id > lastSeen` достаточна для текущего объёма.
- Изменения схемы БД, миграции — нечего менять.
- Web-админка — никаких изменений (она и так знает что публикация отправляет push «жителям выбранных районов» — текст подсказки под формой уже есть).

## 6. Тестирование

### 6.1. Unit (commonTest)

- `NotificationEventBusTest` — эмит-коллект работает, при переполнении буфера сбрасывается старейший.
- `SeenNotificationStoreTest` (Android-instrumented или JVM с fake DataStore) — get/set/clear, per-user изоляция.
- `UnreadCountStorePollTest` (расширяем существующий):
  - Первый poll, lastSeen=0 → события **не** эмитятся, seenStore выставлен в max.
  - Второй poll, новых нет → ничего не эмитится.
  - Второй poll, новый ANNOUNCEMENT → эмит.
  - Второй poll, новый COMPLAINT_STATUS → **не** эмит.
  - Logout-trigger → seenStore.clear был вызван.

### 6.2. Unit (androidUnitTest)

- `AnnouncementCheckWorkerTest` через `TestListenableWorkerBuilder` + `runBlocking`:
  - Не залогинен → `Result.success()`, api не вызван.
  - Network fail → `Result.retry()`.
  - lastSeen=0 → no `dispatcher.notify`, seenStore выставлен.
  - Новые ANNOUNCEMENT → `dispatcher.notify` вызван N раз.
  - Только COMPLAINT_STATUS → `dispatcher.notify` не вызван.

### 6.3. Manual / e2e на Samsung A33

Helper-скрипт `ops/trigger-announcement.sh` (по аналогии с `trigger-status-change.sh`) — публикует объявление через API.

Чек-лист `docs/superpowers/specs/2026-05-28-day17b-push-test-plan.md` (генерится после плана):
1. App открыто на табе «Лента» → публикуем → ≤30 сек → in-app banner снизу. Тап «Посмотреть» → переход на таб «Уведомления».
2. App в фоне (свёрнуто) → публикуем → ≤30 сек → шторка показывает уведомление. Тап → открывает приложение на табе «Уведомления», запись прочитана.
3. App force-stopped → публикуем → ≤15 мин → шторка показывает. Тап → запускает приложение на табе «Уведомления».
4. Permission denied на Android 13+ → системные не приходят, in-app banner работает, бейдж работает.
5. Logout → login другим аккаунтом → старые объявления не сыпятся, бейдж синхронизирован с сервером.
6. Объявление без districts (все районы) → приходит. С конкретным district → приходит только юзерам из этого района.

### 6.4. Что не покрываем тестами

- Reboot — доверяем WorkManager.
- Дедуп-гонка ms-уровня worker/polling — `NotificationManager.notify(id, ...)` гарантирует replace-by-id.
- iOS push — нет реализации.

## 7. Файлы — итог

**Новые:**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationEventBus.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.kt` (expect)
- `composeApp/src/androidMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.android.kt` (actual)
- `composeApp/src/iosMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.ios.kt` (actual no-op)
- `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementCheckWorker.kt`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/SystemNotificationDispatcher.kt` (Android-only)
- `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementBusBridge.kt` (Android-only)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt`
- `composeApp/src/androidMain/res/drawable/ic_notification.xml` (vector mono)
- `ops/trigger-announcement.sh`
- `composeApp/src/commonTest/kotlin/.../NotificationEventBusTest.kt`
- `composeApp/src/androidUnitTest/kotlin/.../AnnouncementCheckWorkerTest.kt`

**Изменяемые:**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt` — добавить seenStore, bus, новую `pollOnce` логику.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt` — Snackbar host + LaunchedEffect.
- `composeApp/src/androidMain/AndroidManifest.xml` — `POST_NOTIFICATIONS`.
- `composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt` — enqueue WorkManager, channel init, `AnnouncementBusBridge.start()`.
- `composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt` — обработка extras в `onNewIntent`.
- Koin модули (`commonModule`, `androidModule`) — регистрация новых типов.
- `composeApp/src/commonTest/kotlin/.../UnreadCountStoreTest.kt` — новые сценарии.

## 8. Оценка

- Backend: 0 ч.
- Common (EventBus, SeenStore expect, UnreadCountStore доработка, banner, тесты): ~2-3 ч.
- Android (Worker, Dispatcher, permission, manifest, drawable, Application init, MainActivity intent, тесты): ~2-3 ч.
- E2e на A33 + чек-лист + helper-скрипт: ~1 ч.
- Итого ~5-7 ч с учётом отладки специфики A33.

## 9. Демо-сценарий на защите

1. На экране проектор — web-админка `admin.cleancity.ru/announcements`. Заполняем демо-объявление («Уборка парка Ривьера в субботу в 9:00»), district = «Центральный», публикуем.
2. На A33 (приложение свёрнуто на главный экран) — за ~30 сек в системной шторке heads-up notification со звуком. Тап → приложение открывается сразу на табе «Уведомления», запись помечена прочитанной.
3. Комиссия видит реальный push без Firebase. На вопрос «почему не FCM?» — ответ: «polling + WorkManager + NotificationManager работают без зависимости от Google Play Services и могут работать в РФ без VPN; FCM подключается как расширение после защиты».

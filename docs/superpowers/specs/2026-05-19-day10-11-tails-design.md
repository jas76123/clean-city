# Day 10/11 tails — closeout design (2026-05-19)

## Context

Today is Day 12 в плане (19.05). Day 10 (17.05) и Day 11 (18.05) фактически реализованы в коде (commits `f6c5dfd`, `b8ccba4`, `94d0bb8` и др.), но `PLAN.md` рассинхронизирован, и осталось несколько точечных хвостов.

Цель сессии — закрыть всё 5 хвостов, синхронизировать `PLAN.md`, и освободить контекст для Day 12 (уведомления + мои/поддержанные).

## Что закрываем (5 хвостов)

| # | Хвост | Тип |
|---|-------|-----|
| 1 | Polling unread-count для bell-бейджа | код |
| 2 | Snackbar-заглушка «Настройки уведомлений» | код (UX) |
| 3 | Smoke #7 (cluster-tap) подтверждение на Samsung A33 | smoke + docs |
| 4 | Day 11 финальный smoke (только ERR-1/ERR-2) | smoke |
| 5 | PLAN.md sync | docs |

## Аудит фактического состояния

Перед дизайном проведён аудит против PLAN.md. Реально **сделано в коде**, но в `PLAN.md` ещё `[ ]`:

- `MainShellScreen` + `BottomNav` + 4 таба (`FeedTab`, `MapTab`, `NotificationsTab`, `ProfileTab`) с собственными sub-Navigator'ами.
- `FeedScreen` с каруселью объявлений, списком жалоб, фильтром Все/Мои, `PullToRefreshBox`, infinite-scroll пагинацией, empty/loading/error.
- `ComplaintDetailScreen` с `VoteCard` (одностороннее «Подтверждаю»), guest-диалог «Войдите, чтобы поддержать», `VoteEventBus` для синхронизации счётчика с лентой/картой.
- `MapSearchBar` + `MapSearchProvider` (Yandex Geosuggest), `onSuggestionSelected` → перемещение камеры.
- `ProfileScreen` с header, 4 stat-карточками (включая `confirmed` через `/complaints/voted?size=1` → `total`), меню «Мои жалобы / О приложении / Выйти», скрытая edit-кнопка.
- `NotificationsApi.unreadCount()` готов, `FeedTopBar` Badge на bell готов.

**Действительно НЕ реализовано:**

- Хвост 1: моста между `NotificationsApi.unreadCount()` и `FeedTopBar`. В `FeedScreen.kt:84` хардкод `unreadCount = 0 // bridged in Day 12`.
- Хвост 2: `onSettingsClick` в `ProfileScreen` — placeholder без действия.
- Хвост 3: smoke #7 не проверен на реальном устройстве — открыт.
- Хвост 4: Day 11 ERR-сценарии не проверены — открыто.

## Архитектурное расхождение с PLAN.md

PLAN.md предполагал badge на пункте «Уведомл.» нижней навигации. Фактически принятое решение (по комментарию в `FeedScreen.kt:84`) — badge живёт на колокольчике в `FeedTopBar`. Дизайн идёт за фактом: polling питает bell-badge, не nav-item. PLAN.md синхронизируется под это.

---

## Секция 1. Polling unread-count

### Состояние

Koin singleton `UnreadCountStore` с `StateFlow<Int>`. Собственный `CoroutineScope(SupervisorJob() + Dispatchers.Default)`. Singleton нужен потому что bell в `FeedTopBar`, но запускать polling в `FeedScreenModel` нельзя — при переключении табов screenModel может пересоздаваться (см. [[project_cleancity_voyager_backstack]]). Store переживает табы и переживает logout/login через `key(processSeed, sessionKey)` в `App.kt`.

### API

```kotlin
class UnreadCountStore(private val api: NotificationsApi) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state.asStateFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                runCatching { api.unreadCount().count }
                    .onSuccess { _state.value = it }
                delay(30_000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = 0
    }
}
```

Методы `refresh()` / `decrement()` / `reset()` — НЕ в скоупе Day 10. Подключатся в Day 12 когда NotificationsScreen wired с реальным read-API.

### Lifecycle

В `MainShellScreen` Composable:

```kotlin
val store: UnreadCountStore = koinInject()
val authState by authRepo.state.collectAsState()
LaunchedEffect(authState) {
    if (authState is AuthState.Authenticated) store.start() else store.stop()
}
DisposableEffect(Unit) {
    onDispose { store.stop() }
}
```

Guest: `start()` всё равно зовётся для авторизованного юзера, для guest — никогда не зовётся (условие в `LaunchedEffect`). Store держит 0.

### Подписка

В `FeedScreen`: заменяем `unreadCount = 0` на `unreadCount = koinInject<UnreadCountStore>().state.collectAsState().value`. `FeedTopBar` сам прячет Badge при 0.

### Error handling

Silent — ошибки запроса игнорируются (`runCatching` без `.onFailure`), state не сбрасывается, держит последнее значение. Без Napier-логов в скоупе (избегаем шума).

### Тестирование

`UnreadCountStoreTest` через `kotlinx-coroutines-test runTest`:

1. `start()` → immediate fetch → `state.value == api response`.
2. `start()` → `advanceTimeBy(30_001)` → второй fetch → `state` обновлён.
3. API бросает → `state` не сбрасывается, остаётся предыдущее значение.
4. `stop()` → `state.value == 0`, повторный `start()` работает.

Fake `NotificationsApi` — простой класс с `var nextCount` и `var shouldThrow`.

### Файлы

- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt` (новый)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt` (+1 `single`)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt` (+LaunchedEffect+DisposableEffect)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/feed/FeedScreen.kt` (замена хардкода)
- `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt` (новый)

### Решение по background

Не обрабатываем явно. `Dispatchers.Default` будет тикать раз в 30с даже если app в background — это копеечный network call. Жёсткий lifecycle-aware stop — over-engineering для текущего скоупа.

---

## Секция 2. Snackbar «Настройки уведомлений»

В `ProfileScreen.kt:340` `MenuItemRow("Настройки уведомлений", onClick = onSettingsClick)` — `onSettingsClick` сейчас бесполезен.

Меняем callback на показ snackbar. В `ProfileScreen`-Loaded уже есть `SnackbarHostState` (импорт на line 38). Добавляем `rememberCoroutineScope()`, в lambda — `scope.launch { snackbarHost.showSnackbar("Появится в ближайшем обновлении") }`.

~5 строк. Unit-тестов не требуется (UI-тривия).

---

## Секция 3. Smoke #7 (cluster-tap) — закрытие

Пользователь подтвердил: на Samsung A33 cluster-tap корректно зумит. AVD-specific bug, кода не нужно.

**Действия:**
- В `PLAN.md:238` (заметка про smoke #7) добавить: «✅ закрыто 2026-05-19 — на Samsung A33 5G работает корректно; AVD-only bug в Yandex MapKit ↔ Compose обёртке».
- В `docs/superpowers/checklists/2026-05-19-day10-11-tails-smoke.md` зафиксировать тот же результат.

---

## Секция 4. Day 11 финальный smoke

Пользователь подтвердил: основные пути (GPS / Suggest / Picker) работают на устройстве. Остались только ERR-сценарии.

| # | Сценарий | Ожидание |
|---|----------|----------|
| ERR-1 | Wi-Fi off + mobile data off → submit жалобы | AlertDialog с понятной ошибкой («Нет интернета» / «Сервер недоступен»), форма не теряет данные, не крашит |
| ERR-2 | Фото из галереи >10MB | Pre-check блокирует фото с диалогом «Файл слишком большой, до 10MB», submit-кнопка остаётся доступной с другими фото |

**Подготовка ERR-2:** Samsung A33 в стандартном режиме делает фото ~6-9MB. Резерв — закинуть файл >10MB и принудительно ре-сканировать галерею:

```bash
adb push big.jpg /sdcard/DCIM/Camera/
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
  -d file:///sdcard/DCIM/Camera/big.jpg
```

Если ERR-1 или ERR-2 находят баг — фиксим одной волной перед PLAN.md sync. Иначе протоколируем зелёное.

---

## Секция 5. PLAN.md sync + порядок коммитов

### Прожать `[x]` в Day 10 (PLAN.md:244-275)

- Хост-оболочка: все 4 пункта (MainShell + Tabs + бейдж + Guest stubs). Бейдж переформулировать на «Бейдж на bell в FeedTopBar — polling раз в 30с через UnreadCountStore».
- Лента: все 4 пункта (FeedScreen, фильтр, pull-to-refresh+пагинация, состояния).
- Детали жалобы: VoteCard + guest dialog (переход из карты уже был `[x]`).
- Map: search bar `[x]`. Smoke #7 → `[x]` с note про Samsung A33.
- Профиль: все 6 пунктов, включая «Настройки уведомлений» → Snackbar.

### Прожать `[x]` в Day 11 (PLAN.md:281-298)

- Добавить запись «Финальный smoke ERR-1/ERR-2 закрыт 2026-05-19 на Samsung A33» (или с найденными багами + фиксами).

### Порядок коммитов (5-6 атомарных)

1. `feat(notifications): UnreadCountStore + polling в MainShellScreen` — стор, инжект в Koin, lifecycle в shell, unit-тесты
2. `feat(feed): подключить UnreadCountStore вместо хардкода unreadCount=0`
3. `feat(profile): snackbar для «Настройки уведомлений» (заглушка до FCM)`
4. *(условно)* `fix(create): <конкретный фикс по ERR-1/ERR-2>` — только если smoke найдёт баг
5. `docs(plan): sync Day 10 — реализованные пункты, закрытие smoke #7`
6. `docs(checklists): 2026-05-19 day10-11 tails smoke результаты`

## Что НЕ в скоупе

- Decrement unread-count при чтении уведомления → Day 12
- Mark-all-read reset → Day 12
- FCM-токен и push-нотификации → Day 12
- Lifecycle-aware остановка polling при background → не требуется (см. секция 1)
- Любые улучшения других экранов сверх 5 хвостов
- Изменения backend (всё уже есть: `/notifications/unread-count`, `/complaints/voted`)

## Риски

- **ERR-1 находит баг в обработке network-failure.** Mitigation: фиксим в волне 4, переносим коммит 5-6 на потом.
- **ERR-2 находит баг в pre-check.** Mitigation: тот же подход.
- **Polling вызывает баги авторизации** (например, token expired не triggers logout). Mitigation: `runCatching` silent — не падаем. Если 401 не handled централизованно — добавим в Day 12 invalidator.
- **Conflict в PLAN.md** при последовательных коммитах. Mitigation: атомарные коммиты, sync — последний.

## Чек после закрытия

- 82 unit-теста (текущий suite) + 4 новых на UnreadCountStore = 86/86 зелёные.
- APK устанавливается, bell-badge показывает реальный unread (можно сэмулировать: создать жалобу через web/Postman, изменить статус → backend пушит notification → через ≤30с badge обновится).
- ERR-1/ERR-2 зелёные в чеклисте (или зафиксированные баги исправлены).
- PLAN.md Day 10 и Day 11 без `[ ]` (кроме явно перенесённых в Day 12+).
- Day 12 можно стартовать с чистым контекстом.

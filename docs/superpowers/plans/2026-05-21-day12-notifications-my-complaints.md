# Day 12 — Уведомления + Мои жалобы Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать на мобильном (Compose Multiplatform) экран уведомлений с polling, экран «Мои жалобы», блок «Решение администрации» в деталях закрытой жалобы и поправить слоган регистрации.

**Architecture:** Следуем существующим паттернам проекта — Voyager `Screen` + `ScreenModel`, Koin DI (`appModule()`), Ktor `*Api`-контракты, модели в `shared`. Уведомления доставляются опросом backend API (FCM отложен в Day 14-буфер). Бейдж непрочитанных уже работает через `UnreadCountStore` (Day 10) — синхронизируем его при локальной отметке прочитанным.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager (навигация + ScreenModel), Koin, Ktor Client, kotlinx-coroutines, kotlinx-datetime, kotlin.test.

**Спека:** `docs/superpowers/specs/2026-05-21-day12-notifications-my-complaints-design.md`

---

## File Structure

**Создаём:**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/util/RelativeTime.kt` — чистая функция форматирования относительного времени.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/util/RelativeTimeTest.kt` — тесты.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/NotificationsApiTest.kt` — MockEngine-тесты API.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModel.kt` — состояние + логика экрана уведомлений.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModelTest.kt` — тесты модели.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/FakeNotificationsListApi.kt` — fake для тестов модели уведомлений.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/components/NotificationCard.kt` — карточка одного уведомления.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreen.kt` — экран «Мои жалобы».
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModel.kt` — состояние + логика.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModelTest.kt` — тесты модели.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/FakeMineComplaintsApi.kt` — fake для тестов.

**Модифицируем:**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreen.kt:42` — слоган.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/NotificationsApi.kt` — добавить методы.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeNotificationsApi.kt` — реализовать новые методы контракта.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt` — метод `decrement`.
- `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt` — тест `decrement`.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreen.kt` — заменить заглушку.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt` — зарегистрировать 2 ScreenModel.
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt` — навигация в «Мои жалобы».
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/detail/ComplaintDetailScreen.kt` — блок «Решение администрации».

**Команды:**
- Тесты (по одному классу): `./gradlew composeApp:commonTest --tests "<FQN>"`
- Весь suite: `./gradlew composeApp:commonTest`
- Компиляция (проверка UI-изменений): `./gradlew composeApp:compileDebugKotlinAndroid`

---

## Task 1: Правка слогана регистрации

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreen.kt:42`

Это однострочная правка текста — unit-теста не имеет.

- [ ] **Step 1: Заменить слоган**

В файле `RegisterScreen.kt` строка 42 сейчас:

```kotlin
                AuthSub("За 30 секунд — и вы можете влиять на состояние Сочи.")
```

Заменить на:

```kotlin
                AuthSub("За 30 секунд — и вы можете влиять на состояние города.")
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/auth/RegisterScreen.kt
git commit -m "fix: слоган регистрации без привязки к городу"
```

---

## Task 2: Утилита относительного времени `relativeTime`

Чистая функция для текста «N мин назад» в карточках уведомлений. Принимает ISO-строку и опциональный `now` (для тестируемости). Существующая приватная `relativeTime` в `ComplaintCard.kt` не трогается — рефакторинг вне scope.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/util/RelativeTime.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/util/RelativeTimeTest.kt`

- [ ] **Step 1: Написать падающий тест**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/util/RelativeTimeTest.kt`:

```kotlin
package com.example.cleancity.ui.util

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now = Instant.parse("2026-05-21T12:00:00Z")

    @Test fun `less than a minute is just now`() {
        assertEquals("только что", relativeTime("2026-05-21T11:59:30Z", now))
    }

    @Test fun `minutes ago`() {
        assertEquals("5 мин назад", relativeTime("2026-05-21T11:55:00Z", now))
    }

    @Test fun `hours ago`() {
        assertEquals("3 ч назад", relativeTime("2026-05-21T09:00:00Z", now))
    }

    @Test fun `yesterday`() {
        assertEquals("вчера", relativeTime("2026-05-20T10:00:00Z", now))
    }

    @Test fun `several days ago`() {
        assertEquals("4 дн назад", relativeTime("2026-05-17T12:00:00Z", now))
    }

    @Test fun `older than a week falls back to date`() {
        assertEquals("2026-05-01", relativeTime("2026-05-01T12:00:00Z", now))
    }

    @Test fun `unparseable string falls back to first ten chars`() {
        assertEquals("garbage-st", relativeTime("garbage-string", now))
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.util.RelativeTimeTest"`
Expected: FAIL — `RelativeTime.kt` ещё не существует, не компилируется.

- [ ] **Step 3: Реализовать утилиту**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/util/RelativeTime.kt`:

```kotlin
package com.example.cleancity.ui.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Относительное время из ISO-8601 строки для карточек уведомлений.
 * «только что» / «N мин назад» / «N ч назад» / «вчера» / «N дн назад» / дата.
 * При неразборчивой строке — fallback на первые 10 символов.
 */
fun relativeTime(iso: String, now: Instant = Clock.System.now()): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull()
        ?: return iso.take(10)
    val seconds = (now - instant).inWholeSeconds
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        seconds < 60 -> "только что"
        minutes < 60 -> "$minutes мин назад"
        hours < 24 -> "$hours ч назад"
        days == 1L -> "вчера"
        days < 7 -> "$days дн назад"
        else -> iso.take(10)
    }
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.util.RelativeTimeTest"`
Expected: PASS — 7 тестов.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/util/RelativeTime.kt composeApp/src/commonTest/kotlin/com/example/cleancity/ui/util/RelativeTimeTest.kt
git commit -m "feat: relativeTime util для карточек уведомлений"
```

---

## Task 3: Расширение `NotificationsApi` — список, отметка прочитанным

`NotificationsApiContract` сейчас умеет только `unreadCount()`. Добавляем три метода. Модели `NotificationListResponse`, `NotificationResponse`, `MarkAllReadResponse` уже существуют в `shared/.../models/NotificationResponse.kt` — не создаём.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/NotificationsApi.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeNotificationsApi.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/NotificationsApiTest.kt`

- [ ] **Step 1: Написать падающий тест API**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/NotificationsApiTest.kt`:

```kotlin
package com.example.cleancity.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsApiTest {

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        }
        defaultRequest { url("http://localhost/") }
    }

    @Test
    fun `list passes limit and parses items`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"items":[
                    {"id":1,"kind":"COMPLAINT_STATUS","title":"t","body":"b","complaintId":7,"createdAt":"2026-05-21T10:00:00Z"}
                ],"total":1,"hasMore":false}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = NotificationsApi(httpClient(engine))

        val result = api.list(limit = 50)

        assertTrue(capturedUrl!!.contains("limit=50"))
        assertEquals(1, result.items.size)
        assertEquals(7L, result.items[0].complaintId)
    }

    @Test
    fun `markRead sends PATCH to notification id`() = runTest {
        var method: HttpMethod? = null
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            method = request.method
            capturedUrl = request.url.toString()
            respond("", HttpStatusCode.OK)
        }
        val api = NotificationsApi(httpClient(engine))

        api.markRead(42L)

        assertEquals(HttpMethod.Patch, method)
        assertTrue(capturedUrl!!.contains("/notifications/42/read"))
    }

    @Test
    fun `markAllRead sends PATCH and parses markedCount`() = runTest {
        var method: HttpMethod? = null
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            method = request.method
            capturedUrl = request.url.toString()
            respond(
                content = """{"markedCount":3}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = NotificationsApi(httpClient(engine))

        val result = api.markAllRead()

        assertEquals(HttpMethod.Patch, method)
        assertTrue(capturedUrl!!.contains("/notifications/read-all"))
        assertEquals(3, result.markedCount)
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.data.network.NotificationsApiTest"`
Expected: FAIL — методов `list`/`markRead`/`markAllRead` нет, не компилируется.

- [ ] **Step 3: Расширить контракт и реализацию**

Заменить полностью содержимое `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/NotificationsApi.kt`:

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch

interface NotificationsApiContract {
    suspend fun unreadCount(): UnreadCountResponse
    suspend fun list(limit: Int = 50): NotificationListResponse
    suspend fun markRead(id: Long)
    suspend fun markAllRead(): MarkAllReadResponse
}

class NotificationsApi(private val client: HttpClient) : NotificationsApiContract {
    override suspend fun unreadCount(): UnreadCountResponse =
        client.get("/notifications/unread-count").body()

    override suspend fun list(limit: Int): NotificationListResponse =
        client.get("/notifications") {
            parameter("limit", limit)
        }.body()

    override suspend fun markRead(id: Long) {
        client.patch("/notifications/$id/read")
    }

    override suspend fun markAllRead(): MarkAllReadResponse =
        client.patch("/notifications/read-all").body()
}
```

- [ ] **Step 4: Реализовать новые методы в `FakeNotificationsApi`**

`FakeNotificationsApi` реализует `NotificationsApiContract` — после расширения контракта он перестанет компилироваться. Заменить полностью содержимое `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeNotificationsApi.kt`:

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse
import kotlinx.coroutines.CompletableDeferred

class FakeNotificationsApi : NotificationsApiContract {
    var nextCount: Long = 0L
    var shouldThrow: Boolean = false
    var callCount: Int = 0
        private set

    /** Если выставить — следующий вызов unreadCount() ждёт пока этот deferred завершится. */
    var gate: CompletableDeferred<Unit>? = null

    var nextListResult: Result<NotificationListResponse> =
        Result.success(NotificationListResponse(items = emptyList(), total = 0, hasMore = false))
    var nextMarkAllResult: Result<MarkAllReadResponse> =
        Result.success(MarkAllReadResponse(markedCount = 0))
    val markReadCalls = mutableListOf<Long>()
    var markReadShouldThrow: Boolean = false

    override suspend fun unreadCount(): UnreadCountResponse {
        gate?.await()
        callCount += 1
        if (shouldThrow) throw RuntimeException("network error")
        return UnreadCountResponse(count = nextCount)
    }

    override suspend fun list(limit: Int): NotificationListResponse =
        nextListResult.getOrThrow()

    override suspend fun markRead(id: Long) {
        markReadCalls += id
        if (markReadShouldThrow) throw RuntimeException("network error")
    }

    override suspend fun markAllRead(): MarkAllReadResponse =
        nextMarkAllResult.getOrThrow()
}
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.data.network.NotificationsApiTest"`
Expected: PASS — 3 теста.

- [ ] **Step 6: Прогнать существующий suite уведомлений (не сломали `FakeNotificationsApi`)**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.domain.UnreadCountStoreTest"`
Expected: PASS — 5 тестов.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/NotificationsApi.kt composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeNotificationsApi.kt composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/NotificationsApiTest.kt
git commit -m "feat: NotificationsApi — список, markRead, markAllRead"
```

---

## Task 4: `UnreadCountStore.decrement` — синхронизация бейджа

Чтобы бейдж непрочитанных уменьшался сразу при локальной отметке, а не ждал следующего 30-секундного опроса. Добавляем метод `decrement`.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt`

- [ ] **Step 1: Написать падающий тест**

Добавить в `UnreadCountStoreTest.kt` перед последним `}` класса:

```kotlin
    @Test
    fun `decrement reduces state and clamps at zero`() = runTest {
        val api = FakeNotificationsApi().apply { nextCount = 5L }
        val store = newStore(api, this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(5, store.state.value)

        store.decrement(2)
        assertEquals(3, store.state.value)

        store.decrement(10)   // не уходит в минус
        assertEquals(0, store.state.value)
        store.stop()
    }

    @Test
    fun `decrement defaults to one`() = runTest {
        val api = FakeNotificationsApi().apply { nextCount = 4L }
        val store = newStore(api, this)

        store.start()
        testScheduler.runCurrent()

        store.decrement()
        assertEquals(3, store.state.value)
        store.stop()
    }
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.domain.UnreadCountStoreTest"`
Expected: FAIL — метода `decrement` нет.

- [ ] **Step 3: Добавить метод `decrement`**

В `UnreadCountStore.kt` добавить метод после `stop()`:

```kotlin
    /** Локально уменьшить счётчик (при отметке прочитанным). Не уходит ниже нуля. */
    fun decrement(by: Int = 1) {
        _state.value = (_state.value - by).coerceAtLeast(0)
    }
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.domain.UnreadCountStoreTest"`
Expected: PASS — 7 тестов.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt
git commit -m "feat: UnreadCountStore.decrement для мгновенной синхронизации бейджа"
```

---

## Task 5: `NotificationsScreenModel` — состояние и логика

Состояния, загрузка, pull-to-refresh, оптимистичная отметка прочитанным. Навигацию по тапу делает сам экран (есть `complaintId`/`announcementId` в `NotificationResponse`), модель только грузит и отмечает.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/FakeNotificationsListApi.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModelTest.kt`

- [ ] **Step 1: Создать fake API для тестов модели**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/FakeNotificationsListApi.kt`:

```kotlin
package com.example.cleancity.ui.feature.notifications

import com.example.cleancity.data.network.NotificationsApiContract
import com.example.cleancity.shared.models.MarkAllReadResponse
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.UnreadCountResponse

class FakeNotificationsListApi : NotificationsApiContract {
    var nextListResult: Result<NotificationListResponse> =
        Result.success(NotificationListResponse(items = emptyList(), total = 0, hasMore = false))
    var nextMarkAllResult: Result<MarkAllReadResponse> =
        Result.success(MarkAllReadResponse(markedCount = 0))
    var markReadShouldThrow: Boolean = false

    val markReadCalls = mutableListOf<Long>()
    var markAllReadCalls: Int = 0
        private set

    override suspend fun unreadCount(): UnreadCountResponse = UnreadCountResponse(count = 0)

    override suspend fun list(limit: Int): NotificationListResponse =
        nextListResult.getOrThrow()

    override suspend fun markRead(id: Long) {
        markReadCalls += id
        if (markReadShouldThrow) throw RuntimeException("network error")
    }

    override suspend fun markAllRead(): MarkAllReadResponse {
        markAllReadCalls += 1
        return nextMarkAllResult.getOrThrow()
    }
}
```

- [ ] **Step 2: Написать падающий тест модели**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModelTest.kt`:

```kotlin
package com.example.cleancity.ui.feature.notifications

import com.example.cleancity.data.network.FakeNotificationsApi
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsScreenModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun notification(id: Long, read: Boolean = false) = NotificationResponse(
        id = id,
        kind = NotificationKind.COMPLAINT_STATUS,
        title = "Заголовок $id",
        body = "Текст $id",
        complaintId = id,
        readAt = if (read) "2026-05-21T10:00:00Z" else null,
        createdAt = "2026-05-21T09:00:00Z",
    )

    // UnreadCountStore.stop() обнуляет счётчик, поэтому посеять значение можно
    // только через один прогон polling: start() + runCurrent(). Polling-job
    // оставляем активным до конца теста — в самом тесте вызвать store.stop().
    private fun TestScope.seededStore(initial: Int): UnreadCountStore {
        val store = UnreadCountStore(
            api = FakeNotificationsApi().apply { nextCount = initial.toLong() },
            scope = this,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        store.start()
        testScheduler.runCurrent()
        return store
    }

    @Test fun `load with empty list yields Empty`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(NotificationListResponse(emptyList(), 0, false))
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))

        model.load()

        assertEquals(NotificationsState.Empty, model.state.value)
    }

    @Test fun `load with items yields Loaded`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))

        model.load()

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(2, state.items.size)
    }

    @Test fun `load failure yields Error`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.failure(RuntimeException("нет сети"))
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))

        model.load()

        assertTrue(model.state.value is NotificationsState.Error)
    }

    @Test fun `markRead optimistically marks item and decrements badge`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val store = seededStore(2)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markRead(1L)

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(true, state.items.first { it.id == 1L }.readAt != null)
        assertEquals(listOf(1L), api.markReadCalls)
        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test fun `markRead on already-read item does not decrement badge`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1, read = true)), 1, false)
            )
        }
        val store = seededStore(3)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markRead(1L)

        assertEquals(3, store.state.value)
        assertTrue(api.markReadCalls.isEmpty())
        store.stop()
    }

    @Test fun `markRead rolls item back to unread on api failure`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1)), 1, false)
            )
            markReadShouldThrow = true
        }
        val store = seededStore(1)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markRead(1L)

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(null, state.items.first { it.id == 1L }.readAt)  // откат — снова непрочитано
        store.stop()
    }

    @Test fun `markAllRead marks every item and zeroes badge`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(listOf(notification(1), notification(2)), 2, false)
            )
        }
        val store = seededStore(2)
        val model = NotificationsScreenModel(api, store)
        model.load()

        model.markAllRead()

        val state = model.state.value as NotificationsState.Loaded
        assertTrue(state.items.all { it.readAt != null })
        assertEquals(1, api.markAllReadCalls)
        assertEquals(0, store.state.value)
        store.stop()
    }

    @Test fun `unreadCount reflects unread items`() = runTest {
        val api = FakeNotificationsListApi().apply {
            nextListResult = Result.success(
                NotificationListResponse(
                    listOf(notification(1), notification(2, read = true), notification(3)), 3, false
                )
            )
        }
        val model = NotificationsScreenModel(api, UnreadCountStore(FakeNotificationsApi()))
        model.load()

        val state = model.state.value as NotificationsState.Loaded
        assertEquals(2, state.unreadCount)
    }
}
```

- [ ] **Step 3: Запустить тест — убедиться, что падает**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.feature.notifications.NotificationsScreenModelTest"`
Expected: FAIL — `NotificationsScreenModel` и `NotificationsState` не существуют.

- [ ] **Step 4: Реализовать модель**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModel.kt`:

```kotlin
package com.example.cleancity.ui.feature.notifications

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.NotificationsApiContract
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NotificationsState {
    data object Initial : NotificationsState
    data object Loading : NotificationsState
    data object Empty : NotificationsState
    data class Error(val message: String) : NotificationsState
    data class Loaded(
        val items: List<NotificationResponse>,
        val isRefreshing: Boolean = false,
    ) : NotificationsState {
        val unreadCount: Int get() = items.count { it.readAt == null }
    }
}

private const val LIST_LIMIT = 50

class NotificationsScreenModel(
    private val api: NotificationsApiContract,
    private val unreadCountStore: UnreadCountStore,
) : ScreenModel {

    private val _state = MutableStateFlow<NotificationsState>(NotificationsState.Initial)
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    /** Грузит список. Вызывается при открытии экрана; повторно — через pull-to-refresh. */
    fun load() {
        if (_state.value !is NotificationsState.Loaded) {
            _state.value = NotificationsState.Loading
        }
        screenModelScope.launch {
            runCatching { api.list(limit = LIST_LIMIT) }
                .onSuccess { resp ->
                    _state.value = if (resp.items.isEmpty()) {
                        NotificationsState.Empty
                    } else {
                        NotificationsState.Loaded(items = resp.items)
                    }
                }
                .onFailure { e ->
                    if (_state.value !is NotificationsState.Loaded) {
                        _state.value = NotificationsState.Error(
                            e.message ?: "Не удалось загрузить уведомления"
                        )
                    } else {
                        _state.update { s ->
                            (s as? NotificationsState.Loaded)?.copy(isRefreshing = false) ?: s
                        }
                    }
                }
        }
    }

    fun refresh() {
        _state.update { s ->
            (s as? NotificationsState.Loaded)?.copy(isRefreshing = true) ?: s
        }
        load()
    }

    /** Оптимистично помечает уведомление прочитанным и синхронизирует бейдж. */
    fun markRead(id: Long) {
        val loaded = _state.value as? NotificationsState.Loaded ?: return
        val target = loaded.items.firstOrNull { it.id == id } ?: return
        if (target.readAt != null) return  // уже прочитано — ничего не делаем

        _state.value = loaded.copy(items = loaded.items.markRead(setOf(id)))
        unreadCountStore.decrement(1)
        screenModelScope.launch {
            runCatching { api.markRead(id) }
                .onFailure {
                    // откат: возвращаем элемент в непрочитанное
                    _state.update { s ->
                        val l = s as? NotificationsState.Loaded ?: return@update s
                        l.copy(items = l.items.map {
                            if (it.id == id) it.copy(readAt = null) else it
                        })
                    }
                }
        }
    }

    /** Оптимистично помечает все уведомления прочитанными. */
    fun markAllRead() {
        val loaded = _state.value as? NotificationsState.Loaded ?: return
        val unread = loaded.unreadCount
        if (unread == 0) return

        _state.value = loaded.copy(
            items = loaded.items.markRead(loaded.items.map { it.id }.toSet())
        )
        unreadCountStore.decrement(unread)
        screenModelScope.launch {
            runCatching { api.markAllRead() }
                .onFailure {
                    // откат к снимку до отметки
                    _state.update { s ->
                        if (s is NotificationsState.Loaded) loaded else s
                    }
                }
        }
    }

    private fun List<NotificationResponse>.markRead(ids: Set<Long>): List<NotificationResponse> =
        map { n ->
            if (n.id in ids && n.readAt == null) {
                n.copy(readAt = "1970-01-01T00:00:00Z")  // маркер «прочитано», точное время неважно
            } else n
        }
}
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.feature.notifications.NotificationsScreenModelTest"`
Expected: PASS — 8 тестов.

- [ ] **Step 6: Зарегистрировать модель в DI**

В `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt` добавить импорт рядом с другими `ui.feature`-импортами:

```kotlin
import com.example.cleancity.ui.feature.notifications.NotificationsScreenModel
```

И добавить в блок `module { ... }` после `factory { ProfileScreenModel(...) }`:

```kotlin
    factory {
        NotificationsScreenModel(
            api = get<NotificationsApiContract>(),
            unreadCountStore = get<UnreadCountStore>(),
        )
    }
```

- [ ] **Step 7: Проверить компиляцию**

Run: `./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModel.kt composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/ composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat: NotificationsScreenModel — загрузка, отметка прочитанным"
```

---

## Task 6: `NotificationCard` + `NotificationsScreen` UI

Заменяем заглушку. UI-композаблы unit-тестами не покрываются (Compose UI-тесты — backlog проекта); проверка — компиляция и smoke. Визуал — по экрану `screen-notifications` в `docs/mockups/mobile-mockup-v3.html`.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/components/NotificationCard.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreen.kt`

- [ ] **Step 1: Создать `NotificationCard`**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/components/NotificationCard.kt`:

```kotlin
package com.example.cleancity.ui.feature.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray100
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green50
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.util.relativeTime

@Composable
fun NotificationCard(
    notification: NotificationResponse,
    modifier: Modifier = Modifier,
) {
    val isUnread = notification.readAt == null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isUnread) Green50 else MaterialTheme.colorScheme.surface)
            .border(1.dp, Gray100, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Green50),
            contentAlignment = Alignment.Center,
        ) {
            val icon = when (notification.kind) {
                NotificationKind.COMPLAINT_STATUS -> Icons.Default.NotificationsActive
                NotificationKind.ANNOUNCEMENT -> Icons.Default.Campaign
            }
            Icon(icon, contentDescription = null, tint = Green600, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                notification.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = Gray900,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                notification.body,
                style = MaterialTheme.typography.bodySmall,
                color = Gray500,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                relativeTime(notification.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = Gray500,
            )
        }
        if (isUnread) {
            Spacer(Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Accent),
            )
        }
    }
}
```

Примечание: импорт `androidx.compose.ui.draw.clip` — добавить строкой `import androidx.compose.ui.draw.clip` (используется `.clip(...)`).

- [ ] **Step 2: Заменить `NotificationsScreen`**

Заменить полностью содержимое `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreen.kt`:

```kotlin
package com.example.cleancity.ui.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.feature.notifications.components.NotificationCard
import com.example.cleancity.ui.feature.shell.tabs.FeedTab
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green700

class NotificationsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: NotificationsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val tabNavigator = LocalTabNavigator.current

        LaunchedEffect(Unit) { model.load() }

        fun onClick(n: NotificationResponse) {
            model.markRead(n.id)
            when {
                n.complaintId != null -> navigator.push(ComplaintDetailScreen(n.complaintId!!))
                n.announcementId != null -> tabNavigator.current = FeedTab
            }
        }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NotificationsTopBar(
                showMarkAll = (state as? NotificationsState.Loaded)?.let { it.unreadCount > 0 } == true,
                onMarkAll = model::markAllRead,
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
            when (val s = state) {
                NotificationsState.Initial, NotificationsState.Loading ->
                    CenteredSpinner()
                NotificationsState.Empty ->
                    EmptyState()
                is NotificationsState.Error ->
                    ErrorState(s.message) { model.load() }
                is NotificationsState.Loaded ->
                    NotificationsList(
                        loaded = s,
                        onRefresh = model::refresh,
                        onItemClick = ::onClick,
                    )
            }
        }
    }
}

@Composable
private fun NotificationsTopBar(
    showMarkAll: Boolean,
    onMarkAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Уведомления",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
        )
        if (showMarkAll) {
            TextButton(onClick = onMarkAll) { Text("Прочитать все", color = Green700) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsList(
    loaded: NotificationsState.Loaded,
    onRefresh: () -> Unit,
    onItemClick: (NotificationResponse) -> Unit,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = loaded.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items = loaded.items, key = { it.id }) { n ->
                NotificationCard(
                    notification = n,
                    modifier = Modifier.clickable { onItemClick(n) },
                )
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "У вас пока нет уведомлений",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray500,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = Gray600, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}
```

- [ ] **Step 3: Проверить компиляцию**

Run: `./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

Если компилятор ругается на отсутствующий цвет в `com.example.cleancity.ui.theme` (например `Accent`, `Green600`) — открыть `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/theme/Color.kt` и заменить импорт на существующий близкий цвет (палитра уже используется в `ProfileScreen.kt` и `ComplaintCard.kt` — брать имена оттуда).

- [ ] **Step 4: Прогнать весь suite**

Run: `./gradlew composeApp:commonTest`
Expected: PASS — все тесты (прежние + новые из Task 2–5).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/
git commit -m "feat: экран уведомлений — список, отметка, pull-to-refresh"
```

---

## Task 7: `MyComplaintsScreenModel` — список «Мои жалобы»

Загрузка `/complaints/mine` с пагинацией по паттерну `FeedScreenModel`.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/FakeMineComplaintsApi.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModelTest.kt`

- [ ] **Step 1: Создать fake API**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/FakeMineComplaintsApi.kt`:

```kotlin
package com.example.cleancity.ui.feature.mycomplaints

import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.domain.photo.PhotoBytes
import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.VoteResponse
import com.example.cleancity.shared.requests.CreateComplaintRequest

class FakeMineComplaintsApi : ComplaintsApiContract {
    /** Результат для очередной страницы по индексу page. */
    var pages: Map<Int, Result<ComplaintListResponse>> = emptyMap()
    val mineCalls = mutableListOf<Pair<Int, Int>>()

    override suspend fun mine(page: Int, size: Int): ComplaintListResponse {
        mineCalls += page to size
        return (pages[page] ?: error("page $page not set")).getOrThrow()
    }

    override suspend fun getMapMarkers(
        swLat: Double, swLon: Double, neLat: Double, neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse = error("not used")

    override suspend fun list(
        page: Int, size: Int, sort: String,
        category: ProblemCategory?, district: String?,
    ): ComplaintListResponse = error("not used")

    override suspend fun voted(page: Int, size: Int): ComplaintListResponse = error("not used")
    override suspend fun getById(id: Long): ComplaintResponse = error("not used")
    override suspend fun vote(id: Long): VoteResponse = error("not used")
    override suspend fun unvote(id: Long): VoteResponse = error("not used")
    override suspend fun findDuplicates(
        latitude: Double, longitude: Double, category: ProblemCategory,
    ): List<ComplaintResponse> = error("not used")
    override suspend fun create(
        request: CreateComplaintRequest, photos: List<PhotoBytes>,
    ): ComplaintResponse = error("not used")
}
```

Примечание: точные сигнатуры `ComplaintsApiContract` (методы `list`, `findDuplicates`, `create`, типы параметров) свериться с `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ComplaintsApi.kt` — реализовать ВСЕ методы контракта, неиспользуемые через `error("not used")`. Образец полной реализации фейка — `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/detail/FakeDetailComplaintsApi.kt`.

- [ ] **Step 2: Написать падающий тест модели**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModelTest.kt`:

```kotlin
package com.example.cleancity.ui.feature.mycomplaints

import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyComplaintsScreenModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun complaint(id: Long) = ComplaintResponse(
        id = id,
        authorId = 1,
        category = ProblemCategory.GARBAGE,
        title = "Жалоба $id",
        description = "описание",
        latitude = 43.5,
        longitude = 39.7,
        address = "адрес",
        status = ComplaintStatus.NEW,
        createdAt = "2026-05-21T09:00:00Z",
        updatedAt = "2026-05-21T09:00:00Z",
    )

    private fun page(ids: LongRange) = ComplaintListResponse(
        items = ids.map { complaint(it) },
        page = 0, size = 20, total = ids.count().toLong(),
    )

    @Test fun `load with items yields Loaded`() = runTest {
        val api = FakeMineComplaintsApi().apply { pages = mapOf(0 to Result.success(page(1L..3L))) }
        val model = MyComplaintsScreenModel(api)

        model.load()

        val state = model.state.value as MyComplaintsState.Loaded
        assertEquals(3, state.complaints.size)
        assertTrue(state.endReached)
    }

    @Test fun `load with empty list yields Empty`() = runTest {
        val api = FakeMineComplaintsApi().apply {
            pages = mapOf(0 to Result.success(ComplaintListResponse(emptyList(), 0, 20, 0)))
        }
        val model = MyComplaintsScreenModel(api)

        model.load()

        assertEquals(MyComplaintsState.Empty, model.state.value)
    }

    @Test fun `load failure yields Error`() = runTest {
        val api = FakeMineComplaintsApi().apply {
            pages = mapOf(0 to Result.failure(RuntimeException("нет сети")))
        }
        val model = MyComplaintsScreenModel(api)

        model.load()

        assertTrue(model.state.value is MyComplaintsState.Error)
    }

    @Test fun `full first page is not endReached and loadNextPage appends`() = runTest {
        val api = FakeMineComplaintsApi().apply {
            pages = mapOf(
                0 to Result.success(page(1L..20L)),
                1 to Result.success(page(21L..25L)),
            )
        }
        val model = MyComplaintsScreenModel(api)
        model.load()
        assertEquals(false, (model.state.value as MyComplaintsState.Loaded).endReached)

        model.loadNextPage()

        val state = model.state.value as MyComplaintsState.Loaded
        assertEquals(25, state.complaints.size)
        assertTrue(state.endReached)
        assertEquals(listOf(0 to 20, 1 to 20), api.mineCalls)
    }
}
```

- [ ] **Step 3: Запустить тест — убедиться, что падает**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.feature.mycomplaints.MyComplaintsScreenModelTest"`
Expected: FAIL — `MyComplaintsScreenModel`/`MyComplaintsState` не существуют.

- [ ] **Step 4: Реализовать модель**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModel.kt`:

```kotlin
package com.example.cleancity.ui.feature.mycomplaints

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.shared.models.ComplaintResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface MyComplaintsState {
    data object Initial : MyComplaintsState
    data object Loading : MyComplaintsState
    data object Empty : MyComplaintsState
    data class Error(val message: String) : MyComplaintsState
    data class Loaded(
        val complaints: List<ComplaintResponse>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val endReached: Boolean = false,
        val nextPage: Int = 1,
    ) : MyComplaintsState
}

private const val PAGE_SIZE = 20

class MyComplaintsScreenModel(
    private val complaintsApi: ComplaintsApiContract,
) : ScreenModel {

    private val _state = MutableStateFlow<MyComplaintsState>(MyComplaintsState.Initial)
    val state: StateFlow<MyComplaintsState> = _state.asStateFlow()

    fun load() {
        if (_state.value is MyComplaintsState.Loaded) return
        _state.value = MyComplaintsState.Loading
        screenModelScope.launch {
            runCatching { complaintsApi.mine(page = 0, size = PAGE_SIZE) }
                .onSuccess { resp ->
                    _state.value = if (resp.items.isEmpty()) {
                        MyComplaintsState.Empty
                    } else {
                        MyComplaintsState.Loaded(
                            complaints = resp.items,
                            endReached = resp.items.size < PAGE_SIZE,
                            nextPage = 1,
                        )
                    }
                }
                .onFailure { e ->
                    _state.value = MyComplaintsState.Error(
                        e.message ?: "Не удалось загрузить ваши жалобы"
                    )
                }
        }
    }

    fun refresh() {
        val current = _state.value as? MyComplaintsState.Loaded ?: return run { load() }
        _state.value = current.copy(isRefreshing = true)
        screenModelScope.launch {
            runCatching { complaintsApi.mine(page = 0, size = PAGE_SIZE) }
                .onSuccess { resp ->
                    _state.value = current.copy(
                        complaints = resp.items,
                        isRefreshing = false,
                        endReached = resp.items.size < PAGE_SIZE,
                        nextPage = 1,
                    )
                }
                .onFailure {
                    _state.update { s ->
                        (s as? MyComplaintsState.Loaded)?.copy(isRefreshing = false) ?: s
                    }
                }
        }
    }

    fun loadNextPage() {
        val current = _state.value as? MyComplaintsState.Loaded ?: return
        if (current.isLoadingMore || current.endReached || current.isRefreshing) return
        _state.value = current.copy(isLoadingMore = true)
        screenModelScope.launch {
            runCatching { complaintsApi.mine(page = current.nextPage, size = PAGE_SIZE) }
                .onSuccess { resp ->
                    val now = _state.value as? MyComplaintsState.Loaded ?: return@onSuccess
                    _state.value = now.copy(
                        complaints = now.complaints + resp.items,
                        isLoadingMore = false,
                        endReached = resp.items.size < PAGE_SIZE,
                        nextPage = now.nextPage + 1,
                    )
                }
                .onFailure {
                    _state.update { s ->
                        (s as? MyComplaintsState.Loaded)?.copy(isLoadingMore = false) ?: s
                    }
                }
        }
    }
}
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.feature.mycomplaints.MyComplaintsScreenModelTest"`
Expected: PASS — 4 теста.

- [ ] **Step 6: Зарегистрировать модель в DI**

В `AppModule.kt` добавить импорт:

```kotlin
import com.example.cleancity.ui.feature.mycomplaints.MyComplaintsScreenModel
```

И в блок `module { ... }` после регистрации `NotificationsScreenModel`:

```kotlin
    factory {
        MyComplaintsScreenModel(complaintsApi = get<ComplaintsApiContract>())
    }
```

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModel.kt composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/ composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat: MyComplaintsScreenModel — список своих жалоб с пагинацией"
```

---

## Task 8: `MyComplaintsScreen` UI + точка входа из профиля

UI-экран и переход из пункта меню «Мои жалобы» в профиле. UI unit-тестами не покрывается.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt`

- [ ] **Step 1: Создать `MyComplaintsScreen`**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreen.kt`:

```kotlin
package com.example.cleancity.ui.feature.mycomplaints

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreen
import com.example.cleancity.ui.feature.feed.components.ComplaintCard
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray900

class MyComplaintsScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: MyComplaintsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) { model.load() }

        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TopBar(
                onBack = { navigator.pop() },
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
            )
            when (val s = state) {
                MyComplaintsState.Initial, MyComplaintsState.Loading -> CenteredSpinner()
                MyComplaintsState.Empty -> EmptyState()
                is MyComplaintsState.Error -> ErrorState(s.message) { model.load() }
                is MyComplaintsState.Loaded -> LoadedList(
                    loaded = s,
                    onRefresh = model::refresh,
                    onLoadMore = model::loadNextPage,
                    onComplaintClick = { id -> navigator.push(ComplaintDetailScreen(id)) },
                )
            }
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
        }
        Text(
            "Мои жалобы",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedList(
    loaded: MyComplaintsState.Loaded,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onComplaintClick: (Long) -> Unit,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    val shouldLoadMore by remember(loaded) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && lastVisible >= total - 5 && !loaded.endReached && !loaded.isLoadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) onLoadMore() }

    PullToRefreshBox(
        isRefreshing = loaded.isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            items(items = loaded.complaints, key = { it.id }) { complaint ->
                ComplaintCard(
                    complaint = complaint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onComplaintClick(complaint.id) },
                )
            }
            if (loaded.isLoadingMore) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(strokeWidth = 2.dp) }
                }
            }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            "Вы пока не создавали жалоб",
            style = MaterialTheme.typography.bodyMedium,
            color = Gray500,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = Gray600, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}
```

- [ ] **Step 2: Подключить переход из меню профиля**

В `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt` найти строку 107:

```kotlin
                    onMyComplaintsClick = { /* shell-level переключение таба — Day 13 */ },
```

Заменить на:

```kotlin
                    onMyComplaintsClick = { navigator.push(MyComplaintsScreen()) },
```

Добавить импорт в начало файла рядом с другими `ui.feature`-импортами:

```kotlin
import com.example.cleancity.ui.feature.mycomplaints.MyComplaintsScreen
```

(`navigator` уже объявлен в `ProfileScreen.Content()` — `val navigator = LocalNavigator.currentOrThrow`, строка 90 — дополнительных изменений не нужно.)

- [ ] **Step 3: Проверить компиляцию**

Run: `./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Прогнать весь suite**

Run: `./gradlew composeApp:commonTest`
Expected: PASS — все тесты.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreen.kt composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt
git commit -m "feat: экран «Мои жалобы» + переход из меню профиля"
```

---

## Task 9: Блок «Решение администрации» в деталях закрытой жалобы

Для статусов `REJECTED` и `DUPLICATE` показать выделенный callout с комментарием администрации (последняя запись `statusHistory`). Логику извлечения комментария вынести в чистую тестируемую функцию.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/detail/Resolution.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/detail/ResolutionTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/detail/ComplaintDetailScreen.kt`

- [ ] **Step 1: Написать падающий тест функции извлечения резолюции**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/detail/ResolutionTest.kt`:

```kotlin
package com.example.cleancity.ui.feature.detail

import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.StatusChangeResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolutionTest {

    private fun change(to: ComplaintStatus, comment: String) = StatusChangeResponse(
        toStatus = to, comment = comment, createdAt = "2026-05-21T10:00:00Z",
    )

    @Test fun `rejected returns last status change comment`() {
        val history = listOf(
            change(ComplaintStatus.IN_PROGRESS, "взято в работу"),
            change(ComplaintStatus.REJECTED, "вне зоны ответственности города"),
        )
        assertEquals("вне зоны ответственности города", resolutionComment(ComplaintStatus.REJECTED, history))
    }

    @Test fun `duplicate returns last status change comment`() {
        val history = listOf(change(ComplaintStatus.DUPLICATE, "дубликат существующей жалобы"))
        assertEquals("дубликат существующей жалобы", resolutionComment(ComplaintStatus.DUPLICATE, history))
    }

    @Test fun `non-terminal status returns null`() {
        val history = listOf(change(ComplaintStatus.IN_PROGRESS, "взято в работу"))
        assertNull(resolutionComment(ComplaintStatus.IN_PROGRESS, history))
    }

    @Test fun `resolved status returns null`() {
        val history = listOf(change(ComplaintStatus.RESOLVED, "проблема устранена"))
        assertNull(resolutionComment(ComplaintStatus.RESOLVED, history))
    }

    @Test fun `terminal status with empty history returns null`() {
        assertNull(resolutionComment(ComplaintStatus.REJECTED, emptyList()))
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.feature.detail.ResolutionTest"`
Expected: FAIL — функции `resolutionComment` нет.

- [ ] **Step 3: Реализовать функцию**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/detail/Resolution.kt`:

```kotlin
package com.example.cleancity.ui.feature.detail

import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.StatusChangeResponse

/**
 * Комментарий администрации для закрытой жалобы (REJECTED/DUPLICATE) —
 * текст последней записи истории статусов. Для остальных статусов — null.
 */
fun resolutionComment(
    status: ComplaintStatus,
    statusHistory: List<StatusChangeResponse>,
): String? {
    if (status != ComplaintStatus.REJECTED && status != ComplaintStatus.DUPLICATE) return null
    return statusHistory.lastOrNull()?.comment
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew composeApp:commonTest --tests "com.example.cleancity.ui.feature.detail.ResolutionTest"`
Expected: PASS — 5 тестов.

- [ ] **Step 5: Добавить callout-блок в `ComplaintDetailScreen`**

В `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/detail/ComplaintDetailScreen.kt`, в композабле `LoadedContent`, между `item { DescriptionSection(...) }` и блоком `if (c.statusHistory.isNotEmpty())` (около строки 200) вставить:

```kotlin
        resolutionComment(c.status, c.statusHistory)?.let { comment ->
            item {
                ResolutionBlock(
                    comment = comment,
                    duplicateOfId = c.duplicateOfId.takeIf { c.status == ComplaintStatus.DUPLICATE },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
```

И добавить в конец файла новый приватный композабл:

```kotlin
@Composable
private fun ResolutionBlock(
    comment: String,
    duplicateOfId: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Red.copy(alpha = 0.08f))
            .border(1.dp, Red.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Решение администрации",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = Red,
        )
        Text(
            comment,
            style = MaterialTheme.typography.bodyMedium,
            color = Gray700,
        )
        if (duplicateOfId != null) {
            Text(
                "Дубликат жалобы #$duplicateOfId",
                style = MaterialTheme.typography.bodySmall,
                color = Gray500,
            )
        }
    }
}
```

Проверить, что все используемые в `ResolutionBlock` импорты уже есть в файле (`Column`, `Arrangement`, `clip`, `background`, `border`, `RoundedCornerShape`, `padding`, `fillMaxWidth`, `Text`, `MaterialTheme`, `FontWeight`, цвета `Red`/`Gray700`/`Gray500`, `ComplaintStatus`). Файл уже использует `StatusHistoryRow` со схожим набором — недостающие импорты добавить по образцу существующих в шапке файла. Цвет `Red` уже импортирован (используется на строке 319).

- [ ] **Step 6: Проверить компиляцию**

Run: `./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Прогнать весь suite**

Run: `./gradlew composeApp:commonTest`
Expected: PASS — все тесты (ожидаемо ~82 прежних + новые этого плана).

- [ ] **Step 8: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/detail/Resolution.kt composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/detail/ResolutionTest.kt composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/detail/ComplaintDetailScreen.kt
git commit -m "feat: блок «Решение администрации» в деталях закрытой жалобы"
```

---

## Task 10: Финальная проверка и обновление плана проекта

- [ ] **Step 1: Полный прогон тестов и сборка APK**

Run: `./gradlew composeApp:commonTest`
Expected: PASS — весь suite зелёный.

Run: `./gradlew composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL — APK собирается.

- [ ] **Step 2: Smoke на реальном Samsung A33 5G**

Установить APK (`~/Library/Android/sdk/platform-tools/adb install -r <apk>`), включить `adb reverse tcp:8081`, проверить сценарии чекпоинта:
- Создать жалобу → сменить её статус через web/Postman → уведомление появляется в `NotificationsScreen` с unread-меткой.
- Тап по уведомлению о статусе → открывается деталь жалобы, unread снимается, бейдж на навигации уменьшается.
- «Прочитать все» → все уведомления становятся прочитанными, бейдж обнуляется.
- Тап по ANNOUNCEMENT-уведомлению → переключение на вкладку «Лента».
- Профиль → «Мои жалобы» → список своих жалоб, тап открывает деталь.
- Открыть отклонённую (`REJECTED`) жалобу → виден блок «Решение администрации».
- Экран регистрации: слоган «...влиять на состояние города.».

- [ ] **Step 3: Отметить Day 12 в `docs/PLAN.md`**

В `docs/PLAN.md` в разделе «День 12» проставить `[x]` у реализованных пунктов и добавить строку о состоянии с датой закрытия и пометкой, что `VotedComplaintsScreen` исключён, а FCM/системные push перенесены в Day 14-буфер (см. спеку `docs/superpowers/specs/2026-05-21-day12-notifications-my-complaints-design.md`).

- [ ] **Step 4: Commit**

```bash
git add docs/PLAN.md
git commit -m "docs: Day 12 закрыт — уведомления + мои жалобы"
```

---

## Заметки для исполнителя

- **TDD строго:** для моделей, API и чистых функций (Task 2–5, 7, 9) — сначала падающий тест, затем реализация. UI-композаблы (Task 6, 8) тестов не имеют — проверка через компиляцию и smoke.
- **Существующий suite не ломать:** после расширения `NotificationsApiContract` и `FakeNotificationsApi` обязательно прогнать `UnreadCountStoreTest` (Task 3, Step 6).
- **Гостевой режим вне scope:** по утверждённой спеке `NotificationsScreen` имеет 4 состояния (`Loading/Empty/Error/Loaded`). Гость, открывший вкладку, при ошибке `401` увидит `Error` с «Повторить» — это приемлемо для дипломного scope. Отдельный guest-prompt не добавлять.
- **Авто-обновление списка:** спека упоминала перезагрузку списка при возврате в foreground. В плане список обновляется при открытии экрана и через pull-to-refresh; счётчик-бейдж непрерывно опрашивается `UnreadCountStore` (Day 10) — поэтому новые уведомления в foreground видны на бейдже сразу, а список подтягивается жестом. Отдельный lifecycle-хук foreground не вводим (expect/actual вне scope дипломного Day 12).
- **Цвета темы:** имена цветов (`Accent`, `Green50`, `Green600`, `Green700`, `Gray100/500/600/700/900`, `Red`) брать из `com.example.cleancity.ui.theme` — те же, что в `ProfileScreen.kt` и `ComplaintCard.kt`. Если имя не совпало — свериться с `ui/theme/Color.kt`.
- **Контракт `ComplaintsApiContract`:** при создании `FakeMineComplaintsApi` реализовать ВСЕ методы контракта (образец полноты — `FakeDetailComplaintsApi`), иначе commonTest не скомпилируется.

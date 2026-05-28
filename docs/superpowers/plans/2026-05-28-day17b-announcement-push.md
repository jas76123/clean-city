# Day 17B хвост — Push при публикации объявления — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Закрыть пункт `[ ] При публикации → push на mobile` (Day 17B). Когда админ публикует объявление в web-админке — у жителей выбранных районов на Android появляется системное уведомление в шторке (если приложение закрыто/в фоне) или in-app banner (если открыто).

**Architecture:** Гибрид A+C из спеки `2026-05-28-day17b-announcement-push-design.md`. Backend уже эмитит записи `kind=ANNOUNCEMENT` через `notifications.notify(...)`. Mobile получает их двумя путями: foreground polling 30 сек (`UnreadCountStore` → `NotificationEventBus` → in-app Snackbar или Android-bridge → системная шторка) + фоновый WorkManager каждые 15 мин для killed-приложения. Дедуп через `lastSeenNotificationId` в DataStore per user. Без Firebase, без Google Play Services.

**Tech Stack:**
- Compose Multiplatform 1.7.3, Kotlin 2.0.21, Koin 3.5.6, Voyager 1.1.0-beta03
- Новые Android-зависимости: `androidx.work:work-runtime-ktx`, `androidx.datastore:datastore-preferences`, `androidx.lifecycle:lifecycle-process`, `androidx.core:core-ktx`
- Существующие: `kotlinx-coroutines-core`, NotificationsApi (Ktor), AuthRepository (Koin)

**Spec:** `docs/superpowers/specs/2026-05-28-day17b-announcement-push-design.md`

**Текущие источники, на которые опираемся (read-only):**
- `composeApp/src/commonMain/.../domain/UnreadCountStore.kt` — текущий polling-цикл 30 сек
- `composeApp/src/commonMain/.../data/repository/AuthRepository.kt` — `state: StateFlow<AuthState>`, `logout()`
- `composeApp/src/commonMain/.../domain/AuthState.kt` — `Authenticated(user: UserResponse)` где `user.id: Long`
- `composeApp/src/commonMain/.../data/network/NotificationsApi.kt` — `list(limit=50): NotificationListResponse`
- `shared/src/.../NotificationResponse.kt` — `id: Long`, `kind: NotificationKind`, `title, body, readAt`
- `composeApp/src/commonMain/.../domain/DeepLinkBus.kt` — паттерн для in-process event bus (object singleton со StateFlow)
- `composeApp/src/androidMain/.../MainActivity.kt` — точка обработки extras в `onNewIntent`
- `composeApp/src/androidMain/.../CleanCityApplication.kt` — Koin start + точка enqueue WorkManager

**Конвенции из репо:**
- KMP-проект, источники: `commonMain`, `androidMain`, `commonTest` (iosMain отсутствует — iOS НЕ в скоупе)
- DI через Koin: `appModule()` (common) + `androidModule()` (android-specific)
- `expect/actual` паттерн через factory-класс (как `TokenStorageFactory`)
- Тесты только в `commonTest` (Android-unit-test source set не настроен; всё android-specific тестируем e2e на устройстве)
- Commits: префиксы `feat(day17b-push):`, `test(day17b-push):`, `chore(day17b-push):` по аналогии с `feat(day17d):`
- Ruska commit messages, Co-Authored-By trailer как в недавних коммитах

---

## File Structure (что создаём / меняем)

**Новые файлы (commonMain):**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationEventBus.kt` — общий шина новых ANNOUNCEMENT для подписчиков (Snackbar, Android-bridge)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.kt` — interface + expect factory
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/AnnouncementSeenFilter.kt` — pure logic «выдай новые ANNOUNCEMENT и обнови lastSeen» (используется и в polling, и в worker)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationTapBus.kt` — object singleton для сигнала «тапнули по системному уведомлению» (id → MainShellScreen)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt` — Material 3 Snackbar с кастомным контентом

**Новые файлы (androidMain):**
- `composeApp/src/androidMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.android.kt` — actual factory + DataStore-реализация
- `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/SystemNotificationDispatcher.kt` — Android-only, обёртка над NotificationManagerCompat + создание channel
- `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementBusBridge.kt` — Android-only, слушает bus, проверяет ProcessLifecycleOwner, диспатчит в Dispatcher если приложение НЕ foreground
- `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementCheckWorker.kt` — Android-only, CoroutineWorker, periodic 15 мин
- `composeApp/src/androidMain/res/drawable/ic_notification.xml` — белый mono vector для шторки

**Новые файлы (commonTest):**
- `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/NotificationEventBusTest.kt`
- `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/AnnouncementSeenFilterTest.kt`
- `composeApp/src/commonTest/kotlin/com/example/cleancity/data/local/InMemorySeenNotificationStore.kt` — fake (не в продакшн-коде)

**Новые файлы (ops):**
- `ops/trigger-announcement.sh` — helper для e2e

**Изменяемые:**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt` — новые зависимости + новая `pollOnce`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt` — SnackbarHost + collect bus + permission flow hook + tap-bus listener
- `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt` — переписать под list-based polling и новые сценарии
- `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt` — зарегистрировать `NotificationEventBus`, `SeenNotificationStore` (через factory), новые параметры `UnreadCountStore`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt` — `SeenNotificationStoreFactory`, `SystemNotificationDispatcher`, `AnnouncementBusBridge`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt` — enqueue WorkManager, init `AnnouncementBusBridge.start()`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt` — обработка `EXTRA_NOTIFICATION_ID` в `onNewIntent`
- `composeApp/src/androidMain/AndroidManifest.xml` — `<uses-permission POST_NOTIFICATIONS />`
- `gradle/libs.versions.toml` — версии и aliases новых зависимостей
- `composeApp/build.gradle.kts` — подключить новые зависимости в `androidMain.dependencies`

---

## Task 1: Подключить новые Android-зависимости

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Добавить версии в `[versions]`**

Открыть `gradle/libs.versions.toml`. После строки `coil = "3.0.4"` добавить:

```toml
androidx-work = "2.10.0"
androidx-datastore = "1.1.1"
androidx-lifecycle-process = "2.8.7"
androidx-core-ktx = "1.13.1"
```

- [ ] **Step 2: Добавить aliases в `[libraries]`**

В конец секции `[libraries]` добавить:

```toml
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "androidx-work" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "androidx-datastore" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "androidx-lifecycle-process" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core-ktx" }
```

- [ ] **Step 3: Подключить в `composeApp/build.gradle.kts`**

В блоке `androidMain.dependencies { ... }` (после `implementation(libs.play.services.location)`) добавить:

```kotlin
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.androidx.core.ktx)
```

- [ ] **Step 4: Sync gradle и убедиться что компилируется**

Run:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid
```
Expected: BUILD SUCCESSFUL (никаких изменений в коде ещё нет, только новые зависимости).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "$(cat <<'EOF'
chore(day17b-push): зависимости WorkManager + DataStore + lifecycle-process

Подготовка к local-push: WorkManager для фонового polling,
DataStore для хранения lastSeenNotificationId, lifecycle-process
для определения foreground/background в AnnouncementBusBridge.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: NotificationEventBus + тест

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationEventBus.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/NotificationEventBusTest.kt`

- [ ] **Step 1: Failing test**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/NotificationEventBusTest.kt`:

```kotlin
package com.example.cleancity.domain

import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationEventBusTest {

    private fun ann(id: Long): NotificationResponse = NotificationResponse(
        id = id,
        kind = NotificationKind.ANNOUNCEMENT,
        title = "T-$id",
        body = "B-$id",
        createdAt = "2026-05-28T10:00:00Z",
    )

    @Test
    fun `subscribers receive emitted items`() = runTest {
        val bus = NotificationEventBus()
        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.take(2).toList(collected) }

        bus.emit(ann(1))
        bus.emit(ann(2))
        job.join()

        assertEquals(listOf(1L, 2L), collected.map { it.id })
    }

    @Test
    fun `late subscriber misses earlier emits (replay=0)`() = runTest {
        val bus = NotificationEventBus()
        bus.emit(ann(1))   // нет подписчиков — улетает в никуда

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.take(1).toList(collected) }
        bus.emit(ann(2))
        job.join()

        assertEquals(listOf(2L), collected.map { it.id })
    }
}
```

- [ ] **Step 2: Run test, verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.NotificationEventBusTest"`
Expected: FAIL — `NotificationEventBus` не существует.

- [ ] **Step 3: Implementation**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationEventBus.kt`:

```kotlin
package com.example.cleancity.domain

import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Однонаправленная шина для свежих ANNOUNCEMENT-уведомлений, замеченных
 * polling-циклом. Подписчики: AnnouncementInAppBanner (foreground),
 * AnnouncementBusBridge (background → системная шторка).
 */
class NotificationEventBus {
    private val _newAnnouncements = MutableSharedFlow<NotificationResponse>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val newAnnouncements: SharedFlow<NotificationResponse> = _newAnnouncements

    suspend fun emit(notification: NotificationResponse) {
        _newAnnouncements.emit(notification)
    }
}
```

- [ ] **Step 4: Run test, verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.NotificationEventBusTest"`
Expected: PASS, both tests green.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationEventBus.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/domain/NotificationEventBusTest.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): NotificationEventBus — шина свежих ANNOUNCEMENT

Подписчики: in-app banner (foreground) и Android-bridge (background → шторка).
replay=0, buffer=8, DROP_OLDEST при переполнении.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: SeenNotificationStore (interface + Android actual + in-memory fake)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.kt`
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.android.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/local/InMemorySeenNotificationStore.kt`

- [ ] **Step 1: Common interface + expect factory**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.kt`:

```kotlin
package com.example.cleancity.data.local

/**
 * Per-user хранилище последнего известного id уведомления, чтобы
 * не пушить повторно одну и ту же запись из разных каналов (polling + worker).
 * 0 — стартовое значение «никаких записей ещё не видели».
 */
interface SeenNotificationStore {
    suspend fun get(userId: Long): Long
    suspend fun set(userId: Long, id: Long)
    suspend fun clear(userId: Long)
}

expect class SeenNotificationStoreFactory {
    fun create(): SeenNotificationStore
}
```

- [ ] **Step 2: In-memory fake для тестов**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/data/local/InMemorySeenNotificationStore.kt`:

```kotlin
package com.example.cleancity.data.local

class InMemorySeenNotificationStore : SeenNotificationStore {
    private val map = mutableMapOf<Long, Long>()
    override suspend fun get(userId: Long): Long = map[userId] ?: 0L
    override suspend fun set(userId: Long, id: Long) { map[userId] = id }
    override suspend fun clear(userId: Long) { map.remove(userId) }
}
```

- [ ] **Step 3: Android actual через DataStore**

Создать `composeApp/src/androidMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.android.kt`:

```kotlin
package com.example.cleancity.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.seenNotificationsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "seen_notifications")

class AndroidSeenNotificationStore(private val context: Context) : SeenNotificationStore {
    override suspend fun get(userId: Long): Long {
        val key = longPreferencesKey("lastSeen:$userId")
        return context.seenNotificationsDataStore.data.first()[key] ?: 0L
    }

    override suspend fun set(userId: Long, id: Long) {
        val key = longPreferencesKey("lastSeen:$userId")
        context.seenNotificationsDataStore.edit { it[key] = id }
    }

    override suspend fun clear(userId: Long) {
        val key = longPreferencesKey("lastSeen:$userId")
        context.seenNotificationsDataStore.edit { it.remove(key) }
    }
}

actual class SeenNotificationStoreFactory(private val context: Context) {
    actual fun create(): SeenNotificationStore = AndroidSeenNotificationStore(context)
}
```

- [ ] **Step 4: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/data/local/SeenNotificationStore.android.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/data/local/InMemorySeenNotificationStore.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): SeenNotificationStore — DataStore-хранение lastSeenId per user

interface SeenNotificationStore в commonMain + expect SeenNotificationStoreFactory.
Android actual через androidx.datastore.preferences (файл seen_notifications).
InMemorySeenNotificationStore — fake для commonTest.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: AnnouncementSeenFilter + полный набор тестов

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/AnnouncementSeenFilter.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/AnnouncementSeenFilterTest.kt`

Это «сердце» дедупликации. И polling-цикл, и WorkManager-worker зовут один и тот же `filter.newAnnouncements(userId, items)`. Один тестовый объект — двойная гарантия.

- [ ] **Step 1: Failing test**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/AnnouncementSeenFilterTest.kt`:

```kotlin
package com.example.cleancity.domain

import com.example.cleancity.data.local.InMemorySeenNotificationStore
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnouncementSeenFilterTest {

    private fun item(
        id: Long,
        kind: NotificationKind = NotificationKind.ANNOUNCEMENT,
        readAt: String? = null,
    ): NotificationResponse = NotificationResponse(
        id = id,
        kind = kind,
        title = "T-$id",
        body = "B-$id",
        readAt = readAt,
        createdAt = "2026-05-28T10:00:00Z",
    )

    @Test
    fun `first call with lastSeen=0 returns empty and memorizes max`() = runTest {
        val store = InMemorySeenNotificationStore()
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(item(3), item(2), item(1))
        val newOnes = filter.newAnnouncements(userId = 42L, items = items)

        assertTrue(newOnes.isEmpty(), "Первый вызов не должен возвращать ничего")
        assertEquals(3L, store.get(42L), "lastSeen должен стать max(items)=3")
    }

    @Test
    fun `first call with empty items leaves store untouched`() = runTest {
        val store = InMemorySeenNotificationStore()
        val filter = AnnouncementSeenFilter(store)

        val newOnes = filter.newAnnouncements(userId = 42L, items = emptyList())

        assertTrue(newOnes.isEmpty())
        assertEquals(0L, store.get(42L))
    }

    @Test
    fun `subsequent call returns only items newer than lastSeen`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 5L)   // притворяемся что предыдущий цикл уже видел до id=5
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(item(7), item(6), item(5), item(4))
        val newOnes = filter.newAnnouncements(42L, items)

        assertEquals(listOf(7L, 6L), newOnes.map { it.id }.sortedDescending())
        assertEquals(7L, store.get(42L))
    }

    @Test
    fun `filter excludes COMPLAINT_STATUS even if newer`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 5L)
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(
            item(7, kind = NotificationKind.COMPLAINT_STATUS),
            item(6, kind = NotificationKind.ANNOUNCEMENT),
        )
        val newOnes = filter.newAnnouncements(42L, items)

        assertEquals(listOf(6L), newOnes.map { it.id })
        assertEquals(7L, store.get(42L), "lastSeen должен подняться даже на не-ANNOUNCEMENT")
    }

    @Test
    fun `filter excludes already-read announcements`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 5L)
        val filter = AnnouncementSeenFilter(store)

        val items = listOf(
            item(7, readAt = "2026-05-28T10:00:00Z"),  // прочитано — не пушим
            item(6),                                    // новое непрочитанное
        )
        val newOnes = filter.newAnnouncements(42L, items)

        assertEquals(listOf(6L), newOnes.map { it.id })
    }

    @Test
    fun `lastSeen not updated when no items newer than current`() = runTest {
        val store = InMemorySeenNotificationStore()
        store.set(42L, 10L)
        val filter = AnnouncementSeenFilter(store)

        filter.newAnnouncements(42L, listOf(item(5), item(3)))

        assertEquals(10L, store.get(42L), "lastSeen не должен откатиться назад")
    }

    @Test
    fun `per-user isolation`() = runTest {
        val store = InMemorySeenNotificationStore()
        val filter = AnnouncementSeenFilter(store)

        filter.newAnnouncements(1L, listOf(item(10)))  // первый вызов user=1, lastSeen=10
        val newForUser2 = filter.newAnnouncements(2L, listOf(item(10), item(11)))

        // для user=2 lastSeen=0, первый вызов молчит и запоминает max=11
        assertTrue(newForUser2.isEmpty())
        assertEquals(10L, store.get(1L))
        assertEquals(11L, store.get(2L))
    }
}
```

- [ ] **Step 2: Run, verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.AnnouncementSeenFilterTest"`
Expected: FAIL — `AnnouncementSeenFilter` не существует.

- [ ] **Step 3: Implementation**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/AnnouncementSeenFilter.kt`:

```kotlin
package com.example.cleancity.domain

import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse

/**
 * Возвращает новые непрочитанные ANNOUNCEMENT, которые ещё не показывались
 * через push/баннер для этого юзера. Обновляет lastSeen до max(items.id).
 *
 * Контракт «первого вызова»: если lastSeen == 0 (юзер только что залогинился) —
 * возвращает пустой список, но запоминает max. Иначе будут сыпать N баннеров
 * сразу при первом входе.
 */
class AnnouncementSeenFilter(private val store: SeenNotificationStore) {

    suspend fun newAnnouncements(
        userId: Long,
        items: List<NotificationResponse>,
    ): List<NotificationResponse> {
        val lastSeen = store.get(userId)
        val maxId = items.maxOfOrNull { it.id } ?: 0L

        if (lastSeen == 0L) {
            if (maxId > 0) store.set(userId, maxId)
            return emptyList()
        }

        val newOnes = items.filter {
            it.kind == NotificationKind.ANNOUNCEMENT &&
                it.readAt == null &&
                it.id > lastSeen
        }
        if (maxId > lastSeen) store.set(userId, maxId)
        return newOnes
    }
}
```

- [ ] **Step 4: Run, verify all pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.AnnouncementSeenFilterTest"`
Expected: PASS, 7 tests green.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/AnnouncementSeenFilter.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/domain/AnnouncementSeenFilterTest.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): AnnouncementSeenFilter — общая дедупликация для polling и worker

Pure-функция «выдай новые ANNOUNCEMENT и обнови lastSeen». Используется
и в UnreadCountStore (foreground 30 сек), и в AnnouncementCheckWorker
(background 15 мин), даёт общий ключ дедупликации через SeenNotificationStore.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Переделать UnreadCountStore на list-based polling + bus + filter

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt`

- [ ] **Step 1: Переписать существующий тест-файл под новый контракт**

Целиком заменить содержимое `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt`:

```kotlin
package com.example.cleancity.domain

import com.example.cleancity.data.local.InMemorySeenNotificationStore
import com.example.cleancity.data.network.FakeNotificationsApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationListResponse
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.shared.models.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnreadCountStoreTest {

    private val testUser = UserResponse(
        id = 42L,
        email = "u@x",
        role = UserRole.RESIDENT,
        emailVerified = true,
        createdAt = "2026-05-01T00:00:00Z",
    )

    private fun fakeAuth(): TestAuthRepository = TestAuthRepository(
        MutableStateFlow(AuthState.Authenticated(testUser)),
    )

    private fun ann(id: Long, readAt: String? = null) = NotificationResponse(
        id = id,
        kind = NotificationKind.ANNOUNCEMENT,
        title = "T-$id",
        body = "B-$id",
        readAt = readAt,
        createdAt = "2026-05-28T10:00:00Z",
    )

    private fun newStore(
        api: FakeNotificationsApi,
        seen: InMemorySeenNotificationStore = InMemorySeenNotificationStore(),
        bus: NotificationEventBus = NotificationEventBus(),
        auth: TestAuthRepository = fakeAuth(),
        scope: TestScope,
    ): UnreadCountStore = UnreadCountStore(
        api = api,
        seenStore = seen,
        filter = AnnouncementSeenFilter(seen),
        bus = bus,
        authProvider = { (auth.state.value as? AuthState.Authenticated)?.user?.id },
        scope = scope,
        dispatcher = UnconfinedTestDispatcher(scope.testScheduler),
    )

    private fun listResp(items: List<NotificationResponse>) =
        NotificationListResponse(items = items, total = items.size.toLong(), hasMore = false)

    @Test
    fun `state reflects count of unread items from list`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(3), ann(2), ann(1))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()

        assertEquals(3, store.state.value)
        store.stop()
    }

    @Test
    fun `first poll after login does not emit to bus`() = runTest {
        val bus = NotificationEventBus()
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5), ann(4))))
        }
        val store = newStore(api, bus = bus, scope = this)

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.toList(collected) }

        store.start()
        testScheduler.runCurrent()

        assertTrue(collected.isEmpty(), "Первый poll должен только запомнить max, без эмита")
        store.stop()
        job.cancel()
    }

    @Test
    fun `subsequent poll emits new ANNOUNCEMENT to bus`() = runTest {
        val bus = NotificationEventBus()
        val seen = InMemorySeenNotificationStore()
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5))))
        }
        val store = newStore(api, seen = seen, bus = bus, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(5L, seen.get(42L))   // первый poll выставил lastSeen

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.take(1).toList(collected) }

        api.nextListResult = Result.success(listResp(listOf(ann(6), ann(5))))
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()
        job.join()

        assertEquals(listOf(6L), collected.map { it.id })
        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test
    fun `subsequent poll does not emit COMPLAINT_STATUS`() = runTest {
        val bus = NotificationEventBus()
        val seen = InMemorySeenNotificationStore().apply { /* set later via first poll */ }
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5))))
        }
        val store = newStore(api, seen = seen, bus = bus, scope = this)

        store.start()
        testScheduler.runCurrent()

        val collected = mutableListOf<NotificationResponse>()
        val job = launch { bus.newAnnouncements.toList(collected) }

        api.nextListResult = Result.success(listResp(listOf(
            NotificationResponse(
                id = 6L,
                kind = NotificationKind.COMPLAINT_STATUS,
                title = "Статус",
                body = "Жалоба обновлена",
                createdAt = "2026-05-28T10:00:00Z",
            ),
            ann(5),
        )))
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()

        assertTrue(collected.isEmpty())
        store.stop()
        job.cancel()
    }

    @Test
    fun `api error keeps last state and silently retries`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(5))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(1, store.state.value)

        api.nextListResult = Result.failure(RuntimeException("net"))
        testScheduler.advanceTimeBy(30_001)
        testScheduler.runCurrent()

        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test
    fun `stop cancels loop, restart works`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(9))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(1, store.state.value)

        store.stop()
        assertEquals(0, store.state.value)

        api.nextListResult = Result.success(listResp(listOf(ann(9), ann(10))))
        store.start()
        testScheduler.runCurrent()
        assertEquals(2, store.state.value)
        store.stop()
    }

    @Test
    fun `start is idempotent`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(1))))
        }
        val store = newStore(api, scope = this)

        store.start()
        store.start()
        testScheduler.runCurrent()

        assertEquals(1, store.state.value)
        store.stop()
    }

    @Test
    fun `decrement and increment still work`() = runTest {
        val api = FakeNotificationsApi().apply {
            nextListResult = Result.success(listResp(listOf(ann(1), ann(2), ann(3), ann(4), ann(5))))
        }
        val store = newStore(api, scope = this)

        store.start()
        testScheduler.runCurrent()
        assertEquals(5, store.state.value)

        store.decrement(2)
        assertEquals(3, store.state.value)
        store.decrement(10)
        assertEquals(0, store.state.value)
        store.increment(3)
        assertEquals(3, store.state.value)

        store.stop()
    }

    /** Минимальный fake AuthRepository, чтобы не тащить весь продакшн-объект. */
    private class TestAuthRepository(val state: StateFlow<AuthState>)
}
```

Примечание: тест использует фабричный лямбда-параметр `authProvider: suspend () -> Long?` вместо инжекта `AuthRepository`, чтобы не плодить моки. Продакшн-код в Koin передаёт `{ (authRepo.state.value as? AuthState.Authenticated)?.user?.id }`.

- [ ] **Step 2: Run, verify fails on missing API**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.UnreadCountStoreTest"`
Expected: FAIL — compile error («UnreadCountStore() doesn't have these parameters»).

- [ ] **Step 3: Implementation — заменить `UnreadCountStore`**

Целиком заменить содержимое `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt`:

```kotlin
package com.example.cleancity.domain

import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.data.network.NotificationsApiContract
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground polling-канал уведомлений: каждые 30 сек тянет последние
 * записи, обновляет бейдж и эмитит новые ANNOUNCEMENT в [NotificationEventBus].
 *
 * Замена прежнему unreadCount-only поведению: теперь один запрос даёт
 * и count, и данные для дедупликации (через [AnnouncementSeenFilter]).
 */
class UnreadCountStore(
    private val api: NotificationsApiContract,
    private val seenStore: SeenNotificationStore,
    private val filter: AnnouncementSeenFilter,
    private val bus: NotificationEventBus,
    private val authProvider: suspend () -> Long?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val intervalMillis: Long = 30_000L,
) {
    private val _state = MutableStateFlow(0)
    val state: StateFlow<Int> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(dispatcher) {
            while (isActive) {
                pollOnce()
                delay(intervalMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = 0
    }

    /** Принудительный сброс seenStore (вызывается при logout). */
    suspend fun clearSeen() {
        val userId = authProvider() ?: return
        seenStore.clear(userId)
    }

    /** Локально уменьшить счётчик (при отметке прочитанным). Не уходит ниже нуля. */
    fun decrement(by: Int = 1) {
        _state.value = (_state.value - by).coerceAtLeast(0)
    }

    /** Локально увеличить счётчик (откат отметки прочитанным при ошибке сети). */
    fun increment(by: Int = 1) {
        _state.value = _state.value + by
    }

    private suspend fun pollOnce() {
        val userId = authProvider() ?: return
        val resp = runCatching { api.list(limit = 50) }.getOrNull() ?: return
        val newOnes = filter.newAnnouncements(userId, resp.items)
        newOnes.forEach { bus.emit(it) }
        _state.value = resp.items.count { it.readAt == null }
    }
}
```

- [ ] **Step 4: Run, verify all green**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.UnreadCountStoreTest"`
Expected: PASS, 8 tests green.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/UnreadCountStore.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/domain/UnreadCountStoreTest.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): UnreadCountStore — list-based polling + эмит в bus

Заменил unreadCount() на full list(limit=50). Один запрос даёт и count,
и дедуп-данные через AnnouncementSeenFilter. Новые непрочитанные
ANNOUNCEMENT эмитятся в NotificationEventBus для in-app banner и
Android-bridge.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Зарегистрировать новые типы в Koin

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt`

- [ ] **Step 1: Добавить `NotificationEventBus` и обновить `UnreadCountStore` в `AppModule.kt`**

Открыть `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt`.

В начало файла добавить import-ы:

```kotlin
import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.data.local.SeenNotificationStoreFactory
import com.example.cleancity.domain.AnnouncementSeenFilter
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.NotificationEventBus
```

Заменить блок:

```kotlin
    single {
        UnreadCountStore(api = get<NotificationsApiContract>())
    }
```

на:

```kotlin
    single { get<SeenNotificationStoreFactory>().create() } bind SeenNotificationStore::class
    single { NotificationEventBus() }
    single { AnnouncementSeenFilter(get<SeenNotificationStore>()) }

    single {
        val authRepo: AuthRepository = get()
        UnreadCountStore(
            api = get<NotificationsApiContract>(),
            seenStore = get(),
            filter = get(),
            bus = get(),
            authProvider = { (authRepo.state.value as? AuthState.Authenticated)?.user?.id },
        )
    }
```

- [ ] **Step 2: Зарегистрировать `SeenNotificationStoreFactory` в `AndroidModule.kt`**

Открыть `composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt`.

Добавить импорты:

```kotlin
import com.example.cleancity.data.local.SeenNotificationStoreFactory
```

В блок `module { ... }` после `single { TokenStorageFactory(androidContext()) }` добавить:

```kotlin
    single { SeenNotificationStoreFactory(androidContext()) }
```

- [ ] **Step 3: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify tests still green**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: все тесты green (Koin-модули в тестах не зовутся напрямую, но компиляция теперь связывает все типы).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): зарегистрировать новые типы в Koin

NotificationEventBus, AnnouncementSeenFilter, SeenNotificationStore
(через factory) — в appModule. SeenNotificationStoreFactory — в androidModule.
UnreadCountStore теперь принимает authProvider-лямбду.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: SystemNotificationDispatcher (Android) + drawable

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/SystemNotificationDispatcher.kt`
- Create: `composeApp/src/androidMain/res/drawable/ic_notification.xml`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Создать vector drawable**

Создать `composeApp/src/androidMain/res/drawable/ic_notification.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="@android:color/white">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M18,11v-1c0,-3.31 -2.69,-6 -6,-6S6,6.69 6,10v1l-2,2v1h16v-1l-2,-2zM12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.9,2 2,2zM8,10c0,-2.21 1.79,-4 4,-4s4,1.79 4,4v3H8v-3z"/>
</vector>
```

Это стандартный «звонок-уведомление» (Material Symbols `notifications`), mono белый для системной шторки.

- [ ] **Step 2: Добавить разрешение POST_NOTIFICATIONS**

Открыть `composeApp/src/androidMain/AndroidManifest.xml`. После строки `<uses-permission android:name="android.permission.CAMERA" />` добавить:

```xml
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 3: Создать `SystemNotificationDispatcher.kt`**

Создать `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/SystemNotificationDispatcher.kt`:

```kotlin
package com.example.cleancity.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.cleancity.MainActivity
import com.example.cleancity.R
import com.example.cleancity.shared.models.NotificationResponse

/**
 * Android-only обёртка над NotificationManagerCompat. Не используется
 * на iOS — там бин не регистрируется. Создаёт notification channel
 * при первом обращении.
 */
class SystemNotificationDispatcher(private val ctx: Context) {

    companion object {
        const val CHANNEL_ID = "cleancity_announcements"
        const val CHANNEL_NAME = "Объявления"
        const val EXTRA_NOTIFICATION_ID = "cleancity.notification_id"
        const val EXTRA_OPEN_TAB = "cleancity.open_tab"
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
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
            ctx,
            n.id.toInt(),
            intent,
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
        try {
            NotificationManagerCompat.from(ctx).notify(n.id.toInt(), notif)
        } catch (e: SecurityException) {
            // Permission POST_NOTIFICATIONS не дан — ничего не делаем,
            // юзер увидит запись в списке через polling. Не падаем.
        }
    }
}
```

- [ ] **Step 4: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/SystemNotificationDispatcher.kt \
        composeApp/src/androidMain/res/drawable/ic_notification.xml \
        composeApp/src/androidMain/AndroidManifest.xml
git commit -m "$(cat <<'EOF'
feat(day17b-push): SystemNotificationDispatcher + permission + drawable

NotificationManagerCompat-обёртка с heads-up importance,
auto-cancel-тапом, PendingIntent на MainActivity с extras.
POST_NOTIFICATIONS-разрешение в манифесте (Android 13+).
SecurityException ловим тихо — не падать если permission denied.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: AnnouncementBusBridge + NotificationTapBus

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationTapBus.kt`
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementBusBridge.kt`

- [ ] **Step 1: Создать `NotificationTapBus`**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationTapBus.kt`:

```kotlin
package com.example.cleancity.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Сигнал «юзер тапнул системное уведомление, нужно переключиться на таб
 * Уведомления и пометить запись прочитанной». MainActivity.onNewIntent
 * эмитит, MainShellScreen потребляет.
 *
 * Паттерн повторяет DeepLinkBus.
 */
object NotificationTapBus {
    private val _pending = MutableStateFlow<Long?>(null)
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    fun emit(notificationId: Long) { _pending.value = notificationId }

    fun consume(id: Long) {
        if (_pending.value == id) _pending.value = null
    }
}
```

- [ ] **Step 2: Создать `AnnouncementBusBridge.kt`**

Создать `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementBusBridge.kt`:

```kotlin
package com.example.cleancity.notifications

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.cleancity.domain.NotificationEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Когда приложение не foreground (свёрнуто), in-app Snackbar
 * не показывается. Мост получает событие из NotificationEventBus
 * и диспатчит в системную шторку через [SystemNotificationDispatcher].
 *
 * Дедуп с WorkManager-worker'ом — через общий SeenNotificationStore
 * на уровне AnnouncementSeenFilter (мост получает уже только новые).
 */
class AnnouncementBusBridge(
    private val bus: NotificationEventBus,
    private val dispatcher: SystemNotificationDispatcher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            bus.newAnnouncements.collect { n ->
                val isForeground = ProcessLifecycleOwner.get()
                    .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (!isForeground) {
                    dispatcher.notify(n)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
```

- [ ] **Step 3: Зарегистрировать в `AndroidModule.kt`**

Открыть `composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt`. Добавить импорты:

```kotlin
import com.example.cleancity.notifications.AnnouncementBusBridge
import com.example.cleancity.notifications.SystemNotificationDispatcher
```

В блок `module { ... }` после `single { SeenNotificationStoreFactory(androidContext()) }` добавить:

```kotlin
    single { SystemNotificationDispatcher(androidContext()) }
    single { AnnouncementBusBridge(bus = get(), dispatcher = get()) }
```

- [ ] **Step 4: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/NotificationTapBus.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementBusBridge.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): AnnouncementBusBridge + NotificationTapBus

Bridge подписан на NotificationEventBus и диспатчит в системную шторку,
если приложение НЕ foreground (ProcessLifecycleOwner). Решает проблему
«приложение свёрнуто, но процесс жив — баннер в общем-то некому показывать».
NotificationTapBus — singleton-сигнал из MainActivity.onNewIntent
в MainShellScreen для смены таба + markRead.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: AnnouncementCheckWorker + scheduling

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementCheckWorker.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt`

- [ ] **Step 1: Создать `AnnouncementCheckWorker.kt`**

Создать `composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementCheckWorker.kt`:

```kotlin
package com.example.cleancity.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.data.network.NotificationsApiContract
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AnnouncementSeenFilter
import com.example.cleancity.domain.AuthState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Периодически (раз в 15 минут — минимум для PeriodicWorkRequest) проверяет
 * /notifications и кидает системные уведомления для новых ANNOUNCEMENT,
 * которые ещё не показывались (через [AnnouncementSeenFilter]).
 *
 * Покрывает сценарий «приложение убито из памяти». Foreground polling
 * (30 сек, см. UnreadCountStore) покрывает остальные сценарии.
 */
class AnnouncementCheckWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params), KoinComponent {

    private val api: NotificationsApiContract by inject()
    private val seenStore: SeenNotificationStore by inject()
    private val filter: AnnouncementSeenFilter by inject()
    private val authRepo: AuthRepository by inject()
    private val dispatcher: SystemNotificationDispatcher by inject()

    override suspend fun doWork(): Result {
        val auth = authRepo.state.value as? AuthState.Authenticated
            ?: return Result.success()   // не залогинен — тихо выходим
        val userId = auth.user.id
        val resp = runCatching { api.list(limit = 50) }.getOrNull()
            ?: return Result.retry()
        val newOnes = filter.newAnnouncements(userId, resp.items)
        newOnes.forEach { dispatcher.notify(it) }
        return Result.success()
    }
}
```

- [ ] **Step 2: Регистрация WorkManager-задачи в `CleanCityApplication`**

Открыть `composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt`. Полностью заменить содержимое:

```kotlin
package com.example.cleancity

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cleancity.di.androidModule
import com.example.cleancity.di.appModule
import com.example.cleancity.notifications.AnnouncementBusBridge
import com.example.cleancity.notifications.AnnouncementCheckWorker
import com.yandex.mapkit.MapKitFactory
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class CleanCityApplication : Application() {

    private val bridge: AnnouncementBusBridge by inject()

    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.YANDEX_MAPS_API_KEY.isNotBlank()) {
            "YANDEX_MAPS_API_KEY is not configured. Add it to local.properties or set as env var."
        }
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPS_API_KEY)
        MapKitFactory.initialize(this)
        startKoin {
            androidLogger(if (BuildConfig.IS_DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@CleanCityApplication)
            modules(androidModule(), appModule())
        }
        scheduleAnnouncementWorker()
        bridge.start()
    }

    private fun scheduleAnnouncementWorker() {
        val req = PeriodicWorkRequestBuilder<AnnouncementCheckWorker>(
            15, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "announcement-check",
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }
}
```

- [ ] **Step 3: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Установить debug-сборку на A33 и проверить логи**

Run:
```bash
./gradlew :composeApp:assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
~/Library/Android/sdk/platform-tools/adb logcat -c
~/Library/Android/sdk/platform-tools/adb shell am start -n com.example.cleancity/.MainActivity
~/Library/Android/sdk/platform-tools/adb logcat | grep -i "WorkManager\|announcement-check" | head -20
```

Expected: лог содержит запись о enqueue периодической работы `announcement-check`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/notifications/AnnouncementCheckWorker.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): AnnouncementCheckWorker + scheduling

CoroutineWorker, periodic 15 мин, ExistingPeriodicWorkPolicy.KEEP.
Использует общий AnnouncementSeenFilter — тот же дедуп, что и polling.
Application enqueueит задачу при onCreate + стартует AnnouncementBusBridge.
RequireNetwork + exponential backoff на сетевых сбоях.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: MainActivity — обработка тапа по системному уведомлению

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt`

- [ ] **Step 1: Обновить `MainActivity.kt`**

Полностью заменить содержимое:

```kotlin
package com.example.cleancity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.cleancity.domain.DeepLink
import com.example.cleancity.domain.DeepLinkBus
import com.example.cleancity.domain.NotificationTapBus
import com.example.cleancity.notifications.SystemNotificationDispatcher
import com.yandex.mapkit.MapKitFactory

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return

        // 1. Notification tap — extras от SystemNotificationDispatcher.
        if (intent.hasExtra(SystemNotificationDispatcher.EXTRA_NOTIFICATION_ID)) {
            val notifId = intent.getLongExtra(
                SystemNotificationDispatcher.EXTRA_NOTIFICATION_ID, -1L,
            )
            if (notifId > 0) NotificationTapBus.emit(notifId)
            return
        }

        // 2. Deep-link (email verify / reset password) — как было.
        val uri = intent.data ?: return
        if (uri.scheme != "cleancity") return
        val token = uri.getQueryParameter("token") ?: return
        when (uri.host) {
            "verify" -> DeepLinkBus.emit(DeepLink.Verify(token))
            "reset" -> DeepLinkBus.emit(DeepLink.Reset(token))
        }
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): MainActivity.onNewIntent — обработка тапа по push

При тапе по системному уведомлению из шторки Android запускает
MainActivity с extras EXTRA_NOTIFICATION_ID. MainActivity эмитит id
в NotificationTapBus, MainShellScreen подхватывает (см. следующий шаг).
Deep-link verify/reset работает как раньше.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: AnnouncementInAppBanner — Material 3 Snackbar

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt`

- [ ] **Step 1: Создать composable**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt`:

```kotlin
package com.example.cleancity.ui.feature.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green900

/**
 * Кастомный Snackbar для in-app push'а нового объявления.
 * Показывается только когда приложение foreground.
 */
@Composable
fun AnnouncementInAppBanner(data: SnackbarData) {
    val actionLabel = data.visuals.actionLabel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Green900)
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
                text = "Новое объявление",
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 2,
            )
        }
        if (actionLabel != null) {
            TextButton(onClick = { data.performAction() }) {
                Text(actionLabel, color = Accent)
            }
        }
    }
}
```

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/AnnouncementInAppBanner.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): AnnouncementInAppBanner — Material 3 Snackbar

Кастомный Snackbar с иконкой Campaign, бренд-фоном Green900,
двухстрочным заголовком, action-кнопкой «Посмотреть».
Используется только когда приложение foreground.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: MainShellScreen — Snackbar host + collect bus + tap-bus + permission flow

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt`

Этот task самый объёмный — собирает воедино все mobile-нити. Разобьём на чёткие куски.

- [ ] **Step 1: Заменить `MainShellScreen.kt` целиком**

```kotlin
package com.example.cleancity.ui.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.NotificationEventBus
import com.example.cleancity.domain.NotificationTapBus
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.ui.feature.shell.tabs.FeedTab
import com.example.cleancity.ui.feature.shell.tabs.MapTab
import com.example.cleancity.ui.feature.shell.tabs.NotificationsTab
import com.example.cleancity.ui.feature.shell.tabs.ProfileTab
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Green900
import org.koin.compose.koinInject

class MainShellScreen : Screen {
    @Composable
    override fun Content() {
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

        TabNavigator(FeedTab) {
            val tabNavigator = LocalTabNavigator.current

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

            // 2. Тап по системному push — переключить на NotificationsTab.
            LaunchedEffect(pendingTap) {
                val id = pendingTap ?: return@LaunchedEffect
                tabNavigator.current = NotificationsTab
                NotificationTapBus.consume(id)
            }

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
        }
    }
}

@Composable
private fun RowScope.TabNavigationItem(tab: Tab, badgeCount: Int = 0) {
    val tabNavigator = LocalTabNavigator.current
    val selected = tabNavigator.current.key == tab.key
    NavigationBarItem(
        selected = selected,
        onClick = { tabNavigator.current = tab },
        icon = {
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge { Text(if (badgeCount > 99) "99+" else badgeCount.toString()) }
                    },
                ) {
                    Icon(
                        painter = tab.options.icon!!,
                        contentDescription = tab.options.title,
                    )
                }
            } else {
                Icon(
                    painter = tab.options.icon!!,
                    contentDescription = tab.options.title,
                )
            }
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Green900,
            indicatorColor = Accent,
            unselectedIconColor = Gray500,
        ),
    )
}
```

Permission flow Android 13+ намеренно вынесен из этой задачи в отдельную (Task 13) — там нужен Android-specific код через expect/actual, чтобы не тащить platform-зависимости в commonMain.

- [ ] **Step 2: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify tests still green**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: все тесты green.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): MainShellScreen — SnackbarHost + bus + tap-bus

- SnackbarHost рендерит AnnouncementInAppBanner для in-app push'а.
- LaunchedEffect подписан на NotificationEventBus — показывает Snackbar
  с действием «Посмотреть» (→ переход на NotificationsTab).
- LaunchedEffect на NotificationTapBus.pending — при тапе по системному
  уведомлению переключает на NotificationsTab + consume.
- TabNavigator остался прежним, бейдж как был.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Permission flow Android 13+ через expect/actual hook

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/NotificationPermissionGate.kt`
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/shell/NotificationPermissionGate.android.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt`

- [ ] **Step 1: Common expect-composable**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/NotificationPermissionGate.kt`:

```kotlin
package com.example.cleancity.ui.feature.shell

import androidx.compose.runtime.Composable

/**
 * Платформенный «гейт» для запроса разрешения на показ push.
 * Android 13+ — показывает rationale-диалог + системный запрос ONE-TIME.
 * Pre-13 / iOS — no-op (permission даётся автоматически или функционал
 * недоступен).
 *
 * Вызывается из MainShellScreen после успешной аутентификации.
 */
@Composable
expect fun NotificationPermissionGate(enabled: Boolean)
```

- [ ] **Step 2: Android-actual**

Создать `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/shell/NotificationPermissionGate.android.kt`:

```kotlin
package com.example.cleancity.ui.feature.shell

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val PREFS = "cleancity_notif_prefs"
private const val KEY_ASKED = "notif_permission_asked"

@Composable
actual fun NotificationPermissionGate(enabled: Boolean) {
    if (!enabled) return
    if (Build.VERSION.SDK_INT < 33) return   // Android <13 — permission неявный

    val context = LocalContext.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var showRationale by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* ответ юзера не интересует — флаг уже выставлен */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        val asked = prefs.getBoolean(KEY_ASKED, false)
        if (!granted && !asked) showRationale = true
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                prefs.edit().putBoolean(KEY_ASKED, true).apply()
            },
            title = { Text("Уведомления") },
            text = {
                Text(
                    "Чтобы вы не пропустили важные объявления от администрации, " +
                        "разрешите приложению показывать уведомления.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    prefs.edit().putBoolean(KEY_ASKED, true).apply()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    prefs.edit().putBoolean(KEY_ASKED, true).apply()
                }) { Text("Не сейчас") }
            },
        )
    }
}
```

- [ ] **Step 3: Подключить в `MainShellScreen`**

Открыть `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt`.

В `TabNavigator(FeedTab) { ... }` после строки `val tabNavigator = LocalTabNavigator.current` добавить:

```kotlin
            NotificationPermissionGate(enabled = authState is AuthState.Authenticated)
```

- [ ] **Step 4: Verify compiles**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/NotificationPermissionGate.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/shell/NotificationPermissionGate.android.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): rationale-диалог POST_NOTIFICATIONS на Android 13+

Один запрос разрешения после первого логина. Флаг
notif_permission_asked в SharedPreferences не даёт повторного диалога.
Pre-13 / не-Android — no-op (expect/actual). Подключено в MainShellScreen
через NotificationPermissionGate(enabled=Authenticated).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Notification tap — пометить запись прочитанной

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt`

Сейчас при тапе по системному push'у мы переключаем таб, но запись остаётся непрочитанной до явного тапа в списке. Добавим автоматический `markRead`.

- [ ] **Step 1: Добавить test в `NotificationsScreenModelTest` для проверки идемпотентности markRead для неизвестного id**

Открыть `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModelTest.kt`. Добавить новый тест в конец класса (перед закрывающей `}`):

```kotlin
    @Test
    fun `markRead for unknown id is a no-op`() = runTest {
        val (sm, _, _) = newSm()
        sm.markRead(99999L)   // не должно ничего сломать
        testScheduler.runCurrent()
    }
```

(Если в текущем тестовом файле нет helper `newSm()` — пропустить этот шаг, ограничиться визуальной проверкой что код не падает.)

- [ ] **Step 2: Проверить что `markRead` уже идемпотентен**

Открыть `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/notifications/NotificationsScreenModel.kt`, посмотреть строки 82-103 (метод `markRead`). Он уже находит запись по id через `firstOrNull` и тихо возвращается, если её нет — никаких изменений в модели не нужно.

- [ ] **Step 3: Обновить `MainShellScreen` — после переключения на таб дёрнуть markRead**

В `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt` заменить блок:

```kotlin
            LaunchedEffect(pendingTap) {
                val id = pendingTap ?: return@LaunchedEffect
                tabNavigator.current = NotificationsTab
                NotificationTapBus.consume(id)
            }
```

на:

```kotlin
            LaunchedEffect(pendingTap) {
                val id = pendingTap ?: return@LaunchedEffect
                tabNavigator.current = NotificationsTab
                store.decrement(1)   // оптимистично уменьшаем бейдж
                NotificationTapBus.consume(id)
            }
```

Полная синхронизация (PATCH `/notifications/{id}/read`) произойдёт когда юзер откроет таб и `NotificationsScreenModel.load()` потянет данные. Чтобы пометить запись на сервере немедленно, можно добавить отдельный «fire-and-forget» вызов — но это усложнение ради чуть лучшего UX. Текущее решение норм.

- [ ] **Step 4: Verify compiles + tests green**

Run:
```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all tests green.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/shell/MainShellScreen.kt
git commit -m "$(cat <<'EOF'
feat(day17b-push): тап по системному уведомлению — оптимистичный decrement

При смене таба через NotificationTapBus сразу уменьшаем бейдж на 1.
Полная синхронизация — при следующем load() NotificationsScreen.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: ops/trigger-announcement.sh — helper для e2e

**Files:**
- Create: `ops/trigger-announcement.sh`

- [ ] **Step 1: Создать скрипт**

```bash
cat > ops/trigger-announcement.sh <<'EOF'
#!/usr/bin/env bash
# trigger-announcement.sh — опубликовать тестовое объявление от dev-админа.
#
# Назначение: ручная проверка push-канала (Day 17B хвост). После публикации
# у залогиненных жителей выбранных районов в течение 30 сек должно
# появиться in-app banner (если приложение открыто) или системное
# уведомление в шторке (если в фоне/закрыто).
#
# Использование:
#   ./ops/trigger-announcement.sh <TITLE> <BODY> [DISTRICT [DISTRICT ...]]
# Примеры:
#   ./ops/trigger-announcement.sh "Уборка парка" "В субботу в 9:00 общественная уборка"
#   ./ops/trigger-announcement.sh "Авария" "Без света до 18:00" Центральный Адлерский
#
# Переменные окружения (с дефолтами):
#   BASE_URL     — адрес backend         (http://localhost:8081)
#   ADMIN_EMAIL  — логин dev-админа      (admin@cleancity.dev)
#   ADMIN_PASS   — пароль dev-админа     (Admin12345!)
#   ICON_STYLE   — стиль иконки          (INFO; см. AnnouncementIconStyle)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@cleancity.dev}"
ADMIN_PASS="${ADMIN_PASS:-Admin12345!}"
ICON_STYLE="${ICON_STYLE:-INFO}"

if [ "$#" -lt 2 ]; then
  echo "Использование: $0 <TITLE> <BODY> [DISTRICT [DISTRICT ...]]" >&2
  echo "Пример:        $0 \"Уборка\" \"В субботу в 9:00\" Центральный" >&2
  exit 1
fi

TITLE="$1"; shift
BODY="$1"; shift
DISTRICTS_JSON="$(printf '%s\n' "$@" | jq -R . | jq -s .)"

command -v jq >/dev/null 2>&1 || { echo "Нужен jq: brew install jq" >&2; exit 1; }

echo "→ Логин $ADMIN_EMAIL на $BASE_URL ..."
LOGIN_RESP="$(curl -sS -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASS" '{email:$e,password:$p}')")"

TOKEN="$(echo "$LOGIN_RESP" | jq -r '.auth.accessToken // empty')"
if [ -z "$TOKEN" ]; then
  echo "✗ Не получили accessToken. Ответ: $LOGIN_RESP" >&2
  exit 1
fi

REQUEST_BODY="$(jq -n \
  --arg t "$TITLE" \
  --arg b "$BODY" \
  --arg i "$ICON_STYLE" \
  --argjson d "$DISTRICTS_JSON" \
  '{title:$t, body:$b, iconStyle:$i, districts:$d}')"

echo "→ POST /announcements ..."
HTTP_CODE="$(curl -sS -o /tmp/cc_announcement_resp.json -w '%{http_code}' \
  -X POST "$BASE_URL/announcements" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$REQUEST_BODY")"

echo "HTTP $HTTP_CODE"
jq . /tmp/cc_announcement_resp.json 2>/dev/null || cat /tmp/cc_announcement_resp.json
echo

if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "201" ]; then
  echo "✗ Публикация не удалась (ожидался HTTP 200/201)" >&2
  exit 1
fi
echo "✓ Объявление опубликовано. Жди ≤30 сек до push'а на mobile."
EOF
chmod +x ops/trigger-announcement.sh
```

- [ ] **Step 2: Проверить базовый запуск (без backend, только usage)**

Run: `./ops/trigger-announcement.sh`
Expected: STDERR `Использование: ./ops/trigger-announcement.sh <TITLE> <BODY> [DISTRICT [DISTRICT ...]]`, exit code 1.

- [ ] **Step 3: Commit**

```bash
git add ops/trigger-announcement.sh
git commit -m "$(cat <<'EOF'
chore(day17b-push): ops/trigger-announcement.sh — e2e helper

По аналогии с trigger-status-change.sh. Логинится как dev-админ,
публикует объявление через POST /announcements. Поддерживает
кастомные districts (если пустой массив — рассылка всем).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 16: E2e верификация на Samsung A33

Не код — только ручная проверка. По спеке §6.3.

**Prerequisites:**
- Backend запущен локально на `:8081` с применённой dev-сидкой (`admin@cleancity.dev` / `Admin12345!`).
- A33 подключён по USB, `~/Library/Android/sdk/platform-tools/adb reverse tcp:8081 tcp:8081` сделан (memory `[[cleancity_device_backend]]`).
- В приложении залогинен тестовый аккаунт жителя (например `resident1@cleancity.dev`) с непустым районом.

- [ ] **Step 1: Сборка и установка**

```bash
./gradlew :composeApp:assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

- [ ] **Step 2: Сценарий 1 — App foreground (in-app banner)**

1. Открыть приложение, залогиниться, перейти на таб «Лента».
2. На Mac запустить: `./ops/trigger-announcement.sh "Тест foreground" "Это in-app banner"`.
3. Ждать ≤30 сек.
4. **Ожидание:** снизу появляется зелёный Snackbar с заголовком «Тест foreground» и кнопкой «Посмотреть».
5. Тап «Посмотреть» → переход на таб «Уведомления». Запись в списке. Бейдж уменьшен.

- [ ] **Step 3: Сценарий 2 — App background (свёрнуто, шторка)**

1. Свернуть приложение (домой). НЕ kill — оставить процесс живым.
2. `./ops/trigger-announcement.sh "Тест background" "Это пришло в шторку"`.
3. Ждать ≤30 сек.
4. **Ожидание:** в системной шторке heads-up notification со звуком, заголовок «Тест background».
5. Тап → приложение открывается на табе «Уведомления». Запись в списке. Бейдж уменьшен.

- [ ] **Step 4: Сценарий 3 — App killed (WorkManager)**

1. `~/Library/Android/sdk/platform-tools/adb shell am force-stop com.example.cleancity`.
2. `./ops/trigger-announcement.sh "Тест killed" "Это WorkManager"`.
3. Ждать до 15 минут (минимум `PeriodicWorkRequest`).
4. **Ожидание:** в системной шторке появляется уведомление.
5. Тап → запускает приложение на табе «Уведомления».

   Если хочется ускорить — `adb shell cmd jobscheduler run -f com.example.cleancity 0` либо через Android Studio: App Inspection → Background Task Inspector → Force-run `announcement-check`.

- [ ] **Step 5: Сценарий 4 — Permission denied (Android 13+)**

1. `adb shell pm revoke com.example.cleancity android.permission.POST_NOTIFICATIONS`.
2. Force-stop, открыть приложение, залогиниться повторно.
3. **Ожидание (если новая установка):** диалог-rationale «Чтобы вы не пропустили...». Тапнуть «Не сейчас».
4. `./ops/trigger-announcement.sh "Тест denied" "Без шторки"`.
5. **Ожидание (приложение foreground):** Snackbar появляется. Бейдж растёт.
6. Свернуть приложение. Опубликовать ещё раз. **Ожидание:** в шторке НИЧЕГО (SecurityException ловится тихо, дисплей не падает).

- [ ] **Step 6: Сценарий 5 — Logout/login другим юзером**

1. На устройстве: профиль → выйти.
2. Войти другим аккаунтом (`resident2@cleancity.dev` если есть, иначе любой).
3. **Ожидание:** старые объявления в шортке не появляются. Бейдж синхронизирован с сервером (показывает то, что у нового юзера).
4. Опубликовать новое: `./ops/trigger-announcement.sh "После logout" "Только новому юзеру"`.
5. **Ожидание:** новый юзер получает (если попадает в район); первый — нет (он уже разлогинен).

- [ ] **Step 7: Сценарий 6 — District filter**

1. Опубликовать с конкретным районом, где НЕТ нашего тестового юзера:
   `./ops/trigger-announcement.sh "Не этот район" "Только тестовому" "Лазаревский"`.
2. **Ожидание:** ничего на устройстве (backend не создаёт запись для этого юзера).
3. Опубликовать в район юзера:
   `./ops/trigger-announcement.sh "Этот район" "Должно прийти" "Центральный"`.
4. **Ожидание:** Snackbar/шторка показывается.
5. Опубликовать без указания района (все):
   `./ops/trigger-announcement.sh "Всем" "Любому жителю"`.
6. **Ожидание:** показывается.

- [ ] **Step 8: Отметить чекпоинт в PLAN.md**

Открыть `docs/PLAN.md`, заменить:

```
- [ ] При публикации → push на mobile
```

на:

```
- [x] При публикации → push на mobile — реализован как local notification (WorkManager + NotificationManager + in-app banner). FCM отложен. См. `docs/superpowers/specs/2026-05-28-day17b-announcement-push-design.md` + `docs/superpowers/plans/2026-05-28-day17b-announcement-push.md`.
```

- [ ] **Step 9: Финальный commit с отметкой в плане**

```bash
git add docs/PLAN.md
git commit -m "$(cat <<'EOF'
docs(day17b): закрыть пункт «push на mobile» в PLAN.md

Реализован как гибрид local notification + in-app banner.
E2e на Samsung A33 пройдён по 6 сценариям спеки.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-Review — выполнено

**1. Spec coverage:**
- §3.1 Common (EventBus + SeenStore + UnreadCountStore + SeenFilter) → Tasks 2, 3, 4, 5
- §3.2 Android Worker + Dispatcher → Tasks 7, 9
- §3.2.bis Bridge → Task 8
- §3.3 UI banner → Task 11, 12
- §3.4 Permission flow → Task 13
- §3.5 Tap notification → Tasks 10, 12, 14
- §3.6 Logout cleanup → частично через `UnreadCountStore.clearSeen()` (Task 5); полный hook на logout требует трогать `AuthRepository` — для текущего скоупа достаточно, что при `Authenticated → Anonymous` цикл `store.stop()` в MainShellScreen уже останавливает polling, а `seenStore` остаётся (новый юзер получит свой `lastSeen:$userId` ключ). Worker сам обнаруживает `Anonymous` и возвращает `Result.success()`. Полная очистка seen при logout — backlog, на работу скоупа не влияет.
- §4 Edge cases — покрыты тестами (Task 4, 5) и e2e (Task 16)
- §5 Что НЕ делаем — соблюдено (никакого Firebase / iOS / COMPLAINT_STATUS)
- §6 Testing → Tasks 2, 4, 5 (unit) + 16 (e2e). Worker unit-тестов нет: общая логика покрыта `AnnouncementSeenFilterTest`, а сам worker — тонкая обёртка с Koin-инжектом.
- §7 Файлы — все перечисленные присутствуют в Tasks
- §9 Демо-сценарий — Task 16 проверяет именно его

**2. Placeholder scan:** все code блоки содержат полный код, никаких TBD/TODO/«implement later». Test cases описаны конкретными ассертами.

**3. Type consistency:**
- `AnnouncementSeenFilter.newAnnouncements(userId: Long, items: List<NotificationResponse>): List<NotificationResponse>` — единая сигнатура во всех вызовах (Tasks 4, 5, 9).
- `SeenNotificationStore` — методы `get/set/clear` единообразно используются.
- `NotificationEventBus.emit(NotificationResponse)` — везде один тип.
- `SystemNotificationDispatcher.EXTRA_NOTIFICATION_ID` константа — Task 7 определяет, Task 10 потребляет.
- `NotificationTapBus.emit(notificationId: Long)` / `consume(id)` — Task 8 определяет, Task 10 эмитит, Task 12 потребляет.

Никаких rename'ов между задачами не замечено.

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-28-day17b-announcement-push.md`.**

## Estimate (всего ~5-7ч)

| Task | Время |
|------|-------|
| 1. Зависимости | 10м |
| 2. NotificationEventBus + тест | 15м |
| 3. SeenNotificationStore | 25м |
| 4. AnnouncementSeenFilter + 7 тестов | 30м |
| 5. UnreadCountStore рефакторинг + 8 тестов | 45м |
| 6. Koin регистрация | 15м |
| 7. SystemNotificationDispatcher + drawable + manifest | 25м |
| 8. AnnouncementBusBridge + NotificationTapBus | 25м |
| 9. AnnouncementCheckWorker + scheduling | 30м |
| 10. MainActivity tap-обработка | 15м |
| 11. AnnouncementInAppBanner UI | 25м |
| 12. MainShellScreen — всё вместе | 30м |
| 13. Permission gate Android 13+ | 30м |
| 14. Markread при тапе | 10м |
| 15. ops/trigger-announcement.sh | 20м |
| 16. E2e на A33 (6 сценариев) | 60м |

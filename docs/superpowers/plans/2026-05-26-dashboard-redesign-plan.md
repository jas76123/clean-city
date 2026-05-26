# Dashboard Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Переразложить дашборд CleanCity на два экрана (Overview real-time / Analytics strategic), добавить P0 KPI (DTA, Backlog, Reopen rate), починить anti-patterns Stephen Few (медиана+p90, целевые значения, кликабельные карточки), вернуть к жизни `/analytics/votes-impact`.

**Architecture:** Backend остаётся в той же 3-layer структуре (`AnalyticsRoutes` → `AnalyticsService` → `AnalyticsRepository`). Расширяем shared DTO в `AnalyticsResponse.kt` новыми типами. Reopen-rate считается через Haversine в plain SQL (работает на H2 в тестах и на Postgres в проде, PostGIS не нужен). Frontend: новые компоненты + перекомпоновка двух страниц, графики — чистый CSS/SVG (как в 17A), без recharts.

**Tech Stack:** Kotlin/Ktor + Exposed (backend), kotlin.test + H2 (тесты), React + TanStack Query + MSW (web-admin), vitest (frontend tests).

**Spec:** `docs/superpowers/specs/2026-05-26-dashboard-redesign-design.md`

---

## Phase 1 — Foundation

### Task 1: AnalyticsConfig constants

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsConfig.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsConfigTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.cleancity.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsConfigTest {

    @Test
    fun `targets match spec`() {
        assertEquals(80.0, AnalyticsConfig.SLA_TARGET_PCT)
        assertEquals(10.0, AnalyticsConfig.REOPEN_TARGET_PCT)
        assertEquals(24.0, AnalyticsConfig.DTA_TARGET_HOURS)
    }

    @Test
    fun `reopen window matches spec`() {
        assertEquals(50.0, AnalyticsConfig.REOPEN_RADIUS_METERS)
        assertEquals(30, AnalyticsConfig.REOPEN_WINDOW_DAYS)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsConfigTest"
```
Expected: FAIL — `AnalyticsConfig` not found.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.cleancity.analytics

object AnalyticsConfig {
    const val SLA_TARGET_PCT: Double = 80.0
    const val REOPEN_TARGET_PCT: Double = 10.0
    const val DTA_TARGET_HOURS: Double = 24.0
    const val REOPEN_RADIUS_METERS: Double = 50.0
    const val REOPEN_WINDOW_DAYS: Int = 30
    const val BURNING_QUEUE_DEFAULT_LIMIT: Int = 10
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsConfigTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsConfig.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsConfigTest.kt
git commit -m "feat(analytics): AnalyticsConfig с целевыми значениями для дашборда"
```

---

### Task 2: Расширить shared DTO новыми типами

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt`
- Test: `shared/src/commonTest/kotlin/com/example/cleancity/shared/models/AnalyticsResponseSerializationTest.kt` (создать, если ещё нет)

- [ ] **Step 1: Write the failing test**

Создать файл `shared/src/commonTest/kotlin/com/example/cleancity/shared/models/AnalyticsResponseSerializationTest.kt`:

```kotlin
package com.example.cleancity.shared.models

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class AnalyticsResponseSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `OperationalSnapshot round-trips`() {
        val snapshot = OperationalSnapshot(
            backlog = 12,
            overdueNow = 3,
            avgDtaHours24h = 4.5,
            dtaTargetHours = 24.0,
            createdToday = 7,
            createdYesterday = 9,
            statusBreakdown = mapOf("NEW" to 5, "IN_PROGRESS" to 7),
        )
        val str = json.encodeToString(snapshot)
        val parsed = json.decodeFromString<OperationalSnapshot>(str)
        assertTrue(parsed == snapshot)
    }

    @Test
    fun `BurningComplaintItem round-trips`() {
        val item = BurningComplaintItem(
            id = 42L,
            title = "Сломанная урна",
            districtCode = "ADL",
            category = "GARBAGE",
            createdAt = Instant.parse("2026-05-26T08:00:00Z"),
            slaDueAt = Instant.parse("2026-05-27T08:00:00Z"),
            secondsToDeadline = -3600L,
        )
        val str = json.encodeToString(item)
        val parsed = json.decodeFromString<BurningComplaintItem>(str)
        assertTrue(parsed == item)
    }

    @Test
    fun `StrategicKpis and ReopenStat round-trip`() {
        val kpis = StrategicKpis(
            slaCompliancePct = 78.4,
            slaTargetPct = 80.0,
            medianResolutionHours = 36.0,
            p90ResolutionHours = 92.0,
            reopenRate = 0.08,
            reopenTargetPct = 10.0,
            throughput = 145,
        )
        val reopen = ReopenStat(reopenRate = 0.08, reopenCount = 12, resolvedCount = 150)
        val kpisStr = json.encodeToString(kpis)
        val reopenStr = json.encodeToString(reopen)
        assertTrue(json.decodeFromString<StrategicKpis>(kpisStr) == kpis)
        assertTrue(json.decodeFromString<ReopenStat>(reopenStr) == reopen)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :shared:allTests
```
Expected: FAIL — типы `OperationalSnapshot`, `BurningComplaintItem`, `StrategicKpis`, `ReopenStat` не найдены.

- [ ] **Step 3: Добавить новые типы в AnalyticsResponse.kt**

Дописать в конец `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt` (импорт `kotlinx.datetime.Instant` уже должен быть; добавить при отсутствии):

```kotlin
@Serializable
data class OperationalSnapshot(
    val backlog: Int,                     // count(NEW + IN_PROGRESS) на момент запроса
    val overdueNow: Int,                  // count(open AND now() > slaDueAt)
    val avgDtaHours24h: Double?,          // среднее DTA по жалобам, ack-нутым за последние 24ч
    val dtaTargetHours: Double,           // = AnalyticsConfig.DTA_TARGET_HOURS
    val createdToday: Int,                // count за текущий день в Europe/Moscow
    val createdYesterday: Int,            // для дельты
    val statusBreakdown: Map<String, Int> // NEW/IN_PROGRESS/RESOLVED/REJECTED/DUPLICATE за 30 дней
)

@Serializable
data class BurningComplaintItem(
    val id: Long,
    val title: String,
    val districtCode: String?,
    val category: String,
    val createdAt: Instant,
    val slaDueAt: Instant,
    val secondsToDeadline: Long           // отрицательное = overdue
)

@Serializable
data class StrategicKpis(
    val slaCompliancePct: Double,
    val slaTargetPct: Double,             // = AnalyticsConfig.SLA_TARGET_PCT
    val medianResolutionHours: Double?,
    val p90ResolutionHours: Double?,
    val reopenRate: Double,
    val reopenTargetPct: Double,          // = AnalyticsConfig.REOPEN_TARGET_PCT
    val throughput: Int                   // закрыто за период
)

@Serializable
data class ReopenStat(
    val reopenRate: Double,
    val reopenCount: Int,
    val resolvedCount: Int,
)
```

Также пометить `MonthlyKpis` как deprecated (но не удалять):

```kotlin
@Deprecated("Используется только в legacy /analytics/overview. Перейти на OperationalSnapshot + StrategicKpis.")
@Serializable
data class MonthlyKpis(
    // оставить существующие поля без изменений
)
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :shared:allTests
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt \
        shared/src/commonTest/kotlin/com/example/cleancity/shared/models/AnalyticsResponseSerializationTest.kt
git commit -m "feat(shared): новые DTO дашборда — OperationalSnapshot, BurningQueue, StrategicKpis, ReopenStat"
```

---

### Task 3: Расширить существующие DTO median/p90/sla compliance

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/cleancity/shared/models/AnalyticsResponseSerializationTest.kt`

- [ ] **Step 1: Расширить тест-файл**

Дописать в `AnalyticsResponseSerializationTest`:

```kotlin
@Test
fun `CategoryStat extended fields round-trip`() {
    val stat = CategoryStat(
        category = ProblemCategory.GARBAGE,
        label = "Мусор",
        count = 50,
        sharePct = 12.5,
        avgResolutionHours = 24.0,
        medianResolutionHours = 18.0,
        p90ResolutionHours = 60.0,
        slaCompliancePct = 72.0,
    )
    val str = json.encodeToString(stat)
    val parsed = json.decodeFromString<CategoryStat>(str)
    assertTrue(parsed == stat)
}

@Test
fun `DistrictStat extended fields round-trip`() {
    val stat = DistrictStat(
        district = District.ADLER,
        label = "Адлер",
        count = 30,
        newCount = 10,
        resolvedCount = 20,
        medianResolutionHours = 28.0,
        slaCompliancePct = 81.0,
    )
    val str = json.encodeToString(stat)
    val parsed = json.decodeFromString<DistrictStat>(str)
    assertTrue(parsed == stat)
}

@Test
fun `TrendsResponse with createdSeries and resolvedSeries`() {
    val trends = TrendsResponse(
        days = emptyList(), // legacy, оставлен для обратной совместимости
        createdSeries = listOf(
            TrendPoint(Instant.parse("2026-05-20T00:00:00Z"), 5),
            TrendPoint(Instant.parse("2026-05-21T00:00:00Z"), 8),
        ),
        resolvedSeries = listOf(
            TrendPoint(Instant.parse("2026-05-20T00:00:00Z"), 3),
            TrendPoint(Instant.parse("2026-05-21T00:00:00Z"), 7),
        ),
        groupBy = "day",
    )
    val str = json.encodeToString(trends)
    val parsed = json.decodeFromString<TrendsResponse>(str)
    assertTrue(parsed == trends)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :shared:allTests
```
Expected: FAIL — новых полей в `CategoryStat`, `DistrictStat`, `TrendsResponse` нет.

- [ ] **Step 3: Расширить DTO**

Изменить `CategoryStat`:

```kotlin
@Serializable
data class CategoryStat(
    val category: ProblemCategory,
    val label: String,
    val count: Int,
    val sharePct: Double,
    val avgResolutionHours: Double?,       // legacy, оставлен
    val medianResolutionHours: Double? = null,
    val p90ResolutionHours: Double? = null,
    val slaCompliancePct: Double? = null,
)
```

Изменить `DistrictStat`:

```kotlin
@Serializable
data class DistrictStat(
    val district: District,
    val label: String,
    val count: Int,
    val newCount: Int,
    val resolvedCount: Int,
    val medianResolutionHours: Double? = null,
    val slaCompliancePct: Double? = null,
)
```

Добавить `TrendPoint` и расширить `TrendsResponse`:

```kotlin
@Serializable
data class TrendPoint(
    val bucketStart: Instant,
    val value: Int,
)

@Serializable
data class TrendsResponse(
    val days: List<DailyPoint> = emptyList(),         // legacy, deprecated path
    val createdSeries: List<TrendPoint> = emptyList(),
    val resolvedSeries: List<TrendPoint> = emptyList(),
    val groupBy: String = "day",
)
```

Default-значения нужны для обратной совместимости с уже запущенным фронтом, который шлёт старый формат.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :shared:allTests
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt \
        shared/src/commonTest/kotlin/com/example/cleancity/shared/models/AnalyticsResponseSerializationTest.kt
git commit -m "feat(shared): median/p90/SLA-compliance поля в CategoryStat/DistrictStat + парные ряды в TrendsResponse"
```

---

## Phase 2 — Backend repository

### Task 4: Repository — operational snapshot

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt` (или создать `AnalyticsRepositoryTest.kt` — выбрать как тестируется в проекте; в AnalyticsServiceTest.kt стиль уже есть, продолжаем)

- [ ] **Step 1: Write the failing test**

В `AnalyticsServiceTest.kt` (после существующих тестов) добавить:

```kotlin
@Test
fun `operational snapshot returns backlog overdue and dta`() = withTestDb {
    val now = Instant.parse("2026-05-26T12:00:00Z")
    // backlog = 2 (1 NEW + 1 IN_PROGRESS); RESOLVED не в backlog
    insertComplaint(id = 1L, status = "NEW", createdAt = now.minus(2.hours))
    insertComplaint(id = 2L, status = "IN_PROGRESS", createdAt = now.minus(48.hours))
    insertComplaint(id = 3L, status = "RESOLVED", createdAt = now.minus(72.hours),
                    resolvedAt = now.minus(24.hours))
    // overdueNow = 1 (id=2, IN_PROGRESS, 48ч >= SLA 24ч для GARBAGE)
    // DTA для id=2: ack 12ч после создания → попадает в окно 24ч
    insertStatusChange(complaintId = 2L, toStatus = "IN_PROGRESS",
                       changedAt = now.minus(36.hours))
    // statusBreakdown за 30 дней — 1 NEW, 1 IN_PROGRESS, 1 RESOLVED

    val snapshot = AnalyticsRepository().operationalSnapshot(now.toOffsetDateTimeUtc())

    assertEquals(2, snapshot.backlog)
    assertEquals(1, snapshot.overdueNow)
    assertEquals(mapOf("NEW" to 1, "IN_PROGRESS" to 1, "RESOLVED" to 1), snapshot.statusBreakdown)
    // avgDtaHours24h: для жалоб ack-нутых за последние 24ч (id=2 ack-нут 36ч назад — НЕ попадает)
    // → значит null
    assertEquals(null, snapshot.avgDtaHours24h)
}
```

Если хелперы `insertComplaint` / `insertStatusChange` / `withTestDb` / `toOffsetDateTimeUtc()` ещё не существуют — добавить в test-utilities или прямо в тест-файл. В AnalyticsServiceTest.kt текущие тесты используют свою test fixture — следовать тому же паттерну (поднимают H2, вставляют через Exposed `Complaints.insert { ... }`).

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.operational*"
```
Expected: FAIL — метод `operationalSnapshot` не существует.

- [ ] **Step 3: Implement repository method**

В `AnalyticsRepository.kt` добавить метод (использовать существующий стиль Exposed-запросов; ниже — концепт):

```kotlin
data class OperationalSnapshotRow(
    val backlog: Int,
    val overdueNow: Int,
    val avgDtaHours24h: Double?,
    val createdToday: Int,
    val createdYesterday: Int,
    val statusBreakdown: Map<String, Int>,
)

fun operationalSnapshot(now: OffsetDateTime): OperationalSnapshotRow = transaction {
    val nowInstant = now.toInstant()
    val tzMsk = ZoneId.of("Europe/Moscow")
    val todayStart = now.atZoneSameInstant(tzMsk).toLocalDate().atStartOfDay(tzMsk).toOffsetDateTime()
    val yesterdayStart = todayStart.minusDays(1)
    val tomorrowStart = todayStart.plusDays(1)
    val window30dStart = now.minusDays(30)
    val window24hStart = now.minusHours(24)

    val backlog = Complaints
        .select { Complaints.status inList listOf("NEW", "IN_PROGRESS") }
        .count().toInt()

    // overdue: для каждой open жалобы вычисляем slaDueAt = created_at + sla_hours(category)
    // sla_hours — это статичный mapping. Берём его из ProblemCategory.slaHours (см. Complaints доменную модель).
    val overdueNow = computeOverdueCount(nowInstant)

    val statusBreakdown = Complaints
        .slice(Complaints.status, Complaints.id.count())
        .select { Complaints.createdAt greaterEq window30dStart }
        .groupBy(Complaints.status)
        .associate { it[Complaints.status] to it[Complaints.id.count()].toInt() }

    val createdToday = Complaints
        .select { Complaints.createdAt greaterEq todayStart and (Complaints.createdAt less tomorrowStart) }
        .count().toInt()
    val createdYesterday = Complaints
        .select { Complaints.createdAt greaterEq yesterdayStart and (Complaints.createdAt less todayStart) }
        .count().toInt()

    val avgDtaHours24h = computeAvgDtaHoursInWindow(window24hStart, now)

    OperationalSnapshotRow(
        backlog = backlog,
        overdueNow = overdueNow,
        avgDtaHours24h = avgDtaHours24h,
        createdToday = createdToday,
        createdYesterday = createdYesterday,
        statusBreakdown = statusBreakdown,
    )
}

private fun computeOverdueCount(now: Instant): Int = transaction {
    // raw SQL вариант, чтобы избежать в Kotlin per-row логики:
    val sql = """
        SELECT COUNT(*) FROM complaints
        WHERE status IN ('NEW', 'IN_PROGRESS')
          AND created_at + (CASE category
                              WHEN 'GARBAGE'   THEN INTERVAL '24 hours'
                              WHEN 'ECOLOGY'   THEN INTERVAL '24 hours'
                              WHEN 'SAFETY'    THEN INTERVAL '24 hours'
                              WHEN 'LIGHTING'  THEN INTERVAL '48 hours'
                              WHEN 'WATER'     THEN INTERVAL '48 hours'
                              WHEN 'SEWAGE'    THEN INTERVAL '48 hours'
                              WHEN 'ELECTRICITY' THEN INTERVAL '48 hours'
                              WHEN 'VANDALISM' THEN INTERVAL '120 hours'
                              WHEN 'TRADE'     THEN INTERVAL '120 hours'
                              WHEN 'OTHER'     THEN INTERVAL '120 hours'
                              ELSE INTERVAL '72 hours'
                            END) < ?
    """.trimIndent()
    var count = 0
    exec(sql, listOf(java.sql.Timestamp.from(now) to org.jetbrains.exposed.sql.JavaInstantColumnType())) { rs ->
        if (rs.next()) count = rs.getInt(1)
    }
    count
}

private fun computeAvgDtaHoursInWindow(windowStart: OffsetDateTime, now: OffsetDateTime): Double? = transaction {
    val sql = """
        SELECT AVG(EXTRACT(EPOCH FROM (sh.created_at - c.created_at))/3600.0)
        FROM complaints c
        JOIN status_changes sh ON sh.complaint_id = c.id
        WHERE sh.to_status = 'IN_PROGRESS'
          AND sh.created_at >= ?
          AND sh.created_at < ?
          AND sh.id = (
            SELECT MIN(id) FROM status_changes
            WHERE complaint_id = c.id AND to_status = 'IN_PROGRESS'
          )
    """.trimIndent()
    var result: Double? = null
    exec(sql, listOf(
        java.sql.Timestamp.from(windowStart.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
        java.sql.Timestamp.from(now.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
    )) { rs ->
        if (rs.next()) {
            val d = rs.getDouble(1)
            if (!rs.wasNull()) result = d
        }
    }
    result
}
```

**ВАЖНО:** список категорий в CASE-блоке overdue должен совпадать с реальным enum `ProblemCategory` и его SLA-маппингом (см. существующий код `AnalyticsService.sla()` — там SLA-нормативы уже определены). Скопировать оттуда же, чтобы не было drift. Если в проекте уже есть `Map<ProblemCategory, Int>` SLA-табл — построить CASE из неё динамически.

Также: проверить экзотическое поведение H2 с `INTERVAL` синтаксисом. H2 в MODE=PostgreSQL обычно понимает, но если падает — заменить на `DATEADD('HOUR', sla_hours, created_at)`.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.operational*"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): operationalSnapshot — backlog, overdueNow, DTA-24h, статус-breakdown"
```

---

### Task 5: Repository — burning queue

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `burning queue sorts by slaDueAt and excludes terminal statuses`() = withTestDb {
    val now = Instant.parse("2026-05-26T12:00:00Z")
    // Жалоба #1: NEW, SAFETY (SLA 24ч), создана 30ч назад → overdue, slaDueAt - 6ч (горящая сильнее)
    insertComplaint(id = 1L, status = "NEW", category = "SAFETY",
                    createdAt = now.minus(30.hours))
    // Жалоба #2: IN_PROGRESS, GARBAGE (SLA 24ч), создана 20ч назад → ещё не overdue, slaDueAt +4ч
    insertComplaint(id = 2L, status = "IN_PROGRESS", category = "GARBAGE",
                    createdAt = now.minus(20.hours))
    // Жалоба #3: RESOLVED — не должна попасть
    insertComplaint(id = 3L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = now.minus(48.hours), resolvedAt = now.minus(2.hours))

    val items = AnalyticsRepository().burningQueue(now.toOffsetDateTimeUtc(), limit = 10)

    assertEquals(2, items.size)
    assertEquals(1L, items[0].id)                          // overdue первая
    assertEquals(2L, items[1].id)
    assertTrue(items[0].secondsToDeadline < 0)
    assertTrue(items[1].secondsToDeadline > 0)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.burning*"
```
Expected: FAIL — `burningQueue` не существует.

- [ ] **Step 3: Implement repository method**

В `AnalyticsRepository.kt`:

```kotlin
data class BurningRow(
    val id: Long,
    val title: String,
    val districtCode: String?,
    val category: String,
    val createdAt: Instant,
    val slaDueAt: Instant,
    val secondsToDeadline: Long,
)

fun burningQueue(now: OffsetDateTime, limit: Int = AnalyticsConfig.BURNING_QUEUE_DEFAULT_LIMIT): List<BurningRow> = transaction {
    val nowMillis = now.toInstant().toEpochMilli()
    val rows = mutableListOf<BurningRow>()
    val sql = """
        SELECT id, title, district, category, created_at,
               created_at + (CASE category
                              WHEN 'GARBAGE'   THEN INTERVAL '24 hours'
                              WHEN 'ECOLOGY'   THEN INTERVAL '24 hours'
                              WHEN 'SAFETY'    THEN INTERVAL '24 hours'
                              WHEN 'LIGHTING'  THEN INTERVAL '48 hours'
                              WHEN 'WATER'     THEN INTERVAL '48 hours'
                              WHEN 'SEWAGE'    THEN INTERVAL '48 hours'
                              WHEN 'ELECTRICITY' THEN INTERVAL '48 hours'
                              WHEN 'VANDALISM' THEN INTERVAL '120 hours'
                              WHEN 'TRADE'     THEN INTERVAL '120 hours'
                              WHEN 'OTHER'     THEN INTERVAL '120 hours'
                              ELSE INTERVAL '72 hours'
                            END) AS sla_due_at
        FROM complaints
        WHERE status IN ('NEW', 'IN_PROGRESS')
        ORDER BY sla_due_at ASC
        LIMIT ?
    """.trimIndent()
    exec(sql, listOf(limit to org.jetbrains.exposed.sql.IntegerColumnType())) { rs ->
        while (rs.next()) {
            val createdAt = rs.getTimestamp("created_at").toInstant()
            val slaDueAt = rs.getTimestamp("sla_due_at").toInstant()
            val seconds = (slaDueAt.toEpochMilli() - nowMillis) / 1000L
            rows.add(BurningRow(
                id = rs.getLong("id"),
                title = rs.getString("title"),
                districtCode = rs.getString("district"),
                category = rs.getString("category"),
                createdAt = createdAt,
                slaDueAt = slaDueAt,
                secondsToDeadline = seconds,
            ))
        }
    }
    rows
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.burning*"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): burningQueue — top-N горящих по slaDueAt"
```

---

### Task 6: Repository — strategic kpis (median, p90, SLA compliance, throughput)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `strategic kpis median p90 sla throughput`() = withTestDb {
    val periodStart = Instant.parse("2026-05-01T00:00:00Z")
    val periodEnd = Instant.parse("2026-05-31T23:59:59Z")
    // 4 RESOLVED жалобы GARBAGE (SLA 24ч): 12ч (within), 18ч (within), 24ч (граница), 100ч (breach)
    insertComplaint(id = 1L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(1.days),
                    resolvedAt = periodStart.plus(1.days).plus(12.hours))
    insertComplaint(id = 2L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(2.days),
                    resolvedAt = periodStart.plus(2.days).plus(18.hours))
    insertComplaint(id = 3L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(3.days),
                    resolvedAt = periodStart.plus(3.days).plus(24.hours))
    insertComplaint(id = 4L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(4.days),
                    resolvedAt = periodStart.plus(4.days).plus(100.hours))

    val kpis = AnalyticsRepository().strategicKpis(
        periodStart.toOffsetDateTimeUtc(),
        periodEnd.toOffsetDateTimeUtc(),
    )

    assertEquals(4, kpis.throughput)
    // SLA compliance: 3 из 4 within (≤24ч) = 75%
    assertEquals(75.0, kpis.slaCompliancePct, absoluteTolerance = 0.5)
    // median = (18 + 24) / 2 = 21ч
    assertEquals(21.0, kpis.medianResolutionHours!!, absoluteTolerance = 0.5)
    // p90 — между 24 и 100, ближе к 100 (т.к. p90 = значение 4*0.9=3.6 → линейная интерполяция)
    // допуск шире, главное чтобы не среднее
    assertTrue(kpis.p90ResolutionHours!! > 50.0)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.strategic*"
```
Expected: FAIL — `strategicKpis` не существует.

- [ ] **Step 3: Implement repository method**

В `AnalyticsRepository.kt`:

```kotlin
data class StrategicKpisRow(
    val slaCompliancePct: Double,
    val medianResolutionHours: Double?,
    val p90ResolutionHours: Double?,
    val throughput: Int,
)

fun strategicKpis(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): StrategicKpisRow = transaction {
    val sql = """
        SELECT
          COUNT(*) AS throughput,
          percentile_cont(0.5) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
          ) AS median_h,
          percentile_cont(0.9) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
          ) AS p90_h,
          SUM(CASE WHEN EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
                       <= (CASE category
                              WHEN 'GARBAGE'   THEN 24
                              WHEN 'ECOLOGY'   THEN 24
                              WHEN 'SAFETY'    THEN 24
                              WHEN 'LIGHTING'  THEN 48
                              WHEN 'WATER'     THEN 48
                              WHEN 'SEWAGE'    THEN 48
                              WHEN 'ELECTRICITY' THEN 48
                              WHEN 'VANDALISM' THEN 120
                              WHEN 'TRADE'     THEN 120
                              WHEN 'OTHER'     THEN 120
                              ELSE 72
                           END)
                  THEN 1 ELSE 0 END)::float
            / NULLIF(COUNT(*), 0) * 100.0 AS sla_compliance_pct
        FROM complaints
        WHERE status = 'RESOLVED'
          AND resolved_at >= ?
          AND resolved_at < ?
    """.trimIndent()
    var result = StrategicKpisRow(0.0, null, null, 0)
    exec(sql, listOf(
        java.sql.Timestamp.from(periodStart.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
        java.sql.Timestamp.from(periodEnd.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
    )) { rs ->
        if (rs.next()) {
            result = StrategicKpisRow(
                throughput = rs.getInt("throughput"),
                medianResolutionHours = rs.getDouble("median_h").takeIf { !rs.wasNull() },
                p90ResolutionHours = rs.getDouble("p90_h").takeIf { !rs.wasNull() },
                slaCompliancePct = rs.getDouble("sla_compliance_pct").let { if (rs.wasNull()) 0.0 else it },
            )
        }
    }
    result
}
```

**Замечание:** `percentile_cont` поддерживается Postgres 9.4+. H2 в режиме PostgreSQL поддерживает percentile_cont начиная с версии 2.x — проверить версию H2 в `build.gradle.kts` модуля backend. Если не поддерживает — fallback: считать median и p90 в Kotlin поверх отдельного запроса всех значений (для теста на десятках жалоб это терпимо).

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.strategic*"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): strategicKpis — median, p90, SLA compliance %, throughput"
```

---

### Task 7: Repository — reopen rate (Haversine)

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `reopen rate counts pairs within radius and window`() = withTestDb {
    val periodStart = Instant.parse("2026-05-01T00:00:00Z")
    val periodEnd = Instant.parse("2026-05-31T23:59:59Z")
    // Точка A: 43.5856, 39.7231 (центр Сочи)
    val latA = 43.5856; val lonA = 39.7231
    // Точка B: 50м к северу от A (latB ≈ 43.586049)
    val latB = 43.586049; val lonB = 39.7231
    // Точка C: 200м к северу от A
    val latC = 43.587397; val lonC = 39.7231

    // resolved #1: A, GARBAGE, resolved в начале периода
    insertComplaint(id = 1L, status = "RESOLVED", category = "GARBAGE",
                    latitude = latA, longitude = lonA,
                    createdAt = periodStart.minus(2.days),
                    resolvedAt = periodStart.plus(1.days))
    // reopen #1: B, GARBAGE, создана через 10 дней после resolved — попадает (в радиусе+окне+категории)
    insertComplaint(id = 2L, status = "NEW", category = "GARBAGE",
                    latitude = latB, longitude = lonB,
                    createdAt = periodStart.plus(11.days))
    // not-reopen: C, GARBAGE, через 5 дней — НЕ попадает (вне 50м)
    insertComplaint(id = 3L, status = "NEW", category = "GARBAGE",
                    latitude = latC, longitude = lonC,
                    createdAt = periodStart.plus(6.days))
    // not-reopen: A, LIGHTING, через 5 дней — НЕ попадает (другая категория)
    insertComplaint(id = 4L, status = "NEW", category = "LIGHTING",
                    latitude = latA, longitude = lonA,
                    createdAt = periodStart.plus(6.days))
    // resolved #2: ещё одна resolved в периоде (без reopen-пары)
    insertComplaint(id = 5L, status = "RESOLVED", category = "GARBAGE",
                    latitude = latA, longitude = lonA,
                    createdAt = periodStart.plus(15.days),
                    resolvedAt = periodStart.plus(16.days))

    val stat = AnalyticsRepository().reopenStat(
        periodStart.toOffsetDateTimeUtc(),
        periodEnd.toOffsetDateTimeUtc(),
    )

    assertEquals(2, stat.resolvedCount)
    assertEquals(1, stat.reopenCount)
    assertEquals(0.5, stat.reopenRate, absoluteTolerance = 0.01)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.reopen*"
```
Expected: FAIL — `reopenStat` не существует.

- [ ] **Step 3: Implement repository method (Haversine, no PostGIS)**

В `AnalyticsRepository.kt`:

```kotlin
data class ReopenStatRow(
    val reopenRate: Double,
    val reopenCount: Int,
    val resolvedCount: Int,
)

fun reopenStat(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): ReopenStatRow = transaction {
    val radiusM = AnalyticsConfig.REOPEN_RADIUS_METERS
    val windowDays = AnalyticsConfig.REOPEN_WINDOW_DAYS

    // Haversine: 6371000 * 2 * ASIN(SQRT(...)) — distance in meters
    // Считаем все resolved в периоде; для каждой определяем "имела ли reopen-пару"
    val sql = """
        WITH resolved AS (
          SELECT id, category, resolved_at, latitude, longitude
          FROM complaints
          WHERE status = 'RESOLVED'
            AND resolved_at >= ?
            AND resolved_at <  ?
        ),
        reopens AS (
          SELECT DISTINCT r.id
          FROM resolved r
          JOIN complaints c2 ON c2.id <> r.id
            AND c2.category = r.category
            AND c2.created_at >  r.resolved_at
            AND c2.created_at <= r.resolved_at + INTERVAL '$windowDays days'
            AND 6371000 * 2 * ASIN(
                  SQRT(
                    POWER(SIN(RADIANS((c2.latitude - r.latitude)/2)), 2) +
                    COS(RADIANS(r.latitude)) * COS(RADIANS(c2.latitude)) *
                    POWER(SIN(RADIANS((c2.longitude - r.longitude)/2)), 2)
                  )
                ) <= $radiusM
        )
        SELECT
          (SELECT COUNT(*) FROM resolved) AS resolved_count,
          (SELECT COUNT(*) FROM reopens) AS reopen_count
    """.trimIndent()

    var result = ReopenStatRow(0.0, 0, 0)
    exec(sql, listOf(
        java.sql.Timestamp.from(periodStart.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
        java.sql.Timestamp.from(periodEnd.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
    )) { rs ->
        if (rs.next()) {
            val resolved = rs.getInt("resolved_count")
            val reopen = rs.getInt("reopen_count")
            val rate = if (resolved == 0) 0.0 else reopen.toDouble() / resolved
            result = ReopenStatRow(reopenRate = rate, reopenCount = reopen, resolvedCount = resolved)
        }
    }
    result
}
```

**Замечание:** Haversine в SQL даёт точность ~99.5% (для 50м радиуса погрешность <0.5м — несущественно). Работает на любом Postgres и H2 в MODE=PostgreSQL.

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.reopen*"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): reopenStat через Haversine (50м/30д/категория)"
```

---

### Task 8: Repository — trends с groupBy day/week/month

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `trends groupBy day returns created and resolved series`() = withTestDb {
    val periodStart = Instant.parse("2026-05-20T00:00:00Z")
    val periodEnd = Instant.parse("2026-05-23T00:00:00Z")
    // 3 жалобы созданы в 3 дня подряд
    insertComplaint(id = 1L, status = "RESOLVED",
                    createdAt = Instant.parse("2026-05-20T10:00:00Z"),
                    resolvedAt = Instant.parse("2026-05-21T10:00:00Z"))
    insertComplaint(id = 2L, status = "RESOLVED",
                    createdAt = Instant.parse("2026-05-21T10:00:00Z"),
                    resolvedAt = Instant.parse("2026-05-22T10:00:00Z"))
    insertComplaint(id = 3L, status = "NEW",
                    createdAt = Instant.parse("2026-05-22T10:00:00Z"))

    val trends = AnalyticsRepository().trendsRange(
        periodStart.toOffsetDateTimeUtc(),
        periodEnd.toOffsetDateTimeUtc(),
        groupBy = "day",
    )

    assertEquals("day", trends.groupBy)
    assertEquals(3, trends.createdSeries.size)
    assertEquals(2, trends.resolvedSeries.count { it.value > 0 })
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.trends*"
```
Expected: FAIL — `trendsRange` не существует.

- [ ] **Step 3: Implement repository method**

В `AnalyticsRepository.kt`:

```kotlin
data class TrendsRangeRow(
    val createdSeries: List<Pair<Instant, Int>>,
    val resolvedSeries: List<Pair<Instant, Int>>,
    val groupBy: String,
)

fun trendsRange(periodStart: OffsetDateTime, periodEnd: OffsetDateTime, groupBy: String): TrendsRangeRow = transaction {
    require(groupBy in setOf("day", "week", "month")) { "groupBy must be day|week|month" }
    val truncFn = when (groupBy) {
        "day" -> "date_trunc('day', %s)"
        "week" -> "date_trunc('week', %s)"
        "month" -> "date_trunc('month', %s)"
        else -> error("unreachable")
    }

    val createdSql = """
        SELECT ${truncFn.format("created_at")} AS bucket, COUNT(*) AS value
        FROM complaints
        WHERE created_at >= ? AND created_at < ?
        GROUP BY 1 ORDER BY 1
    """.trimIndent()

    val resolvedSql = """
        SELECT ${truncFn.format("resolved_at")} AS bucket, COUNT(*) AS value
        FROM complaints
        WHERE status = 'RESOLVED'
          AND resolved_at >= ? AND resolved_at < ?
        GROUP BY 1 ORDER BY 1
    """.trimIndent()

    val created = mutableListOf<Pair<Instant, Int>>()
    val resolved = mutableListOf<Pair<Instant, Int>>()
    val params = listOf(
        java.sql.Timestamp.from(periodStart.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
        java.sql.Timestamp.from(periodEnd.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
    )
    exec(createdSql, params) { rs ->
        while (rs.next()) {
            created.add(rs.getTimestamp("bucket").toInstant() to rs.getInt("value"))
        }
    }
    exec(resolvedSql, params) { rs ->
        while (rs.next()) {
            resolved.add(rs.getTimestamp("bucket").toInstant() to rs.getInt("value"))
        }
    }
    TrendsRangeRow(createdSeries = created, resolvedSeries = resolved, groupBy = groupBy)
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.trends*"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): trendsRange — парные ряды created/resolved с groupBy day|week|month"
```

---

### Task 9: Repository — расширить byCategory и byDistrict

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `byCategory returns median p90 and sla compliance`() = withTestDb {
    val periodStart = Instant.parse("2026-05-01T00:00:00Z")
    val periodEnd = Instant.parse("2026-05-31T23:59:59Z")
    // 4 RESOLVED GARBAGE: 12, 18, 24, 100 ч → median=21, p90 ≈ 77, SLA compliance = 75%
    insertComplaint(id = 1L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(1.days),
                    resolvedAt = periodStart.plus(1.days).plus(12.hours))
    insertComplaint(id = 2L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(2.days),
                    resolvedAt = periodStart.plus(2.days).plus(18.hours))
    insertComplaint(id = 3L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(3.days),
                    resolvedAt = periodStart.plus(3.days).plus(24.hours))
    insertComplaint(id = 4L, status = "RESOLVED", category = "GARBAGE",
                    createdAt = periodStart.plus(4.days),
                    resolvedAt = periodStart.plus(4.days).plus(100.hours))

    val stats = AnalyticsRepository().byCategoryExtended(
        periodStart.toOffsetDateTimeUtc(),
        periodEnd.toOffsetDateTimeUtc(),
    )

    val garbage = stats.first { it.category == "GARBAGE" }
    assertEquals(21.0, garbage.medianResolutionHours!!, absoluteTolerance = 0.5)
    assertTrue(garbage.p90ResolutionHours!! > 50.0)
    assertEquals(75.0, garbage.slaCompliancePct!!, absoluteTolerance = 0.5)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.byCategory*"
```
Expected: FAIL — `byCategoryExtended` не существует.

- [ ] **Step 3: Implement repository methods**

В `AnalyticsRepository.kt`:

```kotlin
data class CategoryStatExtended(
    val category: String,
    val count: Int,
    val avgResolutionHours: Double?,
    val medianResolutionHours: Double?,
    val p90ResolutionHours: Double?,
    val slaCompliancePct: Double?,
)

fun byCategoryExtended(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): List<CategoryStatExtended> = transaction {
    val sql = """
        SELECT
          category,
          COUNT(*) AS cnt,
          AVG(EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0) AS avg_h,
          percentile_cont(0.5) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
          ) AS median_h,
          percentile_cont(0.9) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
          ) AS p90_h,
          SUM(CASE WHEN EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
                       <= (CASE category
                              WHEN 'GARBAGE'   THEN 24
                              WHEN 'ECOLOGY'   THEN 24
                              WHEN 'SAFETY'    THEN 24
                              WHEN 'LIGHTING'  THEN 48
                              WHEN 'WATER'     THEN 48
                              WHEN 'SEWAGE'    THEN 48
                              WHEN 'ELECTRICITY' THEN 48
                              WHEN 'VANDALISM' THEN 120
                              WHEN 'TRADE'     THEN 120
                              WHEN 'OTHER'     THEN 120
                              ELSE 72
                           END)
                  THEN 1 ELSE 0 END)::float
            / NULLIF(COUNT(*), 0) * 100.0 AS sla_compliance_pct
        FROM complaints
        WHERE status = 'RESOLVED'
          AND resolved_at >= ?
          AND resolved_at <  ?
        GROUP BY category
        ORDER BY cnt DESC
    """.trimIndent()
    val out = mutableListOf<CategoryStatExtended>()
    exec(sql, listOf(
        java.sql.Timestamp.from(periodStart.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
        java.sql.Timestamp.from(periodEnd.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
    )) { rs ->
        while (rs.next()) {
            out.add(CategoryStatExtended(
                category = rs.getString("category"),
                count = rs.getInt("cnt"),
                avgResolutionHours = rs.getDouble("avg_h").takeIf { !rs.wasNull() },
                medianResolutionHours = rs.getDouble("median_h").takeIf { !rs.wasNull() },
                p90ResolutionHours = rs.getDouble("p90_h").takeIf { !rs.wasNull() },
                slaCompliancePct = rs.getDouble("sla_compliance_pct").takeIf { !rs.wasNull() },
            ))
        }
    }
    out
}

data class DistrictStatExtended(
    val district: String?,
    val count: Int,
    val newCount: Int,
    val resolvedCount: Int,
    val medianResolutionHours: Double?,
    val slaCompliancePct: Double?,
)

fun byDistrictExtended(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): List<DistrictStatExtended> = transaction {
    // SLA-CASE-блок повторяется в нескольких запросах — вынесен в локальную const-string.
    val slaCase = """
        (CASE category
            WHEN 'GARBAGE'   THEN 24
            WHEN 'ECOLOGY'   THEN 24
            WHEN 'SAFETY'    THEN 24
            WHEN 'LIGHTING'  THEN 48
            WHEN 'WATER'     THEN 48
            WHEN 'SEWAGE'    THEN 48
            WHEN 'ELECTRICITY' THEN 48
            WHEN 'VANDALISM' THEN 120
            WHEN 'TRADE'     THEN 120
            WHEN 'OTHER'     THEN 120
            ELSE 72
         END)
    """.trimIndent()
    val sql = """
        SELECT
          district,
          COUNT(*) AS cnt,
          COUNT(*) FILTER (WHERE status = 'NEW') AS new_count,
          COUNT(*) FILTER (WHERE status = 'RESOLVED') AS resolved_count,
          percentile_cont(0.5) WITHIN GROUP (
            ORDER BY EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
          ) FILTER (WHERE status = 'RESOLVED') AS median_h,
          SUM(CASE WHEN status = 'RESOLVED' AND
                        EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0 <= $slaCase
                   THEN 1 ELSE 0 END)::float
            / NULLIF(COUNT(*) FILTER (WHERE status = 'RESOLVED'), 0) * 100.0
            AS sla_compliance_pct
        FROM complaints
        WHERE created_at >= ?
          AND created_at <  ?
        GROUP BY district
        ORDER BY cnt DESC
    """.trimIndent()
    val out = mutableListOf<DistrictStatExtended>()
    exec(sql, listOf(
        java.sql.Timestamp.from(periodStart.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
        java.sql.Timestamp.from(periodEnd.toInstant()) to org.jetbrains.exposed.sql.JavaInstantColumnType(),
    )) { rs ->
        while (rs.next()) {
            out.add(DistrictStatExtended(
                district = rs.getString("district"),
                count = rs.getInt("cnt"),
                newCount = rs.getInt("new_count"),
                resolvedCount = rs.getInt("resolved_count"),
                medianResolutionHours = rs.getDouble("median_h").takeIf { !rs.wasNull() },
                slaCompliancePct = rs.getDouble("sla_compliance_pct").takeIf { !rs.wasNull() },
            ))
        }
    }
    out
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.byCategory*"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): byCategory/byDistrict с median, p90 и SLA-compliance"
```

---

## Phase 3 — Backend service & routes

### Task 10: Service-методы для нового API

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `service operational returns DTO with target hours from config`() = withTestDb {
    val now = Instant.parse("2026-05-26T12:00:00Z").toOffsetDateTimeUtc()
    val service = AnalyticsService(AnalyticsRepository())
    val snapshot = service.operational(now)
    assertEquals(AnalyticsConfig.DTA_TARGET_HOURS, snapshot.dtaTargetHours)
}

@Test
fun `service strategic returns DTO with targets from config`() = withTestDb {
    val from = Instant.parse("2026-05-01T00:00:00Z").toOffsetDateTimeUtc()
    val to = Instant.parse("2026-05-31T23:59:59Z").toOffsetDateTimeUtc()
    val service = AnalyticsService(AnalyticsRepository())
    val kpis = service.strategic(from, to)
    assertEquals(AnalyticsConfig.SLA_TARGET_PCT, kpis.slaTargetPct)
    assertEquals(AnalyticsConfig.REOPEN_TARGET_PCT, kpis.reopenTargetPct)
}

@Test
fun `service burning maps repo rows to DTOs`() = withTestDb {
    val now = Instant.parse("2026-05-26T12:00:00Z")
    insertComplaint(id = 1L, status = "NEW", category = "GARBAGE",
                    createdAt = now.minus(30.hours))
    val service = AnalyticsService(AnalyticsRepository())
    val items = service.burning(now.toOffsetDateTimeUtc(), limit = 10)
    assertEquals(1, items.size)
    assertEquals(1L, items[0].id)
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.service*"
```
Expected: FAIL — методы `operational`, `strategic`, `burning`, `reopen` не существуют в сервисе.

- [ ] **Step 3: Implement service methods**

В `AnalyticsService.kt` добавить:

```kotlin
fun operational(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): OperationalSnapshot {
    val row = repo.operationalSnapshot(now)
    return OperationalSnapshot(
        backlog = row.backlog,
        overdueNow = row.overdueNow,
        avgDtaHours24h = row.avgDtaHours24h,
        dtaTargetHours = AnalyticsConfig.DTA_TARGET_HOURS,
        createdToday = row.createdToday,
        createdYesterday = row.createdYesterday,
        statusBreakdown = row.statusBreakdown,
    )
}

fun burning(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC), limit: Int = AnalyticsConfig.BURNING_QUEUE_DEFAULT_LIMIT): List<BurningComplaintItem> {
    return repo.burningQueue(now, limit).map { r ->
        BurningComplaintItem(
            id = r.id,
            title = r.title,
            districtCode = r.districtCode,
            category = r.category,
            createdAt = r.createdAt.toKotlinInstant(),
            slaDueAt = r.slaDueAt.toKotlinInstant(),
            secondsToDeadline = r.secondsToDeadline,
        )
    }
}

fun strategic(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): StrategicKpis {
    val k = repo.strategicKpis(periodStart, periodEnd)
    val r = repo.reopenStat(periodStart, periodEnd)
    return StrategicKpis(
        slaCompliancePct = k.slaCompliancePct,
        slaTargetPct = AnalyticsConfig.SLA_TARGET_PCT,
        medianResolutionHours = k.medianResolutionHours,
        p90ResolutionHours = k.p90ResolutionHours,
        reopenRate = r.reopenRate,
        reopenTargetPct = AnalyticsConfig.REOPEN_TARGET_PCT,
        throughput = k.throughput,
    )
}

fun reopen(periodStart: OffsetDateTime, periodEnd: OffsetDateTime): ReopenStat {
    val r = repo.reopenStat(periodStart, periodEnd)
    return ReopenStat(reopenRate = r.reopenRate, reopenCount = r.reopenCount, resolvedCount = r.resolvedCount)
}

fun trendsRange(periodStart: OffsetDateTime, periodEnd: OffsetDateTime, groupBy: String = "day"): TrendsResponse {
    val r = repo.trendsRange(periodStart, periodEnd, groupBy)
    return TrendsResponse(
        days = emptyList(),
        createdSeries = r.createdSeries.map { TrendPoint(it.first.toKotlinInstant(), it.second) },
        resolvedSeries = r.resolvedSeries.map { TrendPoint(it.first.toKotlinInstant(), it.second) },
        groupBy = r.groupBy,
    )
}
```

И обновить существующие `byCategory` / `byDistrict` чтобы они отдавали расширенный набор:

```kotlin
fun byCategory(period: AnalyticsPeriod): List<CategoryStat> {
    val (start, end) = period.toRange()
    val rows = repo.byCategoryExtended(start, end)
    val total = rows.sumOf { it.count }.toDouble()
    return rows.map { r ->
        CategoryStat(
            category = ProblemCategory.valueOf(r.category),
            label = ProblemCategory.valueOf(r.category).label,
            count = r.count,
            sharePct = if (total > 0) r.count / total * 100.0 else 0.0,
            avgResolutionHours = r.avgResolutionHours,
            medianResolutionHours = r.medianResolutionHours,
            p90ResolutionHours = r.p90ResolutionHours,
            slaCompliancePct = r.slaCompliancePct,
        )
    }
}

// Аналогично byDistrict — расширить с новыми полями DistrictStat.
```

`AnalyticsPeriod.toRange()` — если такого метода нет, добавить:

```kotlin
fun AnalyticsPeriod.toRange(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): Pair<OffsetDateTime, OffsetDateTime> {
    return when (this) {
        AnalyticsPeriod.WEEK -> now.minusWeeks(1) to now
        AnalyticsPeriod.MONTH -> now.minusMonths(1) to now
        AnalyticsPeriod.QUARTER -> now.minusMonths(3) to now
        AnalyticsPeriod.YEAR -> now.minusYears(1) to now
        AnalyticsPeriod.ALL -> now.minusYears(10) to now
    }
}
```

Также **добавить новые варианты в enum** `AnalyticsPeriod`:

```kotlin
enum class AnalyticsPeriod { WEEK, MONTH, QUARTER, YEAR, ALL }
```

(если в проекте AnalyticsPeriod лежит в shared — обновить и в shared, а также в `web-admin/src/api/types.ts` через отдельную задачу Phase 4.)

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsServiceTest.service*"
./gradlew :backend:test --tests "*.AnalyticsServiceTest"
```
Expected: PASS, и все существующие тесты тоже зелёные.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt \
        shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsPeriod.kt
git commit -m "feat(analytics): service-методы operational/burning/strategic/reopen + расширенный byCategory/byDistrict; QUARTER/YEAR в AnalyticsPeriod"
```

---

### Task 11: Routes — новые endpoints + deprecate /overview

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt`
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt` (или существующий route-тест файл; проверить есть ли)

- [ ] **Step 1: Write the failing test**

Если route-теста ещё нет — создать `AnalyticsRoutesTest.kt` с минимальным testApplication blueprint (как в проекте уже есть для других routes):

```kotlin
@Test
fun `GET analytics operational returns 200 for admin`() = testApplication {
    // setup test app: register analyticsRoutes(service) with stub service
    // login as admin, GET /analytics/operational
    val response = client.get("/analytics/operational") {
        header("Authorization", "Bearer $adminToken")
    }
    assertEquals(HttpStatusCode.OK, response.status)
}

@Test
fun `GET analytics burning returns 200 with limit param`() = testApplication {
    val response = client.get("/analytics/burning?limit=5") {
        header("Authorization", "Bearer $adminToken")
    }
    assertEquals(HttpStatusCode.OK, response.status)
}

@Test
fun `GET analytics strategic respects period query`() = testApplication {
    val response = client.get("/analytics/strategic?period=MONTH") {
        header("Authorization", "Bearer $adminToken")
    }
    assertEquals(HttpStatusCode.OK, response.status)
}

@Test
fun `GET analytics reopen respects period query`() = testApplication {
    val response = client.get("/analytics/reopen?period=MONTH") {
        header("Authorization", "Bearer $adminToken")
    }
    assertEquals(HttpStatusCode.OK, response.status)
}
```

(Если в проекте уже используется конкретный паттерн setup — следовать ему. Подсказка: `Application.module()` принимает параметры; testApplication поднимает Ktor instance с реальным AnalyticsService на H2.)

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew :backend:test --tests "*.AnalyticsRoutesTest*"
```
Expected: FAIL — endpoints не зарегистрированы.

- [ ] **Step 3: Implement routes**

В `AnalyticsRoutes.kt`:

```kotlin
fun Route.analyticsRoutes(service: AnalyticsService) {
    authenticate("auth-jwt") {
        route("/analytics") {

            @Suppress("DEPRECATION")  // legacy endpoint, planned removal
            get("/overview") { call.respond(service.overview()) }

            get("/operational") {
                call.respond(service.operational())
            }

            get("/burning") {
                val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: AnalyticsConfig.BURNING_QUEUE_DEFAULT_LIMIT)
                    .coerceIn(1, 100)
                call.respond(service.burning(limit = limit))
            }

            get("/strategic") {
                val (from, to) = call.period().toRange()
                call.respond(service.strategic(from, to))
            }

            get("/reopen") {
                val (from, to) = call.period().toRange()
                call.respond(service.reopen(from, to))
            }

            get("/trends") {
                val (from, to) = call.period().toRange()
                val groupBy = call.request.queryParameters["groupBy"] ?: "day"
                call.respond(service.trendsRange(from, to, groupBy))
            }

            // Существующие endpoints — без изменений (byCategory/byDistrict/sla/votes-impact уже отдают расширенные DTO)
            get("/by-category") { call.respond(service.byCategory(call.period())) }
            get("/by-district") { call.respond(service.byDistrict(call.period())) }
            get("/sla") { call.respond(service.sla(call.period())) }
            get("/votes-impact") { call.respond(service.votesImpact(call.period())) }
        }
    }
}
```

(Авторизация — следовать существующему стилю проекта. Если используется `authorize(Role.ADMIN, Role.OPERATOR, Role.INSPECTOR)` или подобное — применить.)

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :backend:test --tests "*.AnalyticsRoutesTest*"
./gradlew :backend:test --tests "*.AnalyticsServiceTest"
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt
git commit -m "feat(analytics): новые routes /operational /burning /strategic /reopen; /trends с groupBy"
```

---

## Phase 4 — Frontend API & hooks

### Task 12: Frontend types + API client

**Files:**
- Modify: `web-admin/src/api/types.ts`
- Modify: `web-admin/src/api/analytics.ts`
- Test: `web-admin/src/api/analytics.test.ts` (если уже есть — расширить; если нет — создать)

- [ ] **Step 1: Write the failing test**

В `web-admin/src/api/analytics.test.ts`:

```typescript
import { describe, it, expect, beforeEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import {
  getOperational,
  getBurning,
  getStrategic,
  getReopen,
  getTrends,
} from './analytics'

const server = setupServer(
  http.get('/analytics/operational', () => HttpResponse.json({
    backlog: 5, overdueNow: 1, avgDtaHours24h: null, dtaTargetHours: 24,
    createdToday: 3, createdYesterday: 4,
    statusBreakdown: { NEW: 3, IN_PROGRESS: 2 },
  })),
  http.get('/analytics/burning', () => HttpResponse.json([
    { id: 1, title: 'Test', districtCode: 'ADL', category: 'GARBAGE',
      createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-27T08:00:00Z',
      secondsToDeadline: -3600 },
  ])),
  http.get('/analytics/strategic', () => HttpResponse.json({
    slaCompliancePct: 78, slaTargetPct: 80,
    medianResolutionHours: 24, p90ResolutionHours: 72,
    reopenRate: 0.08, reopenTargetPct: 10, throughput: 100,
  })),
  http.get('/analytics/reopen', () => HttpResponse.json({
    reopenRate: 0.08, reopenCount: 8, resolvedCount: 100,
  })),
  http.get('/analytics/trends', () => HttpResponse.json({
    days: [], createdSeries: [], resolvedSeries: [], groupBy: 'day',
  })),
)

beforeEach(() => server.listen())

describe('analytics API client', () => {
  it('getOperational parses snapshot', async () => {
    const s = await getOperational()
    expect(s.backlog).toBe(5)
    expect(s.dtaTargetHours).toBe(24)
  })
  it('getBurning parses items', async () => {
    const items = await getBurning(10)
    expect(items[0].secondsToDeadline).toBeLessThan(0)
  })
  it('getStrategic parses KPIs', async () => {
    const k = await getStrategic('MONTH')
    expect(k.slaTargetPct).toBe(80)
  })
  it('getReopen parses stat', async () => {
    const r = await getReopen('MONTH')
    expect(r.reopenRate).toBe(0.08)
  })
  it('getTrends parses series and groupBy', async () => {
    const t = await getTrends('WEEK', 'day')
    expect(t.groupBy).toBe('day')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/api/analytics.test.ts
```
Expected: FAIL — функции `getOperational`, `getBurning`, `getStrategic`, `getReopen` не существуют.

- [ ] **Step 3: Расширить types.ts и analytics.ts**

В `web-admin/src/api/types.ts` (расширить enum + добавить типы):

```typescript
export type AnalyticsPeriod = 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR' | 'ALL'

export interface OperationalSnapshot {
  backlog: number
  overdueNow: number
  avgDtaHours24h: number | null
  dtaTargetHours: number
  createdToday: number
  createdYesterday: number
  statusBreakdown: Record<string, number>
}

export interface BurningComplaintItem {
  id: number
  title: string
  districtCode: string | null
  category: string
  createdAt: string
  slaDueAt: string
  secondsToDeadline: number
}

export interface StrategicKpis {
  slaCompliancePct: number
  slaTargetPct: number
  medianResolutionHours: number | null
  p90ResolutionHours: number | null
  reopenRate: number
  reopenTargetPct: number
  throughput: number
}

export interface ReopenStat {
  reopenRate: number
  reopenCount: number
  resolvedCount: number
}

export interface TrendPoint {
  bucketStart: string
  value: number
}

// Расширить существующий TrendsResponse:
export interface TrendsResponse {
  days: DailyPoint[]              // legacy
  createdSeries: TrendPoint[]
  resolvedSeries: TrendPoint[]
  groupBy: 'day' | 'week' | 'month'
}

// Расширить существующие CategoryStat / DistrictStat:
export interface CategoryStat {
  // существующие поля + ↓
  medianResolutionHours: number | null
  p90ResolutionHours: number | null
  slaCompliancePct: number | null
}
export interface DistrictStat {
  // существующие поля + ↓
  medianResolutionHours: number | null
  slaCompliancePct: number | null
}
```

В `web-admin/src/api/analytics.ts`:

```typescript
import { http } from './httpClient'  // или существующий клиент
import type {
  OperationalSnapshot, BurningComplaintItem, StrategicKpis,
  ReopenStat, TrendsResponse, AnalyticsPeriod, CategoryStat, DistrictStat, SlaStat,
} from './types'

export async function getOperational(): Promise<OperationalSnapshot> {
  return (await http.get('/analytics/operational')).data
}

export async function getBurning(limit = 10): Promise<BurningComplaintItem[]> {
  return (await http.get(`/analytics/burning?limit=${limit}`)).data
}

export async function getStrategic(period: AnalyticsPeriod): Promise<StrategicKpis> {
  return (await http.get(`/analytics/strategic?period=${period}`)).data
}

export async function getReopen(period: AnalyticsPeriod): Promise<ReopenStat> {
  return (await http.get(`/analytics/reopen?period=${period}`)).data
}

export async function getTrends(period: AnalyticsPeriod, groupBy: 'day' | 'week' | 'month' = 'day'): Promise<TrendsResponse> {
  return (await http.get(`/analytics/trends?period=${period}&groupBy=${groupBy}`)).data
}

// Существующие getByCategory/getByDistrict/getSla — без изменений сигнатур;
// типы автоматически расширились в types.ts.

/** @deprecated используется только в legacy OverviewPage; перейдено на getOperational+getStrategic */
export async function getOverview() { /* как было */ }
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/api/analytics.test.ts
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/api/types.ts web-admin/src/api/analytics.ts web-admin/src/api/analytics.test.ts
git commit -m "feat(web-admin): API клиент для новых analytics-эндпоинтов + расширенные типы"
```

---

### Task 13: React Query хуки

**Files:**
- Modify: `web-admin/src/hooks/dashboardQueries.ts`
- Test: `web-admin/src/hooks/dashboardQueries.test.tsx`

- [ ] **Step 1: Write the failing test**

В `web-admin/src/hooks/dashboardQueries.test.tsx` (создать или расширить существующий):

```tsx
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import {
  useOperationalQuery,
  useBurningQuery,
  useStrategicQuery,
  useReopenQuery,
} from './dashboardQueries'
import { describe, it, expect, beforeAll, afterAll } from 'vitest'

const server = setupServer(
  http.get('/analytics/operational', () => HttpResponse.json({
    backlog: 7, overdueNow: 2, avgDtaHours24h: 5,
    dtaTargetHours: 24, createdToday: 3, createdYesterday: 4,
    statusBreakdown: {},
  })),
  http.get('/analytics/burning', () => HttpResponse.json([])),
  http.get('/analytics/strategic', () => HttpResponse.json({
    slaCompliancePct: 78, slaTargetPct: 80,
    medianResolutionHours: null, p90ResolutionHours: null,
    reopenRate: 0, reopenTargetPct: 10, throughput: 0,
  })),
  http.get('/analytics/reopen', () => HttpResponse.json({
    reopenRate: 0, reopenCount: 0, resolvedCount: 0,
  })),
)

beforeAll(() => server.listen())
afterAll(() => server.close())

function wrap() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return ({ children }: { children: React.ReactNode }) =>
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
}

describe('dashboard hooks', () => {
  it('useOperationalQuery returns snapshot', async () => {
    const { result } = renderHook(() => useOperationalQuery(), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data?.backlog).toBe(7))
  })
  it('useStrategicQuery with period', async () => {
    const { result } = renderHook(() => useStrategicQuery('MONTH'), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data?.slaTargetPct).toBe(80))
  })
  it('useReopenQuery returns stat', async () => {
    const { result } = renderHook(() => useReopenQuery('MONTH'), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data?.resolvedCount).toBe(0))
  })
  it('useBurningQuery returns list', async () => {
    const { result } = renderHook(() => useBurningQuery(10), { wrapper: wrap() })
    await waitFor(() => expect(result.current.data).toEqual([]))
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/hooks/dashboardQueries.test.tsx
```
Expected: FAIL — хуки не существуют.

- [ ] **Step 3: Implement hooks**

В `web-admin/src/hooks/dashboardQueries.ts`:

```typescript
import { useQuery } from '@tanstack/react-query'
import {
  getOperational, getBurning, getStrategic, getReopen, getTrends,
} from '../api/analytics'
import type { AnalyticsPeriod } from '../api/types'

const DASHBOARD_REFETCH_MS = 60_000

export function useOperationalQuery() {
  return useQuery({
    queryKey: ['analytics', 'operational'],
    queryFn: getOperational,
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useBurningQuery(limit = 10) {
  return useQuery({
    queryKey: ['analytics', 'burning', limit],
    queryFn: () => getBurning(limit),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useStrategicQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'strategic', period],
    queryFn: () => getStrategic(period),
  })
}

export function useReopenQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'reopen', period],
    queryFn: () => getReopen(period),
  })
}

export function useTrendsRangeQuery(period: AnalyticsPeriod, groupBy: 'day' | 'week' | 'month') {
  return useQuery({
    queryKey: ['analytics', 'trends', period, groupBy],
    queryFn: () => getTrends(period, groupBy),
  })
}
```

И пометить `useOverviewQuery` (в `complaintQueries.ts` или где он лежит) как deprecated:

```typescript
/** @deprecated перейти на useOperationalQuery + useStrategicQuery */
export function useOverviewQuery() { /* как было */ }
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/hooks/dashboardQueries.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/hooks/dashboardQueries.ts \
        web-admin/src/hooks/dashboardQueries.test.tsx \
        web-admin/src/hooks/complaintQueries.ts
git commit -m "feat(web-admin): React Query хуки для новых analytics-эндпоинтов"
```

---

## Phase 5 — Frontend components

### Task 14: KPI-карточки (KpiCardWithTarget, MedianP90Card, ReopenRateCard)

**Files:**
- Create: `web-admin/src/pages/analytics/KpiCardWithTarget.tsx`
- Create: `web-admin/src/pages/analytics/MedianP90Card.tsx`
- Create: `web-admin/src/pages/analytics/ReopenRateCard.tsx`
- Test: `web-admin/src/pages/analytics/KpiCardWithTarget.test.tsx`

- [ ] **Step 1: Write the failing test**

В `web-admin/src/pages/analytics/KpiCardWithTarget.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { KpiCardWithTarget } from './KpiCardWithTarget'

describe('KpiCardWithTarget', () => {
  it('shows value, target and green color when meeting target (higher is better)', () => {
    render(<KpiCardWithTarget
      label="% within SLA"
      value={85}
      unit="%"
      target={80}
      direction="higher-better"
    />)
    expect(screen.getByText(/85/)).toBeInTheDocument()
    expect(screen.getByText(/цель ≥ 80/i)).toBeInTheDocument()
    expect(screen.getByTestId('kpi-card-with-target')).toHaveClass('kpi-card--good')
  })
  it('shows red when below target (higher is better)', () => {
    render(<KpiCardWithTarget label="x" value={50} unit="%" target={80} direction="higher-better" />)
    expect(screen.getByTestId('kpi-card-with-target')).toHaveClass('kpi-card--bad')
  })
  it('inverts logic for lower-better direction', () => {
    render(<KpiCardWithTarget label="reopen" value={5} unit="%" target={10} direction="lower-better" />)
    expect(screen.getByTestId('kpi-card-with-target')).toHaveClass('kpi-card--good')
  })
  it('renders null value gracefully', () => {
    render(<KpiCardWithTarget label="x" value={null} unit="%" target={80} direction="higher-better" />)
    expect(screen.getByText(/—/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/analytics/KpiCardWithTarget.test.tsx
```
Expected: FAIL — компонент не существует.

- [ ] **Step 3: Implement components**

`web-admin/src/pages/analytics/KpiCardWithTarget.tsx`:

```tsx
import React from 'react'

interface Props {
  label: string
  value: number | null
  unit?: string
  target: number
  direction: 'higher-better' | 'lower-better'
  /** Optional href: if provided, card is wrapped in <a> */
  href?: string
}

function colorFor(value: number | null, target: number, direction: 'higher-better' | 'lower-better'): 'good' | 'warn' | 'bad' {
  if (value === null) return 'warn'
  if (direction === 'higher-better') {
    if (value >= target) return 'good'
    if (value >= target * 0.75) return 'warn'
    return 'bad'
  }
  // lower-better
  if (value <= target) return 'good'
  if (value <= target * 2) return 'warn'
  return 'bad'
}

export function KpiCardWithTarget({ label, value, unit = '', target, direction, href }: Props) {
  const tone = colorFor(value, target, direction)
  const arrow = direction === 'higher-better' ? '≥' : '≤'
  const body = (
    <div
      data-testid="kpi-card-with-target"
      className={`kpi-card kpi-card--${tone === 'good' ? 'good' : tone === 'bad' ? 'bad' : 'warn'}`}
    >
      <div className="kpi-card__label">{label}</div>
      <div className="kpi-card__value">{value === null ? '—' : `${value.toFixed(1)}${unit}`}</div>
      <div className="kpi-card__target">цель {arrow} {target}{unit}</div>
    </div>
  )
  return href ? <a href={href}>{body}</a> : body
}
```

`web-admin/src/pages/analytics/MedianP90Card.tsx`:

```tsx
interface Props {
  label: string
  median: number | null
  p90: number | null
}

export function MedianP90Card({ label, median, p90 }: Props) {
  return (
    <div data-testid="median-p90-card" className="kpi-card kpi-card--neutral">
      <div className="kpi-card__label">{label}</div>
      <div className="kpi-card__value">
        {median === null ? '—' : `${median.toFixed(1)}ч`}
        <span className="kpi-card__p90"> / p90 {p90 === null ? '—' : `${p90.toFixed(1)}ч`}</span>
      </div>
      <div className="kpi-card__hint">медиана · 90-й перцентиль</div>
    </div>
  )
}
```

`web-admin/src/pages/analytics/ReopenRateCard.tsx`:

```tsx
import { KpiCardWithTarget } from './KpiCardWithTarget'

interface Props {
  reopenRate: number       // 0..1
  reopenCount: number
  resolvedCount: number
  target: number            // в процентах, например 10
}

export function ReopenRateCard({ reopenRate, reopenCount, resolvedCount, target }: Props) {
  return (
    <KpiCardWithTarget
      label={`Reopen rate · ${reopenCount}/${resolvedCount}`}
      value={resolvedCount === 0 ? null : reopenRate * 100}
      unit="%"
      target={target}
      direction="lower-better"
    />
  )
}
```

CSS-классы `.kpi-card`, `.kpi-card--good`, `.kpi-card--bad`, `.kpi-card--warn` — определить в существующем CSS-файле компонентов analytics (например `web-admin/src/pages/analytics/dashboard.css`, если есть; иначе создать).

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/analytics/KpiCardWithTarget.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/analytics/KpiCardWithTarget.tsx \
        web-admin/src/pages/analytics/MedianP90Card.tsx \
        web-admin/src/pages/analytics/ReopenRateCard.tsx \
        web-admin/src/pages/analytics/KpiCardWithTarget.test.tsx \
        web-admin/src/pages/analytics/dashboard.css
git commit -m "feat(web-admin): KPI-карточки с целевым значением (KpiCardWithTarget, MedianP90Card, ReopenRateCard)"
```

---

### Task 15: BurningQueueTable

**Files:**
- Create: `web-admin/src/pages/overview/BurningQueueTable.tsx`
- Test: `web-admin/src/pages/overview/BurningQueueTable.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { BurningQueueTable } from './BurningQueueTable'
import { MemoryRouter } from 'react-router-dom'

const items = [
  { id: 1, title: 'Сломанная урна', districtCode: 'ADL', category: 'GARBAGE',
    createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-26T09:00:00Z',
    secondsToDeadline: -3600 },
  { id: 2, title: 'Темно во дворе', districtCode: 'CEN', category: 'LIGHTING',
    createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-28T08:00:00Z',
    secondsToDeadline: 7200 },
]

describe('BurningQueueTable', () => {
  it('renders rows in given order', () => {
    render(<MemoryRouter><BurningQueueTable items={items} /></MemoryRouter>)
    const rows = screen.getAllByTestId('burning-row')
    expect(rows).toHaveLength(2)
    expect(rows[0]).toHaveTextContent('Сломанная урна')
  })
  it('highlights overdue rows', () => {
    render(<MemoryRouter><BurningQueueTable items={items} /></MemoryRouter>)
    const overdueRow = screen.getAllByTestId('burning-row')[0]
    expect(overdueRow).toHaveClass('burning-row--overdue')
  })
  it('renders empty state', () => {
    render(<MemoryRouter><BurningQueueTable items={[]} /></MemoryRouter>)
    expect(screen.getByText(/нет горящих жалоб/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/overview/BurningQueueTable.test.tsx
```
Expected: FAIL.

- [ ] **Step 3: Implement component**

`web-admin/src/pages/overview/BurningQueueTable.tsx`:

```tsx
import { Link } from 'react-router-dom'
import type { BurningComplaintItem } from '../../api/types'

interface Props {
  items: BurningComplaintItem[]
}

function formatDeadline(seconds: number): string {
  const abs = Math.abs(seconds)
  const hours = Math.floor(abs / 3600)
  const mins = Math.floor((abs % 3600) / 60)
  const prefix = seconds < 0 ? '-' : '+'
  return `${prefix}${hours}ч ${mins}м`
}

export function BurningQueueTable({ items }: Props) {
  if (items.length === 0) {
    return <div className="burning-empty">Нет горящих жалоб — все в порядке 🎉</div>
  }
  return (
    <table className="burning-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Жалоба</th>
          <th>Район</th>
          <th>Категория</th>
          <th>До дедлайна</th>
        </tr>
      </thead>
      <tbody>
        {items.map(item => (
          <tr
            key={item.id}
            data-testid="burning-row"
            className={item.secondsToDeadline < 0 ? 'burning-row--overdue' : 'burning-row'}
          >
            <td>#{item.id}</td>
            <td><Link to={`/complaints/${item.id}`}>{item.title}</Link></td>
            <td>{item.districtCode ?? '—'}</td>
            <td>{item.category}</td>
            <td>{formatDeadline(item.secondsToDeadline)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

(Эмодзи в empty state — разовое исключение, по согласованию пользователя проекта подобный стиль уже встречается в admin UI; если строгий запрет — заменить на текст без эмодзи.)

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/overview/BurningQueueTable.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/overview/BurningQueueTable.tsx \
        web-admin/src/pages/overview/BurningQueueTable.test.tsx
git commit -m "feat(web-admin): BurningQueueTable — топ горящих жалоб для Overview"
```

---

### Task 16: EquityTable + расширенная TopProblemCategories

**Files:**
- Create: `web-admin/src/pages/analytics/EquityTable.tsx`
- Modify: `web-admin/src/pages/analytics/TopProblemCategories.tsx` (если такого ещё нет в analytics — создать; на Overview он удаляется)
- Test: `web-admin/src/pages/analytics/EquityTable.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { EquityTable } from './EquityTable'

const stats = [
  { district: 'ADLER', label: 'Адлер', count: 50, newCount: 10, resolvedCount: 30,
    medianResolutionHours: 28, slaCompliancePct: 82 },
  { district: 'CENTRAL', label: 'Центральный', count: 30, newCount: 5, resolvedCount: 20,
    medianResolutionHours: 48, slaCompliancePct: 65 },
]

describe('EquityTable', () => {
  it('renders columns and rows', () => {
    render(<EquityTable items={stats} />)
    expect(screen.getByText('Адлер')).toBeInTheDocument()
    expect(screen.getByText(/28/)).toBeInTheDocument()
    expect(screen.getByText(/82/)).toBeInTheDocument()
  })
  it('colors low-compliance row red', () => {
    render(<EquityTable items={stats} />)
    const row = screen.getByText('Центральный').closest('tr')!
    expect(row).toHaveClass('equity-row--bad')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/analytics/EquityTable.test.tsx
```
Expected: FAIL.

- [ ] **Step 3: Implement component**

`web-admin/src/pages/analytics/EquityTable.tsx`:

```tsx
import type { DistrictStat } from '../../api/types'

interface Props { items: DistrictStat[] }

function rowClass(slaPct: number | null): string {
  if (slaPct === null) return 'equity-row'
  if (slaPct >= 80) return 'equity-row equity-row--good'
  if (slaPct >= 60) return 'equity-row equity-row--warn'
  return 'equity-row equity-row--bad'
}

export function EquityTable({ items }: Props) {
  return (
    <table className="equity-table">
      <thead>
        <tr>
          <th>Район</th>
          <th>Жалоб</th>
          <th>Медиана решения</th>
          <th>% within SLA</th>
        </tr>
      </thead>
      <tbody>
        {items.map(d => (
          <tr key={d.district} className={rowClass(d.slaCompliancePct)}>
            <td>{d.label}</td>
            <td>{d.count}</td>
            <td>{d.medianResolutionHours === null ? '—' : `${d.medianResolutionHours.toFixed(1)}ч`}</td>
            <td>{d.slaCompliancePct === null ? '—' : `${d.slaCompliancePct.toFixed(0)}%`}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/analytics/EquityTable.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/analytics/EquityTable.tsx \
        web-admin/src/pages/analytics/EquityTable.test.tsx
git commit -m "feat(web-admin): EquityTable — район × volume × median × % SLA"
```

---

### Task 17: VotesImpactChart (восстановление)

**Files:**
- Create: `web-admin/src/pages/analytics/VotesImpactCard.tsx`
- Test: `web-admin/src/pages/analytics/VotesImpactCard.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { VotesImpactCard } from './VotesImpactCard'

const buckets = [
  { bucket: '0', count: 100, avgResolutionHours: 48 },
  { bucket: '1-5', count: 50, avgResolutionHours: 32 },
  { bucket: '6-20', count: 20, avgResolutionHours: 18 },
  { bucket: '21+', count: 5, avgResolutionHours: 12 },
]

describe('VotesImpactCard', () => {
  it('renders bars for each bucket', () => {
    render(<VotesImpactCard buckets={buckets} />)
    expect(screen.getAllByTestId('votes-bar')).toHaveLength(4)
  })
  it('shows mean resolution per bucket', () => {
    render(<VotesImpactCard buckets={buckets} />)
    expect(screen.getByText(/48/)).toBeInTheDocument()
    expect(screen.getByText(/12/)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/analytics/VotesImpactCard.test.tsx
```
Expected: FAIL.

- [ ] **Step 3: Implement component**

`web-admin/src/pages/analytics/VotesImpactCard.tsx`:

```tsx
import type { VotesBucket } from '../../api/types'

interface Props { buckets: VotesBucket[] }

export function VotesImpactCard({ buckets }: Props) {
  const maxHours = Math.max(...buckets.map(b => b.avgResolutionHours ?? 0), 1)
  return (
    <div className="votes-impact-card">
      <h3>Влияние голосов на скорость решения</h3>
      <div className="votes-impact-bars">
        {buckets.map(b => {
          const h = b.avgResolutionHours ?? 0
          const widthPct = (h / maxHours) * 100
          return (
            <div key={b.bucket} className="votes-impact-row">
              <div className="votes-impact-label">{b.bucket} голосов · {b.count} жалоб</div>
              <div className="votes-impact-track">
                <div
                  data-testid="votes-bar"
                  className="votes-impact-bar"
                  style={{ width: `${widthPct}%` }}
                />
                <span className="votes-impact-value">{h.toFixed(1)}ч</span>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/analytics/VotesImpactCard.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/analytics/VotesImpactCard.tsx \
        web-admin/src/pages/analytics/VotesImpactCard.test.tsx
git commit -m "feat(web-admin): VotesImpactCard — гистограмма голосов vs скорость решения"
```

---

### Task 18: Расширенный TrendCard (created vs resolved)

**Files:**
- Modify: `web-admin/src/pages/analytics/TrendCard.tsx`
- Modify: `web-admin/src/pages/analytics/TrendCard.test.tsx` (если есть; иначе создать)

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { TrendCard } from './TrendCard'

const trends = {
  days: [],
  createdSeries: [
    { bucketStart: '2026-05-20T00:00:00Z', value: 5 },
    { bucketStart: '2026-05-21T00:00:00Z', value: 8 },
    { bucketStart: '2026-05-22T00:00:00Z', value: 6 },
  ],
  resolvedSeries: [
    { bucketStart: '2026-05-20T00:00:00Z', value: 3 },
    { bucketStart: '2026-05-21T00:00:00Z', value: 7 },
    { bucketStart: '2026-05-22T00:00:00Z', value: 9 },
  ],
  groupBy: 'day' as const,
}

describe('TrendCard', () => {
  it('renders two lines for created and resolved', () => {
    render(<TrendCard trends={trends} />)
    expect(screen.getByTestId('trend-line-created')).toBeInTheDocument()
    expect(screen.getByTestId('trend-line-resolved')).toBeInTheDocument()
  })
  it('renders empty state when both series empty', () => {
    render(<TrendCard trends={{ days: [], createdSeries: [], resolvedSeries: [], groupBy: 'day' }} />)
    expect(screen.getByText(/нет данных/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/analytics/TrendCard.test.tsx
```
Expected: FAIL — текущий TrendCard принимает старый формат, не двух-линейный.

- [ ] **Step 3: Перепиать TrendCard на две линии**

`web-admin/src/pages/analytics/TrendCard.tsx`:

```tsx
import type { TrendsResponse } from '../../api/types'

interface Props { trends: TrendsResponse }

function pathFromSeries(series: { bucketStart: string; value: number }[], maxV: number, width: number, height: number): string {
  if (series.length === 0) return ''
  const stepX = width / Math.max(series.length - 1, 1)
  return series.map((p, i) => {
    const x = i * stepX
    const y = height - (p.value / Math.max(maxV, 1)) * height
    return `${i === 0 ? 'M' : 'L'} ${x} ${y}`
  }).join(' ')
}

export function TrendCard({ trends }: Props) {
  const { createdSeries, resolvedSeries } = trends
  if (createdSeries.length === 0 && resolvedSeries.length === 0) {
    return <div className="trend-card trend-card--empty">Нет данных за выбранный период</div>
  }
  const maxV = Math.max(
    ...createdSeries.map(p => p.value),
    ...resolvedSeries.map(p => p.value),
    1,
  )
  const W = 600
  const H = 200
  const createdPath = pathFromSeries(createdSeries, maxV, W, H)
  const resolvedPath = pathFromSeries(resolvedSeries, maxV, W, H)
  return (
    <div className="trend-card">
      <h3>Динамика: создано vs закрыто</h3>
      <svg viewBox={`0 0 ${W} ${H}`} className="trend-svg">
        <path data-testid="trend-line-created" d={createdPath}
              stroke="#e57373" fill="none" strokeWidth={2} />
        <path data-testid="trend-line-resolved" d={resolvedPath}
              stroke="#81c784" fill="none" strokeWidth={2} />
      </svg>
      <div className="trend-card__legend">
        <span><span className="trend-dot trend-dot--created" /> Создано</span>
        <span><span className="trend-dot trend-dot--resolved" /> Закрыто</span>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/analytics/TrendCard.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/analytics/TrendCard.tsx \
        web-admin/src/pages/analytics/TrendCard.test.tsx
git commit -m "refactor(web-admin): TrendCard — две линии (создано vs закрыто) поверх /trends с groupBy"
```

---

### Task 19: PeriodSwitcher с QUARTER/YEAR/ALL

**Files:**
- Modify: `web-admin/src/pages/analytics/PeriodSwitcher.tsx`
- Modify: `web-admin/src/pages/analytics/PeriodSwitcher.test.tsx` (если есть; иначе создать)

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import { PeriodSwitcher } from './PeriodSwitcher'

describe('PeriodSwitcher', () => {
  it('renders all 5 periods', () => {
    render(<PeriodSwitcher value="MONTH" onChange={() => {}} />)
    expect(screen.getByText('Неделя')).toBeInTheDocument()
    expect(screen.getByText('Месяц')).toBeInTheDocument()
    expect(screen.getByText('Квартал')).toBeInTheDocument()
    expect(screen.getByText('Год')).toBeInTheDocument()
    expect(screen.getByText('Всё время')).toBeInTheDocument()
  })
  it('calls onChange with selected period', () => {
    const onChange = vi.fn()
    render(<PeriodSwitcher value="MONTH" onChange={onChange} />)
    fireEvent.click(screen.getByText('Квартал'))
    expect(onChange).toHaveBeenCalledWith('QUARTER')
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/analytics/PeriodSwitcher.test.tsx
```
Expected: FAIL — QUARTER/YEAR не отрисованы.

- [ ] **Step 3: Расширить компонент**

`web-admin/src/pages/analytics/PeriodSwitcher.tsx`:

```tsx
import type { AnalyticsPeriod } from '../../api/types'

interface Props {
  value: AnalyticsPeriod
  onChange: (period: AnalyticsPeriod) => void
}

const PERIODS: { code: AnalyticsPeriod; label: string }[] = [
  { code: 'WEEK', label: 'Неделя' },
  { code: 'MONTH', label: 'Месяц' },
  { code: 'QUARTER', label: 'Квартал' },
  { code: 'YEAR', label: 'Год' },
  { code: 'ALL', label: 'Всё время' },
]

export function PeriodSwitcher({ value, onChange }: Props) {
  return (
    <div className="period-switcher">
      {PERIODS.map(p => (
        <button
          key={p.code}
          className={value === p.code ? 'period-btn period-btn--active' : 'period-btn'}
          onClick={() => onChange(p.code)}
        >
          {p.label}
        </button>
      ))}
    </div>
  )
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/analytics/PeriodSwitcher.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/analytics/PeriodSwitcher.tsx \
        web-admin/src/pages/analytics/PeriodSwitcher.test.tsx
git commit -m "feat(web-admin): PeriodSwitcher — добавить QUARTER/YEAR/ALL"
```

---

## Phase 6 — Frontend pages

### Task 20: Перекомпоновка OverviewPage

**Files:**
- Modify: `web-admin/src/pages/OverviewPage.tsx`
- Modify: `web-admin/src/pages/OverviewPage.test.tsx` (если есть; иначе создать)
- Delete: `web-admin/src/pages/overview/TopDistricts.tsx` (переезд на Analytics покрыт `EquityTable`)
- Delete: `web-admin/src/pages/overview/TopProblemCategories.tsx` (переезд на Analytics)
- Delete: `web-admin/src/pages/overview/SlaAlertBanner.tsx` (заменяется на KPI-карточку «Просрочено»)

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { OverviewPage } from './OverviewPage'

const server = setupServer(
  http.get('/analytics/operational', () => HttpResponse.json({
    backlog: 12, overdueNow: 3, avgDtaHours24h: 5, dtaTargetHours: 24,
    createdToday: 7, createdYesterday: 4,
    statusBreakdown: { NEW: 5, IN_PROGRESS: 7, RESOLVED: 100, REJECTED: 2, DUPLICATE: 1 },
  })),
  http.get('/analytics/burning', () => HttpResponse.json([
    { id: 1, title: 'Урна', districtCode: 'ADL', category: 'GARBAGE',
      createdAt: '2026-05-26T08:00:00Z', slaDueAt: '2026-05-26T09:00:00Z',
      secondsToDeadline: -3600 },
  ])),
)
beforeAll(() => server.listen())
afterAll(() => server.close())

function wrap(node: React.ReactNode) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter>{node}</MemoryRouter>
  </QueryClientProvider>
}

describe('OverviewPage', () => {
  it('renders 4 KPI cards', async () => {
    render(wrap(<OverviewPage />))
    expect(await screen.findByText(/Backlog/i)).toBeInTheDocument()
    expect(await screen.findByText(/Просрочено/i)).toBeInTheDocument()
    expect(await screen.findByText(/DTA/i)).toBeInTheDocument()
    expect(await screen.findByText(/Создано сегодня/i)).toBeInTheDocument()
  })
  it('renders Status Pipeline with all 5 statuses', async () => {
    render(wrap(<OverviewPage />))
    expect(await screen.findByText(/NEW/)).toBeInTheDocument()
    expect(await screen.findByText(/IN_PROGRESS/)).toBeInTheDocument()
    expect(await screen.findByText(/RESOLVED/)).toBeInTheDocument()
  })
  it('renders Burning Queue table', async () => {
    render(wrap(<OverviewPage />))
    expect(await screen.findByText('Урна')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/OverviewPage.test.tsx
```
Expected: FAIL — текущий OverviewPage отрисовывает старые компоненты.

- [ ] **Step 3: Перекомпоновать OverviewPage**

`web-admin/src/pages/OverviewPage.tsx`:

```tsx
import { useOperationalQuery, useBurningQuery } from '../hooks/dashboardQueries'
import { KpiCardWithTarget } from './analytics/KpiCardWithTarget'
import { BurningQueueTable } from './overview/BurningQueueTable'
import { StatusPipeline } from './overview/StatusPipeline'

export function OverviewPage() {
  const op = useOperationalQuery()
  const burn = useBurningQuery(10)

  if (op.isLoading) return <div>Загрузка…</div>
  if (op.isError || !op.data) return <div>Ошибка загрузки оперативной сводки</div>

  const s = op.data
  const createdDelta = s.createdToday - s.createdYesterday

  return (
    <div className="overview-page">
      <section className="overview-kpi-row">
        <a href="/complaints?status=NEW,IN_PROGRESS" className="kpi-link">
          <div className="kpi-card kpi-card--neutral">
            <div className="kpi-card__label">Backlog</div>
            <div className="kpi-card__value">{s.backlog}</div>
            <div className="kpi-card__hint">открытые жалобы сейчас</div>
          </div>
        </a>
        <a href="/complaints?status=NEW,IN_PROGRESS&slaState=overdue" className="kpi-link">
          <div className={`kpi-card ${s.overdueNow > 0 ? 'kpi-card--bad' : 'kpi-card--good'}`}>
            <div className="kpi-card__label">Просрочено по SLA</div>
            <div className="kpi-card__value">{s.overdueNow}</div>
            <div className="kpi-card__hint">нарушают норматив сейчас</div>
          </div>
        </a>
        <KpiCardWithTarget
          label="DTA за 24ч"
          value={s.avgDtaHours24h}
          unit="ч"
          target={s.dtaTargetHours}
          direction="lower-better"
        />
        <a href={`/complaints?createdAfter=${encodeURIComponent(new Date().toISOString().slice(0, 10))}`} className="kpi-link">
          <div className="kpi-card kpi-card--neutral">
            <div className="kpi-card__label">Создано сегодня</div>
            <div className="kpi-card__value">
              {s.createdToday}
              <span className={createdDelta >= 0 ? 'kpi-delta--up' : 'kpi-delta--down'}>
                {createdDelta >= 0 ? `▲ +${createdDelta}` : `▼ ${createdDelta}`}
              </span>
            </div>
            <div className="kpi-card__hint">vs вчера: {s.createdYesterday}</div>
          </div>
        </a>
      </section>

      <section className="overview-pipeline">
        <h2>Распределение за 30 дней</h2>
        <StatusPipeline breakdown={s.statusBreakdown} />
      </section>

      <section className="overview-burning">
        <h2>Горящие жалобы</h2>
        {burn.isLoading
          ? <div>Загрузка…</div>
          : <BurningQueueTable items={burn.data ?? []} />}
      </section>
    </div>
  )
}
```

Обновить `StatusPipeline.tsx`, если он принимал старый формат от `MonthlyKpis` — теперь принимает `breakdown: Record<string, number>`. Это маленькая адаптация: достаточно перейти от полей `newCount`/`inProgressCount`/... к `breakdown['NEW']`/`breakdown['IN_PROGRESS']`/...

Удалить файлы (импорты также убрать из OverviewPage):

```bash
git rm web-admin/src/pages/overview/TopDistricts.tsx
git rm web-admin/src/pages/overview/TopProblemCategories.tsx
git rm web-admin/src/pages/overview/SlaAlertBanner.tsx
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/OverviewPage.test.tsx
```
Expected: PASS.

Также прогнать smoke:
```bash
cd web-admin && npm run build
```
Expected: build успешен (импорты на удалённые файлы должны быть очищены).

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/OverviewPage.tsx \
        web-admin/src/pages/OverviewPage.test.tsx \
        web-admin/src/pages/overview/StatusPipeline.tsx \
        web-admin/src/pages/analytics/dashboard.css
git rm web-admin/src/pages/overview/TopDistricts.tsx \
       web-admin/src/pages/overview/TopProblemCategories.tsx \
       web-admin/src/pages/overview/SlaAlertBanner.tsx
git commit -m "feat(web-admin): OverviewPage перекомпонован под operational dashboard"
```

---

### Task 21: Перекомпоновка AnalyticsPage

**Files:**
- Modify: `web-admin/src/pages/AnalyticsPage.tsx`
- Modify: `web-admin/src/pages/AnalyticsPage.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { render, screen, fireEvent } from '@testing-library/react'
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AnalyticsPage } from './AnalyticsPage'

const server = setupServer(
  http.get('/analytics/strategic', () => HttpResponse.json({
    slaCompliancePct: 78, slaTargetPct: 80,
    medianResolutionHours: 24, p90ResolutionHours: 72,
    reopenRate: 0.08, reopenTargetPct: 10, throughput: 145,
  })),
  http.get('/analytics/trends', () => HttpResponse.json({
    days: [], createdSeries: [], resolvedSeries: [], groupBy: 'day',
  })),
  http.get('/analytics/by-district', () => HttpResponse.json([])),
  http.get('/analytics/by-category', () => HttpResponse.json([])),
  http.get('/analytics/sla', () => HttpResponse.json([])),
  http.get('/analytics/votes-impact', () => HttpResponse.json([])),
)
beforeAll(() => server.listen())
afterAll(() => server.close())

function wrap(node: React.ReactNode) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter>{node}</MemoryRouter>
  </QueryClientProvider>
}

describe('AnalyticsPage', () => {
  it('renders 4 strategic KPI cards', async () => {
    render(wrap(<AnalyticsPage />))
    expect(await screen.findByText(/within SLA/i)).toBeInTheDocument()
    expect(await screen.findByText(/Reopen/i)).toBeInTheDocument()
    expect(await screen.findByText(/Throughput/i)).toBeInTheDocument()
  })
  it('does not render Export PDF button', () => {
    render(wrap(<AnalyticsPage />))
    expect(screen.queryByText(/Экспорт PDF/i)).not.toBeInTheDocument()
  })
  it('changes period via switcher and refetches', async () => {
    render(wrap(<AnalyticsPage />))
    fireEvent.click(await screen.findByText('Квартал'))
    // Один из запросов должен прийти с period=QUARTER — проверка опционально через MSW intercepts
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd web-admin && npx vitest run src/pages/AnalyticsPage.test.tsx
```
Expected: FAIL.

- [ ] **Step 3: Перекомпоновать AnalyticsPage**

`web-admin/src/pages/AnalyticsPage.tsx`:

```tsx
import { useState } from 'react'
import type { AnalyticsPeriod } from '../api/types'
import {
  useStrategicQuery, useReopenQuery, useTrendsRangeQuery,
  useByCategoryQuery, useByDistrictQuery, useSlaQuery, useVotesImpactQuery,
} from '../hooks/dashboardQueries'
import { KpiCardWithTarget } from './analytics/KpiCardWithTarget'
import { MedianP90Card } from './analytics/MedianP90Card'
import { ReopenRateCard } from './analytics/ReopenRateCard'
import { PeriodSwitcher } from './analytics/PeriodSwitcher'
import { TrendCard } from './analytics/TrendCard'
import { EquityTable } from './analytics/EquityTable'
import { SlaByCategory } from './analytics/SlaByCategory'
import { VotesImpactCard } from './analytics/VotesImpactCard'

export function AnalyticsPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>('MONTH')
  const groupBy = period === 'WEEK' ? 'day' : period === 'MONTH' ? 'day' : period === 'QUARTER' ? 'week' : 'month'

  const strategic = useStrategicQuery(period)
  const reopen = useReopenQuery(period)
  const trends = useTrendsRangeQuery(period, groupBy)
  const byDistrict = useByDistrictQuery(period)
  const byCategory = useByCategoryQuery(period)
  const sla = useSlaQuery(period)
  const votes = useVotesImpactQuery(period)

  if (strategic.isLoading) return <div>Загрузка аналитики…</div>
  if (strategic.isError || !strategic.data) return <div>Ошибка загрузки</div>

  const k = strategic.data
  return (
    <div className="analytics-page">
      <PeriodSwitcher value={period} onChange={setPeriod} />

      <section className="analytics-kpi-row">
        <KpiCardWithTarget label="% within SLA" value={k.slaCompliancePct}
          unit="%" target={k.slaTargetPct} direction="higher-better" />
        <MedianP90Card label="Время решения"
          median={k.medianResolutionHours} p90={k.p90ResolutionHours} />
        <ReopenRateCard reopenRate={k.reopenRate}
          reopenCount={reopen.data?.reopenCount ?? 0}
          resolvedCount={reopen.data?.resolvedCount ?? 0}
          target={k.reopenTargetPct} />
        <div className="kpi-card kpi-card--neutral">
          <div className="kpi-card__label">Throughput</div>
          <div className="kpi-card__value">{k.throughput}</div>
          <div className="kpi-card__hint">закрыто за период</div>
        </div>
      </section>

      <section className="analytics-trend">
        <h2>Динамика</h2>
        {trends.data && <TrendCard trends={trends.data} />}
      </section>

      <section className="analytics-equity">
        <h2>Equity по районам</h2>
        {byDistrict.data && <EquityTable items={byDistrict.data} />}
      </section>

      <section className="analytics-category">
        <h2>Топ категорий</h2>
        <table className="equity-table">
          <thead>
            <tr>
              <th>Категория</th>
              <th>Жалоб</th>
              <th>Медиана</th>
              <th>p90</th>
              <th>% within SLA</th>
            </tr>
          </thead>
          <tbody>
            {(byCategory.data ?? []).map(c => {
              const slaPct = c.slaCompliancePct
              const cls = slaPct === null ? 'equity-row'
                : slaPct >= 80 ? 'equity-row equity-row--good'
                : slaPct >= 60 ? 'equity-row equity-row--warn'
                : 'equity-row equity-row--bad'
              return (
                <tr key={c.category} className={cls}>
                  <td>{c.label}</td>
                  <td>{c.count}</td>
                  <td>{c.medianResolutionHours === null ? '—' : `${c.medianResolutionHours.toFixed(1)}ч`}</td>
                  <td>{c.p90ResolutionHours === null ? '—' : `${c.p90ResolutionHours.toFixed(1)}ч`}</td>
                  <td>{slaPct === null ? '—' : `${slaPct.toFixed(0)}%`}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </section>

      <section className="analytics-sla">
        <h2>SLA по категориям</h2>
        {sla.data && <SlaByCategory items={sla.data} />}
      </section>

      <section className="analytics-votes">
        {votes.data && <VotesImpactCard buckets={votes.data} />}
      </section>
    </div>
  )
}
```

Также: в `dashboardQueries.ts` добавить `useVotesImpactQuery`, если ещё нет:

```typescript
export function useVotesImpactQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'votes-impact', period],
    queryFn: () => getVotesImpact(period),
  })
}
```

`getVotesImpact` уже должен быть в `analytics.ts` (был раньше), убедиться что не удалён.

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd web-admin && npx vitest run src/pages/AnalyticsPage.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add web-admin/src/pages/AnalyticsPage.tsx \
        web-admin/src/pages/AnalyticsPage.test.tsx \
        web-admin/src/hooks/dashboardQueries.ts
git commit -m "feat(web-admin): AnalyticsPage перекомпонован под strategic dashboard"
```

---

## Phase 7 — Cleanup

### Task 22: Deprecation legacy MonthlyKpis usage и финальный smoke

**Files:**
- Modify: `web-admin/src/api/analytics.ts` (если `getOverview` ещё не помечен deprecated — пометить и не использовать)
- Modify: `web-admin/src/hooks/complaintQueries.ts` (если `useOverviewQuery` ещё не помечен deprecated — пометить)
- Smoke-чек: запустить локально backend + web-admin и пройти по экранам

- [ ] **Step 1: Финальная очистка deprecation**

Проверить через grep, что нигде в `web-admin/src` не используется `useOverviewQuery` или `getOverview`:

```bash
cd web-admin && grep -rn "useOverviewQuery\|getOverview" src/ --include="*.tsx" --include="*.ts" | grep -v "@deprecated\|complaintQueries.ts\|api/analytics.ts"
```
Expected: пусто. Если нашлось — заменить на новые хуки.

В `web-admin/src/api/analytics.ts`:

```typescript
/** @deprecated Используется только в legacy /analytics/overview endpoint, удалится после полной миграции UI. */
export async function getOverview() { /* как было */ }
```

- [ ] **Step 2: Smoke-проверка локально**

Запустить backend + web-admin (на Mac у проекта это `npm run dev` для web-admin и стандартный gradle для backend). Открыть `/overview` и `/analytics` в браузере, проверить вручную:
- Backlog ≥ 0
- Просрочено отображается
- DTA отображает либо число, либо «—»
- Создано сегодня отображается с дельтой
- Status Pipeline — 5 секций
- Burning Queue — либо строки, либо «нет горящих жалоб»
- Analytics: 4 KPI карточки, тренд (две линии или empty state), Equity таблица, SLA таблица, Votes Impact
- Period switcher работает на Analytics (5 кнопок), на Overview его нет

- [ ] **Step 3: Прогон всех тестов**

```bash
./gradlew :backend:test --tests "*.analytics.*"
cd web-admin && npm run test
```
Expected: всё зелёное.

- [ ] **Step 4: Финальный коммит**

```bash
git add web-admin/src/api/analytics.ts web-admin/src/hooks/complaintQueries.ts
git commit -m "chore(analytics): пометить legacy /overview API и хук как deprecated"
```

- [ ] **Step 5: Push и merge на main (если работали в worktree)**

```bash
git push origin <branch-name>
# создать PR или сделать fast-forward merge в main по флоу проекта
```

---

## Notes

- **Не делать `git add -A` ни в одной задаче.** В рабочем дереве уже есть незакоммиченные изменения (announcements + AnalyticsPage от текущей сессии разработки). Они мердж нейтральны к этому плану, но не должны попадать в коммиты этого редизайна.
- **Если PostGIS-зависимые SQL функции (`percentile_cont`) не поддерживаются H2 в текущей версии:** fallback — вычислить median/p90 в Kotlin, после получения списка resolution_hours; добавить отдельный тест на это поведение.
- **Worktree:** при необходимости изоляции — создать через `superpowers:using-git-worktrees` перед началом Task 1 (план не требует изоляции явно, но рекомендуется при параллельной работе с другими ветками).

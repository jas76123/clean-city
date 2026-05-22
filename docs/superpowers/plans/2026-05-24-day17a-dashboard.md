# Day 17A — Dashboard (Overview + Analytics) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить заглушки на роутах `/overview` и `/analytics` веб-админки рабочими экранами с реальными данными аналитики.

**Architecture:** Бэкенд — обогащаем `/analytics/overview` вложенным объектом `monthlyKpis` (сравнение период-к-периоду) и добавляем новый `GET /analytics/trends` (дневной ряд за 30 дней). Фронт — две страницы (`OverviewPage`, `AnalyticsPage`) на TanStack Query с авто-обновлением, графики рисуем чистым CSS без библиотек, светлая тема в стиле существующей `ComplaintsPage`.

**Tech Stack:** Kotlin/Ktor/Exposed + H2 (тесты) на бэкенде; React 19 + TypeScript + Vite + TanStack Query + Tailwind v4 + Vitest + MSW на фронте.

**Спека:** `docs/superpowers/specs/2026-05-24-day17a-dashboard-design.md`

---

## File Structure

**Бэкенд (создаём/меняем):**
- `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt` — добавить `MonthlyKpis`, `DailyPoint`, `TrendsResponse`; расширить `AnalyticsOverview`.
- `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt` — `monthlyKpis()` helper в `overview()`, новый метод `trends()`.
- `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt` — роут `GET /analytics/trends`.
- `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt` — тесты `monthlyKpis` и `trends`.
- `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt` — тест роута `/trends`.

**Фронт (создаём):**
- `web-admin/src/api/analytics.ts` — новые API-функции (модифицируем).
- `web-admin/src/api/types.ts` — новые типы (модифицируем).
- `web-admin/src/api/analytics.test.ts` — тест API-слоя.
- `web-admin/src/hooks/dashboardQueries.ts` — query-хуки дашборда.
- `web-admin/src/components/dashboard/KpiCard.tsx`, `Sparkline.tsx`, `HBar.tsx` — переиспользуемые компоненты.
- `web-admin/src/pages/overview/` — `SlaAlertBanner.tsx`, `StatusPipeline.tsx`, `DailyBarChart.tsx`, `TopDistricts.tsx`, `TopVotedComplaints.tsx`.
- `web-admin/src/pages/analytics/` — `PeriodSwitcher.tsx`, `TrendCard.tsx`, `SlaByCategory.tsx`, `VotesImpactCard.tsx`.
- `web-admin/src/pages/OverviewPage.tsx`, `AnalyticsPage.tsx` + тесты.
- `web-admin/src/App.tsx` — подключить страницы к роутам (модифицируем).

---

## Команды

- Бэкенд, один тест-класс: `cd ~/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsServiceTest"`
- Бэкенд, все тесты: `./gradlew :backend:test`
- Фронт, один файл: `cd web-admin && npx vitest run src/api/analytics.test.ts`
- Фронт, все тесты: `cd web-admin && npm test`
- Фронт, типы/сборка: `cd web-admin && npm run build`

---

## Task 1: Бэкенд — модели + `monthlyKpis` в overview

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Написать падающий тест**

Добавить в `AnalyticsServiceTest.kt` (после теста `overview counts by status...`):

```kotlin
    @Test
    fun `overview monthlyKpis compares current 30d window with previous`() {
        val author = seedUser()
        // Текущее окно (0-30 дней назад): 2 создано, 1 решена за 5 дней (в пределах 7д)
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(3))
        seedComplaint(
            author, ProblemCategory.ROADS, ComplaintStatus.RESOLVED,
            now.minusDays(10), resolvedAt = now.minusDays(5),
        )
        // Предыдущее окно (30-60 дней назад): 1 создано, 1 решена за 20 дней (вне 7д)
        seedComplaint(
            author, ProblemCategory.GARBAGE, ComplaintStatus.RESOLVED,
            now.minusDays(50), resolvedAt = now.minusDays(40),
        )

        val k = service.overview(now).monthlyKpis
        assertEquals(2, k.total, "в текущем окне создано 2")
        assertEquals(1, k.prevTotal, "в предыдущем окне создано 1")
        assertEquals(100.0, k.resolvedWithin7dPct, "1 из 1 решена в пределах 7д")
        assertEquals(0.0, k.prevResolvedWithin7dPct, "решена за 20д — вне 7д")
        assertNotNull(k.avgResolutionHours)
        assertEquals(120.0, k.avgResolutionHours!!, 1.0, "5 суток = 120ч")
    }

    @Test
    fun `overview monthlyKpis on empty dataset returns zeros and nulls`() {
        val k = service.overview(now).monthlyKpis
        assertEquals(0, k.total)
        assertEquals(0, k.prevTotal)
        assertNull(k.avgResolutionHours)
        assertEquals(0.0, k.resolvedWithin7dPct)
    }
```

- [ ] **Step 2: Запустить тест — убедиться, что не компилируется**

Run: `./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsServiceTest"`
Expected: FAIL — ошибка компиляции `unresolved reference: monthlyKpis`.

- [ ] **Step 3: Добавить модели в `AnalyticsResponse.kt`**

В конец файла `shared/.../AnalyticsResponse.kt` добавить:

```kotlin
@Serializable
data class MonthlyKpis(
    val total: Int,
    val prevTotal: Int,
    val avgResolutionHours: Double?,
    val prevAvgResolutionHours: Double?,
    val resolvedWithin7dPct: Double,
    val prevResolvedWithin7dPct: Double,
)

@Serializable
data class DailyPoint(
    val date: String,
    val created: Int,
    val resolved: Int,
)

@Serializable
data class TrendsResponse(
    val days: List<DailyPoint>,
)
```

В `data class AnalyticsOverview` добавить последним полем:

```kotlin
    val slaBreachCount: Int,
    val monthlyKpis: MonthlyKpis,
```

(добавить запятую после `slaBreachCount` и строку `val monthlyKpis: MonthlyKpis`).

- [ ] **Step 4: Реализовать `monthlyKpis` в `AnalyticsService`**

В `AnalyticsService.kt` добавить импорт `import com.example.cleancity.shared.models.MonthlyKpis` к существующим импортам.

В функцию `overview()` — перед `return AnalyticsOverview(...)` добавить:

```kotlin
        val monthly = monthlyKpis(rows, now)
```

В вызов `AnalyticsOverview(...)` добавить последним аргументом:

```kotlin
            slaBreachCount = slaBreachCount,
            monthlyKpis = monthly
```

Добавить приватный helper (рядом с другими приватными методами, например после `avgResolutionHours`):

```kotlin
    /** KPI за текущие 30 дней и предыдущие 30 дней — для дельт на дашборде. */
    private fun monthlyKpis(rows: List<AnalyticsRepository.Row>, now: OffsetDateTime): MonthlyKpis {
        val curStart = now.minusDays(30)
        val prevStart = now.minusDays(60)

        fun window(from: OffsetDateTime, to: OffsetDateTime): Triple<Int, Double?, Double> {
            val created = rows.count { it.createdAt >= from && it.createdAt < to }
            val resolvedInWindow = rows.filter {
                it.status == ComplaintStatus.RESOLVED && it.resolvedAt != null &&
                    it.resolvedAt >= from && it.resolvedAt < to
            }
            val within7d = resolvedInWindow.count {
                Duration.between(it.createdAt, it.resolvedAt!!).toHours() <= 7 * 24
            }
            val pct = if (resolvedInWindow.isEmpty()) 0.0
            else round1(within7d * 100.0 / resolvedInWindow.size)
            return Triple(created, avgResolutionHours(resolvedInWindow), pct)
        }

        val (curTotal, curAvg, curPct) = window(curStart, now)
        val (prevTotal, prevAvg, prevPct) = window(prevStart, curStart)
        return MonthlyKpis(
            total = curTotal,
            prevTotal = prevTotal,
            avgResolutionHours = curAvg,
            prevAvgResolutionHours = prevAvg,
            resolvedWithin7dPct = curPct,
            prevResolvedWithin7dPct = prevPct,
        )
    }
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsServiceTest"`
Expected: PASS — все тесты `AnalyticsServiceTest` зелёные.

- [ ] **Step 6: Коммит**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt \
  backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt \
  backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): monthlyKpis в overview — сравнение период-к-периоду"
```

---

## Task 2: Бэкенд — метод `trends()`

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Написать падающий тест**

Добавить в `AnalyticsServiceTest.kt`:

```kotlin
    @Test
    fun `trends returns 30 daily points with created and resolved counts`() {
        val author = seedUser()
        // 2 жалобы созданы сегодня, 1 решена сегодня
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now)
        seedComplaint(
            author, ProblemCategory.ROADS, ComplaintStatus.RESOLVED,
            now.minusDays(2), resolvedAt = now,
        )
        // 1 жалоба создана 100 дней назад — вне окна 30 дней
        seedComplaint(author, ProblemCategory.OTHER, ComplaintStatus.NEW, now.minusDays(100))

        val t = service.trends(now)
        assertEquals(30, t.days.size, "ряд за 30 дней")
        val today = t.days.last()
        assertEquals(now.toLocalDate().toString(), today.date)
        assertEquals(2, today.created, "2 жалобы созданы сегодня")
        assertEquals(1, today.resolved, "1 жалоба решена сегодня")
        assertTrue(t.days.all { it.created >= 0 && it.resolved >= 0 })
    }

    @Test
    fun `trends on empty dataset returns 30 zero points`() {
        val t = service.trends(now)
        assertEquals(30, t.days.size)
        assertTrue(t.days.all { it.created == 0 && it.resolved == 0 })
    }
```

- [ ] **Step 2: Запустить тест — убедиться, что не компилируется**

Run: `./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsServiceTest"`
Expected: FAIL — `unresolved reference: trends`.

- [ ] **Step 3: Реализовать `trends()`**

В `AnalyticsService.kt` добавить импорт `import com.example.cleancity.shared.models.DailyPoint` и `import com.example.cleancity.shared.models.TrendsResponse`.

Добавить публичный метод (после `votesImpact(...)`):

```kotlin
    /** Дневной ряд за последние 30 дней: создано/решено по датам (UTC). */
    fun trends(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): TrendsResponse {
        val rows = repo.loadComplaints(periodStart = null)
        val today = now.toLocalDate()
        val days = (29 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DailyPoint(
                date = day.toString(),
                created = rows.count { it.createdAt.toLocalDate() == day },
                resolved = rows.count { it.resolvedAt != null && it.resolvedAt.toLocalDate() == day },
            )
        }
        return TrendsResponse(days)
    }
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsServiceTest"`
Expected: PASS.

- [ ] **Step 5: Коммит**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt \
  backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "feat(analytics): метод trends() — дневной ряд за 30 дней"
```

---

## Task 3: Бэкенд — роут `GET /analytics/trends`

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt`

- [ ] **Step 1: Написать падающий тест**

Добавить в `AnalyticsRoutesTest.kt`:

```kotlin
    @Test
    fun `admin gets 200 with 30 day series on trends`() = testApplication {
        val ctx = initDb()
        appWith()

        val resp = client.get("/analytics/trends") {
            header("Authorization", "Bearer ${bearerFor(ctx.adminId, UserRole.ADMIN)}")
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        val body = resp.bodyAsText()
        assertEquals(true, body.contains("\"days\""), "ответ содержит поле days; body=$body")
    }

    @Test
    fun `guest gets 401 on trends`() = testApplication {
        initDb()
        appWith()

        val resp = client.get("/analytics/trends")
        assertEquals(HttpStatusCode.Unauthorized, resp.status)
    }
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsRoutesTest"`
Expected: FAIL — `admin gets 200...` падает с 404 (роута ещё нет).

- [ ] **Step 3: Добавить роут**

В `AnalyticsRoutes.kt`, внутри `route("/analytics") { ... }`, после блока `get("/votes-impact") { ... }` добавить:

```kotlin
            get("/trends") {
                call.requireAdmin()
                call.respond(service.trends())
            }
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.analytics.AnalyticsRoutesTest"`
Expected: PASS.

- [ ] **Step 5: Прогнать все тесты бэкенда**

Run: `./gradlew :backend:test`
Expected: PASS — весь модуль `backend` зелёный (новый обязательный параметр `monthlyKpis` не ломает существующие тесты, т.к. они проверяют отдельные поля).

- [ ] **Step 6: Коммит**

```bash
git add backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRoutes.kt \
  backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsRoutesTest.kt
git commit -m "feat(analytics): роут GET /analytics/trends (admin-only)"
```

---

## Task 4: Фронт — типы и API-функции аналитики

**Files:**
- Modify: `web-admin/src/api/types.ts`
- Modify: `web-admin/src/api/analytics.ts`
- Test: `web-admin/src/api/analytics.test.ts` (create)

- [ ] **Step 1: Написать падающий тест API-слоя**

Создать `web-admin/src/api/analytics.test.ts`:

```ts
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { getTrends, getSla } from './analytics'

const BASE = 'http://localhost:8081'
let lastSlaUrl = ''

const server = setupServer(
  http.get(`${BASE}/analytics/trends`, () =>
    HttpResponse.json({ days: [{ date: '2026-05-01', created: 3, resolved: 1 }] }),
  ),
  http.get(`${BASE}/analytics/sla`, ({ request }) => {
    lastSlaUrl = request.url
    return HttpResponse.json([])
  }),
)

beforeAll(() => server.listen())
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

describe('analytics API', () => {
  it('getTrends возвращает дневной ряд', async () => {
    const res = await getTrends()
    expect(res.days[0].created).toBe(3)
  })

  it('getSla передаёт period в query-параметрах', async () => {
    await getSla('MONTH')
    expect(lastSlaUrl).toContain('period=MONTH')
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться, что не компилируется**

Run: `cd web-admin && npx vitest run src/api/analytics.test.ts`
Expected: FAIL — `getTrends`/`getSla` не экспортируются из `./analytics`.

- [ ] **Step 3: Добавить типы в `types.ts`**

В `web-admin/src/api/types.ts`, в блок `AnalyticsOverview`, добавить поле `monthlyKpis` и новые интерфейсы. Заменить существующий `interface AnalyticsOverview` на:

```ts
export interface MonthlyKpis {
  total: number
  prevTotal: number
  avgResolutionHours: number | null
  prevAvgResolutionHours: number | null
  resolvedWithin7dPct: number
  prevResolvedWithin7dPct: number
}

export interface AnalyticsOverview {
  total: number
  new: number
  inProgress: number
  resolved: number
  rejected: number
  duplicate: number
  today: number
  week: number
  slaBreachCount: number
  monthlyKpis: MonthlyKpis
}

export type AnalyticsPeriod = 'WEEK' | 'MONTH' | 'ALL'

export interface DailyPoint {
  date: string
  created: number
  resolved: number
}

export interface TrendsResponse {
  days: DailyPoint[]
}

export interface CategoryStat {
  category: ProblemCategory
  label: string
  count: number
  sharePct: number
  avgResolutionHours: number | null
}

export interface DistrictStat {
  district: string
  label: string
  count: number
  newCount: number
  resolvedCount: number
}

export interface SlaStat {
  category: ProblemCategory
  label: string
  slaHours: number
  avgResolutionHours: number | null
  breachPct: number
  resolvedCount: number
}

export interface VotesBucket {
  bucket: string
  count: number
  avgResolutionHours: number | null
}
```

- [ ] **Step 4: Добавить API-функции в `analytics.ts`**

Заменить содержимое `web-admin/src/api/analytics.ts` на:

```ts
import { api } from './client'
import type {
  AnalyticsOverview,
  AnalyticsPeriod,
  CategoryStat,
  DistrictStat,
  SlaStat,
  TrendsResponse,
  VotesBucket,
} from './types'

export async function getOverview(): Promise<AnalyticsOverview> {
  const res = await api.get<AnalyticsOverview>('/analytics/overview')
  return res.data
}

export async function getTrends(): Promise<TrendsResponse> {
  const res = await api.get<TrendsResponse>('/analytics/trends')
  return res.data
}

export async function getByCategory(period: AnalyticsPeriod): Promise<CategoryStat[]> {
  const res = await api.get<CategoryStat[]>('/analytics/by-category', { params: { period } })
  return res.data
}

export async function getByDistrict(period: AnalyticsPeriod): Promise<DistrictStat[]> {
  const res = await api.get<DistrictStat[]>('/analytics/by-district', { params: { period } })
  return res.data
}

export async function getSla(period: AnalyticsPeriod): Promise<SlaStat[]> {
  const res = await api.get<SlaStat[]>('/analytics/sla', { params: { period } })
  return res.data
}

export async function getVotesImpact(period: AnalyticsPeriod): Promise<VotesBucket[]> {
  const res = await api.get<VotesBucket[]>('/analytics/votes-impact', { params: { period } })
  return res.data
}
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/api/analytics.test.ts`
Expected: PASS.

- [ ] **Step 6: Проверить, что типы не сломаны**

Run: `cd web-admin && npm run build`
Expected: сборка проходит без ошибок типов. (`ComplaintsPage.test.tsx` использует объект `overview` без `monthlyKpis` — это тестовый литерал, не типизированный как `AnalyticsOverview`, ошибки не даёт. Если `tsc` всё же ругается на этот файл — добавить `monthlyKpis` в литерал `overview` в `ComplaintsPage.test.tsx`: `monthlyKpis: { total: 0, prevTotal: 0, avgResolutionHours: null, prevAvgResolutionHours: null, resolvedWithin7dPct: 0, prevResolvedWithin7dPct: 0 }`.)

- [ ] **Step 7: Коммит**

```bash
git add web-admin/src/api/types.ts web-admin/src/api/analytics.ts web-admin/src/api/analytics.test.ts
git commit -m "feat(web): типы и API-функции аналитики (trends, by-*, sla, votes)"
```

---

## Task 5: Фронт — query-хуки дашборда

**Files:**
- Create: `web-admin/src/hooks/dashboardQueries.ts`

- [ ] **Step 1: Создать файл хуков**

Создать `web-admin/src/hooks/dashboardQueries.ts`:

```ts
import { useQuery } from '@tanstack/react-query'
import { getTrends, getByDistrict, getSla, getVotesImpact } from '@/api/analytics'
import { listComplaints } from '@/api/complaints'
import type { AnalyticsPeriod, ComplaintFilter } from '@/api/types'

// Дашборд автообновляется раз в минуту — синхронно с таблицей жалоб (Day 16).
const DASHBOARD_REFETCH_MS = 60_000

export function useTrendsQuery() {
  return useQuery({
    queryKey: ['analytics', 'trends'],
    queryFn: getTrends,
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

const TOP_VOTED_FILTER: ComplaintFilter = {
  status: null, slaBreached: false, category: null, district: null, sort: 'votes', page: 0,
}

export function useTopVotedQuery() {
  return useQuery({
    queryKey: ['analytics', 'top-voted'],
    queryFn: () => listComplaints(TOP_VOTED_FILTER),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useByDistrictQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'by-district', period],
    queryFn: () => getByDistrict(period),
  })
}

export function useSlaQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'sla', period],
    queryFn: () => getSla(period),
  })
}

export function useVotesImpactQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'votes-impact', period],
    queryFn: () => getVotesImpact(period),
  })
}
```

- [ ] **Step 2: Проверить сборку**

Run: `cd web-admin && npm run build`
Expected: без ошибок типов.

- [ ] **Step 3: Коммит**

```bash
git add web-admin/src/hooks/dashboardQueries.ts
git commit -m "feat(web): query-хуки дашборда (trends, top-voted, by-district, sla, votes)"
```

---

## Task 6: Фронт — переиспользуемые компоненты (KpiCard, Sparkline, HBar)

**Files:**
- Create: `web-admin/src/components/dashboard/KpiCard.tsx`
- Create: `web-admin/src/components/dashboard/Sparkline.tsx`
- Create: `web-admin/src/components/dashboard/HBar.tsx`
- Test: `web-admin/src/components/dashboard/KpiCard.test.tsx` (create)

- [ ] **Step 1: Написать падающий тест `KpiCard`**

Создать `web-admin/src/components/dashboard/KpiCard.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { KpiCard } from './KpiCard'

describe('KpiCard', () => {
  it('показывает значение и подпись', () => {
    render(<KpiCard label="Жалоб за месяц" value="247" />)
    expect(screen.getByText('247')).toBeInTheDocument()
    expect(screen.getByText('Жалоб за месяц')).toBeInTheDocument()
  })

  it('считает рост в процентах при наличии previous', () => {
    render(<KpiCard label="x" value="120" current={120} previous={100} />)
    expect(screen.getByText(/\+20%/)).toBeInTheDocument()
  })

  it('не показывает дельту если previous отсутствует или равен 0', () => {
    render(<KpiCard label="x" value="8" current={8} previous={0} />)
    expect(screen.queryByText(/%/)).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `cd web-admin && npx vitest run src/components/dashboard/KpiCard.test.tsx`
Expected: FAIL — модуль `./KpiCard` не найден.

- [ ] **Step 3: Создать `KpiCard.tsx`**

Создать `web-admin/src/components/dashboard/KpiCard.tsx`:

```tsx
interface KpiCardProps {
  label: string
  value: string
  /** Текущее и предыдущее числовое значение — для расчёта дельты. */
  current?: number | null
  previous?: number | null
  /** true → снижение показателя считается хорошим (зелёным). Напр. время решения. */
  lowerIsBetter?: boolean
}

export function KpiCard({ label, value, current, previous, lowerIsBetter }: KpiCardProps) {
  const delta =
    current != null && previous != null && previous !== 0
      ? Math.round(((current - previous) / previous) * 100)
      : null
  const good = delta == null ? false : lowerIsBetter ? delta < 0 : delta > 0
  const deltaClass = good ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'

  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="flex items-start justify-between">
        <span className="text-2xl font-semibold text-slate-900">{value}</span>
        {delta != null && (
          <span className={`rounded-full px-2 py-0.5 text-xs font-semibold ${deltaClass}`}>
            {delta > 0 ? '+' : ''}
            {delta}%
          </span>
        )}
      </div>
      <div className="mt-1 text-xs text-slate-500">{label}</div>
    </div>
  )
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `cd web-admin && npx vitest run src/components/dashboard/KpiCard.test.tsx`
Expected: PASS.

- [ ] **Step 5: Создать `Sparkline.tsx`**

Создать `web-admin/src/components/dashboard/Sparkline.tsx`:

```tsx
interface SparklineProps {
  /** Серия значений. Последний столбик подсвечивается. */
  values: number[]
}

export function Sparkline({ values }: SparklineProps) {
  const max = Math.max(1, ...values)
  return (
    <div className="mt-2 flex h-10 items-end gap-1">
      {values.map((v, i) => (
        <div
          key={i}
          className={`flex-1 rounded-sm ${i === values.length - 1 ? 'bg-emerald-500' : 'bg-emerald-200'}`}
          style={{ height: `${Math.max(4, (v / max) * 100)}%` }}
        />
      ))}
    </div>
  )
}
```

- [ ] **Step 6: Создать `HBar.tsx`**

Создать `web-admin/src/components/dashboard/HBar.tsx`:

```tsx
interface HBarProps {
  label: string
  /** Доля заполнения 0..1. */
  fraction: number
  /** Число справа. */
  value: number | string
  /** Tailwind-класс цвета полосы, напр. 'bg-red-500'. */
  colorClass?: string
}

export function HBar({ label, fraction, value, colorClass = 'bg-emerald-500' }: HBarProps) {
  const pct = Math.max(2, Math.min(100, fraction * 100))
  return (
    <div className="flex items-center gap-3 py-1">
      <span className="w-28 shrink-0 truncate text-xs text-slate-600">{label}</span>
      <div className="h-2 flex-1 rounded bg-slate-100">
        <div className={`h-full rounded ${colorClass}`} style={{ width: `${pct}%` }} />
      </div>
      <span className="w-10 shrink-0 text-right text-xs font-semibold text-slate-700">{value}</span>
    </div>
  )
}
```

- [ ] **Step 7: Прогнать тесты + сборку**

Run: `cd web-admin && npx vitest run src/components/dashboard/KpiCard.test.tsx && npm run build`
Expected: тест PASS, сборка без ошибок.

- [ ] **Step 8: Коммит**

```bash
git add web-admin/src/components/dashboard/
git commit -m "feat(web): переиспользуемые компоненты дашборда (KpiCard, Sparkline, HBar)"
```

---

## Task 7: Фронт — OverviewPage и её секции

**Files:**
- Create: `web-admin/src/pages/overview/SlaAlertBanner.tsx`
- Create: `web-admin/src/pages/overview/StatusPipeline.tsx`
- Create: `web-admin/src/pages/overview/DailyBarChart.tsx`
- Create: `web-admin/src/pages/overview/TopDistricts.tsx`
- Create: `web-admin/src/pages/overview/TopVotedComplaints.tsx`
- Create: `web-admin/src/pages/OverviewPage.tsx`
- Test: `web-admin/src/pages/OverviewPage.test.tsx`
- Modify: `web-admin/src/App.tsx`

- [ ] **Step 1: Создать `SlaAlertBanner.tsx`**

```tsx
interface SlaAlertBannerProps {
  count: number
}

/** Красный баннер вверху Overview. Рендерится только при count > 0 (проверка у вызывающего). */
export function SlaAlertBanner({ count }: SlaAlertBannerProps) {
  return (
    <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      <strong>{count}</strong>{' '}
      {count === 1 ? 'жалоба превысила' : 'жалоб превысили'} норматив SLA — требуется внимание.
    </div>
  )
}
```

- [ ] **Step 2: Создать `StatusPipeline.tsx`**

```tsx
interface StatusPipelineProps {
  newCount: number
  inProgress: number
  resolved: number
}

const STAGES = [
  { key: 'new', label: 'В обработке', color: 'text-amber-600' },
  { key: 'inProgress', label: 'В работе', color: 'text-blue-600' },
  { key: 'resolved', label: 'Решено', color: 'text-emerald-600' },
] as const

export function StatusPipeline({ newCount, inProgress, resolved }: StatusPipelineProps) {
  const values = { new: newCount, inProgress, resolved }
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Распределение по статусам</div>
      <div className="grid grid-cols-3 gap-4">
        {STAGES.map((s) => (
          <div key={s.key} className="text-center">
            <div className={`text-2xl font-semibold ${s.color}`}>{values[s.key]}</div>
            <div className="text-xs text-slate-500">{s.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 3: Создать `DailyBarChart.tsx`**

```tsx
import type { DailyPoint } from '@/api/types'

interface DailyBarChartProps {
  days: DailyPoint[]
}

/** CSS-график за 30 дней: пара столбиков (создано / решено) на день. */
export function DailyBarChart({ days }: DailyBarChartProps) {
  const max = Math.max(1, ...days.map((d) => Math.max(d.created, d.resolved)))
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-1 flex items-center justify-between">
        <span className="text-sm font-medium text-slate-700">Активность по дням</span>
        <span className="flex gap-3 text-xs text-slate-500">
          <span className="flex items-center gap-1">
            <span className="inline-block h-2 w-2 rounded-sm bg-sky-400" /> создано
          </span>
          <span className="flex items-center gap-1">
            <span className="inline-block h-2 w-2 rounded-sm bg-emerald-500" /> решено
          </span>
        </span>
      </div>
      {days.length === 0 ? (
        <div className="py-8 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <div className="flex h-32 items-end gap-[3px]">
          {days.map((d) => (
            <div key={d.date} className="flex flex-1 items-end justify-center gap-[1px]" title={d.date}>
              <div
                className="w-1/2 rounded-sm bg-sky-400"
                style={{ height: `${(d.created / max) * 100}%` }}
              />
              <div
                className="w-1/2 rounded-sm bg-emerald-500"
                style={{ height: `${(d.resolved / max) * 100}%` }}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Создать `TopDistricts.tsx`**

```tsx
import type { DistrictStat } from '@/api/types'
import { HBar } from '@/components/dashboard/HBar'

interface TopDistrictsProps {
  stats: DistrictStat[]
}

const COLORS = ['bg-red-500', 'bg-amber-500', 'bg-sky-500', 'bg-emerald-500']

export function TopDistricts({ stats }: TopDistrictsProps) {
  const top = stats.slice(0, 4)
  const max = Math.max(1, ...top.map((s) => s.count))
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Топ районов по жалобам</div>
      {top.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        top.map((s, i) => (
          <HBar
            key={s.district}
            label={s.label}
            fraction={s.count / max}
            value={s.count}
            colorClass={COLORS[i] ?? 'bg-slate-400'}
          />
        ))
      )}
    </div>
  )
}
```

- [ ] **Step 5: Создать `TopVotedComplaints.tsx`**

```tsx
import type { Complaint } from '@/api/types'

interface TopVotedComplaintsProps {
  items: Complaint[]
}

export function TopVotedComplaints({ items }: TopVotedComplaintsProps) {
  const top = items.slice(0, 5)
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Топ-5 по голосам жителей</div>
      {top.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <ol className="flex flex-col gap-2">
          {top.map((c, i) => (
            <li key={c.id} className="flex items-center gap-3 border-b border-slate-100 pb-2 last:border-0">
              <span className="w-5 text-sm font-semibold text-emerald-600">{i + 1}</span>
              <span className="flex-1 truncate text-xs text-slate-700">{c.title}</span>
              <span className="text-xs font-semibold text-slate-500">{c.votesCount} ▲</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
```

- [ ] **Step 6: Создать `OverviewPage.tsx`**

```tsx
import { useOverviewQuery } from '@/hooks/complaintQueries'
import { useTrendsQuery, useTopVotedQuery, useByDistrictQuery } from '@/hooks/dashboardQueries'
import { KpiCard } from '@/components/dashboard/KpiCard'
import { SlaAlertBanner } from './overview/SlaAlertBanner'
import { StatusPipeline } from './overview/StatusPipeline'
import { DailyBarChart } from './overview/DailyBarChart'
import { TopDistricts } from './overview/TopDistricts'
import { TopVotedComplaints } from './overview/TopVotedComplaints'

function fmtHours(h: number | null): string {
  return h == null ? '—' : `${Math.round(h)} ч`
}

export function OverviewPage() {
  const overview = useOverviewQuery()
  const trends = useTrendsQuery()
  const topVoted = useTopVotedQuery()
  const districts = useByDistrictQuery('MONTH')

  if (overview.isError) {
    return (
      <div className="p-6 text-center text-sm text-red-600">
        Не удалось загрузить дашборд.{' '}
        <button onClick={() => overview.refetch()} className="underline">
          Повторить
        </button>
      </div>
    )
  }
  if (overview.isLoading || !overview.data) {
    return <div className="p-6 text-center text-sm text-slate-400">Загрузка…</div>
  }

  const o = overview.data
  const k = o.monthlyKpis

  return (
    <div className="flex flex-col gap-4 p-4">
      {o.slaBreachCount > 0 && <SlaAlertBanner count={o.slaBreachCount} />}

      <div className="grid grid-cols-4 gap-4">
        <KpiCard label="Жалоб за месяц" value={String(k.total)} current={k.total} previous={k.prevTotal} />
        <KpiCard
          label="Среднее время решения"
          value={fmtHours(k.avgResolutionHours)}
          current={k.avgResolutionHours}
          previous={k.prevAvgResolutionHours}
          lowerIsBetter
        />
        <KpiCard
          label="Решено за 7 дней"
          value={`${Math.round(k.resolvedWithin7dPct)}%`}
          current={k.resolvedWithin7dPct}
          previous={k.prevResolvedWithin7dPct}
        />
        <KpiCard label="SLA-просрочки" value={String(o.slaBreachCount)} />
      </div>

      <StatusPipeline newCount={o.new} inProgress={o.inProgress} resolved={o.resolved} />

      <DailyBarChart days={trends.data?.days ?? []} />

      <div className="grid grid-cols-2 gap-4">
        <TopDistricts stats={districts.data ?? []} />
        <TopVotedComplaints items={topVoted.data?.items ?? []} />
      </div>
    </div>
  )
}
```

- [ ] **Step 7: Подключить роут в `App.tsx`**

В `web-admin/src/App.tsx` добавить импорт рядом с другими импортами страниц:

```tsx
import { OverviewPage } from '@/pages/OverviewPage'
```

Заменить строку:

```tsx
          <Route path="/overview" element={<SectionPlaceholder title="Обзор" />} />
```

на:

```tsx
          <Route path="/overview" element={<OverviewPage />} />
```

- [ ] **Step 8: Написать тест `OverviewPage.test.tsx`**

Создать `web-admin/src/pages/OverviewPage.test.tsx`:

```tsx
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { OverviewPage } from './OverviewPage'
import type { AnalyticsOverview } from '@/api/types'

const BASE = 'http://localhost:8081'

function overview(slaBreachCount: number): AnalyticsOverview {
  return {
    total: 50, new: 10, inProgress: 15, resolved: 25, rejected: 0, duplicate: 0,
    today: 2, week: 8, slaBreachCount,
    monthlyKpis: {
      total: 50, prevTotal: 40, avgResolutionHours: 41, prevAvgResolutionHours: 50,
      resolvedWithin7dPct: 78, prevResolvedWithin7dPct: 70,
    },
  }
}

function server(slaBreachCount: number) {
  return setupServer(
    http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview(slaBreachCount))),
    http.get(`${BASE}/analytics/trends`, () => HttpResponse.json({ days: [] })),
    http.get(`${BASE}/analytics/by-district`, () => HttpResponse.json([])),
    http.get(`${BASE}/complaints`, () =>
      HttpResponse.json({ items: [], page: 0, size: 20, total: 0 }),
    ),
  )
}

const srv = server(8)
beforeAll(() => srv.listen())
afterEach(() => srv.resetHandlers())
afterAll(() => srv.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <OverviewPage />
    </QueryClientProvider>,
  )
}

describe('OverviewPage', () => {
  it('показывает KPI и пайплайн статусов из overview', async () => {
    renderPage()
    expect(await screen.findByText('Жалоб за месяц')).toBeInTheDocument()
    expect(screen.getByText('Распределение по статусам')).toBeInTheDocument()
    expect(screen.getByText('+25%')).toBeInTheDocument() // 50 vs 40
  })

  it('показывает SLA-баннер когда slaBreachCount > 0', async () => {
    renderPage()
    expect(await screen.findByText(/превысили норматив SLA/)).toBeInTheDocument()
  })

  it('скрывает SLA-баннер когда slaBreachCount = 0', async () => {
    srv.use(http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview(0))))
    renderPage()
    expect(await screen.findByText('Жалоб за месяц')).toBeInTheDocument()
    expect(screen.queryByText(/превысили норматив SLA/)).not.toBeInTheDocument()
  })
})
```

- [ ] **Step 9: Запустить тесты и сборку**

Run: `cd web-admin && npx vitest run src/pages/OverviewPage.test.tsx && npm run build`
Expected: 3 теста PASS, сборка без ошибок.

- [ ] **Step 10: Коммит**

```bash
git add web-admin/src/pages/OverviewPage.tsx web-admin/src/pages/OverviewPage.test.tsx \
  web-admin/src/pages/overview/ web-admin/src/App.tsx
git commit -m "feat(web): OverviewPage — KPI, пайплайн, график по дням, топы"
```

---

## Task 8: Фронт — AnalyticsPage и её секции

**Files:**
- Create: `web-admin/src/pages/analytics/PeriodSwitcher.tsx`
- Create: `web-admin/src/pages/analytics/TrendCard.tsx`
- Create: `web-admin/src/pages/analytics/SlaByCategory.tsx`
- Create: `web-admin/src/pages/analytics/VotesImpactCard.tsx`
- Create: `web-admin/src/pages/AnalyticsPage.tsx`
- Test: `web-admin/src/pages/AnalyticsPage.test.tsx`
- Modify: `web-admin/src/App.tsx`

- [ ] **Step 1: Создать `PeriodSwitcher.tsx`**

```tsx
import type { AnalyticsPeriod } from '@/api/types'

interface PeriodSwitcherProps {
  value: AnalyticsPeriod
  onChange: (p: AnalyticsPeriod) => void
}

const OPTIONS: { value: AnalyticsPeriod; label: string }[] = [
  { value: 'WEEK', label: 'Неделя' },
  { value: 'MONTH', label: 'Месяц' },
  { value: 'ALL', label: 'Всё время' },
]

export function PeriodSwitcher({ value, onChange }: PeriodSwitcherProps) {
  return (
    <div className="inline-flex rounded-lg border bg-white p-0.5">
      {OPTIONS.map((o) => (
        <button
          key={o.value}
          onClick={() => onChange(o.value)}
          className={`rounded-md px-3 py-1 text-xs font-medium ${
            value === o.value ? 'bg-emerald-600 text-white' : 'text-slate-600'
          }`}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}
```

- [ ] **Step 2: Создать `TrendCard.tsx`**

```tsx
import { Sparkline } from '@/components/dashboard/Sparkline'

interface TrendCardProps {
  title: string
  value: string
  sub: string
  /** Серия для спарклайна. Если не задана — спарклайн не рисуется. */
  series?: number[]
}

export function TrendCard({ title, value, sub, series }: TrendCardProps) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="text-xs text-slate-500">{title}</div>
      <div className="mt-1 text-2xl font-semibold text-slate-900">{value}</div>
      <div className="text-xs text-slate-400">{sub}</div>
      {series && series.length > 0 && <Sparkline values={series} />}
    </div>
  )
}
```

- [ ] **Step 3: Создать `SlaByCategory.tsx`**

```tsx
import type { SlaStat } from '@/api/types'

interface SlaByCategoryProps {
  stats: SlaStat[]
}

/** Цвет строки по доле нарушений SLA. */
function tone(breachPct: number): { row: string; text: string; label: string } {
  if (breachPct >= 50) return { row: 'bg-red-50 border-red-200', text: 'text-red-600', label: 'Превышение' }
  if (breachPct >= 20) return { row: 'bg-amber-50 border-amber-200', text: 'text-amber-600', label: 'Близко к лимиту' }
  return { row: 'bg-emerald-50 border-emerald-200', text: 'text-emerald-600', label: 'В норме' }
}

export function SlaByCategory({ stats }: SlaByCategoryProps) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">SLA по категориям</div>
      {stats.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <div className="flex flex-col gap-2">
          {stats.map((s) => {
            const t = tone(s.breachPct)
            return (
              <div
                key={s.category}
                className={`flex items-center justify-between rounded-lg border px-3 py-2 ${t.row}`}
              >
                <div>
                  <div className="text-xs font-medium text-slate-700">{s.label}</div>
                  <div className="text-[10px] text-slate-400">Норматив: {s.slaHours} ч</div>
                </div>
                <div className="text-right">
                  <div className={`text-sm font-semibold ${t.text}`}>
                    {s.avgResolutionHours == null ? '—' : `${Math.round(s.avgResolutionHours)} ч`}
                  </div>
                  <div className={`text-[10px] ${t.text}`}>{t.label}</div>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Создать `VotesImpactCard.tsx`**

```tsx
import type { VotesBucket } from '@/api/types'
import { HBar } from '@/components/dashboard/HBar'

interface VotesImpactCardProps {
  buckets: VotesBucket[]
}

const BUCKET_LABEL: Record<string, string> = {
  '50+': 'Жалобы с 50+ голосов',
  '10-49': 'Жалобы с 10–49 голосов',
  '1-9': 'Жалобы с 1–9 голосов',
  '0': 'Жалобы без голосов',
}

export function VotesImpactCard({ buckets }: VotesImpactCardProps) {
  const withData = buckets.filter((b) => b.avgResolutionHours != null)
  const max = Math.max(1, ...withData.map((b) => b.avgResolutionHours as number))
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-1 text-sm font-medium text-slate-700">Голоса как сигнал приоритизации</div>
      <div className="mb-3 text-xs text-slate-400">Среднее время решения по числу голосов</div>
      {withData.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <>
          {withData.map((b) => (
            <HBar
              key={b.bucket}
              label={BUCKET_LABEL[b.bucket] ?? b.bucket}
              fraction={(b.avgResolutionHours as number) / max}
              value={`${Math.round(b.avgResolutionHours as number)} ч`}
              colorClass="bg-sky-500"
            />
          ))}
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-xs text-slate-600">
            💡 <strong className="text-emerald-700">Голосование жителей работает.</strong>{' '}
            Социальный сигнал помогает выявлять важные проблемы быстрее формального SLA.
          </div>
        </>
      )}
    </div>
  )
}
```

- [ ] **Step 5: Создать `AnalyticsPage.tsx`**

```tsx
import { useState } from 'react'
import type { AnalyticsPeriod } from '@/api/types'
import { useOverviewQuery } from '@/hooks/complaintQueries'
import { useTrendsQuery, useSlaQuery, useVotesImpactQuery } from '@/hooks/dashboardQueries'
import { PeriodSwitcher } from './analytics/PeriodSwitcher'
import { TrendCard } from './analytics/TrendCard'
import { SlaByCategory } from './analytics/SlaByCategory'
import { VotesImpactCard } from './analytics/VotesImpactCard'

export function AnalyticsPage() {
  const [period, setPeriod] = useState<AnalyticsPeriod>('MONTH')
  const overview = useOverviewQuery()
  const trends = useTrendsQuery()
  const sla = useSlaQuery(period)
  const votes = useVotesImpactQuery(period)

  const k = overview.data?.monthlyKpis
  const created = trends.data?.days.map((d) => d.created) ?? []
  const resolved = trends.data?.days.map((d) => d.resolved) ?? []
  const createdSum = created.reduce((a, b) => a + b, 0)

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-base font-semibold text-slate-900">Аналитика</h1>
          <p className="text-xs text-slate-400">Тренды по жалобам, SLA и голосам жителей</p>
        </div>
        <div className="flex items-center gap-3">
          <PeriodSwitcher value={period} onChange={setPeriod} />
          <button
            disabled
            title="Скоро — экспорт PDF появится отдельно"
            className="cursor-not-allowed rounded-lg border bg-white px-3 py-1.5 text-xs font-medium text-slate-300"
          >
            Экспорт PDF
          </button>
        </div>
      </div>

      <div className="grid grid-cols-3 gap-4">
        <TrendCard
          title="Решено за 7 дней"
          value={k ? `${Math.round(k.resolvedWithin7dPct)}%` : '—'}
          sub="доля жалоб, решённых в течение недели"
          series={resolved}
        />
        <TrendCard
          title="Среднее время решения"
          value={k?.avgResolutionHours == null ? '—' : `${Math.round(k.avgResolutionHours)} ч`}
          sub="за последние 30 дней"
        />
        <TrendCard
          title="Жалоб создано"
          value={String(createdSum)}
          sub="за последние 30 дней"
          series={created}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        <SlaByCategory stats={sla.data ?? []} />
        <VotesImpactCard buckets={votes.data ?? []} />
      </div>
    </div>
  )
}
```

- [ ] **Step 6: Подключить роут в `App.tsx`**

В `web-admin/src/App.tsx` добавить импорт:

```tsx
import { AnalyticsPage } from '@/pages/AnalyticsPage'
```

Заменить строку:

```tsx
          <Route path="/analytics" element={<SectionPlaceholder title="Аналитика" />} />
```

на:

```tsx
          <Route path="/analytics" element={<AnalyticsPage />} />
```

- [ ] **Step 7: Написать тест `AnalyticsPage.test.tsx`**

Создать `web-admin/src/pages/AnalyticsPage.test.tsx`:

```tsx
import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { setupServer } from 'msw/node'
import { http, HttpResponse } from 'msw'
import { AnalyticsPage } from './AnalyticsPage'
import type { AnalyticsOverview, SlaStat } from '@/api/types'

const BASE = 'http://localhost:8081'

const overview: AnalyticsOverview = {
  total: 50, new: 10, inProgress: 15, resolved: 25, rejected: 0, duplicate: 0,
  today: 2, week: 8, slaBreachCount: 3,
  monthlyKpis: {
    total: 50, prevTotal: 40, avgResolutionHours: 41, prevAvgResolutionHours: 50,
    resolvedWithin7dPct: 78, prevResolvedWithin7dPct: 70,
  },
}

const slaWeek: SlaStat[] = [
  { category: 'GARBAGE', label: 'Мусор', slaHours: 24, avgResolutionHours: 38, breachPct: 60, resolvedCount: 5 },
]
const slaMonth: SlaStat[] = [
  { category: 'ROADS', label: 'Дороги', slaHours: 72, avgResolutionHours: 50, breachPct: 0, resolvedCount: 9 },
]

let slaPeriods: string[] = []

const srv = setupServer(
  http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview)),
  http.get(`${BASE}/analytics/trends`, () => HttpResponse.json({ days: [] })),
  http.get(`${BASE}/analytics/votes-impact`, () => HttpResponse.json([])),
  http.get(`${BASE}/analytics/sla`, ({ request }) => {
    const p = new URL(request.url).searchParams.get('period') ?? ''
    slaPeriods.push(p)
    return HttpResponse.json(p === 'WEEK' ? slaWeek : slaMonth)
  }),
)

beforeAll(() => srv.listen())
afterEach(() => {
  srv.resetHandlers()
  slaPeriods = []
})
afterAll(() => srv.close())

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <AnalyticsPage />
    </QueryClientProvider>,
  )
}

describe('AnalyticsPage', () => {
  it('рендерит SLA-список и trend-карты для периода по умолчанию (MONTH)', async () => {
    renderPage()
    expect(await screen.findByText('Дороги')).toBeInTheDocument()
    expect(screen.getByText('SLA по категориям')).toBeInTheDocument()
  })

  it('смена периода перезапрашивает данные с новым period', async () => {
    renderPage()
    await screen.findByText('Дороги')
    await userEvent.click(screen.getByRole('button', { name: 'Неделя' }))
    await waitFor(() => expect(screen.getByText('Мусор')).toBeInTheDocument())
    expect(slaPeriods).toContain('WEEK')
  })
})
```

- [ ] **Step 8: Запустить тесты и сборку**

Run: `cd web-admin && npx vitest run src/pages/AnalyticsPage.test.tsx && npm run build`
Expected: 2 теста PASS, сборка без ошибок.

- [ ] **Step 9: Коммит**

```bash
git add web-admin/src/pages/AnalyticsPage.tsx web-admin/src/pages/AnalyticsPage.test.tsx \
  web-admin/src/pages/analytics/ web-admin/src/App.tsx
git commit -m "feat(web): AnalyticsPage — trend-карты, SLA по категориям, влияние голосов"
```

---

## Task 9: Финальная проверка и закрытие Day 17A

**Files:**
- Modify: `docs/PLAN.md`

- [ ] **Step 1: Прогнать все тесты бэкенда**

Run: `cd ~/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test`
Expected: PASS — весь модуль `backend` зелёный.

- [ ] **Step 2: Прогнать все тесты фронта**

Run: `cd web-admin && npm test`
Expected: PASS — все тесты зелёные (существующие 20+ плюс новые: `analytics`, `KpiCard`, `OverviewPage` ×3, `AnalyticsPage` ×2).

- [ ] **Step 3: Проверить сборку фронта**

Run: `cd web-admin && npm run build`
Expected: сборка без ошибок типов.

- [ ] **Step 4: Ручная проверка на dev-сиде**

Запустить backend и dev-сид (V99), затем `cd web-admin && npm run dev`. Залогиниться
как `admin@cleancity.dev` / `Admin12345!`. Проверить:
- `/overview` — KPI заполнены, виден пайплайн статусов, график по дням, топ районов и топ-5 по голосам;
- SLA-баннер виден, если в сиде есть просроченные жалобы;
- `/analytics` — trend-карты заполнены, переключатель периода (Неделя/Месяц/Всё) меняет данные SLA и влияния голосов;
- кнопка «Экспорт PDF» — неактивна с тултипом.

- [ ] **Step 5: Отметить Day 17A в `docs/PLAN.md`**

В `docs/PLAN.md` под заголовком `### День 17 (24.05) — Web: объявления + аналитика + настройки` добавить строку сразу после заголовка:

```markdown
> **17A (Дашборд) закрыт.** OverviewPage + AnalyticsPage реализованы.
> Бэкенд: monthlyKpis в /analytics/overview + новый GET /analytics/trends.
> Дизайн/план: docs/superpowers/specs/2026-05-24-day17a-dashboard-design.md,
> docs/superpowers/plans/2026-05-24-day17a-dashboard.md.
> Осталось по Дню 17: 17B (объявления), 17C (настройки/команда), 17D (PDF-отчёт).
```

И отметить выполненными пункты Overview/Analytics в чек-листе Дня 17:

```markdown
- [x] `OverviewPage` (главный экран после логина):
  - [x] Карточки KPI (за месяц / среднее время / решено-7д / SLA-просрочки)
  - [x] **SLA-алерт-баннер** наверху если `sla_breach_count > 0`
  - [x] График по дням (CSS, без recharts)
  - [x] Топ районов
  - [x] SLA по категориям (на AnalyticsPage)
  - [x] Топ-5 по голосам жителей
  - [ ] Карта с pins всех активных жалоб — отложено (нужен ключ Yandex JS API)
- [x] `AnalyticsPage` — trend-карты + SLA + влияние голосов + переключатель периода
```

- [ ] **Step 6: Коммит**

```bash
git add docs/PLAN.md
git commit -m "docs: закрыть Day 17A (дашборд) в PLAN.md"
```

---

## Self-Review

**Spec coverage:**
- Секция A спеки (модели, `/trends`, `monthlyKpis`, тесты) → Tasks 1–3. ✓
- Секция B (OverviewPage: баннер, KPI, пайплайн, график, топ районов, топ-5) → Task 7. ✓
- Секция C (AnalyticsPage: переключатель периода, trend-карты, SLA, влияние голосов, disabled-кнопка PDF) → Task 8. ✓
- Секция D (компоненты `KpiCard`/`Sparkline`/`HBar`, секционные компоненты, API-слой, хуки, тесты) → Tasks 4–8. ✓
- Definition of Done (тесты бэка/фронта, build, ручная проверка, PLAN.md) → Task 9. ✓
- Вне объёма (карта, активные юзеры, PDF, `getByDistrict` на Analytics) — карта и PDF явно помечены отложенными; «активные юзеры» не реализуются; `getByDistrict` API-функция создаётся (Секция D спеки требует полный API-слой), но на AnalyticsPage не вызывается. ✓

**Хуки:** спека упоминала `useOverviewData.ts` / `useAnalyticsData.ts` как обёртки. План использует более простой подход — отдельные хуки в `dashboardQueries.ts` плюс существующий `useOverviewQuery`, а страницы комбинируют их напрямую (как `ComplaintsPage` комбинирует `useComplaintsQuery`/`useOverviewQuery`). Это соответствует сложившемуся паттерну кодовой базы и устраняет лишний слой. Отклонение осознанное.

**Placeholder scan:** плейсхолдеров нет — весь код приведён целиком.

**Type consistency:** `MonthlyKpis`/`DailyPoint`/`TrendsResponse` — имена и поля идентичны в Kotlin (Task 1) и TS (Task 4). `AnalyticsPeriod` = `'WEEK' | 'MONTH' | 'ALL'` совпадает с Kotlin-enum. `getSla`/`getTrends`/`useSlaQuery` и т.д. — имена согласованы между Tasks 4, 5, 7, 8. Компоненты `KpiCard`/`Sparkline`/`HBar` — пропсы, объявленные в Task 6, используются с теми же именами в Tasks 7–8.

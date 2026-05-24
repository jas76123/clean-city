# Status Pipeline за месяц + 5 статусов — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Привести блок «Распределение по статусам» на странице Обзор в окно 30 дней (как KPI «Жалоб за месяц») и показать все 5 статусов жалоб вместо 3.

**Architecture:** Расширяем `shared/MonthlyKpis` пятью новыми полями (status-counts за окно), бэкенд их считает, фронтенд использует. Top-level `AnalyticsOverview.new/inProgress/...` (всё время) не трогаем — продолжают питать ComplaintFilters. `StatusPipeline.tsx` полностью переписывается под 5 стейджей.

**Tech Stack:** Kotlin (Ktor + kotlinx.serialization shared), JUnit для бэкенда, React + TypeScript + Vitest + MSW для фронтенда.

**Spec:** `docs/superpowers/specs/2026-05-24-status-pipeline-monthly-design.md`

**Working directories:**
- Backend Gradle root: `~/Desktop/Myapp/cleancity-kmp/`
- Frontend npm root: `~/Desktop/Myapp/cleancity-kmp/web-admin/`
- Git repo: `~/Desktop/Myapp/cleancity-kmp/`

**Compat note:** Мобильный KMP-клиент использует `Json { ignoreUnknownKeys = true }` (`composeApp/.../ApiClient.kt:50`), поэтому добавление полей в `MonthlyKpis` обратносовместимо — старые сборки клиента не упадут при чтении новой JSON-схемы.

---

## File Structure

- **Modify:** `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt` — +5 полей в `data class MonthlyKpis`.
- **Modify:** `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt` — посчитать `byStatus` за окно в `monthlyKpis()`.
- **Modify:** `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt` — новый тест на status breakdown + расширить 2 существующих.
- **Modify:** `web-admin/src/api/types.ts` — +5 полей в `interface MonthlyKpis`.
- **Modify:** `web-admin/src/pages/overview/StatusPipeline.tsx` — полная замена: новые пропсы, 5 стейджей, grid-cols-5, новый заголовок.
- **Modify:** `web-admin/src/pages/OverviewPage.tsx` — передать `k.newCount, k.inProgressCount, ...` вместо `o.new, o.inProgress, o.resolved`.
- **Modify:** `web-admin/src/pages/OverviewPage.test.tsx` — обновить мок `overview()` (+5 полей), новые assertions на 5 статусов.

---

## Task 1: Падающий тест бэкенда на status breakdown

Сначала пишем тест, который не компилируется без новых полей. Это даёт сигнал зелёный при импленте.

**Files:**
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Добавить новый @Test**

В `AnalyticsServiceTest.kt` после теста `overview monthlyKpis on empty dataset returns zeros and nulls` (после строки 252) добавить:

```kotlin
    @Test
    fun `overview monthlyKpis includes status breakdown for current 30d window`() {
        val author = seedUser()
        // В текущем окне: 2 NEW, 1 IN_PROGRESS, 1 RESOLVED, 1 REJECTED, 1 DUPLICATE = 6
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(3))
        seedComplaint(author, ProblemCategory.GARBAGE, ComplaintStatus.NEW, now.minusDays(5))
        seedComplaint(author, ProblemCategory.ROADS, ComplaintStatus.IN_PROGRESS, now.minusDays(7))
        seedComplaint(
            author, ProblemCategory.ROADS, ComplaintStatus.RESOLVED,
            now.minusDays(10), resolvedAt = now.minusDays(8),
        )
        seedComplaint(author, ProblemCategory.LIGHTING, ComplaintStatus.REJECTED, now.minusDays(15))
        seedComplaint(author, ProblemCategory.SAFETY, ComplaintStatus.DUPLICATE, now.minusDays(20))
        // Вне окна (>30д): не должна учитываться
        seedComplaint(author, ProblemCategory.OTHER, ComplaintStatus.NEW, now.minusDays(45))

        val k = service.overview(now).monthlyKpis
        assertEquals(6, k.total, "в окне создано 6")
        assertEquals(2, k.newCount)
        assertEquals(1, k.inProgressCount)
        assertEquals(1, k.resolvedCount)
        assertEquals(1, k.rejectedCount)
        assertEquals(1, k.duplicateCount)
        assertEquals(
            k.total,
            k.newCount + k.inProgressCount + k.resolvedCount + k.rejectedCount + k.duplicateCount,
            "сумма пятёрки == total",
        )
    }
```

- [ ] **Step 2: Запустить gradle и убедиться, что тест НЕ компилируется**

```bash
./gradlew backend:test --tests "*AnalyticsServiceTest*" 2>&1 | tail -30
```

Ожидание: ошибка компиляции вида `unresolved reference: newCount`. Это и есть «красная» фаза TDD.

Не коммитим.

---

## Task 2: Расширить MonthlyKpis + посчитать в сервисе

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt`

- [ ] **Step 1: Добавить 5 полей в data class MonthlyKpis**

Открыть `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt`. Заменить `data class MonthlyKpis` (строки 57–65) на:

```kotlin
@Serializable
data class MonthlyKpis(
    val total: Int,
    val prevTotal: Int,
    val avgResolutionHours: Double?,
    val prevAvgResolutionHours: Double?,
    val resolvedWithin7dPct: Double?,
    val prevResolvedWithin7dPct: Double?,
    val newCount: Int,
    val inProgressCount: Int,
    val resolvedCount: Int,
    val rejectedCount: Int,
    val duplicateCount: Int,
)
```

- [ ] **Step 2: Посчитать byStatus в monthlyKpis()**

В `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt`, в функции `private fun monthlyKpis(rows, now)` (строки 159–192), заменить блок `return MonthlyKpis(...)` (строки 184–191) на:

```kotlin
        val createdInWindow = rows.filter { it.createdAt >= curStart && it.createdAt < now }
        val byStatus = createdInWindow.groupingBy { it.status }.eachCount()

        return MonthlyKpis(
            total = curTotal,
            prevTotal = prevTotal,
            avgResolutionHours = curAvg,
            prevAvgResolutionHours = prevAvg,
            resolvedWithin7dPct = curPct,
            prevResolvedWithin7dPct = prevPct,
            newCount = byStatus[ComplaintStatus.NEW] ?: 0,
            inProgressCount = byStatus[ComplaintStatus.IN_PROGRESS] ?: 0,
            resolvedCount = byStatus[ComplaintStatus.RESOLVED] ?: 0,
            rejectedCount = byStatus[ComplaintStatus.REJECTED] ?: 0,
            duplicateCount = byStatus[ComplaintStatus.DUPLICATE] ?: 0,
        )
```

(Переменные `curTotal/prevTotal/curAvg/...` уже определены строкой выше — их трогать не надо.)

- [ ] **Step 3: Запустить тесты — новый должен пройти, существующие тоже**

```bash
./gradlew backend:test --tests "*AnalyticsServiceTest*" 2>&1 | tail -20
```

Ожидание: все тесты зелёные. Новый тест `monthlyKpis includes status breakdown` проходит. Существующие `compares current 30d window` и `on empty dataset` тоже проходят (они не реферят новые поля).

Не коммитим — Task 3 расширит существующие тесты, потом один коммит.

---

## Task 3: Расширить существующие monthlyKpis-тесты + коммит бэкенда

Defense-in-depth: добавить assertions на новые поля в существующих тестах, чтобы они не дрейфовали.

**Files:**
- Modify: `backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt`

- [ ] **Step 1: Расширить тест `overview monthlyKpis compares current 30d window with previous`**

В существующем тесте после `assertEquals(120.0, k.avgResolutionHours!!, 1.0, "5 суток = 120ч")` (текущая строка 240) добавить:

```kotlin
        // Фикстура текущего окна: 1 NEW + 1 RESOLVED = 2
        assertEquals(1, k.newCount)
        assertEquals(0, k.inProgressCount)
        assertEquals(1, k.resolvedCount)
        assertEquals(0, k.rejectedCount)
        assertEquals(0, k.duplicateCount)
```

- [ ] **Step 2: Расширить тест `overview monthlyKpis on empty dataset returns zeros and nulls`**

После `assertNull(k.prevResolvedWithin7dPct, "пустой предыдущий период — нет базы для расчёта %")` добавить:

```kotlin
        assertEquals(0, k.newCount)
        assertEquals(0, k.inProgressCount)
        assertEquals(0, k.resolvedCount)
        assertEquals(0, k.rejectedCount)
        assertEquals(0, k.duplicateCount)
```

- [ ] **Step 3: Прогнать все backend-тесты**

```bash
./gradlew backend:test 2>&1 | tail -15
```

Ожидание: все тесты зелёные.

- [ ] **Step 4: Коммит бэкенда**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add shared/src/commonMain/kotlin/com/example/cleancity/shared/models/AnalyticsResponse.kt \
        backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsService.kt \
        backend/src/test/kotlin/com/example/cleancity/analytics/AnalyticsServiceTest.kt
git commit -m "$(cat <<'EOF'
feat(analytics): status breakdown в MonthlyKpis за 30-дневное окно

Добавлены newCount/inProgressCount/resolvedCount/rejectedCount/
duplicateCount, считаются по жалобам созданным в текущем окне 30 дней,
сгруппированным по текущему статусу. Сумма пятёрки = total.
Top-level overview.new/inProgress/resolved/rejected/duplicate
(всё время) не меняется.
EOF
)"
```

---

## Task 4: Frontend types — добавить 5 полей в MonthlyKpis interface

Сделать TS-тип симметричным backend-моделью до того, как мок и компоненты начнут их использовать.

**Files:**
- Modify: `web-admin/src/api/types.ts`

- [ ] **Step 1: Расширить interface MonthlyKpis**

Открыть `web-admin/src/api/types.ts`. Найти `interface MonthlyKpis` (около строк 108–115) и заменить на:

```ts
export interface MonthlyKpis {
  total: number
  prevTotal: number
  avgResolutionHours: number | null
  prevAvgResolutionHours: number | null
  resolvedWithin7dPct: number | null
  prevResolvedWithin7dPct: number | null
  newCount: number
  inProgressCount: number
  resolvedCount: number
  rejectedCount: number
  duplicateCount: number
}
```

- [ ] **Step 2: Проверить typecheck**

```bash
cd ~/Desktop/Myapp/cleancity-kmp/web-admin
npx tsc --noEmit 2>&1 | tail -30
```

Ожидание: ошибки в `OverviewPage.test.tsx` (мок `monthlyKpis` теперь не содержит 5 обязательных полей). Это норма — фиксим в Task 5.

Не коммитим.

---

## Task 5: Падающий frontend-тест на 5 статусов

Обновить мок (чтобы тс был чист) и добавить assertion на новый UI.

**Files:**
- Modify: `web-admin/src/pages/OverviewPage.test.tsx`

- [ ] **Step 1: Дополнить хелпер overview() пятью полями**

В `web-admin/src/pages/OverviewPage.test.tsx`, в функции `overview(slaBreachCount)` (строки 11–20), заменить блок `monthlyKpis: {...}` на:

```tsx
    monthlyKpis: {
      total: 50, prevTotal: 40, avgResolutionHours: 41, prevAvgResolutionHours: 50,
      resolvedWithin7dPct: 78, prevResolvedWithin7dPct: 70,
      newCount: 10, inProgressCount: 15, resolvedCount: 20, rejectedCount: 3, duplicateCount: 2,
    },
```

(Сумма 10+15+20+3+2 = 50 = total — удобно для проверок консистентности.)

- [ ] **Step 2: Добавить assertion на новый блок с 5 статусами**

В первом `it`-блоке (`'показывает KPI и пайплайн статусов из overview'`), после существующего `expect(screen.getByText('Топ-5 по категориям проблем'))...` добавить:

```tsx
    expect(screen.getByText('Распределение за месяц')).toBeInTheDocument()
    expect(screen.getByText('Отклонено')).toBeInTheDocument()
    expect(screen.getByText('Дубликаты')).toBeInTheDocument()
    // числа по статусам — счётчики из monthlyKpis
    expect(screen.getByText('20')).toBeInTheDocument() // resolvedCount
    expect(screen.getByText('3')).toBeInTheDocument()  // rejectedCount
    expect(screen.getByText('2')).toBeInTheDocument()  // duplicateCount
```

(Числа 10 и 15 в DOM могут совпасть с `o.new` или `o.inProgress` из top-level overview, поэтому проверяем только 20/3/2 — уникальные.)

- [ ] **Step 3: Запустить тесты**

```bash
npm test -- OverviewPage 2>&1 | tail -20
```

Ожидание: первый тест падает с одним из:
- `Unable to find an element with the text: Распределение за месяц` (заголовок ещё старый), или
- `Unable to find an element with the text: Отклонено` (пайплайн ещё трёхколоночный).

Typecheck чист (npx tsc --noEmit).

---

## Task 6: Переписать StatusPipeline + проводка в OverviewPage

Эти два изменения тесно связаны (новые пропсы StatusPipeline ⇄ обновлённый вызов в OverviewPage), делаем атомарно в одной задаче, чтобы избежать промежуточного броуенного typecheck.

**Files:**
- Modify: `web-admin/src/pages/overview/StatusPipeline.tsx`
- Modify: `web-admin/src/pages/OverviewPage.tsx`

- [ ] **Step 1: Полностью переписать StatusPipeline.tsx**

Заменить ВЕСЬ контент `web-admin/src/pages/overview/StatusPipeline.tsx` на:

```tsx
interface StatusPipelineProps {
  newCount: number
  inProgressCount: number
  resolvedCount: number
  rejectedCount: number
  duplicateCount: number
}

const STAGES = [
  { key: 'newCount', label: 'В обработке', color: 'text-amber-600' },
  { key: 'inProgressCount', label: 'В работе', color: 'text-blue-600' },
  { key: 'resolvedCount', label: 'Решено', color: 'text-emerald-600' },
  { key: 'rejectedCount', label: 'Отклонено', color: 'text-slate-500' },
  { key: 'duplicateCount', label: 'Дубликаты', color: 'text-slate-500' },
] as const

export function StatusPipeline(props: StatusPipelineProps) {
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Распределение за месяц</div>
      <div className="grid grid-cols-5 gap-2">
        {STAGES.map((s) => (
          <div key={s.key} className="text-center">
            <div className={`text-2xl font-semibold ${s.color}`}>{props[s.key]}</div>
            <div className="text-xs text-slate-500">{s.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Обновить вызов в OverviewPage.tsx**

В `web-admin/src/pages/OverviewPage.tsx`, найти строку `<StatusPipeline newCount={o.new} inProgress={o.inProgress} resolved={o.resolved} />` (строка 61) и заменить на:

```tsx
      <StatusPipeline
        newCount={k.newCount}
        inProgressCount={k.inProgressCount}
        resolvedCount={k.resolvedCount}
        rejectedCount={k.rejectedCount}
        duplicateCount={k.duplicateCount}
      />
```

(`k` уже определено как `o.monthlyKpis` в строке 37.)

- [ ] **Step 3: Прогнать typecheck и тесты**

```bash
cd ~/Desktop/Myapp/cleancity-kmp/web-admin
npx tsc --noEmit && npm test 2>&1 | tail -15
```

Ожидание: typecheck чист, все тесты зелёные (включая новые assertions Task 5).

Не коммитим — финальный фронтенд-коммит в Task 7.

---

## Task 7: Финальная проверка + коммит фронтенда

**Files:** —

- [ ] **Step 1: Полный typecheck + тесты ещё раз**

```bash
cd ~/Desktop/Myapp/cleancity-kmp/web-admin
npx tsc --noEmit && npm test 2>&1 | tail -10
```

Ожидание: чисто.

- [ ] **Step 2: Коммит фронтенда**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add web-admin/src/api/types.ts \
        web-admin/src/pages/overview/StatusPipeline.tsx \
        web-admin/src/pages/OverviewPage.tsx \
        web-admin/src/pages/OverviewPage.test.tsx
git commit -m "$(cat <<'EOF'
feat(web-admin): Распределение за месяц с 5 статусами

StatusPipeline теперь рендерит все 5 статусов (NEW/IN_PROGRESS/
RESOLVED/REJECTED/DUPLICATE) из MonthlyKpis за окно 30 дней.
Сумма = KPI «Жалоб за месяц». Top-level overview.* (всё время)
не задействован.
EOF
)"
```

- [ ] **Step 3: Проверить git log**

```bash
git log --oneline -3
```

Ожидание: два новых коммита (backend + frontend) поверх `2ea7116`.

---

## Task 8: Визуальная проверка в браузере

**Files:** —

- [ ] **Step 1: Запустить backend и dev-сервер**

В двух терминалах из `~/Desktop/Myapp/cleancity-kmp/`:

```bash
docker-compose up -d  # если ещё не поднят
./gradlew backend:run
```

И в третьем:

```bash
cd ~/Desktop/Myapp/cleancity-kmp/web-admin && npm run dev
```

- [ ] **Step 2: Открыть Обзор**

`http://localhost:5173/`, логин `admin@cleancity.dev` / `Admin12345!`, перейти на «Обзор» (страница по умолчанию).

Проверить:

1. **Заголовок блока** — «Распределение за месяц» (не «по статусам»).
2. **5 столбцов** — `В обработке / В работе / Решено / Отклонено / Дубликаты` слева направо.
3. **Сумма = KPI**: сложить пять чисел в блоке — должно быть точно равно числу в KPI-карточке «Жалоб за месяц». Если БД пустая — все 5 нулей и KPI = 0.
4. **Цвета**: NEW=янтарный, IN_PROGRESS=синий, RESOLVED=зелёный, REJECTED/DUPLICATE=серый.
5. **Layout не съехал**: блок «Топ-5 категорий» и «Топ-5 районов» внизу видны нормально.

- [ ] **Step 3: Остановить процессы**

`Ctrl+C` в каждом терминале.

# Status Pipeline: распределение за месяц + 5 статусов

**Дата:** 2026-05-24
**Область:** `backend` (Ktor/Kotlin) + `shared` (KMP data classes) + `web-admin` (React)

## Контекст

На странице «Обзор» (`web-admin`) есть KPI «Жалоб за месяц» (счётчик, поле `monthlyKpis.total`, окно — последние 30 дней) и блок «Распределение по статусам» с тремя счётчиками: `NEW / IN_PROGRESS / RESOLVED`. Эти два блока показываются рядом, но считаются по-разному:

- KPI: жалобы, созданные за последние 30 дней (`monthlyKpis()` в `AnalyticsService.kt:159`)
- Pipeline: текущее распределение жалоб по статусам за **всё время** БД (`overview()` в `AnalyticsService.kt:23`, `loadComplaints(periodStart = null)`)

Дополнительно, бэкенд возвращает все 5 статусов (`new/inProgress/resolved/rejected/duplicate`), но `StatusPipeline.tsx` рисует только первые 3 — `REJECTED` и `DUPLICATE` «исчезают», и сумма видимых ≠ реальному total даже за всё время.

В результате: пользователь видит KPI=57 и пайплайн с суммой 39, расхождение выглядит как баг данных. Это не баг — это семантический рассинхрон UI, и его надо устранить.

## Решение

Привести блок «Распределение» в то же временное окно, что и KPI (30 дней), и показать все 5 статусов. Семантика блока: **жалобы, созданные за последние 30 дней, сгруппированные по их текущему статусу**. Сумма пятёрки = `monthlyKpis.total` точно по построению.

Контракт `overview.new/inProgress/resolved/rejected/duplicate` (всё время) **не трогаем** — он используется в `ComplaintFilters` для чипов на странице «Жалобы». Расширяем `MonthlyKpis` пятью новыми полями.

## Изменения

### 1. `shared/src/commonMain/kotlin/.../models/AnalyticsResponse.kt`

В `data class MonthlyKpis` добавить 5 полей:

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

Поля идут после существующих, чтобы не сбивать читаемость. `@Serializable` добавляет поля в JSON; для мобильного KMP-клиента это backward-compatible — клиент игнорирует поля, которые не читает.

### 2. `backend/src/main/kotlin/.../analytics/AnalyticsService.kt`

В `monthlyKpis()` (после строки 192) перед `return MonthlyKpis(...)`:

```kotlin
val createdInWindow = rows.filter { it.createdAt >= curStart && it.createdAt < now }
val byStatus = createdInWindow.groupingBy { it.status }.eachCount()
```

В конструкторе `MonthlyKpis(...)` добавить:

```kotlin
newCount = byStatus[ComplaintStatus.NEW] ?: 0,
inProgressCount = byStatus[ComplaintStatus.IN_PROGRESS] ?: 0,
resolvedCount = byStatus[ComplaintStatus.RESOLVED] ?: 0,
rejectedCount = byStatus[ComplaintStatus.REJECTED] ?: 0,
duplicateCount = byStatus[ComplaintStatus.DUPLICATE] ?: 0,
```

Существующая логика `window(curStart, now)` не меняется — она по-прежнему считает `total/avgHours/pct`.

### 3. `backend/src/test/kotlin/.../analytics/AnalyticsServiceTest.kt`

Расширить тест `overview monthlyKpis compares current 30d window with previous` (после строки 220) дополнительными assertions, что счётчики статусов соответствуют фикстуре. Если фикстура содержит только NEW-жалобы — проверить `assertEquals(N, k.newCount)`, `assertEquals(0, k.resolvedCount)` и т.д. Добавить отдельный тест с миксом статусов (хотя бы 2 разных), где сумма пятёрки == `k.total`.

В тесте `overview monthlyKpis on empty dataset returns zeros and nulls` — добавить `assertEquals(0, k.newCount)` и т.д. для всех 5.

### 4. `web-admin/src/api/types.ts`

В интерфейсе `MonthlyKpis` (строки 108–115) добавить 5 полей, симметрично бэкенду:

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

### 5. `web-admin/src/pages/overview/StatusPipeline.tsx`

Полная замена. Новые пропсы (5 счётчиков), новый STAGES (5 элементов с цветами Tailwind), grid-cols-5, новый заголовок:

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

Цвета REJECTED/DUPLICATE — приглушённый `slate-500` (исходы), как в `complaintMeta.ts:38-39`. Лейблы согласованы по тону: «Решено / Отклонено / Дубликаты» — короткие, в той же грамматической форме где возможно.

### 6. `web-admin/src/pages/OverviewPage.tsx`

Заменить строку рендера пайплайна:

```tsx
<StatusPipeline
  newCount={k.newCount}
  inProgressCount={k.inProgressCount}
  resolvedCount={k.resolvedCount}
  rejectedCount={k.rejectedCount}
  duplicateCount={k.duplicateCount}
/>
```

(было `<StatusPipeline newCount={o.new} inProgress={o.inProgress} resolved={o.resolved} />` — переходим с `o.*` на `k.*`)

### 7. `web-admin/src/pages/OverviewPage.test.tsx`

В функции `overview()` (строки 11–20) добавить 5 новых полей в `monthlyKpis`. Можно подобрать значения так, чтобы их сумма = `k.total` = 50 (например, 10+15+20+3+2). Это удобнее для будущих тестов на консистентность.

Добавить тест: рендер всех 5 статусов с их числами в новом блоке. Использовать `getByText` для проверки лейблов «Отклонено» и «Дубликаты» (на текущей странице их нет, так что коллизий нет).

## Контракт данных

`MonthlyKpis.{new,inProgress,resolved,rejected,duplicate}Count`:
- считаются по жалобам, у которых `createdAt ∈ [now - 30d, now)`
- группируются по **текущему** значению `status` (не «когда-либо был в этом статусе»)
- сумма пятёрки = `total` по построению (одна и та же выборка, разные срезы)

## Что НЕ делаем (out of scope)

- Изменение top-level `AnalyticsOverview.new/inProgress/resolved/rejected/duplicate` (используется в `ComplaintFilters`, ломать контракт незачем).
- Throughput-семантика (сколько жалоб **перешло** в статус X за окно) — требует анализа `status_changes`, более сложная задача.
- Адаптация мобильного клиента (KMP) — новые поля в `MonthlyKpis` для него опциональны, дашборд он не показывает.
- Переключатель периода в блоке.
- Анимации пайплайна.

## План проверки

1. **Backend**: `./gradlew backend:test` — обновлённые AnalyticsServiceTest проходят.
2. **Frontend**: `npm test` в `web-admin` — все 72+ теста зелёные, новый тест на 5 статусов проходит.
3. **Typecheck**: `npx tsc --noEmit` чист.
4. **E2E локально**: запустить docker-compose, web-admin dev-сервер, проверить что:
   - Сумма чисел в новом блоке точно равна KPI «Жалоб за месяц».
   - На пустой БД все 5 счётчиков = 0.
   - Layout не сжат (5 столбцов помещаются в правую половину сетки `grid-cols-2`).

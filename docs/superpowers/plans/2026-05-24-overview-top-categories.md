# Overview: Топ-5 по категориям проблем — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить блок «Топ-5 по голосам жителей» в `OverviewPage` web-admin на «Топ-5 по категориям проблем» (счётчик жалоб за месяц).

**Architecture:** Один новый React-компонент + один новый react-query хук поверх существующего endpoint `GET /analytics/by-category?period=MONTH`. Старый компонент `TopVotedComplaints` и хук `useTopVotedQuery` удаляются. Backend не меняется.

**Tech Stack:** React 18, TypeScript, react-query v5, Vitest + MSW, Tailwind.

**Spec:** `docs/superpowers/specs/2026-05-24-overview-top-categories-design.md`

**Working directory for all commands:** `~/Desktop/Myapp/cleancity-kmp/web-admin`

---

## File Structure

- **Create:** `src/pages/overview/TopProblemCategories.tsx` — презентационный компонент (props: `CategoryStat[]`, рендер «Топ-5 по категориям проблем»).
- **Modify:** `src/hooks/dashboardQueries.ts` — добавить `useByCategoryQuery`, удалить `useTopVotedQuery` + `TOP_VOTED_FILTER`.
- **Modify:** `src/pages/OverviewPage.tsx` — поменять импорты, заменить хук и рендер блока в правой колонке нижней сетки.
- **Modify:** `src/pages/OverviewPage.test.tsx` — заменить MSW-мок `/complaints` на `/analytics/by-category`, добавить проверку нового заголовка блока.
- **Delete:** `src/pages/overview/TopVotedComplaints.tsx`.

---

## Task 1: Падающий тест на новый блок

Сначала ломаем тест: меняем мок и добавляем assertion на новый заголовок. Это даст нам зелёный сигнал в конце.

**Files:**
- Modify: `src/pages/OverviewPage.test.tsx`

- [ ] **Step 1: Обновить мок-сервер и добавить assertion**

Открыть `src/pages/OverviewPage.test.tsx` и заменить функцию `server` (строки 22–30) на:

```tsx
function server(slaBreachCount: number) {
  return setupServer(
    http.get(`${BASE}/analytics/overview`, () => HttpResponse.json(overview(slaBreachCount))),
    http.get(`${BASE}/analytics/by-district`, () => HttpResponse.json([])),
    http.get(`${BASE}/analytics/by-category`, () => HttpResponse.json([])),
  )
}
```

В первом `it`-блоке (строки 47–52) после `expect(screen.getByText('+25%'))...` добавить отдельным выражением:

```tsx
expect(screen.getByText('Топ-5 по категориям проблем')).toBeInTheDocument()
```

- [ ] **Step 2: Запустить тест и убедиться, что он падает**

```bash
npm test -- OverviewPage
```

Ожидание: первый тест `показывает KPI и пайплайн статусов из overview` падает с ошибкой типа `Unable to find an element with the text: Топ-5 по категориям проблем` (на странице ещё старый заголовок «Топ-5 по голосам жителей»). Остальные два теста должны пройти.

Не коммитим — финальный коммит будет после прохождения тестов в Task 5.

---

## Task 2: Хук `useByCategoryQuery`

**Files:**
- Modify: `src/hooks/dashboardQueries.ts`

- [ ] **Step 1: Добавить хук**

Открыть `src/hooks/dashboardQueries.ts`. Найти импорт из `@/api/analytics` (строка 2) и добавить `getByCategory` к импортируемому списку — итоговая строка:

```ts
import { getTrends, getByDistrict, getByCategory, getSla, getVotesImpact } from '@/api/analytics'
```

После функции `useByDistrictQuery` (строки 29–34) добавить новый хук:

```ts
export function useByCategoryQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'by-category', period],
    queryFn: () => getByCategory(period),
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}
```

Хук `useTopVotedQuery` и константу `TOP_VOTED_FILTER` пока **не удаляем** — это в Task 5, чтобы не сломать промежуточные шаги.

- [ ] **Step 2: Проверить, что typecheck зелёный**

```bash
npx tsc --noEmit
```

Ожидание: код компилируется без ошибок.

---

## Task 3: Компонент `TopProblemCategories`

**Files:**
- Create: `src/pages/overview/TopProblemCategories.tsx`

- [ ] **Step 1: Создать файл**

Создать `src/pages/overview/TopProblemCategories.tsx` со следующим содержимым:

```tsx
import type { CategoryStat } from '@/api/types'

interface TopProblemCategoriesProps {
  items: CategoryStat[]
}

export function TopProblemCategories({ items }: TopProblemCategoriesProps) {
  const top = items.slice(0, 5)
  return (
    <div className="rounded-xl border bg-white p-4">
      <div className="mb-3 text-sm font-medium text-slate-700">Топ-5 по категориям проблем</div>
      {top.length === 0 ? (
        <div className="py-6 text-center text-sm text-slate-400">Нет данных</div>
      ) : (
        <ol className="flex flex-col gap-2">
          {top.map((c, i) => (
            <li
              key={c.category}
              className="flex items-center gap-3 border-b border-slate-100 pb-2 last:border-0"
            >
              <span className="w-5 text-sm font-semibold text-emerald-600">{i + 1}</span>
              <span className="flex-1 truncate text-xs text-slate-700">{c.label}</span>
              <span className="text-xs font-semibold text-slate-500">{c.count}</span>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Проверить typecheck**

```bash
npx tsc --noEmit
```

Ожидание: ok. Компонент пока никем не импортирован, но это нормально — подключим в Task 4.

---

## Task 4: Подключить новый блок в `OverviewPage`

**Files:**
- Modify: `src/pages/OverviewPage.tsx`

- [ ] **Step 1: Заменить импорты**

В `src/pages/OverviewPage.tsx` заменить строки 1–7:

```tsx
import { useOverviewQuery } from '@/hooks/complaintQueries'
import { useByCategoryQuery, useByDistrictQuery } from '@/hooks/dashboardQueries'
import { KpiCard } from '@/components/dashboard/KpiCard'
import { SlaAlertBanner } from './overview/SlaAlertBanner'
import { StatusPipeline } from './overview/StatusPipeline'
import { TopDistricts } from './overview/TopDistricts'
import { TopProblemCategories } from './overview/TopProblemCategories'
```

Изменения по сравнению с оригиналом: `useTopVotedQuery` → `useByCategoryQuery`, `TopVotedComplaints` → `TopProblemCategories`.

- [ ] **Step 2: Заменить вызов хука**

Заменить строку 19:

```tsx
  const topCategories = useByCategoryQuery('MONTH')
```

(было: `const topVoted = useTopVotedQuery()`)

- [ ] **Step 3: Заменить рендер блока**

В правой ячейке нижней сетки (строка 65) заменить:

```tsx
        <TopProblemCategories items={topCategories.data ?? []} />
```

(было: `<TopVotedComplaints items={topVoted.data?.items ?? []} />`)

- [ ] **Step 4: Прогнать тесты**

```bash
npm test -- OverviewPage
```

Ожидание: все три теста проходят (новый заголовок теперь рендерится, мок `/analytics/by-category` отдаёт пустой массив, блок показывает «Нет данных»).

---

## Task 5: Удалить мёртвый код и финальная проверка

**Files:**
- Delete: `src/pages/overview/TopVotedComplaints.tsx`
- Modify: `src/hooks/dashboardQueries.ts`

- [ ] **Step 1: Удалить компонент**

```bash
rm src/pages/overview/TopVotedComplaints.tsx
```

- [ ] **Step 2: Удалить мёртвый хук и константу**

В `src/hooks/dashboardQueries.ts` удалить:

- импорт `listComplaints` (строка 3): `import { listComplaints } from '@/api/complaints'`
- импорт типа `ComplaintFilter` (если он импортируется только для `TOP_VOTED_FILTER`)
- константу `TOP_VOTED_FILTER` (строки 17–19)
- функцию `useTopVotedQuery` (строки 21–27)

Итоговый верх файла должен выглядеть так:

```ts
import { useQuery } from '@tanstack/react-query'
import { getTrends, getByDistrict, getByCategory, getSla, getVotesImpact } from '@/api/analytics'
import type { AnalyticsPeriod } from '@/api/types'

// Дашборд автообновляется раз в минуту — синхронно с таблицей жалоб (Day 16).
const DASHBOARD_REFETCH_MS = 60_000

export function useTrendsQuery() {
  return useQuery({
    queryKey: ['analytics', 'trends'],
    queryFn: getTrends,
    refetchInterval: DASHBOARD_REFETCH_MS,
  })
}

export function useByCategoryQuery(period: AnalyticsPeriod) {
  return useQuery({
    queryKey: ['analytics', 'by-category', period],
    queryFn: () => getByCategory(period),
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

- [ ] **Step 3: Проверить, что нигде не остались висячие ссылки**

```bash
grep -rn "TopVotedComplaints\|useTopVotedQuery\|TOP_VOTED_FILTER" src/
```

Ожидание: пустой вывод.

- [ ] **Step 4: Typecheck + полный прогон тестов**

```bash
npx tsc --noEmit && npm test
```

Ожидание: оба зелёные. Если упадёт другой тест, который случайно зависел от мока `/complaints` — починить мок локально в этом тестовом файле.

- [ ] **Step 5: Коммит**

```bash
git add src/pages/overview/TopProblemCategories.tsx \
  src/hooks/dashboardQueries.ts \
  src/pages/OverviewPage.tsx \
  src/pages/OverviewPage.test.tsx
git rm src/pages/overview/TopVotedComplaints.tsx
git commit -m "$(cat <<'EOF'
feat(web-admin): Топ-5 по категориям проблем в Обзоре

Заменить блок «Топ-5 по голосам жителей» на «Топ-5 по категориям
проблем» (период MONTH, источник /analytics/by-category). Старый
useTopVotedQuery и компонент удалены.
EOF
)"
```

---

## Task 6: Визуальная проверка в браузере

**Files:** —

- [ ] **Step 1: Запустить dev-сервер**

В отдельном терминале:

```bash
npm run dev
```

Дождаться `Local: http://localhost:5173/`.

- [ ] **Step 2: Открыть страницу «Обзор»**

Залогиниться через сидового админа (`admin@cleancity.dev` / `Admin12345!`) и убедиться, что:

1. В правой ячейке нижней сетки заголовок — «Топ-5 по категориям проблем» (не «по голосам жителей»).
2. Если в БД есть жалобы за последний месяц — отрисуются строки `№ | Лейбл категории | счётчик`, отсортированные по убыванию счётчика, максимум 5.
3. Если жалоб нет — текст «Нет данных».
4. Layout не поехал, левый блок «Топ-5 районов» на месте.

Если backend сейчас не запущен — поднять docker-compose стек (см. `~/Desktop/Myapp/cleancity-kmp/README.md` или память «CleanCity — dev-сид и грабли Flyway»).

- [ ] **Step 3: Остановить dev-сервер**

`Ctrl+C` в терминале с `npm run dev`.

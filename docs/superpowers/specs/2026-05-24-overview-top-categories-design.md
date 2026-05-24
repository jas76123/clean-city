# Overview: Топ-5 по категориям проблем

**Дата:** 2026-05-24
**Область:** `web-admin` (React), страница «Обзор»
**Бэкенд:** изменений нет.

## Контекст

На странице «Обзор» (`src/pages/OverviewPage.tsx`) в правой колонке нижней сетки сейчас отображается блок «Топ-5 по голосам жителей» — список жалоб, отсортированных по числу голосов. Жасмин просит заменить этот блок на «Топ-5 по категориям проблем», где счётчик — это количество жалоб в категории за период.

Бэкенд уже отдаёт нужные данные через `GET /analytics/by-category?period=...` (см. `src/api/analytics.ts:22`), но в web-admin этот endpoint пока ни одним хуком не используется.

## Решение

Заменить компонент и его источник данных. Период — `MONTH`, чтобы согласоваться с соседним блоком «Топ-5 районов» (`useByDistrictQuery('MONTH')` в `src/pages/OverviewPage.tsx:20`).

### Изменения по файлам

1. **`src/hooks/dashboardQueries.ts`** — новый хук:

   ```ts
   export function useByCategoryQuery(period: AnalyticsPeriod) {
     return useQuery({
       queryKey: ['analytics', 'by-category', period],
       queryFn: () => getByCategory(period),
       refetchInterval: DASHBOARD_REFETCH_MS,
     })
   }
   ```

   Удалить `useTopVotedQuery` и константу `TOP_VOTED_FILTER` — других потребителей нет (проверено `grep`'ом).

2. **`src/pages/overview/TopProblemCategories.tsx`** — новый компонент:

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

   Ключевые отличия от `TopVotedComplaints`: убран значок `▲` (голос → счётчик жалоб), `key` по `category` вместо `id`.

3. **`src/pages/overview/TopVotedComplaints.tsx`** — удалить файл.

4. **`src/pages/OverviewPage.tsx`** — точечная замена:
   - Импорт: убрать `useTopVotedQuery`, `TopVotedComplaints`; добавить `useByCategoryQuery`, `TopProblemCategories`.
   - Хук: `const topCategories = useByCategoryQuery('MONTH')` вместо `const topVoted = useTopVotedQuery()`.
   - Рендер: `<TopProblemCategories items={topCategories.data ?? []} />` вместо `<TopVotedComplaints items={topVoted.data?.items ?? []} />`.

5. **`src/pages/OverviewPage.test.tsx`** — обновить MSW-моки:
   - Удалить `http.get('/complaints', ...)` (нужен был только для `useTopVotedQuery`).
   - Добавить `http.get('/analytics/by-category', () => HttpResponse.json([]))`.

   Существующие assertions проверяют только KPI/пайплайн/SLA — содержимое нового блока в тестах не проверяется, новых тестов в рамках этой задачи не пишем.

## Контракт данных

`CategoryStat` (`src/api/types.ts:142`):

```ts
interface CategoryStat {
  category: ProblemCategory  // enum-ключ, стабильный → используем как React key
  label: string              // человекочитаемое имя категории на русском
  count: number              // ← счётчик, который показываем
  sharePct: number           // не показываем
  avgResolutionHours: number | null  // не показываем
}
```

Бэкенд гарантирует сортировку по `count desc` (см. реализацию endpoint на сервере; на фронте дополнительно не сортируем — берём первые 5).

## UI

Layout не меняется: 2-колоночная сетка `grid-cols-2 gap-4` в `OverviewPage`. Левая ячейка — «Топ-5 районов», правая — «Топ-5 категорий проблем». Стили карточки, нумерации, шрифтов — идентичны старому блоку для визуальной согласованности.

## Состояния

- **Loading:** страница в целом показывает «Загрузка…» пока грузится `useOverviewQuery`. Категории грузятся параллельно; пока пусто — отрисуется empty state. Это приемлемо (отдельного скелетона нет ни у одного из соседних блоков).
- **Empty:** «Нет данных» — текущий стандарт страницы.
- **Error:** молчаливая деградация — если `useByCategoryQuery` ушёл в ошибку, `data ?? []` даст пустой список, отрисуется empty state. Такое же поведение у `TopDistricts` сейчас.

## Что не делаем (out of scope)

- Иконки или цветовая дифференциация категорий — не запрашивали, лишний UI-шум.
- Переключатель периода прямо в блоке — соседи тоже без него.
- Backend-изменения — не нужны.
- Доля % и средний resolution time из `CategoryStat` — пользователь выбрал минимализм.

## План проверки

1. Storybook нет — визуальная проверка в `npm run dev` на странице `/`.
2. Юнит-тесты: `npm test -- OverviewPage` (должны пройти после обновления моков).
3. Бэкенд: ничего не трогаем; e2e-проверка локально с сидом V99 в Docker.

# Day 17A — Web Admin: Дашборд (Overview + Analytics)

**Дата:** 2026-05-24
**Под-проект:** 17A из декомпозиции Дня 17 (`docs/PLAN.md`, «День 17»)
**Статус:** дизайн утверждён, готов к написанию плана реализации

---

## Контекст и декомпозиция

День 17 в `PLAN.md` перегружен — это 4 независимых экрана веб-админки плюс
заметный кусок нового бэкенда. Для одной спеки и одного плана реализации это
слишком много, поэтому День 17 разбит на под-проекты, каждый со своим циклом
спека → план → реализация:

- **17A — Дашборд** (этот документ): `OverviewPage` + `AnalyticsPage`.
- **17B — Объявления**: `AnnouncementsPage` (бэкенд CRUD уже готов).
- **17C — Настройки и команда**: `SettingsPage` (нужен новый бэкенд).
- **17D — PDF-отчёт за месяц**: OpenPDF на бэкенде + кнопка скачивания.

Эта спека покрывает только **17A**.

## Цель

Заменить заглушки на роутах `/overview` и `/analytics` рабочими экранами,
наполненными реальными данными аналитики. Overview — главный экран после
логина и «лицо» админки для защиты диплома.

## Что уже готово (не трогаем сверх необходимого)

Бэкенд-эндпоинты аналитики (`AnalyticsRoutes.kt`, admin-only):
`GET /analytics/overview`, `/by-category`, `/by-district`, `/sla`,
`/votes-impact`. Объявления — отдельный под-проект.

Фронт: роуты `/overview` и `/analytics` существуют как `SectionPlaceholder`
(`web-admin/src/App.tsx`). API-функция `getOverview()` есть в
`src/api/analytics.ts`. Топ-5 по голосам берём через существующий
`listComplaints({ sort: 'votes' })` из `src/api/complaints.ts`.

## Решения, принятые при брейнсторминге

- **Объём:** лёгкое расширение бэкенда — один новый эндпоинт `/analytics/trends`
  плюс обогащение `/analytics/overview`. Без полной верности мокапу.
- **Графики:** только CSS/SVG, без библиотек (recharts не устанавливаем —
  мокап `admin-dashboard-v2.html` тоже рисует всё чистым CSS).
- **Две страницы:** `OverviewPage` (снимок «сейчас») и `AnalyticsPage`
  (тренды за период).
- **Авто-обновление:** TanStack Query с `refetchInterval`, как `ComplaintsPage`.
- **Подход к данным:** обогатить `/overview` скалярами + новый `/analytics/trends`
  для дневного ряда (чистое разделение: overview = снимок, trends = ряд).

## Вне объёма 17A (явно)

- Карта с pins активных жалоб — заблокирована отсутствием ключа Yandex JS API
  (тот же блокер, что отложил детальную карту на Day 16). Места под карту в
  коде не оставляем.
- Trend-карта «Активных пользователей» — метрики числа пользователей нет.
- PDF-экспорт — это под-проект 17D; кнопка на Analytics рендерится `disabled`.
- Разбивка SLA-баннера по районам — ни один эндпоинт её не отдаёт.
- `getByDistrict` на странице Analytics — «Топ районов» уже на Overview,
  не дублируем.

---

## Секция A — Бэкенд

### Модели (`shared/.../models/AnalyticsResponse.kt`)

Новый вложенный объект сравнения период-к-периоду и расширение `AnalyticsOverview`:

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

// AnalyticsOverview: существующие 9 полей + новое поле
//   val monthlyKpis: MonthlyKpis

@Serializable
data class DailyPoint(val date: String, val created: Int, val resolved: Int)

@Serializable
data class TrendsResponse(val days: List<DailyPoint>)
```

### Эндпоинт `GET /analytics/trends`

- Без параметров. Отдаёт **последние 30 дней** ежедневно: `TrendsResponse`.
- `created` за день — число жалоб с `createdAt` в этот день.
- `resolved` за день — число жалоб с `resolvedAt` в этот день.
- `date` — ISO-дата (`YYYY-MM-DD`), UTC.
- Admin-only, внутри существующего `route("/analytics")`.

### `AnalyticsService`

- `overview()` дополнительно считает `monthlyKpis` по двум окнам относительно
  `now`: текущее (0–30 дней) и предыдущее (30–60 дней назад).
  - `total` / `prevTotal` — жалобы по `createdAt` в окне.
  - `avgResolutionHours` / `prev…` — среднее `resolvedAt − createdAt` по
    RESOLVED-жалобам, решённым в окне (по `resolvedAt`); `null` если решённых нет.
  - `resolvedWithin7dPct` / `prev…` — доля (%) RESOLVED-жалоб окна, у которых
    `resolvedAt − createdAt ≤ 7 дней`; `0.0` если решённых нет.
- Новый метод `trends()` — агрегация по дням за последние 30 дней.
- `AnalyticsRepository` не меняем — `loadComplaints(null)` уже отдаёт строки
  с `createdAt`, `resolvedAt`, `status`.

### Дельты KPI — нюанс

Дельты («+23 %» и т.п.) фронт считает из пар `value`/`prevValue` в `monthlyKpis`.
Карточка **«SLA-просрочки»** дельты НЕ имеет: `slaBreachCount` — метрика
состояния «сейчас» (активные просрочки), исторических снимков система не
хранит. В мокапе «+3» у этой карточки — фейковые демо-данные.

### Тесты бэкенда

- `AnalyticsServiceTest` — `monthlyKpis` на двух окнах (включая граничные даты),
  `trends()` агрегация по дням, поведение на пустом датасете.
- `AnalyticsRoutesTest` — `GET /analytics/trends` отдаёт 200 и корректную форму
  JSON; admin-only (401 без токена, 403 для RESIDENT).

---

## Секция B — `OverviewPage.tsx`

Заменяет заглушку на `/overview`. Три запроса через TanStack Query с
`refetchInterval` (как `ComplaintsPage`): `getOverview()`, `getTrends()`,
`listComplaints({ sort: 'votes' })`.

Структура экрана (порядок как в мокапе `docs/mockups/admin-dashboard-v2.html`,
`screen-dashboard`):

1. **SLA-баннер** — `<SlaAlertBanner/>`, рендерится только если
   `overview.slaBreachCount > 0`. Текст: «N жалоб превысили SLA».
   Без разбивки по районам.
2. **4 KPI-карточки** — `<KpiCard/>` ×4:
   | Карточка | Значение | Дельта |
   |---|---|---|
   | Жалоб за месяц | `monthlyKpis.total` | vs `prevTotal` |
   | Среднее время решения | `monthlyKpis.avgResolutionHours` (или «—») | vs `prev` |
   | Решено за 7 дней | `monthlyKpis.resolvedWithin7dPct` % | vs `prev` |
   | SLA-просрочки | `slaBreachCount` | без дельты |
3. **«Распределение по статусам»** — `<StatusPipeline/>`: 3 стадии
   (`new` / `inProgress` / `resolved`) из `overview`.
4. **«Активность по дням»** — `<DailyBarChart/>`: CSS-столбики за 30 дней из
   `trends.days`, две серии (создано / решено) с легендой.
5. **row-2 (две карточки):**
   - **«Топ районов по жалобам»** — `getByDistrict('MONTH')`, горизонтальные
     бары `<HBar/>`, топ-4–5.
   - **«Топ-5 по голосам жителей»** — первые 5 из
     `listComplaints({ sort: 'votes' })`, нумерованный список с числом голосов.

Состояния:
- Загрузка — скелетоны по карточкам (не общий спиннер).
- Ошибка — сообщение + кнопка «Повторить» (через существующий `errors.ts`).
- Пустой датасет — KPI показывают 0, графики/списки — «Нет данных».
- `avgResolutionHours === null` → «—», дельта скрыта.

---

## Секция C — `AnalyticsPage.tsx`

Заменяет заглушку на `/analytics`. Главное отличие от Overview —
переключатель периода.

**Шапка:**
- Заголовок «Аналитика» + подпись.
- **Переключатель периода** — сегменты `Неделя / Месяц / Всё`
  (`AnalyticsPeriod = WEEK | MONTH | ALL`). Хранится в `useState`, входит в
  `queryKey` → смена периода автоматически перезапрашивает данные.
- Кнопка **«Экспорт PDF»** — рендерится `disabled` с тултипом «Скоро»
  (реализация — под-проект 17D).

**Запросы:** `getByCategory(period)`, `getSla(period)`,
`getVotesImpact(period)` (все с `period` в `queryKey`); `getTrends()` —
без периода (фиксированные 30 дней). `getByDistrict` на этой странице не
вызываем.

**Структура экрана:**

1. **3 trend-карточки со спарклайнами** — `<TrendCard/>` ×3 (мокаповские
   «активные пользователи» и «среднее голосов» заменены реальными метриками):
   | Карточка | Большое число | Спарклайн |
   |---|---|---|
   | Решено за 7 дней | `monthlyKpis.resolvedWithin7dPct` % | хвост 7 из `trends` (resolved) |
   | Среднее время решения | `monthlyKpis.avgResolutionHours` | нет ряда → спарклайн скрыт |
   | Жалоб создано | сумма `created` по `trends` | хвост 7 (created) |
2. **row-2 (две карточки):**
   - **«SLA по категориям»** — `getSla(period)`, список `<SlaCategoryRow/>`:
     категория, норматив `slaHours`, факт `avgResolutionHours`, цветовой
     статус (в норме / близко к лимиту / превышение) по `breachPct`.
   - **«Голоса как сигнал приоритизации»** — `getVotesImpact(period)`,
     бакеты `50+ / 10–49 / 1–9 / 0` с `avgResolutionHours`, горизонтальные
     бары `<HBar/>` + инфоблок-вывод (как в мокапе).

Состояния — те же правила, что на Overview (скелетоны / ошибка+повтор /
«Нет данных» при пустом периоде / `null` → «—»).

---

## Секция D — Компоненты, структура файлов, тесты

### Переиспользуемые компоненты (`web-admin/src/components/dashboard/`)

Выносим только то, что используется в 2+ местах или нетривиально:
- `KpiCard.tsx` — иконка, значение, подпись, опциональная дельта-плашка
  (↑/↓, цвет); дельту считает по `value`/`prevValue`.
- `Sparkline.tsx` — 7 CSS-столбиков с подсветкой последнего.
- `HBar.tsx` — горизонтальный бар «подпись — полоса — число»; используют
  «Топ районов» и «Голоса как сигнал».
- `DashboardCard.tsx` — обёртка карточки (заголовок + подзаголовок + тело),
  если в коде Day 16 такой обёртки нет; иначе переиспользуем существующую.

### Секционные компоненты (одноразовые, рядом со страницей)

- `src/pages/overview/` — `SlaAlertBanner`, `StatusPipeline`, `DailyBarChart`,
  `TopDistricts`, `TopVotedComplaints`.
- `src/pages/analytics/` — `PeriodSwitcher`, `TrendCard`, `SlaCategoryRow`,
  `VotesImpactCard`.

### API-слой (`src/api/`)

- `types.ts` — добавить `MonthlyKpis`, `DailyPoint`, `TrendsResponse`,
  `CategoryStat`, `DistrictStat`, `SlaStat`, `VotesBucket`, `AnalyticsPeriod`;
  расширить `AnalyticsOverview` полем `monthlyKpis`.
- `analytics.ts` — добавить `getTrends()`, `getByCategory(period)`,
  `getByDistrict(period)`, `getSla(period)`, `getVotesImpact(period)`.

### Хуки

- `src/hooks/useOverviewData.ts`, `useAnalyticsData.ts` — инкапсулируют
  `useQuery`-вызовы (`queryKey`, `refetchInterval`, `period`); страницы
  получают готовые данные и флаги загрузки/ошибки.

### Тесты

Бэкенд (Kotlin): см. Секцию A.

Фронт (Vitest, в духе существующих 20 зелёных):
- `OverviewPage.test.tsx` — KPI из мок-данных; баннер виден при
  `slaBreachCount > 0` и скрыт при `0`; `null` → «—».
- `AnalyticsPage.test.tsx` — смена периода меняет `queryKey` / перезапрашивает;
  рендер SLA-списка.
- `analytics.api.test.ts` — `period` уходит в query-параметры запросов.

---

## Definition of Done

1. `./gradlew :backend:test` — зелёный, включая новые тесты аналитики.
2. `npm test` в `web-admin` — зелёный.
3. `npm run build` в `web-admin` — без ошибок типов.
4. Ручная проверка на dev-сиде (V99): Overview и Analytics наполнены реальными
   данными; переключатель периода работает; SLA-баннер появляется при наличии
   просрочек и исчезает при их отсутствии.
5. Коммит в git с понятным сообщением; отметка Day 17A в `docs/PLAN.md`.

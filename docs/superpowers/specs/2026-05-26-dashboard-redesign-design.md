# Редизайн дашборда: Overview vs Analytics + P0 метрики

**Дата:** 2026-05-26
**Статус:** дизайн утверждён, готов к написанию плана реализации
**Связанные документы:**
- Предыдущий: `2026-05-24-day17a-dashboard-design.md` (первичная реализация дашборда)
- Изменения: `2026-05-24-overview-top-categories-design.md`, `2026-05-24-status-pipeline-monthly-design.md`

---

## Контекст

День 17A (дашборд) реализован и влит в `main` (см. память
`project_cleancity_day17a_done`). При аудите против индустриальных практик
(SeeClickFix Reports, NYC311, Chicago Data Portal, Stephen Few, Polco —
полный список в разделе «Источники») выявлены следующие проблемы:

1. **Гибрид Overview/Analytics.** Operational и analytical дашборды по индустрии
   разводятся по аудитории и частоте обновления. Сейчас на Overview
   показывается аналитический контент (топ-районов, топ-категорий со средним
   временем решения), а на Analytics — оперативный (тренд за 7 дней).
2. **Anti-patterns Stephen Few:** среднее время решения без распределения
   («Choosing a Deficient Measure»), KPI «% решено» без целевого значения
   («Inadequate Context»), карточки без call-to-action (некликабельны).
3. **Отсутствуют три базовых KPI** из эталонного набора SeeClickFix Reports:
   время до первого действия (DTA, acknowledge), reopen rate, backlog как
   отдельный показатель.
4. **Мёртвый endpoint:** `/analytics/votes-impact` реализован, но UI не
   использует. Голоса жителей — ценный сигнал приоритизации.
5. **Мёртвая кнопка:** «Экспорт PDF» отключена на Analytics (это под-проект 17D,
   но в текущем виде создаёт мусор).

## Цель

Переразложить дашборд на два экрана с **разными аудиториями и метриками**,
закрыть пробелы P0 (DTA, Backlog, Reopen rate), починить anti-patterns
(целевые значения, медиана+p90 вместо среднего, кликабельные карточки),
вернуть к жизни `votes-impact`.

## Решения, принятые при брейнсторминге

- **Объём:** P0 (3 новые метрики) + разделение Overview/Analytics + anti-patterns.
  Heat-map по карте, leaderboard исполнителей, funnel — отложены (P2).
- **Reopen rate:** считаем по повторным жалобам на тот же адрес.
  Параметры: **радиус 50м, окно 30 дней, та же категория**. Зашиваем как
  константы (не env-флаги) — для дипломной защиты простоты достаточно.
- **DTA (Days/Time to Acknowledge):** считаем как время от `created_at` до
  первого перехода `NEW → IN_PROGRESS` для конкретной жалобы. Других
  сигналов acknowledgement в модели нет.
  На Overview агрегат — среднее DTA по жалобам, acknowledged за последние
  24 часа (скользящее окно, не календарный «сегодня»). Это даёт стабильную
  метрику в любое время суток.
- **Backlog:** просто `count(status IN (NEW, IN_PROGRESS))` на момент запроса.
- **Очередь горящих жалоб:** Top-10 жалоб с приближающимся/нарушенным SLA,
  сортировка по времени до дедлайна. Не отдельный раздел, а блок на Overview.
- **Median + p90 вместо среднего** для resolution time везде, где раньше было
  только average.
- **Целевые значения** — глобальные константы в backend (одно `SLA_TARGET_PCT=80`,
  одно `REOPEN_TARGET_PCT=10`, одно `DTA_TARGET_HOURS=24`); per-category цели
  не нужны.

## Вне объёма (явно)

- **Heat-map** жалоб на карте Сочи — отложено.
- **Leaderboard исполнителей** — в текущей модели жалоб нет поля
  `assignee_id`, добавление выходит за рамки P0.
- **Funnel** Created → Acknowledged → Closed — отдельной визуализации не
  делаем (три карточки KPI и без того дают эту воронку численно).
- **Drill-down** глубже клика-в-список жалоб с фильтром — не делаем
  (раскопки отдельной аналитики через табы и т.п.).
- **CSV/PDF экспорт** — мёртвую кнопку «Экспорт PDF» убираем; экспорт
  остаётся в скоупе 17D.
- **Локализация SLA target per category** — одно глобальное значение, без
  настройки per category.
- **Per-район SLA-цели** — равно как глобальная цель.

---

## Секция A — Раскладка экранов

### Overview (operational, для оператора/инспектора)

**Аудитория:** диспетчер, инспектор. **Refresh:** 60 сек (как сейчас).
**Фильтра периода нет** — это real-time экран. Все метрики либо точечные
(snapshot now), либо со скользящим окном с фиксированной длиной.

| Блок | Состав | Источник данных |
|---|---|---|
| Header — 4 KPI-карточки | (1) **Backlog** — `count(status IN (NEW, IN_PROGRESS))` на момент запроса; (2) **Просрочено по SLA сейчас** — `count(status IN (NEW, IN_PROGRESS) AND now() > slaDueAt)`; (3) **Среднее DTA за последние 24ч** (целевое значение ≤24ч); (4) **Создано сегодня** (с дельтой к вчера, в часовом поясе Europe/Moscow) | `GET /analytics/operational` |
| Status Pipeline | NEW / IN_PROGRESS / RESOLVED / REJECTED / DUPLICATE — снапшот по жалобам за последние 30 дней (preserving текущую семантику Day 17A); секции кликабельны → список с фильтром по статусу | `GET /analytics/operational` (поле statusBreakdown) |
| Burning Queue | Top-10 жалоб, сортировка по `slaDueAt ASC`, фильтр `status IN (NEW, IN_PROGRESS)`. Поля: id, title, district, category, createdAt, slaDueAt, secondsToDeadline (отрицательное = overdue). Строка кликабельна → деталь жалобы | `GET /analytics/burning?limit=10` |

**Целевые роуты для кликабельных KPI-карточек:**
- Backlog → `/complaints?status=NEW,IN_PROGRESS`
- Просрочено → `/complaints?status=NEW,IN_PROGRESS&slaState=overdue`
- DTA → `/complaints?sort=createdAt&direction=desc` (последние жалобы — там виден DTA-контекст)
- Создано сегодня → `/complaints?createdAfter=<today-MSK-00:00>`

### Analytics (strategic, для админа/мэрии)

**Аудитория:** руководитель. **Refresh:** при открытии или вручную (без
интервала). **Фильтр периода:** переключатель `WEEK / MONTH / QUARTER / YEAR / ALL`
(по умолчанию MONTH).

| Блок | Состав | Источник данных |
|---|---|---|
| Header — 4 KPI-карточки | (1) **% within SLA** с целью ≥80% (цвет: ≥80% зелёный, 60–79% оранжевый, <60% красный); (2) **Median + p90 resolution time** — без целевого значения на карточке (агрегат по всем категориям, цели per-category в таблице SLA ниже); (3) **Reopen rate** с целью ≤10% (цвет: ≤10% зелёный, 10–20% оранжевый, >20% красный); (4) **Throughput** (закрыто за период) | `GET /analytics/strategic?period=...` |
| Тренд created vs resolved | Line chart, две линии, группировка день/неделя/месяц в зависимости от периода | `GET /analytics/trends?period=...&groupBy=...` |
| Equity по районам | Таблица: район × volume × % within SLA × median resolution time | `GET /analytics/by-district?period=...` |
| Топ категорий | Таблица: категория × volume × % within SLA × median resolution time | `GET /analytics/by-category?period=...` |
| SLA по категориям | Норматив × факт × цвет — остаётся как есть | `GET /analytics/sla?period=...` |
| Votes Impact | Гистограмма по бакетам голосов: как зависит время решения от количества голосов | `GET /analytics/votes-impact?period=...` |

---

## Секция B — Бэкенд

### Новые/изменённые endpoints

Все под `/analytics`, авторизация ADMIN/OPERATOR/INSPECTOR (как уже принято).

| Endpoint | Статус | Описание |
|---|---|---|
| `GET /analytics/operational` | **новый** | Снапшот для Overview: backlog, overdueNow, avgDtaToday, createdToday, createdYesterday, statusBreakdown |
| `GET /analytics/burning?limit=10` | **новый** | Top-N жалоб, сортировка по slaDueAt ASC, фильтр `status IN (NEW, IN_PROGRESS)` |
| `GET /analytics/strategic?period=...` | **новый** | KPI-блок для Analytics: slaCompliancePct + target, medianResolutionHours + p90, reopenRate + target, throughput |
| `GET /analytics/trends?period=...&groupBy=day\|week\|month` | **изменён** | Расширяем горизонт (был фиксированный 30д) + новый параметр `groupBy` + новые ряды `createdSeries`/`resolvedSeries` |
| `GET /analytics/by-category?period=...` | **изменён** | Добавляем `medianResolutionHours`, `p90ResolutionHours`, `slaCompliancePct`. Существующее `avgResolutionHours` оставляем для обратной совместимости с фронтом, пока он не перейдёт |
| `GET /analytics/by-district?period=...` | **изменён** | Добавляем `medianResolutionHours`, `slaCompliancePct` |
| `GET /analytics/reopen?period=...` | **новый** | Reopen-rate с деталью: reopenRate, reopenCount, resolvedCount |
| `GET /analytics/overview` | **deprecated** | Содержимое поглощено `operational` + `strategic`. Endpoint оставляем, чтобы не ломать релизы; удалим после миграции UI |
| `GET /analytics/votes-impact` | **без изменений** | Уже есть, только UI добавляем |

### DTO

Файл `shared/src/commonMain/kotlin/.../models/AnalyticsResponse.kt`:

```kotlin
@Serializable
data class OperationalSnapshot(
    val backlog: Int,                     // count(NEW + IN_PROGRESS) на момент запроса
    val overdueNow: Int,                  // count(open and now() > slaDueAt) на момент запроса
    val avgDtaHours24h: Double?,          // среднее DTA по жалобам, ack-нутым за последние 24ч
    val dtaTargetHours: Double,           // = 24.0
    val createdToday: Int,                // count за текущий день в Europe/Moscow
    val createdYesterday: Int,            // для дельты
    val statusBreakdown: Map<String, Int> // NEW/IN_PROGRESS/RESOLVED/REJECTED/DUPLICATE за последние 30 дней
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
    val slaTargetPct: Double,             // = 80.0
    val medianResolutionHours: Double?,
    val p90ResolutionHours: Double?,
    val reopenRate: Double,
    val reopenTargetPct: Double,          // = 10.0
    val throughput: Int                   // закрыто за период
)

@Serializable
data class ReopenStat(
    val reopenRate: Double,
    val reopenCount: Int,
    val resolvedCount: Int
)

// Расширение существующего:
@Serializable
data class TrendsResponse(
    val createdSeries: List<TrendPoint>,
    val resolvedSeries: List<TrendPoint>,
    val groupBy: String                    // "day" | "week" | "month"
)

@Serializable
data class TrendPoint(
    val bucketStart: Instant,
    val value: Int
)

// Расширение существующих CategoryStat / DistrictStat:
// добавляются поля medianResolutionHours, p90ResolutionHours, slaCompliancePct
```

### Данные: что нужно из БД

- **Reopen-rate** требует пространственного поиска. Проверить, есть ли PostGIS
  в существующих миграциях (`backend/src/main/resources/db/migration/`).
  Если нет — добавить миграцию `V<NN>__enable_postgis.sql` с
  `CREATE EXTENSION IF NOT EXISTS postgis;` и `ALTER TABLE complaints ADD COLUMN location geography(Point, 4326) GENERATED ALWAYS AS ...;`.
  Альтернатива без PostGIS: считать Haversine в SQL через `acos(sin(lat)*sin(lat2) + cos(lat)*cos(lat2)*cos(lon2-lon))`. Дороже по CPU, но без новой extension.

  **Решение по умолчанию:** PostGIS если не уже включён; иначе Haversine.
  Окончательно зафиксируем при написании плана.

- **Median/p90** — `percentile_cont(0.5) WITHIN GROUP (ORDER BY ...)` и
  `percentile_cont(0.9)`. Plain Postgres, без extensions.

- **DTA** — нужна история переходов статусов. По памяти
  `project_cleancity_day17a_done` и устной декомпозиции — таблица
  `status_history` уже существует. План реализации проверит её схему и при
  необходимости добавит индекс по `(complaint_id, created_at)`.

### Концепт SQL: reopen-rate (PostGIS)

```sql
WITH resolved AS (
  SELECT id, category, resolved_at, location
  FROM complaints
  WHERE status = 'RESOLVED'
    AND resolved_at BETWEEN :periodStart AND :periodEnd
),
reopens AS (
  SELECT DISTINCT r.id
  FROM resolved r
  JOIN complaints c2 ON c2.id <> r.id
    AND c2.category = r.category
    AND c2.created_at >  r.resolved_at
    AND c2.created_at <= r.resolved_at + INTERVAL '30 days'
    AND ST_DWithin(c2.location, r.location, 50)
)
SELECT
  count(*) FILTER (WHERE rr.id IS NOT NULL)::float
    / NULLIF(count(*), 0) AS reopen_rate,
  count(*) FILTER (WHERE rr.id IS NOT NULL)::int AS reopen_count,
  count(*)::int AS resolved_count
FROM resolved r
LEFT JOIN reopens rr ON rr.id = r.id;
```

### Концепт SQL: median + p90

```sql
SELECT
  percentile_cont(0.5) WITHIN GROUP (
    ORDER BY EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
  ) AS median_hours,
  percentile_cont(0.9) WITHIN GROUP (
    ORDER BY EXTRACT(EPOCH FROM (resolved_at - created_at))/3600.0
  ) AS p90_hours
FROM complaints
WHERE status = 'RESOLVED'
  AND resolved_at BETWEEN :periodStart AND :periodEnd;
```

### Концепт SQL: DTA за последние 24ч (скользящее окно)

```sql
SELECT AVG(EXTRACT(EPOCH FROM (sh.changed_at - c.created_at))/3600.0)
FROM complaints c
JOIN LATERAL (
  SELECT changed_at FROM status_history
  WHERE complaint_id = c.id AND to_status = 'IN_PROGRESS'
  ORDER BY changed_at ASC LIMIT 1
) sh ON true
WHERE sh.changed_at >= now() - INTERVAL '24 hours';
```

Окно — по моменту acknowledgement, не по моменту создания: метрика отражает
свежие действия команды. `createdToday` / `createdYesterday` отдельно
считаются по календарному дню в Europe/Moscow.

---

## Секция C — Frontend (web-admin, React)

### Новые компоненты

- `BurningQueueTable` — таблица Top-10 для Overview, строка кликабельна → деталь жалобы.
- `KpiCardWithTarget` — карточка с целевым значением (отображает «факт / цель» и цвет статуса).
- `MedianP90Card` — median + p90 на одной карточке.
- `ReopenRateCard` — на карточке: % + цель + total reopens / resolved.
- `VotesImpactChart` — гистограмма по бакетам голосов.
- `EquityTable` — район × volume × % SLA × median (заменяет текущий `DistrictBarChart` на Overview).
- `PeriodSwitcher` — расширяем варианты на Analytics: WEEK / MONTH / QUARTER / YEAR / ALL. На Overview period switcher не используется (real-time экран).

### Удаляем

- Текущий `DistrictBarChart` (Топ-4) с Overview — переезжает на Analytics как `EquityTable` (расширенный набор колонок).
- Текущий `CategoryTopList` со средним временем с Overview — переезжает на Analytics; среднее заменяется на median+p90.
- Текущая KPI-карточка «Жалоб за месяц» (`MonthlyKpis.total/prevTotal`) с Overview — заменяется на «Backlog» + «Создано сегодня».
- Текущая KPI-карточка «Среднее время решения» (`MonthlyKpis.avgResolutionHours`) с Overview — переезжает в Analytics в виде median+p90.
- Текущая KPI-карточка «Решено за 7 дней %» (`MonthlyKpis.resolvedWithin7dPct`) с Overview — заменяется в Analytics на «% within SLA» (более точная метрика, учитывает per-category норматив).
- Кнопка «Экспорт PDF» (мёртвая) с Analytics — целиком убираем UI-элемент. (Если 17D дойдёт до релиза — добавим заново.)
- Файл `web-admin/src/pages/analytics/VotesImpactCard.tsx` уже удалён в текущем рабочем дереве — восстанавливаем как новый компонент с правильной интеграцией.
- DTO `MonthlyKpis` (shared) помечается `@Deprecated` вместе с `/analytics/overview`; удаляется одной итерацией после миграции UI.

### Изменяем

- `OverviewPage`: полностью перекомпонован — header KPI + Status Pipeline + Burning Queue. Без переключателя периода (real-time).
- `AnalyticsPage`: header KPI + Trend (created vs resolved) + Equity + Category + SLA + Votes Impact. Фильтр периода WEEK/MONTH/QUARTER/YEAR/ALL.
- Все KPI-карточки делаем кликабельными → роут `/complaints?<filter>`.
- `getOverview()` в `src/api/analytics.ts` — заменяем на `getOperational()` + `getStrategic()`. Старый оставляем под `// deprecated` пока UI не переедет.
- `dashboardQueries.ts` — добавляем `useOperationalQuery`, `useBurningQuery`, `useStrategicQuery`, `useReopenQuery`; `useOverviewQuery` помечаем deprecated.

---

## Секция D — Целевые значения SLA (industry baseline)

Источников единой «нормативной» цифры нет; ниже — отраслевые ориентиры,
из которых выбраны константы.

| KPI | Цель | Источники |
|---|---|---|
| **% within SLA** (compliance) | **≥ 80%** | NYC311 публикует per-agency % closed within SLA, среднее по агентствам колеблется в 70–95% [council.nyc.gov/data/311-agency]. Chicago Data Portal публикует median response time per type — без agreed-SLA % [data.cityofchicago.org]. SeeClickFix Reports позиционируют «% within SLA» как первый KPI [civicplus.help]. **80%** — типовая baseline-цель городских CRM. |
| **DTA target** (acknowledge) | **≤ 24ч** | Boston 311 ставит «same-day acknowledgment» [civicplus.com/case-studies]. SeeClickFix DTA = «average working days to acknowledge». Для дипломной защиты 24ч (1 рабочий день) — отраслевая планка. |
| **Median resolution time** (цель на карточке) | **≤ норматив SLA категории** | Каждая категория уже имеет норматив (24/48/72/120ч). Карточка Median p90 показывает «median ≤ норматив = зелёный». |
| **Reopen rate** | **≤ 10%** | Reopen rate упомянут CivicPlus как часть стандартизованного 311-набора [civicplus.com/blog/crm]. Конкретного публичного бенчмарка не нашли — **10%** взят как мягкая baseline-цель, может быть откалибрована после внедрения в Сочи. |
| **Closure rate (информативно)** | n/a | Detroit (SeeClickFix-инсталляция) даёт 97% closure rate [civicplus.com/case-studies]. На наш дашборд не выносим — закрытие без reopen rate легко гонится. |

**Где зафиксированы константы:** в backend-конфиге как часть
`AnalyticsConfig` (одно место правды), отдаются клиенту в DTO
`StrategicKpis` / `OperationalSnapshot` рядом со значением. Фронт не зашивает
цифры — берёт из ответа сервера. **Альтернатива:** env-переменные. Для
дипломной защиты значения статичны, env не требуется — отложено до Сочи-внедрения.

**Существующие SLA per category** (24/48/72/120ч) — не трогаем; они и есть
норматив для расчёта compliance.

---

## Секция E — Тестирование

### Backend (unit)

- `reopen-rate`: фикстуры с парами в радиусе/вне радиуса, в окне/вне окна, той же/разной категории; ноль RESOLVED за период → `reopen_rate = null/0` без деления на ноль.
- `percentile_cont`: фикстуры с известным распределением (1, 2, 3, 100ч) → median = 2.5, p90 = 70.3.
- `DTA за 24ч`: переходы вне окна 24ч (старше now-24h) не попадают; жалобы acknowledged внутри окна — попадают независимо от часового пояса; пустое окно → `null`.
- `createdToday/Yesterday`: пересечения суток в часовом поясе Europe/Moscow считаются корректно; жалоба создана в 23:59 не попадает в «yesterday» при запросе в 00:30 уже на следующие сутки.
- `burning queue`: сортировка корректна, не включает RESOLVED/REJECTED/DUPLICATE.
- `trends groupBy`: day/week/month дают корректные bucket'ы; пустые периоды отдают точки с нулём.

### Frontend (unit / smoke)

- KPI-карточки рендерятся при нулевых данных без падений.
- Клик на KPI-карточку ведёт на `/complaints?<expected-filter>`.
- `PeriodSwitcher` правильно переключает endpoint-параметр.
- `BurningQueueTable` сортирует и подсвечивает overdue корректно.

### Manual

- Локально на dev-сиде (V99): проверить что все шесть новых/изменённых endpoint'ов отдают данные не-null и не-исключения.
- Локально: переключение периодов на Analytics не ломает страницу.
- Локально: режим без данных (свежая БД, миграции прокатаны, нет жалоб) — все экраны рендерятся «нулевыми», без exceptions.

---

## Секция F — Миграционный план

1. Backend сначала: новые endpoints добавляются параллельно со старыми, старые
   не удаляются. PostGIS-миграция (если нужна) накатывается отдельно.
2. После того как backend задеплоен, фронт переезжает на новые endpoints.
3. После того как фронт переехал — старый `/analytics/overview` помечается
   `@Deprecated` (Kotlin) и через одну итерацию удаляется.
4. Семантика «overview = снимок, trends = ряд» из 17A сохраняется, расширяется.

Текущие незакоммиченные изменения в рабочем дереве (announcements + анализ
существующего AnalyticsPage) — не зависят от этого spec'a; коммитятся
независимо.

---

## Источники

**Индустриальные практики и эталонные дашборды:**
- SeeClickFix / CivicPlus — *Understand and Use Requests Reports*. https://www.civicplus.help/hc/en-us/articles/360043475254
- CivicPlus — *Is your Legacy 311 process working against you?* https://www.civicplus.com/blog/crm/is-your-legacy-311-process-working-against-you/
- CivicPlus — *Detroit fixes 97% of citizen requests with SeeClickFix*. https://www.civicplus.com/case-studies/crm/detroit-mi-fixes-97-of-citizen-requests-with-seeclickfix/
- CivicPlus — *What is a 311 CRM Solution?* https://www.civicplus.com/blog/crm/what-is-a-311-and-citizen-request-management-solution/
- NYC City Council Data — *Are City Agencies Responding to 311?* https://council.nyc.gov/data/311-agency/
- NYC Office of the State Comptroller — *NYC311 Monitoring Tool*. https://www.osc.ny.gov/reports/osdc/nyc311-monitoring-tool
- Chicago Data Portal — *311 Service Requests — Median Response Time*. https://data.cityofchicago.org/Service-Requests/311-Service-Requests-Median-Response-Time/u6fz-87ei
- What Works Cities — *Pittsburgh Dashburgh*. https://whatworkscities.bloomberg.org/cities/pittsburgh-pennsylvania-usa/

**Российские аналоги:**
- «Добродел» (Московская область): нормативный ответ **8 рабочих дней**. https://maximum.mosreg.ru/deyatelnost/proekty/portal-dobrodel
- «Наш Санкт-Петербург»: классификатор + SLA per категория. https://gorod.gov.spb.ru/about/

**Принципы дизайна дашбордов:**
- Klipfolio — *4 Types of Dashboards*. https://www.klipfolio.com/blog/starter-guide-to-dashboards
- iDashboards — *Three Types of Dashboards*. https://www.idashboards.com/operational-analytical-and-strategic-the-three-types-of-dashboards/
- Yellowfin BI — *Operational, Strategic or Analytical*. https://www.yellowfinbi.com/blog/operational-strategic-or-analytical-dashboard-which-type-best-for-bi
- Luzmo — *Strategic vs Tactical vs Operational*. https://www.luzmo.com/blog/dashboard-types-strategic-operational-tactical
- Stephen Few — *Common Pitfalls in Dashboard Design* (PDF). https://www.perceptualedge.com/articles/Whitepapers/Common_Pitfalls.pdf
- Polco — *Dashboard Theater*. https://blog.polco.us/dashboard-theater-how-local-governments-can-turn-metrics-into-meaningful-action
- ICMA — *Performance Measures for Local Government Customer Service*. https://icma.org/blog-posts/performance-measures-local-government-customer-service

**Академика / методология:**
- World Bank IEG — *Grievance Redress Mechanisms*. https://ieg.worldbankgroup.org/taxonomy/term/15629896
- World Bank — *Assessing Project-Level GRMs*. https://openknowledge.worldbank.org/server/api/core/bitstreams/bd391cff-3eaa-5fbb-8708-4e24ad7d32c5/content
- Schiff, *Public Administration Review* 2025 — *Does collective citizen input impact government service provision?* https://onlinelibrary.wiley.com/doi/10.1111/puar.13747
- *Towards Data Science* — *Analyzing NYC 311*. https://towardsdatascience.com/analyzing-and-modelling-nyc-311-service-requests-eb6a9c9adc7c/

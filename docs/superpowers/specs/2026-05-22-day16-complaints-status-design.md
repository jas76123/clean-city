# Day 16 — Web: жалобы + смена статусов — дизайн

**Дата:** 2026-05-22
**Проект:** CleanCity (`cleancity-kmp/`)
**Подход:** A — backend-first, затем фронт (как Day 12–13, subagent-driven).

## Контекст

Day 15 закрыт: `web-admin/` имеет auth + layout, маршрут `/complaints` — заглушка.
Day 16 строит экран жалоб для веб-админки: таблица + детальная панель + смена статусов.

Layout определён мокапом `docs/mockups/admin-dashboard-v2.html` → `screen-complaints`
(`complaints-layout`).

Бэкенд `GET /complaints` сейчас принимает `category`, `district`, `sort`
(date/votes/priority), пагинацию — но **не** `status`, хотя SPEC §400 это обещает.
`PATCH /complaints/{id}/status` и `GET /complaints/duplicates` готовы.

### Принятые решения (брейншторм 2026-05-22)

1. **SLA** — бэкенд: вычисляемые поля `slaDeadline`/`slaBreached` в ответе +
   query-фильтр `?slaBreached=true`. Корректно при server-side пагинации.
2. **Счётчики чипов** — глобальные, из готового `GET /analytics/overview`;
   рефетч после смены статуса. Не реагируют на категорию/район.
3. **Карта в деталях** — отложена. Показываем адрес + координаты текстом со
   ссылкой «Открыть в Яндекс.Картах» (для JS API нужен отдельный ключ типа
   «JavaScript API и HTTP Геокодер», которого пока нет).
4. **Сортировка** — три backend-варианта `DATE / VOTES / PRIORITY`. Отдельной
   сортировки «по времени SLA» из мокапа не делаем — SLA остаётся фильтром.
5. **Чип SLA** — часть той же single-select группы, что и чипы статуса (мокап:
   один ряд «Все / … / ⚠ SLA»). Выбор «⚠ SLA» = `slaBreached=true` без `status`;
   выбор статуса сбрасывает `slaBreached`; «Все» сбрасывает оба. Backend при этом
   поддерживает оба параметра независимо — ограничение чисто на уровне UI.

## 1. Backend-контракт

**Файлы:** `backend/.../complaints/{ComplaintRoutes,ComplaintService,ComplaintRepository}.kt`,
`shared/.../models/ComplaintResponse.kt`.

### Новые query-параметры `GET /complaints` (одиночные)

- `status` — один `ComplaintStatus`. Невалидное значение → 400 (через существующий
  `queryEnum`).
- `slaBreached` — boolean. `true` → только просроченные активные жалобы.

### Прокидка через слои

- `PublicListFilter` += `status: ComplaintStatus? = null`, `slaBreached: Boolean = false`.
- `ComplaintFilter` += те же два поля.
- `parsePublicFilter()` в `ComplaintRoutes.kt` парсит оба параметра.
- `buildCondition()`:
  - `if (status != null) op = op and (Complaints.status eq status.name)` — естественно
    пересекается с уже существующим `status inList visibleStatuses`, поэтому
    резидент/гость с `?status=REJECTED` получает **пустой список** без спецобработки.
  - `if (slaBreached) op = op and SlaBreachedExpr` — новый `Expression<Boolean>`
    (по образцу `PriorityScoreExpr`), SQL:
    `status IN ('NEW','IN_PROGRESS') AND created_at < NOW() - CASE category
    WHEN 'GARBAGE' THEN INTERVAL '24 hours' … END`. Нормативы — SPEC §4.8.

### SLA-поля в ответе

`ComplaintResponse` (в `shared/`) += `slaDeadline: String? = null`,
`slaBreached: Boolean = false`:

- Вычисляются в Kotlin в `ComplaintRow.toResponse()` через `CategorySla.hoursFor(category)`
  (объект уже есть в `shared/.../models/CategoryMeta.kt`).
- **Заполняются только для админа** (`viewer.role in ADMIN_ROLES`); для резидента/гостя
  → `null`/`false`. Это соблюдает SPEC §146 («метка SLA не видна жителям нигде») —
  поле не утекает в JSON жителю.
- `slaBreached = true` только для активных просроченных (NEW/IN_PROGRESS);
  для RESOLVED/REJECTED/DUPLICATE → `false`. `slaDeadline` вычисляется всегда
  (для админа).
- Новые поля с дефолтами → backward-compatible для mobile-клиента.

### Не трогаем

`PATCH /complaints/{id}/status` и `GET /complaints/duplicates` — готовы, используются
как есть.

## 2. Структура веб-страницы

Новые файлы в `web-admin/src/`:

```
api/
  types.ts          ← дополняем: ComplaintStatus, ProblemCategory,
                       ComplaintListItem, ComplaintDetail, StatusChange,
                       DuplicateCandidate, AnalyticsOverview
  complaints.ts     ← listComplaints(filter) · getComplaint(id) ·
                       changeStatus(id, req) · findDuplicates(lat,lon,category)
  analytics.ts      ← getOverview()
hooks/
  complaintQueries.ts ← TanStack Query: useComplaintsQuery, useComplaintQuery,
                        useOverviewQuery, useDuplicatesQuery, useChangeStatusMutation
lib/
  complaintMeta.ts  ← 18 категорий (label+иконка), статусы (label+цвет),
                       варианты сортировки, карта ALLOWED_TRANSITIONS.
                       Дублируем из shared/ — веб не на Kotlin.
pages/
  ComplaintsPage.tsx ← оркестратор: состояние фильтров, выбранный id, layout
components/complaints/
  ComplaintFilters.tsx     ← single-select группа чипов (статусы + ⚠ SLA, со
                              счётчиками) + селекты категории/района/сортировки
  ComplaintsTable.tsx      ← таблица; клик по строке → выбор
  ComplaintsPagination.tsx ← prev/next + «стр. N из M» (page/size, size=20)
  ComplaintDetailPanel.tsx ← правая панель
  PhotoGallery.tsx         ← миниатюры + просмотр крупно (lightbox)
  StatusBadge.tsx          ← цветной бейдж статуса + вариант «⚠ SLA»
  StatusHistory.tsx        ← лента истории статусов
  StatusChangeDialog.tsx   ← модалка действия: обяз. комментарий + (DUPLICATE) пикер
  DuplicatePicker.tsx      ← список кандидатов из /complaints/duplicates
```

**Layout** (из мокапа `screen-complaints` / `complaints-layout`): двухколоночный flex.

- Слева: `ComplaintFilters` сверху, под ним `ComplaintsTable`, внизу
  `ComplaintsPagination`.
- Справа: `ComplaintDetailPanel`, sticky. Пока строка не выбрана — пустая подсказка
  «Кликни по строке — справа откроется детальная карточка».

**Принцип изоляции:** `ComplaintsPage` владеет состоянием (фильтры, выбранный id) и
раздаёт его вниз через props; дочерние компоненты презентационные либо с одной чёткой
ответственностью. API-слой (`api/`) не знает про React; хуки (`hooks/`) —
единственный мост между API и компонентами.

**Состояние:** фильтры и выбранный id — локальный `useState` в `ComplaintsPage`
(без синка в URL — YAGNI для MVP).

**Маршрут:** в `App.tsx` заглушка `/complaints` заменяется на `<ComplaintsPage />`.

## 3. Поток данных

Query-ключи TanStack Query:

| Ключ | Что грузит | Когда |
|---|---|---|
| `['complaints', filter]` | список (`ComplaintListResponse`) | монтирование + смена фильтра/страницы |
| `['complaint', id]` | детали (фото, история, SLA) | при выборе строки |
| `['analytics','overview']` | счётчики для чипов | монтирование, рефетч после действия |
| `['duplicates', id]` | кандидаты-оригиналы | только когда открыт пикер DUPLICATE |

`filter = { status?, slaBreached, category?, district?, sort, page }`.

- **Начальная загрузка:** `ComplaintsPage` монтируется → параллельно
  `useComplaintsQuery` + `useOverviewQuery`.
- **Смена фильтра:** обновляется стейт, `page` сбрасывается в 0 → рефетч только
  списка. Счётчики (overview) при смене фильтра не рефетчатся — они глобальные.
  У списка `keepPreviousData`, чтобы таблица не мигала.
- **Выбор строки:** `selectedId` → `useComplaintQuery(id)`. Как `placeholderData`
  отдаём строку из кэша списка — панель рисуется мгновенно, затем обогащается полной
  загрузкой.

### Поток смены статуса

1. Кнопка действия в деталь-панели → `StatusChangeDialog` с целевым статусом.
2. Диалог: обязательный textarea комментария. Для DUPLICATE — ещё `DuplicatePicker`
   (`useDuplicatesQuery` по lat/lon/category жалобы). «Подтвердить» disabled, пока
   комментарий пуст (и для DUPLICATE — пока не выбран оригинал).
3. Submit → `useChangeStatusMutation` → `PATCH /complaints/{id}/status` body
   `{toStatus, comment, duplicateOfId?}`.
4. **Успех** → инвалидируем `['complaints']` (статус строки изменился — может выпасть
   из текущего фильтра), `['complaint', id]` (новый статус + запись истории),
   `['analytics','overview']` (счётчики сдвинулись). Toast «Статус изменён». Диалог
   закрывается, деталь-панель остаётся открытой с обновлённой жалобой.
5. **Ошибка** → toast с сообщением из `ApiError`. Диалог не закрывается.

### Доступные действия зависят от статуса

Веб зеркалит backend-карту `ALLOWED_TRANSITIONS` в `complaintMeta.ts`:

- `NEW` → «Принять в работу» / «Отклонить» / «Дубликат»
- `IN_PROGRESS` → «Решить» / «Отклонить» / «Дубликат»
- `RESOLVED / REJECTED / DUPLICATE` → терминальные, кнопок действий нет.

Backend всё равно валидирует переход (409 на недопустимый) — веб-карта только прячет
заведомо невалидные кнопки.

## 4. Обработка ошибок и крайние случаи

Базовый слой уже есть (Day 15): axios-интерцепторы с auto-refresh JWT (401 → refresh →
повтор), `api/errors.ts`, `ProtectedRoute` — не дублируем.

**Ошибки запросов:**

| Запрос упал | Поведение |
|---|---|
| Список | таблица → состояние ошибки + кнопка «Повторить» |
| Детали | деталь-панель → ошибка + «Повторить» |
| Overview | в чипах вместо числа «—», чипы остаются кликабельны |

**Ошибки `changeStatus` по кодам:**

- **409** — недопустимый переход (терминальная жалоба / параллельная смена другим
  админом). Toast «Статус жалобы изменился — панель обновлена», инвалидируем
  `['complaint', id]` → кнопки перерисовываются под актуальный статус. Диалог
  закрывается.
- **400** — пустой/длинный комментарий или плохой `duplicateOfId`. Веб сюда не должен
  доходить (валидация формы), toast с текстом ошибки на подстраховку. Диалог открыт.
- **404** — жалоба не найдена. Toast + закрываем деталь-панель, инвалидируем список.
- **403 / сеть** — toast, диалог остаётся открытым, можно повторить.

**Крайние случаи:**

- **Пустой список** под фильтр → empty state «Под выбранные фильтры ничего не нашлось»
  + кнопка «Сбросить фильтры».
- **DUPLICATE без кандидатов** — пикер вернул 0 → сообщение «Поблизости активных жалоб
  той же категории нет», «Подтвердить» остаётся disabled.
- **Параллельное редактирование** — 409 → рефетч деталя (см. выше).
- **Битое фото из S3** — `<img onError>` → плейсхолдер «фото недоступно».
- **Длинный комментарий** — textarea со счётчиком и `maxLength=2000` (зеркалим
  backend-лимит), submit блокируется при превышении.
- **Загрузка** — skeleton-строки при первой загрузке; при `keepPreviousData` — лёгкий
  индикатор поверх старых данных, без мигания.

## 5. Тестирование

**Backend (Kotlin, `./gradlew :backend:test`)** — пишем по TDD:

- `ComplaintService` / repo-тесты:
  - `status=NEW` → только NEW.
  - `status=REJECTED` глазами резидента → пусто; глазами админа → возвращает REJECTED.
  - `slaBreached=true` → активная просроченная жалоба попадает; свежая и `RESOLVED` — нет.
  - SLA-поля: для админа `slaDeadline`/`slaBreached` заполнены; для резидента →
    `null`/`false`.
- Routes-тест `ComplaintRoutesTest` (создаём, его пока нет): `?status=INVALID` → 400;
  `?status=NEW` → 200 и только NEW; `?slaBreached=true` → 200.

**Web (Vitest + Testing Library + MSW — паттерн Day 15):**

- `complaintMeta.test.ts` — карта переходов отдаёт верные действия для каждого статуса;
  лейблы/цвета статусов.
- `ComplaintFilters.test.tsx` — клик по чипу → `onChange` с нужным статусом; счётчики
  из overview; «—» когда overview недоступен.
- `ComplaintsTable.test.tsx` — рендер строк, клик → `onSelect(id)`, empty state.
- `StatusChangeDialog.test.tsx` — «Подтвердить» disabled при пустом комментарии и
  (для DUPLICATE) пока не выбран оригинал; submit шлёт верный body.
- `ComplaintsPage.test.tsx` (интеграционный, MSW) — загрузка списка + счётчиков; смена
  фильтра рефетчит список; смена статуса → инвалидация → строка обновилась/выпала;
  409 → toast.

**Verification перед закрытием дня:** `./gradlew :backend:test` зелёный ·
`cd web-admin && npm run test` зелёный · `npm run build` (tsc) без ошибок.

**Ручной чекпоинт Day 16** — «Сменить статус → mobile-юзер получил push с комментарием»:

1. Поднять backend локально + `cd web-admin && npm run dev` (:5173); сид-админ
   `admin@cleancity.dev` / `Admin12345!`.
2. Телефон Samsung A33 (`adb reverse tcp:8081`) — резидент создаёт жалобу.
3. В веб-админке открыть жалобу → «Принять в работу» с комментарием.
4. На телефоне приходит уведомление (polling-канал — решение от 2026-05-11, не FCM)
   с текстом комментария.
5. Проверить DUPLICATE: пометить дубликатом, выбрать оригинал → голоса смержились на
   оригинал.

## Вне scope Day 16

- Карта на Yandex Maps JS API (нужен отдельный JS-ключ) — отложена.
- Сортировка «по времени SLA» — не делаем, SLA только фильтр.
- Счётчики чипов с учётом категории/района — глобальные из overview.
- OpenAPI-генерация типов — backlog, типизируем DTO вручную в `api/types.ts`.

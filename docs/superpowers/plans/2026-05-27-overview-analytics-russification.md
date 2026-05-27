# Русификация страниц «Обзор» и «Аналитика» — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Заменить заимствования «Топ» и «Тренды» на русские эквиваленты на страницах `/overview` и `/analytics` web-admin. Аббревиатуру «SLA» оставить во всех местах по решению пользователя.

**Architecture:** Точечные правки строковых литералов в 3 файлах + один пин в тестах. Никакой смены логики, никакой смены типов/API. Каждая правка — отдельный коммит, тесты прогоняются перед каждым commit'ом.

**Tech Stack:** React + TypeScript, Vite, Vitest + Testing Library (`@testing-library/react`).

**Spec:** [`docs/superpowers/specs/2026-05-27-overview-analytics-russification-design.md`](../specs/2026-05-27-overview-analytics-russification-design.md)

**Допущения по среде:**
- Все команды выполняются из корня репозитория `~/Desktop/Myapp/cleancity-kmp/` (если в шаге сказано `cd web-admin && …` — выполняется именно так, без дополнительных `cd`).
- `npm install` в `web-admin/` уже выполнен в этой ветке (по памяти проекта — да).

---

## Предварительные условия (важно)

На момент написания плана в репозитории есть **некоммитнутые изменения**, в том числе в `web-admin/src/pages/AnalyticsPage.tsx`. План делает точечную правку этого файла, и обычный `git add <файл>` поставит в индекс **весь** диф файла, не только нашу строку.

Перед стартом убедитесь в одном из:
- WIP в `AnalyticsPage.tsx` закоммичен или стэшнут (`git stash push -- web-admin/src/pages/AnalyticsPage.tsx`), **либо**
- На шаге коммита Task 3 используется `git add -p web-admin/src/pages/AnalyticsPage.tsx` — выбрать только хунк с подзаголовком.

Остальные файлы (`TopDistricts.tsx`, `TopProblemCategories.tsx`, `OverviewPage.test.tsx`) на момент написания плана чисты.

---

## Файловая структура

Затрагиваем:

- **Modify:** `web-admin/src/pages/overview/TopDistricts.tsx:15` — заголовок блока «Топ районов» → «Районы — лидеры»
- **Modify:** `web-admin/src/pages/overview/TopProblemCategories.tsx:11` — заголовок блока «Топ-5 категорий» → «Главные категории»
- **Modify:** `web-admin/src/pages/OverviewPage.test.tsx:50` — ассерт пина строки на новый
- **Modify:** `web-admin/src/pages/AnalyticsPage.tsx:39` — подзаголовок «Тренды… SLA…» → «Динамика… SLA…»

Не трогаем: компоненты `SlaAlertBanner`, `SlaByCategory`, `StatusPipeline`, `KpiCard`, `Sparkline`, `HBar`, типы `SlaStat`/`AnalyticsPeriod`, JSDoc-комменты и описания `it('…')`.

---

## Task 1: `TopDistricts` — заголовок блока районов

**Files:**
- Modify: `web-admin/src/pages/overview/TopDistricts.tsx:15`

Пинов на эту строку в тестах нет — TDD-шаг с тестом не требуется. Изменение проверяется регрессом существующего набора + визуально.

- [ ] **Step 1: Заменить строку**

В файле `web-admin/src/pages/overview/TopDistricts.tsx` найти строку 15:

```tsx
      <div className="mb-3 text-sm font-medium text-slate-700">Топ районов по жалобам</div>
```

Заменить на:

```tsx
      <div className="mb-3 text-sm font-medium text-slate-700">Районы — лидеры по жалобам</div>
```

- [ ] **Step 2: Прогнать тесты страницы «Обзор»**

```bash
cd web-admin && npx vitest run src/pages/OverviewPage.test.tsx
```

Expected: PASS, ничего не сломалось (этот заголовок нигде не запинен).

- [ ] **Step 3: Коммит (из корня репо)**

```bash
git add web-admin/src/pages/overview/TopDistricts.tsx
git commit -m "$(cat <<'EOF'
chore(web-admin): «Топ районов» → «Районы — лидеры по жалобам»

Убираем заимствование «Топ» из заголовка блока на странице «Обзор».

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `TopProblemCategories` — заголовок блока категорий (с обновлением теста)

**Files:**
- Modify: `web-admin/src/pages/OverviewPage.test.tsx:50` (тест-пин — меняем первым, TDD)
- Modify: `web-admin/src/pages/overview/TopProblemCategories.tsx:11` (реализация)

- [ ] **Step 1: Обновить ассерт-пин в тесте — он станет красным**

В файле `web-admin/src/pages/OverviewPage.test.tsx` найти строку 50:

```tsx
    expect(screen.getByText('Топ-5 по категориям проблем')).toBeInTheDocument()
```

Заменить на:

```tsx
    expect(screen.getByText('Главные категории проблем')).toBeInTheDocument()
```

- [ ] **Step 2: Прогнать тест, убедиться что он падает на этом ассерте**

```bash
cd web-admin && npx vitest run src/pages/OverviewPage.test.tsx
```

Expected: FAIL — `Unable to find element with text: Главные категории проблем` (компонент пока выводит «Топ-5 по категориям проблем»).

- [ ] **Step 3: Поменять заголовок в компоненте**

В файле `web-admin/src/pages/overview/TopProblemCategories.tsx` найти строку 11:

```tsx
      <div className="mb-3 text-sm font-medium text-slate-700">Топ-5 по категориям проблем</div>
```

Заменить на:

```tsx
      <div className="mb-3 text-sm font-medium text-slate-700">Главные категории проблем</div>
```

- [ ] **Step 4: Прогнать тест, убедиться что он зелёный**

```bash
cd web-admin && npx vitest run src/pages/OverviewPage.test.tsx
```

Expected: PASS.

- [ ] **Step 5: Коммит**

```bash
git add web-admin/src/pages/overview/TopProblemCategories.tsx web-admin/src/pages/OverviewPage.test.tsx
git commit -m "$(cat <<'EOF'
chore(web-admin): «Топ-5 по категориям» → «Главные категории проблем»

Убираем «Топ» из заголовка блока категорий на странице «Обзор»
и обновляем соответствующий пин в OverviewPage.test.tsx.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `AnalyticsPage` — подзаголовок страницы

**Files:**
- Modify: `web-admin/src/pages/AnalyticsPage.tsx:39`

Пинов на эту строку в тестах нет (в `AnalyticsPage.test.tsx` пинится только заголовок секции `SLA по категориям`, который мы не трогаем). TDD-шаг с тестом не требуется.

⚠ **Внимание к WIP**: файл может содержать некоммитнутые изменения. См. раздел «Предварительные условия» в шапке плана.

- [ ] **Step 1: Заменить строку**

В файле `web-admin/src/pages/AnalyticsPage.tsx` найти строку 39:

```tsx
          <p className="text-xs text-slate-400">Тренды по жалобам и SLA по категориям</p>
```

Заменить на:

```tsx
          <p className="text-xs text-slate-400">Динамика жалоб и SLA по категориям</p>
```

- [ ] **Step 2: Прогнать тесты «Аналитики»**

```bash
cd web-admin && npx vitest run src/pages/AnalyticsPage.test.tsx
```

Expected: PASS (ничего из запинённого мы не трогали).

- [ ] **Step 3: Коммит — только наш хунк**

Если файл содержит другие WIP-изменения, использовать частичный staging:

```bash
git add -p web-admin/src/pages/AnalyticsPage.tsx
# выбрать только хунк с заменой подзаголовка
```

Иначе:

```bash
git add web-admin/src/pages/AnalyticsPage.tsx
```

Затем:

```bash
git commit -m "$(cat <<'EOF'
chore(web-admin): «Тренды по жалобам» → «Динамика жалоб» в подзаголовке «Аналитики»

Убираем заимствование «Тренды»; SLA в подзаголовке оставлен.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Полный регресс web-admin

**Files:** —

- [ ] **Step 1: Прогнать весь набор web-admin тестов**

```bash
cd web-admin && npm test -- --run
```

Expected: PASS по всему набору. Особое внимание на `OverviewPage.test.tsx`, `AnalyticsPage.test.tsx`.

Если что-то красное — НЕ исправлять «по дороге»; зафиксировать падение, остановиться и эскалировать пользователю.

- [ ] **Step 2: (опционально) Запустить dev-сервер и проверить глазами**

```bash
cd web-admin && npm run dev
# открыть http://localhost:5173/overview и /analytics
```

Что проверить визуально:
- `/overview`: блок с районами называется **«Районы — лидеры по жалобам»**, блок категорий — **«Главные категории проблем»**. KPI-карточка «SLA-просрочки» и красный баннер «норматив SLA» — на месте, не изменились.
- `/analytics`: подзаголовок страницы — **«Динамика жалоб и SLA по категориям»**. Секция «SLA по категориям» — на месте, не изменилась.

Этот шаг не блокирует завершение плана (UI можно проверить и позже), но рекомендуется перед мерджем.

---

## Финальная проверка

После всех коммитов:

```bash
git log --oneline -5
```

Ожидаемо: 3 новых коммита (Task 1, Task 2, Task 3) поверх коммита спеки (`4a644b2`).

```bash
git status
```

Ожидаемо: рабочее дерево либо чистое (если WIP был стэшнут), либо содержит **только** ваш WIP, не относящийся к этой задаче.

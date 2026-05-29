# Демо-сид для аналитики (Медиана + Соблюдение норматива) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Добавить top-up SQL-скрипт + bash-раннер, заполняющие живую dev-БД решёнными жалобами так, чтобы столбцы «Медиана» и «Соблюдение норматива, %» на странице «Аналитика» были непустыми и разноцветными.

**Architecture:** Один идемпотентный PL/pgSQL-скрипт вставляет 90 `RESOLVED`-жалоб (18 категорий × 5) с заданными в прошлом `created_at`/`resolved_at`. Время решения подобрано относительно SLA-порога категории (24 ч / 120 ч), что даёт реалистичный микс соблюдения норматива. Тонкий bash-раннер прокидывает скрипт в Docker-Postgres и поддерживает `--reset`. Код аналитики и Flyway-миграции не трогаются.

**Tech Stack:** PostgreSQL (postgis/postgis:16-3.4) в Docker, PL/pgSQL `DO`-блок, bash, `docker compose exec`.

---

## Контекст для исполнителя (прочитать перед началом)

- Репозиторий: `~/Desktop/Myapp/cleancity-kmp`. Ветка — `main` (для этого проекта Жасмин коммитит в main без feature-веток).
- БД — **Docker-Postgres**, сервис `db` в `docker-compose.yml`. Локальный Postgres на :5432 **не использовать** (там чужие проекты).
- Креды БД — в `.env` (`POSTGRES_USER`, `POSTGRES_DB`). Раннер читает их оттуда.
- Метрики «Медиана»/«Соблюдение норматива, %» считаются в
  `backend/src/main/kotlin/com/example/cleancity/analytics/AnalyticsRepository.kt`
  (`computeResolutionMetrics`) **только по `RESOLVED` + `resolved_at`**, сгруппированным
  по категории и по району. Поэтому задача — данные, а не код.
- Колонки `complaints`, которые заполняем (как в `V99__seed_dev.sql`):
  `author_id, category, title, description, latitude, longitude, address, district,
  status, created_at, updated_at, resolved_at`.
- SLA-пороги (`shared/.../CategoryMeta.kt` → `CategorySla.hoursFor`):
  **24 ч** — GARBAGE, ECOLOGY, SAFETY, LIGHTING, SEWAGE, WATER_SUPPLY, ELECTRICITY;
  **120 ч** — все остальные.
- Районы (строки точно как в enum `District.localizedLabel`):
  `Центральный`, `Адлерский`, `Хостинский`, `Лазаревский`.

### Распределение категорий по тирам и районам (фиксированное)

Маркер всех демо-строк: `address = 'SLA-DEMO'`.

| Тир (доля в норме из 5) | Категории | Район |
|---|---|---|
| Зелёный 5/5 → 100% | GARBAGE, LIGHTING, ROADS, GREENERY, PARKS, ACCESSIBILITY | Центральный |
| Жёлтый 3/5 → 60% | ECOLOGY, SEWAGE, SIDEWALKS | Адлерский |
| Жёлтый 3/5 → 60% | WATER_SUPPLY, LANDSCAPING, TRADE | Лазаревский |
| Красный 2/5 → 40% | SAFETY, ELECTRICITY, PLAYGROUNDS, BEACHES, VANDALISM, OTHER | Хостинский |

Итог на дашборде: категории — 6 зелёных / 6 жёлтых / 6 красных строк; районы — Центральный 100% (зел.), Адлерский/Лазаревский 60% (жёлт.), Хостинский 40% (красн.).

---

## File Structure

- Create: `ops/seed-analytics-demo.sql` — вся логика вставки (идемпотентный `DO`-блок).
- Create: `ops/seed-analytics-demo.sh` — раннер (вставка + `--reset`), исполняемый.

---

### Task 1: SQL-скрипт `ops/seed-analytics-demo.sql`

**Files:**
- Create: `ops/seed-analytics-demo.sql`

- [ ] **Step 1: Создать файл со скриптом**

Записать в `ops/seed-analytics-demo.sql`:

```sql
-- seed-analytics-demo.sql — демо-данные для страницы «Аналитика».
-- Цель: заполнить столбцы «Медиана» и «Соблюдение норматива, %» в таблицах
-- по категориям и по районам реалистичным разноцветным набором.
--
-- Вставляет 90 RESOLVED-жалоб (18 категорий × 5) с заданными в прошлом
-- created_at/resolved_at. Маркер всех строк: address = 'SLA-DEMO'.
--
-- ТОЛЬКО для dev: запускать вручную через ops/seed-analytics-demo.sh.
-- Это НЕ Flyway-миграция — прод не затрагивается.
--
-- Идемпотентно: при наличии демо-строк скрипт ничего не делает.
-- Откат: DELETE FROM complaints WHERE address = 'SLA-DEMO';

DO $$
DECLARE
    resident_ids BIGINT[];
    n_residents  INT;
    rec          RECORD;
    j            INT;
    k            INT := 0;
    cat_ord      INT := 0;
    sla_hours    NUMERIC;
    is_within    BOOLEAN;
    res_hours    NUMERIC;
    created_ts   TIMESTAMPTZ;
    resolved_ts  TIMESTAMPTZ;
    demo_count   INT;
BEGIN
    -- Гард: один раз. Повторный запуск — no-op.
    SELECT COUNT(*) INTO demo_count FROM complaints WHERE address = 'SLA-DEMO';
    IF demo_count > 0 THEN
        RAISE NOTICE 'seed-analytics-demo: already applied (% demo rows), skipping', demo_count;
        RETURN;
    END IF;

    -- Авторы — существующие резиденты (созданы V99). Без них вставлять не от кого.
    SELECT ARRAY_AGG(id ORDER BY id) INTO resident_ids
    FROM users WHERE role = 'RESIDENT';
    n_residents := COALESCE(ARRAY_LENGTH(resident_ids, 1), 0);
    IF n_residents = 0 THEN
        RAISE EXCEPTION 'seed-analytics-demo: нет пользователей с ролью RESIDENT — сначала примените V99 dev-seed';
    END IF;

    -- (category, n_within, district). n_within из 5 задаёт тир/цвет.
    FOR rec IN
        SELECT * FROM (VALUES
            -- Зелёные (5/5) → Центральный
            ('GARBAGE',       5, 'Центральный'),
            ('LIGHTING',      5, 'Центральный'),
            ('ROADS',         5, 'Центральный'),
            ('GREENERY',      5, 'Центральный'),
            ('PARKS',         5, 'Центральный'),
            ('ACCESSIBILITY', 5, 'Центральный'),
            -- Жёлтые (3/5) → Адлерский
            ('ECOLOGY',       3, 'Адлерский'),
            ('SEWAGE',        3, 'Адлерский'),
            ('SIDEWALKS',     3, 'Адлерский'),
            -- Жёлтые (3/5) → Лазаревский
            ('WATER_SUPPLY',  3, 'Лазаревский'),
            ('LANDSCAPING',   3, 'Лазаревский'),
            ('TRADE',         3, 'Лазаревский'),
            -- Красные (2/5) → Хостинский
            ('SAFETY',        2, 'Хостинский'),
            ('ELECTRICITY',   2, 'Хостинский'),
            ('PLAYGROUNDS',   2, 'Хостинский'),
            ('BEACHES',       2, 'Хостинский'),
            ('VANDALISM',     2, 'Хостинский'),
            ('OTHER',         2, 'Хостинский')
        ) AS t(category, n_within, district)
    LOOP
        -- SLA-порог категории (зеркалит CategorySla.hoursFor).
        sla_hours := CASE
            WHEN rec.category IN ('GARBAGE','ECOLOGY','SAFETY','LIGHTING',
                                  'SEWAGE','WATER_SUPPLY','ELECTRICITY') THEN 24
            ELSE 120
        END;

        FOR j IN 1..5 LOOP
            k := k + 1;
            is_within := (j <= rec.n_within);

            -- В норме: 0.48..0.80× порога (varied медиана). Просрочка: 1.4..2.2× порога.
            res_hours := CASE
                WHEN is_within THEN sla_hours * (0.40 + 0.08 * j)
                ELSE sla_hours * (1.20 + 0.20 * j)
            END;

            -- created_at: 13..24 дня назад (k % 12), чтобы resolved_at (макс ~+11 дней)
            -- гарантированно был ≤ now и всё попадало в период «Месяц».
            created_ts  := NOW() - ((13 + (k % 12)) * INTERVAL '1 day');
            resolved_ts := created_ts + (res_hours * INTERVAL '1 hour');

            INSERT INTO complaints (
                author_id, category, title, description,
                latitude, longitude, address, district,
                status, created_at, updated_at, resolved_at
            )
            VALUES (
                resident_ids[(k % n_residents) + 1],
                rec.category,
                'Демо-аналитика #' || k,
                'Решённая жалоба для наглядной аналитики (категория ' || rec.category || ').',
                43.5800 + (RANDOM() * 0.08),
                39.7000 + (RANDOM() * 0.10),
                'SLA-DEMO',
                rec.district,
                'RESOLVED',
                created_ts,
                resolved_ts,
                resolved_ts
            );
        END LOOP;
        cat_ord := cat_ord + 1;
    END LOOP;

    RAISE NOTICE 'seed-analytics-demo: inserted % RESOLVED complaints across % categories', k, cat_ord;
END $$;
```

- [ ] **Step 2: Проверить синтаксис без вставки (dry parse)**

Backend и БД должны быть подняты (`docker compose up -d`). Проверяем, что блок парсится — оборачиваем в ROLLBACK, чтобы ничего не осталось:

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
set -a; . ./.env; set +a
{ echo "BEGIN;"; cat ops/seed-analytics-demo.sql; echo "ROLLBACK;"; } \
  | docker compose exec -T db psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"
```
Expected: вывод `NOTICE: seed-analytics-demo: inserted 90 RESOLVED complaints across 18 categories`, затем `ROLLBACK`, код выхода 0. Ошибок синтаксиса нет. (Данные откатились — это проверка парсинга.)

- [ ] **Step 3: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add ops/seed-analytics-demo.sql
git commit -m "feat(ops): SQL демо-сид аналитики (90 RESOLVED жалоб, микс SLA)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Bash-раннер `ops/seed-analytics-demo.sh`

**Files:**
- Create: `ops/seed-analytics-demo.sh`

- [ ] **Step 1: Создать раннер**

Записать в `ops/seed-analytics-demo.sh`:

```bash
#!/usr/bin/env bash
# seed-analytics-demo.sh — наполнить dev-БД демо-данными для страницы «Аналитика»
# (столбцы «Медиана» и «Соблюдение норматива, %»). Идемпотентно.
#
# Использование:
#   ./ops/seed-analytics-demo.sh          # вставить демо-данные
#   ./ops/seed-analytics-demo.sh --reset  # удалить демо-данные (address='SLA-DEMO')
#
# Требует: docker compose, поднятый сервис db. Креды берутся из .env
# (POSTGRES_USER, POSTGRES_DB). Только для dev.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

if [ ! -f .env ]; then
  echo "Ошибка: .env не найден в $REPO_ROOT" >&2
  exit 1
fi
set -a; . ./.env; set +a

: "${POSTGRES_USER:?POSTGRES_USER не задан в .env}"
: "${POSTGRES_DB:?POSTGRES_DB не задан в .env}"

PSQL=(docker compose exec -T db psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB")

if [ "${1:-}" = "--reset" ]; then
  echo "Удаляю демо-данные (address='SLA-DEMO')…"
  "${PSQL[@]}" -c "DELETE FROM complaints WHERE address = 'SLA-DEMO';"
  echo "Готово."
else
  echo "Вставляю демо-данные аналитики…"
  "${PSQL[@]}" < ops/seed-analytics-demo.sql
  echo "Готово. Открой web-admin → «Аналитика» (период «Месяц»)."
fi
```

- [ ] **Step 2: Сделать исполняемым**

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
chmod +x ops/seed-analytics-demo.sh
```
Expected: код выхода 0.

- [ ] **Step 3: Запустить вставку**

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
./ops/seed-analytics-demo.sh
```
Expected: `NOTICE: seed-analytics-demo: inserted 90 RESOLVED complaints across 18 categories`, затем `Готово.`

- [ ] **Step 4: Проверить идемпотентность (повторный запуск)**

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
./ops/seed-analytics-demo.sh
```
Expected: `NOTICE: seed-analytics-demo: already applied (90 demo rows), skipping`. Дублей нет.

- [ ] **Step 5: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add ops/seed-analytics-demo.sh
git commit -m "feat(ops): раннер демо-сида аналитики (вставка + --reset)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Проверка результата на данных и откат

**Files:** (только проверки, без правок кода)

- [ ] **Step 1: Проверить покрытие категорий через SQL**

Каждая из 18 категорий должна иметь 5 решённых демо-жалоб с непустыми медианой и SLA.

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
set -a; . ./.env; set +a
docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
"SELECT category, count(*) AS resolved,
        round(percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM (resolved_at-created_at))/3600.0)::numeric,1) AS median_h
 FROM complaints WHERE address='SLA-DEMO' GROUP BY category ORDER BY category;"
```
Expected: 18 строк, у каждой `resolved = 5` и непустой `median_h` (числа различаются между категориями).

- [ ] **Step 2: Проверить SLA-микс по категориям через API**

Backend отдаёт уже посчитанные метрики. Логинимся админом и дёргаем `/analytics/by-category`.

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
TOKEN=$(curl -s -X POST http://localhost:8081/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@cleancity.dev","password":"Admin12345!"}' | jq -r '.auth.accessToken')
curl -s "http://localhost:8081/analytics/by-category?period=MONTH" \
  -H "Authorization: Bearer $TOKEN" \
  | jq '[.[] | {label, count, medianResolutionHours, slaCompliancePct}]'
```
Expected: непустой массив; присутствуют значения `slaCompliancePct` около `100`, `60` и `40` (зелёные/жёлтые/красные), `medianResolutionHours` не `null`.
(Токен лежит вложенно: `.auth.accessToken` — у dev-админа 2FA выключена, поэтому объект `auth` заполнен.)

- [ ] **Step 3: Проверить район в `EquityTable` через API**

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
curl -s "http://localhost:8081/analytics/by-district?period=MONTH" \
  -H "Authorization: Bearer $TOKEN" \
  | jq '[.[] | {label, count, medianResolutionHours, slaCompliancePct}]'
```
Expected: для районов Центральный/Адлерский/Лазаревский/Хостинский поля `medianResolutionHours` и `slaCompliancePct` ненулевые; проценты различаются (≈100 / 60 / 60 / 40).

- [ ] **Step 4: Визуальная проверка в web-admin**

Запустить web-admin (`cd web-admin && npm run dev`, :5173), войти `admin@cleancity.dev` / `Admin12345!`, открыть «Аналитика», период «Месяц».
Expected: в таблице «Категории жалоб» столбцы «Медиана» и «Соблюдение норматива, %» заполнены; столбец SLA разноцветный (есть зелёные, жёлтые, красные строки). Таблица по районам не пустая.

- [ ] **Step 5: Проверить откат**

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
./ops/seed-analytics-demo.sh --reset
set -a; . ./.env; set +a
docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
"SELECT count(*) FROM complaints WHERE address='SLA-DEMO';"
```
Expected: после `--reset` счётчик `0`. Дашборд возвращается в исходное состояние.

- [ ] **Step 6: Повторно вставить демо-данные для дальнейшей работы/демо**

Run:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
./ops/seed-analytics-demo.sh
```
Expected: снова `inserted 90 RESOLVED complaints`.

---

## Notes

- Скрипт не трогает 50 жалоб из V99 и не делает wipe.
- Голоса/votesImpact/reopen — вне scope (целевые метрики их не используют).
- Если нужно изменить «цвета» — правится только таблица `(category, n_within, district)`
  в `ops/seed-analytics-demo.sql` (n_within: 5/3/2 → 100/60/40 %).

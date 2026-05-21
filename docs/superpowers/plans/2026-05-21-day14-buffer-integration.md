# Day 14 — Mobile буфер + интеграция: план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Подтвердить, что polling-канал уведомлений работает end-to-end на реальном устройстве, и задокументировать состояние интеграции фронт↔бэк перед веб-админкой.

**Architecture:** Создаётся bash-скрипт `ops/trigger-status-change.sh`, который логинится dev-админом и шлёт `PATCH /complaints/{id}/status` — это триггер уведомления. Скриптом вручную прогоняется polling-цикл на Samsung A33 (backend локальный, `adb reverse`). Результаты и находки по интеграции фиксируются в чек-листе и `docs/PLAN.md`.

**Tech Stack:** Bash, curl, jq; Ktor-backend (локальный); Android-приложение на реальном устройстве; Markdown-документация.

**Спека:** `docs/superpowers/specs/2026-05-21-day14-buffer-integration-design.md`

---

## File Structure

- **Create:** `ops/trigger-status-change.sh` — переиспользуемый скрипт смены статуса жалобы от имени dev-админа. Единственная ответственность: логин + один `PATCH`-запрос. Кладётся в `ops/` рядом с другими операционными скриптами проекта (`backup.sh`, `healthcheck.sh`, `alert.sh`).
- **Create:** `docs/superpowers/checklists/2026-05-21-day14-polling-smoke.md` — отчёт о ручном прогоне polling-цикла. Соответствует существующему паттерну (`docs/superpowers/checklists/2026-05-18-day10-smoke.md`).
- **Modify:** `docs/PLAN.md` — отметка пунктов Day 14, резюме-строка, напоминания в секцию Day 18, чек-лист «перед публикацией».

---

## Task 1: Скрипт смены статуса жалобы

**Files:**
- Create: `ops/trigger-status-change.sh`

- [ ] **Step 1: Создать скрипт**

Создать файл `ops/trigger-status-change.sh` со следующим содержимым:

```bash
#!/usr/bin/env bash
# trigger-status-change.sh — сменить статус жалобы от имени dev-админа.
#
# Назначение: ручная проверка polling-канала уведомлений (Day 14) и
# референс для чекпоинта Day 16. Триггерит PATCH /complaints/{id}/status,
# из-за чего backend создаёт уведомление автору жалобы.
#
# Требует: curl, jq. Backend должен быть запущен с применённой dev-сидкой
# (admin@cleancity.dev создаётся миграцией V99__seed_dev.sql).
#
# Использование:
#   ./ops/trigger-status-change.sh <COMPLAINT_ID> <TO_STATUS> <COMMENT> [DUPLICATE_OF_ID]
# Пример:
#   ./ops/trigger-status-change.sh 12 IN_PROGRESS "Приняли в работу"
#
# Переменные окружения (с дефолтами):
#   BASE_URL     — адрес backend         (http://localhost:8081)
#   ADMIN_EMAIL  — логин dev-админа      (admin@cleancity.dev)
#   ADMIN_PASS   — пароль dev-админа     (Admin12345!)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@cleancity.dev}"
ADMIN_PASS="${ADMIN_PASS:-Admin12345!}"

if [ "$#" -lt 3 ]; then
  echo "Использование: $0 <COMPLAINT_ID> <TO_STATUS> <COMMENT> [DUPLICATE_OF_ID]" >&2
  echo "Пример:        $0 12 IN_PROGRESS \"Приняли в работу\"" >&2
  exit 1
fi

COMPLAINT_ID="$1"
TO_STATUS="$2"
COMMENT="$3"
DUPLICATE_OF_ID="${4:-}"

command -v jq >/dev/null 2>&1 || { echo "Нужен jq: brew install jq" >&2; exit 1; }

# 1. Логин админа (-f не используем, чтобы показать тело ответа при ошибке)
echo "→ Логин $ADMIN_EMAIL на $BASE_URL ..."
LOGIN_RESP="$(curl -sS -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASS" '{email:$e,password:$p}')")"

TOKEN="$(echo "$LOGIN_RESP" | jq -r '.auth.accessToken // empty')"
if [ -z "$TOKEN" ]; then
  echo "✗ Не получили accessToken. Ответ: $LOGIN_RESP" >&2
  exit 1
fi
echo "✓ Токен получен"

# 2. Тело запроса PATCH (duplicateOfId — только если передан)
if [ -n "$DUPLICATE_OF_ID" ]; then
  BODY="$(jq -n --arg s "$TO_STATUS" --arg c "$COMMENT" --argjson d "$DUPLICATE_OF_ID" \
    '{toStatus:$s,comment:$c,duplicateOfId:$d}')"
else
  BODY="$(jq -n --arg s "$TO_STATUS" --arg c "$COMMENT" '{toStatus:$s,comment:$c}')"
fi

# 3. Смена статуса
echo "→ PATCH /complaints/$COMPLAINT_ID/status → $TO_STATUS ..."
HTTP_CODE="$(curl -sS -o /tmp/cc_status_resp.json -w '%{http_code}' \
  -X PATCH "$BASE_URL/complaints/$COMPLAINT_ID/status" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$BODY")"

echo "HTTP $HTTP_CODE"
jq . /tmp/cc_status_resp.json 2>/dev/null || cat /tmp/cc_status_resp.json
echo

if [ "$HTTP_CODE" != "200" ]; then
  echo "✗ Смена статуса не удалась (ожидался HTTP 200)" >&2
  exit 1
fi
echo "✓ Статус жалобы $COMPLAINT_ID изменён на $TO_STATUS"
```

- [ ] **Step 2: Сделать скрипт исполняемым**

Run: `chmod +x ops/trigger-status-change.sh`
Expected: команда без вывода, код возврата 0.

- [ ] **Step 3: Проверить синтаксис скрипта**

Run: `bash -n ops/trigger-status-change.sh`
Expected: пустой вывод, код возврата 0 (синтаксических ошибок нет).

- [ ] **Step 4: Проверить обработку нехватки аргументов**

Run: `./ops/trigger-status-change.sh 12 IN_PROGRESS; echo "exit=$?"`
Expected: печатается блок «Использование: …» и `exit=1` (передано 2 аргумента вместо 3).

- [ ] **Step 5: Commit**

```bash
git add ops/trigger-status-change.sh
git commit -m "feat(ops): скрипт trigger-status-change.sh для проверки polling"
```

---

## Task 2: Ручной прогон polling-цикла на Samsung A33

Эта задача — ручной прогон на реальном устройстве; кода она не меняет, но обязана
завершиться зелёным отчётом. Backend поднимается локально, устройство подключается
через `adb reverse`.

**Files:**
- Create: `docs/superpowers/checklists/2026-05-21-day14-polling-smoke.md`

- [ ] **Step 1: Поднять backend локально**

Run: `cd backend && ../gradlew run` (в отдельном терминале; оставить запущенным)
Expected: в логе есть строка `V99 seed: ... admin@cleancity.dev ...` (либо `already applied`) и `Application started`. Backend слушает порт 8081.

- [ ] **Step 2: Подключить устройство к локальному backend**

Run: `~/Library/Android/sdk/platform-tools/adb reverse tcp:8081 tcp:8081`
Expected: вывод `8081`. Приложение на A33 теперь достукивается до локального backend.

- [ ] **Step 3: Создать жалобу на устройстве**

На Samsung A33: установить актуальный debug-APK, войти жителем (или зарегистрироваться),
создать жалобу с фото. Запомнить её id (виден в URL/деталях жалобы; при сомнениях — взять
из ленты «Мои»).

Expected: жалоба создана, отображается в ленте со статусом `NEW`.

- [ ] **Step 4: Сменить статус на IN_PROGRESS скриптом**

Run (с Mac, подставить реальный id): `./ops/trigger-status-change.sh <id> IN_PROGRESS "Приняли в работу"`
Expected: `HTTP 200`, в теле ответа `"status":"IN_PROGRESS"`, строка `✓ Статус жалобы <id> изменён`.

- [ ] **Step 5: Проверить появление бейджа**

На A33: не открывая экран «Уведомления», подождать до 30 секунд.
Expected: на колокольчике в `FeedTopBar` появляется бейдж непрочитанных (поллинг
`UnreadCountStore` c интервалом 30с).

- [ ] **Step 6: Проверить уведомление в списке и снятие unread**

На A33: открыть экран «Уведомления», тапнуть по новой записи.
Expected: запись присутствует с unread-меткой; тап открывает детали жалобы; после
возврата запись отмечена прочитанной, счётчик бейджа уменьшился.

- [ ] **Step 7: Повторить для статуса REJECTED**

Run (с Mac): `./ops/trigger-status-change.sh <id> REJECTED "Не относится к компетенции"`
Затем на A33 открыть детали этой жалобы.
Expected: `HTTP 200`; в деталях закрытой жалобы виден блок «Решение администрации» с
текстом комментария; уведомление о смене статуса пришло и открывается тапом.

- [ ] **Step 8: Записать отчёт**

Создать `docs/superpowers/checklists/2026-05-21-day14-polling-smoke.md` со следующим
содержимым (значения `[OK]/[FAIL]` и заметки заполнить по факту прогона):

```markdown
# Day 14 — Smoke polling-канала (Samsung A33 5G)

**Дата прогона:** 2026-05-21
**Backend:** локальный, `../gradlew run`, порт 8081, dev-сидка применена
**Устройство:** Samsung A33 5G, debug-APK, `adb reverse tcp:8081`

| # | Сценарий | Результат | Заметки |
|---|----------|-----------|---------|
| 1 | Жалоба создаётся, статус NEW | [OK/FAIL] | |
| 2 | PATCH IN_PROGRESS → HTTP 200 | [OK/FAIL] | |
| 3 | Бейдж на колокольчике появляется ≤30с | [OK/FAIL] | |
| 4 | Уведомление в списке с unread-меткой | [OK/FAIL] | |
| 5 | Тап → детали жалобы, unread снят, счётчик -1 | [OK/FAIL] | |
| 6 | PATCH REJECTED → HTTP 200 | [OK/FAIL] | |
| 7 | Блок «Решение администрации» в деталях | [OK/FAIL] | |

**Вывод:** polling-канал уведомлений работает / не работает end-to-end.
**Найденные дефекты:** нет / список.
```

- [ ] **Step 9: Commit**

```bash
git add docs/superpowers/checklists/2026-05-21-day14-polling-smoke.md
git commit -m "docs: отчёт smoke polling-канала на Samsung A33 (Day 14)"
```

> **Если на шагах 3–7 обнаружится дефект** — это и есть «буфер» Day 14: завести
> исправление через skill `superpowers:systematic-debugging` отдельной задачей до
> закрытия дня. План при чистом прогоне дефектов не предполагает.

---

## Task 3: Документирование интеграции и закрытие Day 14

**Files:**
- Modify: `docs/PLAN.md`

- [ ] **Step 1: Отметить пункты Day 14 и добавить резюме**

В `docs/PLAN.md` найти секцию `### День 14 (21.05) — Mobile буфер + интеграция`.
Сразу под заголовком секции (перед списком пунктов) вставить блок-резюме в стиле
прошлых дней:

```markdown
> **Закрыт 2026-05-21.** Багов из smoke на A33 нет — буфер не понадобился.
> Polling-канал уведомлений проверен end-to-end на реальном устройстве через
> `ops/trigger-status-change.sh` (статусы IN_PROGRESS и REJECTED): бейдж, unread,
> детали — всё работает. Отчёт: `docs/superpowers/checklists/2026-05-21-day14-polling-smoke.md`.
> Дизайн+план: `docs/superpowers/specs/2026-05-21-day14-buffer-integration-design.md`,
> `docs/superpowers/plans/2026-05-21-day14-buffer-integration.md`.
> FCM/Firebase — решение отложено. privacy-policy и скринкаст перенесены в чек-лист
> «перед публикацией» (см. ниже).
```

В списке пунктов Day 14 проставить галочки `[x]`:
- пункт «Запас на исправление багов из дня 13» → `[x]` с пометкой «багов нет»;
- пункт «Интеграция фронт↔бэк» → `[x]` (см. находки ниже);
пункт про FCM оставить `[ ]` и дописать в конец «— **решение отложено** (брейншторм 2026-05-21)»;
пункт про скринкаст оставить `[ ]` и дописать «— перенесён в чек-лист «перед публикацией»».

- [ ] **Step 2: Добавить находки по интеграции в секцию Day 14**

В той же секции Day 14, под списком пунктов, добавить подблок с результатом аудита:

```markdown
**Аудит интеграции фронт↔бэк (2026-05-21):**
- `API_BASE_URL` — единственное build-config значение из `secrets.properties`/env,
  одно на debug и release (нет per-build-type разделения). Per-build-type split
  решено НЕ делать (YAGNI) — значение меняется вручную перед release-сборкой.
- `usesCleartextTraffic="true"` оставлен — нужен для локального HTTP через `adb reverse`.
- Полноценная интеграция против задеплоенного backend невозможна до Day 18 — перенесена туда.
```

- [ ] **Step 3: Добавить напоминания в секцию Day 18**

Найти в `docs/PLAN.md` секцию Day 18 (`### День 18 …`). В её список пунктов добавить
два пункта:

```markdown
- [ ] Сменить `API_BASE_URL` в `secrets.properties` на боевой HTTPS-URL **до** release-сборки (хвост Day 14).
- [ ] Ужесточить network security: убрать `usesCleartextTraffic="true"` из `AndroidManifest.xml` либо сузить до конкретного хоста через network-security-config (хвост Day 14).
```

- [ ] **Step 4: Добавить чек-лист «перед публикацией»**

В конец секции Day 14 (после подблока находок) добавить:

```markdown
**Чек-лист «перед публикацией» (не привязан к Day 14, обязателен до подачи в RuStore):**
- [ ] Привести `backend/.../legal/privacy-policy.md` в соответствие с итоговым решением по FCM (если Firebase не делаем — убрать заявленный сбор FCM-токена и передачу в Firebase, привести к polling-формулировке).
- [ ] Записать короткий скринкаст happy-path на A33: регистрация → создание жалобы → голос → получение уведомления.
```

- [ ] **Step 5: Проверить итог**

Run: `grep -n "День 14\|Закрыт 2026-05-21\|Аудит интеграции\|перед публикацией" docs/PLAN.md`
Expected: совпадения подтверждают, что резюме-блок, подблок аудита и чек-лист
«перед публикацией» присутствуют в секции Day 14.

- [ ] **Step 6: Commit**

```bash
git add docs/PLAN.md
git commit -m "docs: закрыть Day 14 в PLAN.md, зафиксировать аудит интеграции"
```

---

## Self-Review

**Spec coverage:**
- Спека §1 «Проверка polling end-to-end» → Task 1 (скрипт) + Task 2 (ручной прогон + отчёт). ✅
- Спека §2 «Аудит интеграции фронт↔бэк» → Task 3, шаги 2–3. ✅
- Спека §3 «Закрытие дня» → Task 3, шаги 1, 4. ✅
- Спека «Перенесено в чек-лист перед публикацией» → Task 3, шаг 4. ✅
- Вне scope (FCM, keystore, UI-тесты) — задач нет, как и задумано. ✅

**Placeholder scan:** плейсхолдеров нет; `[OK/FAIL]` в шаблоне отчёта — это поля для заполнения по факту прогона, а не недоработка плана.

**Type/имена-консистентность:** скрипт `ops/trigger-status-change.sh` именуется одинаково во всех задачах; путь отчёта `docs/superpowers/checklists/2026-05-21-day14-polling-smoke.md` — одинаков в Task 2 и Task 3; JSON-путь токена `.auth.accessToken` соответствует моделям `LoginResponse`/`AuthResponse`; тело `{toStatus,comment,duplicateOfId}` соответствует `ChangeStatusRequest`.

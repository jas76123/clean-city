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

#!/usr/bin/env bash
# trigger-announcement.sh — опубликовать тестовое объявление от dev-админа.
#
# Назначение: ручная проверка push-канала (Day 17B хвост). После публикации
# у залогиненных жителей выбранных районов в течение 30 сек должно
# появиться in-app banner (если приложение открыто) или системное
# уведомление в шторке (если в фоне/закрыто).
#
# Использование:
#   ./ops/trigger-announcement.sh <TITLE> <BODY> [DISTRICT [DISTRICT ...]]
# Примеры:
#   ./ops/trigger-announcement.sh "Уборка парка" "В субботу в 9:00 общественная уборка"
#   ./ops/trigger-announcement.sh "Авария" "Без света до 18:00" Центральный Адлерский
#
# Переменные окружения (с дефолтами):
#   BASE_URL     — адрес backend         (http://localhost:8081)
#   ADMIN_EMAIL  — логин dev-админа      (admin@cleancity.dev)
#   ADMIN_PASS   — пароль dev-админа     (Admin12345!)
#   ICON_STYLE   — стиль иконки          (INFO; см. AnnouncementIconStyle)

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@cleancity.dev}"
ADMIN_PASS="${ADMIN_PASS:-Admin12345!}"
ICON_STYLE="${ICON_STYLE:-INFO}"

if [ "$#" -lt 2 ]; then
  echo "Использование: $0 <TITLE> <BODY> [DISTRICT [DISTRICT ...]]" >&2
  echo "Пример:        $0 \"Уборка\" \"В субботу в 9:00\" Центральный" >&2
  exit 1
fi

TITLE="$1"; shift
BODY="$1"; shift
DISTRICTS_JSON="$(printf '%s\n' "$@" | jq -R . | jq -s .)"

command -v jq >/dev/null 2>&1 || { echo "Нужен jq: brew install jq" >&2; exit 1; }

echo "→ Логин $ADMIN_EMAIL на $BASE_URL ..."
LOGIN_RESP="$(curl -sS -X POST "$BASE_URL/auth/login" \
  -H 'Content-Type: application/json' \
  -d "$(jq -n --arg e "$ADMIN_EMAIL" --arg p "$ADMIN_PASS" '{email:$e,password:$p}')")"

TOKEN="$(echo "$LOGIN_RESP" | jq -r '.auth.accessToken // empty')"
if [ -z "$TOKEN" ]; then
  echo "✗ Не получили accessToken. Ответ: $LOGIN_RESP" >&2
  exit 1
fi

REQUEST_BODY="$(jq -n \
  --arg t "$TITLE" \
  --arg b "$BODY" \
  --arg i "$ICON_STYLE" \
  --argjson d "$DISTRICTS_JSON" \
  '{title:$t, body:$b, iconStyle:$i, districts:$d}')"

echo "→ POST /announcements ..."
HTTP_CODE="$(curl -sS -o /tmp/cc_announcement_resp.json -w '%{http_code}' \
  -X POST "$BASE_URL/announcements" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "$REQUEST_BODY")"

echo "HTTP $HTTP_CODE"
jq . /tmp/cc_announcement_resp.json 2>/dev/null || cat /tmp/cc_announcement_resp.json
echo

if [ "$HTTP_CODE" != "200" ] && [ "$HTTP_CODE" != "201" ]; then
  echo "✗ Публикация не удалась (ожидался HTTP 200/201)" >&2
  exit 1
fi
echo "✓ Объявление опубликовано. Жди ≤30 сек до push'а на mobile."

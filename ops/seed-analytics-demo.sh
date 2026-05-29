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

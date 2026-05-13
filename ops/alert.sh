#!/usr/bin/env bash
# Отправка одной строки в Telegram-чат.
# Используется cron-скриптами (healthcheck, backup.sh) для алертов.
#
# Деплой:
#   sudo install -m 0755 ops/alert.sh /opt/cleancity/alert.sh
#   sudo install -m 0644 ops/alert.env.example /etc/cleancity/alert.env
#   (отредактировать /etc/cleancity/alert.env, выставить mode 0600)
#
# Использование:
#   /opt/cleancity/alert.sh "API DOWN at $(hostname)"

set -euo pipefail

CONFIG_FILE="${ALERT_CONFIG:-/etc/cleancity/alert.env}"
if [[ -r "$CONFIG_FILE" ]]; then
    # shellcheck disable=SC1090
    source "$CONFIG_FILE"
fi

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN is required}"
: "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID is required}"

MESSAGE="${1:-(empty alert)}"

# Префикс с hostname для удобства разбора в чате.
TEXT="[$(hostname)] $MESSAGE"

# Telegram Bot API. Таймаут 10s, fire-and-forget.
curl --silent --max-time 10 \
    --data-urlencode "chat_id=$TELEGRAM_CHAT_ID" \
    --data-urlencode "text=$TEXT" \
    --data "disable_web_page_preview=true" \
    "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
    > /dev/null || true

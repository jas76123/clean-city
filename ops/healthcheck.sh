#!/usr/bin/env bash
# Дёргает /health и при провале триггерит Telegram-алерт через alert.sh.
# Без зависимостей от backend-кода; работает в чистой VM с curl.
#
# Деплой:
#   sudo install -m 0755 ops/healthcheck.sh /opt/cleancity/healthcheck.sh
#   sudo install -m 0644 ops/cleancity.cron /etc/cron.d/cleancity
#
# По умолчанию проверяет localhost:8080. Можно переопределить через
# HEALTH_URL (например, https://api.cleancity.ru/health) — но cron обычно
# работает рядом с backend, поэтому localhost ОК.
#
# Cooldown: подряд алертить раз в 5 минут нельзя — оставляем флаг
# /tmp/cleancity-healthcheck-failing на каждый последовательный fail,
# алерт пишется только на первом fail и на recovery.

set -euo pipefail

HEALTH_URL="${HEALTH_URL:-http://localhost:8080/health}"
ALERT_SCRIPT="${ALERT_SCRIPT:-/opt/cleancity/alert.sh}"
STATE_FILE="${STATE_FILE:-/tmp/cleancity-healthcheck-failing}"
TIMEOUT_SECS="${TIMEOUT_SECS:-5}"

if curl --silent --fail --max-time "$TIMEOUT_SECS" "$HEALTH_URL" > /dev/null; then
    # ОК — если был fail, шлём recovery.
    if [[ -f "$STATE_FILE" ]]; then
        if [[ -x "$ALERT_SCRIPT" ]]; then
            "$ALERT_SCRIPT" "API RECOVERED at $HEALTH_URL" || true
        fi
        rm -f "$STATE_FILE"
    fi
    exit 0
fi

# Fail — алертим только на первом обнаружении, потом тихо до восстановления.
if [[ ! -f "$STATE_FILE" ]]; then
    touch "$STATE_FILE"
    if [[ -x "$ALERT_SCRIPT" ]]; then
        "$ALERT_SCRIPT" "API DOWN: $HEALTH_URL not responding" || true
    fi
fi
exit 1

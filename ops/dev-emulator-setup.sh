#!/usr/bin/env bash
# Поднимает локальное dev-окружение CleanCity:
#   1. Backend + Postgres через docker compose (если не запущены).
#   2. Android-эмулятор с правильными флагами (если не запущен).
#   3. Установка APK и выдача permissions + mock location Сочи.
#
# Использование:
#   bash ops/dev-emulator-setup.sh              # поднять всё, не пересобирать APK
#   bash ops/dev-emulator-setup.sh --build      # перед установкой пересобрать APK
#   bash ops/dev-emulator-setup.sh --avd Pixel  # выбрать другой AVD
#
# Идемпотентно: повторный запуск не пересоздаёт уже работающие сервисы.

set -euo pipefail

AVD="Medium_Phone"
DO_BUILD=0

while [[ $# -gt 0 ]]; do
    case "$1" in
        --build) DO_BUILD=1; shift ;;
        --avd)   AVD="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *) echo "Неизвестный флаг: $1" >&2; exit 2 ;;
    esac
done

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
EMULATOR="$ANDROID_HOME/emulator/emulator"

# Геокоординаты Сочи (центр) — используются как mock location.
SOCHI_LON="39.7233"
SOCHI_LAT="43.5855"

PKG="com.example.cleancity"
APK="$REPO_ROOT/composeApp/build/outputs/apk/debug/composeApp-debug.apk"

log()  { printf '[\033[36m%s\033[0m] %s\n' "$(date +%H:%M:%S)" "$*"; }
fail() { printf '[\033[31m%s\033[0m] %s\n' "$(date +%H:%M:%S)" "$*" >&2; exit 1; }

[[ -x "$ADB" ]]      || fail "adb не найден: $ADB (проверь ANDROID_HOME)"
[[ -x "$EMULATOR" ]] || fail "emulator не найден: $EMULATOR"

# 1. Backend stack ------------------------------------------------------------
log "Проверяю docker compose стек…"
if ! docker info >/dev/null 2>&1; then
    fail "Docker не запущен. Открой Docker Desktop и повтори."
fi

cd "$REPO_ROOT"
if [[ -z "$(docker compose ps -q backend 2>/dev/null)" ]] \
   || ! docker compose ps backend --format '{{.State}}' | grep -q running; then
    log "Поднимаю backend + db (docker compose up -d)…"
    docker compose up -d
else
    log "Backend уже запущен."
fi

# 2. Эмулятор ----------------------------------------------------------------
log "Проверяю эмулятор ${AVD}…"
if "$ADB" devices | grep -q "^emulator-"; then
    log "Эмулятор уже подключён ($("$ADB" devices | grep '^emulator-' | head -1 | awk '{print $1}'))."
else
    "$EMULATOR" -list-avds | grep -qx "$AVD" \
        || fail "AVD '$AVD' не найден. Доступные: $("$EMULATOR" -list-avds | tr '\n' ' ')"
    log "Стартую ${AVD} с -gpu host -dns-server 8.8.8.8,1.1.1.1…"
    nohup "$EMULATOR" -avd "$AVD" \
        -gpu host \
        -dns-server 8.8.8.8,1.1.1.1 \
        -netdelay none -netspeed full \
        > /tmp/cleancity-emulator.log 2>&1 &
    log "PID=$!, лог: /tmp/cleancity-emulator.log"
fi

log "Жду пока эмулятор полностью загрузится…"
"$ADB" wait-for-device
"$ADB" shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done'
log "Эмулятор готов."

# 2b. adb reverse ------------------------------------------------------------
# Backend зашивает в URL фоток свой app.base_url (= http://localhost:8081 в
# dev). На эмуляторе localhost — это сам эмулятор, поэтому без туннеля фото
# жалоб не грузятся (API при этом ходит по 10.0.2.2 и работает). Пробрасываем
# порт 8081 на хост, чтобы http://localhost:8081/photos/... долетал до Mac.
log "Настраиваю adb reverse tcp:8081 → host:8081 (загрузка фото жалоб)…"
"$ADB" reverse tcp:8081 tcp:8081

# 3. APK ---------------------------------------------------------------------
if [[ "$DO_BUILD" -eq 1 ]] || [[ ! -f "$APK" ]]; then
    log "Собираю APK (./gradlew :composeApp:assembleDebug)…"
    "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :composeApp:assembleDebug
fi

log "Устанавливаю APK на эмулятор…"
"$ADB" install -r "$APK" >/dev/null

# 4. Permissions + mock location ---------------------------------------------
log "Выдаю location permissions для ${PKG}…"
"$ADB" shell pm grant "$PKG" android.permission.ACCESS_FINE_LOCATION
"$ADB" shell pm grant "$PKG" android.permission.ACCESS_COARSE_LOCATION

log "Mock location → Сочи (${SOCHI_LAT}, ${SOCHI_LON})…"
"$ADB" emu geo fix "$SOCHI_LON" "$SOCHI_LAT" >/dev/null

# 5. Старт -------------------------------------------------------------------
log "Запускаю ${PKG}/.MainActivity…"
"$ADB" shell am start -n "$PKG/.MainActivity" >/dev/null

log "Готово. Backend: http://localhost:8081  |  Эмулятор: $($ADB devices | grep '^emulator-' | head -1 | awk '{print $1}')"

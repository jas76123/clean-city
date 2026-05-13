#!/usr/bin/env bash
# Однократный инсталлер cron-инфраструктуры CleanCity на боевой ВМ.
# Запускается с sudo из корня репозитория:
#
#   sudo bash ops/install-cron.sh
#
# Идемпотентно: повторный запуск перезапишет скрипты и cron, но НЕ перетрёт
# /etc/cleancity/*.env (чтобы не потерять секреты). Если нужно пересоздать
# env-файл — удали его руками и запусти снова.

set -euo pipefail

if [[ "$EUID" -ne 0 ]]; then
    echo "Запусти с sudo: sudo bash ops/install-cron.sh" >&2
    exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OPS="$REPO_ROOT/ops"

if [[ ! -f "$OPS/backup.sh" ]]; then
    echo "Не нашёл $OPS/backup.sh — запускай из корня репозитория" >&2
    exit 1
fi

OPT_DIR=/opt/cleancity
ETC_DIR=/etc/cleancity

echo "==> создаю $OPT_DIR и $ETC_DIR"
install -d -m 0755 "$OPT_DIR"
install -d -m 0750 -g root "$ETC_DIR"

echo "==> копирую скрипты в $OPT_DIR"
install -m 0755 "$OPS/backup.sh"      "$OPT_DIR/backup.sh"
install -m 0755 "$OPS/alert.sh"       "$OPT_DIR/alert.sh"
install -m 0755 "$OPS/healthcheck.sh" "$OPT_DIR/healthcheck.sh"

# ----- secrets / env -----

if [[ ! -f "$ETC_DIR/backup-gpg.pass" ]]; then
    echo "==> генерирую gpg-passphrase в $ETC_DIR/backup-gpg.pass"
    openssl rand -base64 64 | tr -d '\n' > "$ETC_DIR/backup-gpg.pass"
    chmod 0600 "$ETC_DIR/backup-gpg.pass"
    echo "    !!! СОХРАНИ ЭТУ PASSPHRASE В МЕНЕДЖЕРЕ ПАРОЛЕЙ:"
    echo "    $(cat $ETC_DIR/backup-gpg.pass)"
    echo "    Без неё восстановление из бэкапа НЕВОЗМОЖНО."
else
    echo "==> $ETC_DIR/backup-gpg.pass уже существует, не трогаю"
fi

prompt_or_keep() {
    # prompt_or_keep <env-file> <key> <prompt-text>
    local file="$1" key="$2" prompt="$3"
    if [[ -f "$file" ]] && grep -q "^$key=" "$file"; then
        echo "    [keep] $key (уже задан в $file)"
        return
    fi
    read -r -p "    $prompt: " value
    echo "$key=$value" >> "$file"
}

if [[ ! -f "$ETC_DIR/backup.env" ]]; then
    echo "==> создаю $ETC_DIR/backup.env (отвечай на запросы)"
    touch "$ETC_DIR/backup.env"
    chmod 0600 "$ETC_DIR/backup.env"
    {
        echo "# CleanCity backup config — сгенерировано $(date -Is)"
        echo "GPG_PASSPHRASE_FILE=$ETC_DIR/backup-gpg.pass"
        echo "PGPORT=5432"
        echo "S3_ENDPOINT=https://storage.yandexcloud.net"
        echo "AWS_DEFAULT_REGION=ru-central1"
        echo "ALERT_SCRIPT=$OPT_DIR/alert.sh"
    } >> "$ETC_DIR/backup.env"
    prompt_or_keep "$ETC_DIR/backup.env" PGHOST                "Postgres host (например, localhost или managed-endpoint)"
    prompt_or_keep "$ETC_DIR/backup.env" PGDATABASE            "имя БД (cleancity)"
    prompt_or_keep "$ETC_DIR/backup.env" PGUSER                "пользователь Postgres"
    prompt_or_keep "$ETC_DIR/backup.env" PGPASSWORD            "пароль Postgres"
    prompt_or_keep "$ETC_DIR/backup.env" S3_BUCKET             "S3-бакет (cleancity-backups)"
    prompt_or_keep "$ETC_DIR/backup.env" AWS_ACCESS_KEY_ID     "Yandex Object Storage access key"
    prompt_or_keep "$ETC_DIR/backup.env" AWS_SECRET_ACCESS_KEY "Yandex Object Storage secret key"
else
    echo "==> $ETC_DIR/backup.env уже существует, не трогаю"
fi

if [[ ! -f "$ETC_DIR/alert.env" ]]; then
    echo "==> создаю $ETC_DIR/alert.env"
    touch "$ETC_DIR/alert.env"
    chmod 0600 "$ETC_DIR/alert.env"
    echo "# CleanCity alert config — сгенерировано $(date -Is)" >> "$ETC_DIR/alert.env"
    prompt_or_keep "$ETC_DIR/alert.env" TELEGRAM_BOT_TOKEN "Telegram Bot Token от @BotFather"
    prompt_or_keep "$ETC_DIR/alert.env" TELEGRAM_CHAT_ID   "Telegram Chat ID (для группы — отрицательный)"
else
    echo "==> $ETC_DIR/alert.env уже существует, не трогаю"
fi

# ----- cron -----

echo "==> ставлю /etc/cron.d/cleancity"
install -m 0644 "$OPS/cleancity.cron" /etc/cron.d/cleancity

# Создаём log-файлы заранее, чтобы у них был владелец root и mode 0640.
touch /var/log/cleancity-healthcheck.log /var/log/cleancity-backup.log
chmod 0640 /var/log/cleancity-healthcheck.log /var/log/cleancity-backup.log

# ----- smoke -----

echo "==> smoke: alert.sh"
if sudo -E "$OPT_DIR/alert.sh" "install-cron.sh: deploy smoke test from $(hostname)"; then
    echo "    alert.sh: OK (проверь, что сообщение пришло в Telegram)"
else
    echo "    !!! alert.sh упал — проверь /etc/cleancity/alert.env"
fi

echo "==> готово."
echo "    healthcheck:  $OPT_DIR/healthcheck.sh"
echo "    backup:       $OPT_DIR/backup.sh"
echo "    cron:         /etc/cron.d/cleancity"
echo "    логи:         /var/log/cleancity-*.log"

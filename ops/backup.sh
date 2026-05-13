#!/usr/bin/env bash
# Бэкап Postgres → gpg → Yandex Object Storage.
#
# Деплой:
#   sudo install -m 0755 ops/backup.sh /opt/cleancity/backup.sh
#   sudo install -m 0644 ops/backup.env.example /etc/cleancity/backup.env
#   (отредактировать /etc/cleancity/backup.env, выставить mode 0600)
#
# Cron (от root, см. SPEC § 9.4):
#   0 3 * * *   /opt/cleancity/backup.sh >> /var/log/cleancity-backup.log 2>&1
#
# Зависимости: postgresql-client, gpg, aws-cli (>=2).
# Retention 30 дней — настраивается через S3 lifecycle policy на бакет
# cleancity-backups (см. terraform или Yandex Cloud UI).

set -euo pipefail

CONFIG_FILE="${BACKUP_CONFIG:-/etc/cleancity/backup.env}"
if [[ ! -r "$CONFIG_FILE" ]]; then
    echo "[$(date -Is)] FATAL: config not readable: $CONFIG_FILE" >&2
    exit 1
fi
# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${PGHOST:?PGHOST is required}"
: "${PGPORT:=5432}"
: "${PGDATABASE:?PGDATABASE is required}"
: "${PGUSER:?PGUSER is required}"
: "${PGPASSWORD:?PGPASSWORD is required}"
: "${GPG_PASSPHRASE_FILE:?GPG_PASSPHRASE_FILE is required (mode 0600)}"
: "${S3_BUCKET:?S3_BUCKET is required (e.g. cleancity-backups)}"
: "${S3_ENDPOINT:=https://storage.yandexcloud.net}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is required}"
: "${AWS_DEFAULT_REGION:=ru-central1}"
: "${ALERT_SCRIPT:=/opt/cleancity/alert.sh}"

export PGPASSWORD AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_DEFAULT_REGION

if [[ ! -r "$GPG_PASSPHRASE_FILE" ]]; then
    echo "[$(date -Is)] FATAL: passphrase file not readable: $GPG_PASSPHRASE_FILE" >&2
    exit 1
fi

TS="$(date -u +%Y%m%dT%H%M%SZ)"
TMPDIR="$(mktemp -d -t cleancity-backup-XXXXXX)"
trap 'rm -rf "$TMPDIR"' EXIT

DUMP_FILE="$TMPDIR/${PGDATABASE}_${TS}.dump"
ENC_FILE="${DUMP_FILE}.gpg"
S3_KEY="postgres/$(date -u +%Y/%m)/${PGDATABASE}_${TS}.dump.gpg"

log() { echo "[$(date -Is)] $*"; }
alert() {
    local msg="$1"
    log "ALERT: $msg"
    if [[ -x "$ALERT_SCRIPT" ]]; then
        "$ALERT_SCRIPT" "BACKUP FAILED: $msg" || true
    fi
}

trap 'alert "backup.sh exited with non-zero status"' ERR

log "starting backup: db=$PGDATABASE host=$PGHOST → s3://$S3_BUCKET/$S3_KEY"

# 1) pg_dump в custom-формате (-Fc): сжатие + параллельный restore через pg_restore.
pg_dump \
    --host="$PGHOST" --port="$PGPORT" \
    --username="$PGUSER" \
    --no-owner --no-privileges \
    --format=custom \
    --file="$DUMP_FILE" \
    "$PGDATABASE"

DUMP_SIZE_BYTES=$(stat -c %s "$DUMP_FILE" 2>/dev/null || stat -f %z "$DUMP_FILE")
log "pg_dump complete: $DUMP_SIZE_BYTES bytes"

# Sanity: dump не должен быть нулевого размера (пустая БД ОК, но < 1KB — подозрительно).
if (( DUMP_SIZE_BYTES < 1024 )); then
    alert "dump suspiciously small: $DUMP_SIZE_BYTES bytes"
    exit 2
fi

# 2) Симметричное шифрование AES256 (passphrase из защищённого файла).
gpg --batch --yes --quiet \
    --cipher-algo AES256 \
    --compress-algo 0 \
    --passphrase-file "$GPG_PASSPHRASE_FILE" \
    --symmetric \
    --output "$ENC_FILE" \
    "$DUMP_FILE"

ENC_SIZE_BYTES=$(stat -c %s "$ENC_FILE" 2>/dev/null || stat -f %z "$ENC_FILE")
log "gpg encryption complete: $ENC_SIZE_BYTES bytes"

# 3) Upload в Yandex Object Storage. SSE на стороне Yandex (опц.) — настроим в UI бакета.
aws --endpoint-url="$S3_ENDPOINT" s3 cp \
    "$ENC_FILE" "s3://$S3_BUCKET/$S3_KEY" \
    --storage-class STANDARD_IA \
    --no-progress

log "upload OK: s3://$S3_BUCKET/$S3_KEY"
log "backup finished successfully"

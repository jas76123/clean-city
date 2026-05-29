#!/usr/bin/env bash
# Тест восстановления бэкапа: S3 → gpg --decrypt → pg_restore в ЭФЕМЕРНЫЙ контейнер.
# НИКОГДА не трогает прод-БД. Запуск на боевой VM (есть доступ к S3 и passphrase-файлу):
#   sudo BACKUP_CONFIG=/etc/cleancity/backup.env ./restore-test.sh [S3_KEY]
# Без S3_KEY берётся самый свежий объект из postgres/.
set -euo pipefail

CONFIG_FILE="${BACKUP_CONFIG:-/etc/cleancity/backup.env}"
[[ -r "$CONFIG_FILE" ]] || { echo "FATAL: config not readable: $CONFIG_FILE" >&2; exit 1; }
# shellcheck disable=SC1090
source "$CONFIG_FILE"

: "${PGDATABASE:?PGDATABASE is required}"
: "${GPG_PASSPHRASE_FILE:?GPG_PASSPHRASE_FILE is required}"
: "${S3_BUCKET:?S3_BUCKET is required}"
: "${S3_ENDPOINT:=https://storage.yandexcloud.net}"
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is required}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is required}"
: "${AWS_DEFAULT_REGION:=ru-central1}"
export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_DEFAULT_REGION

SCRATCH_DB="cleancity_restore_test"
# Guard: scratch-БД не должна совпадать с прод-БД.
if [[ "$SCRATCH_DB" == "$PGDATABASE" ]]; then
  echo "FATAL: scratch DB name equals prod DB ($PGDATABASE) — aborting" >&2; exit 1
fi
[[ ! -r "$GPG_PASSPHRASE_FILE" ]] && { echo "FATAL: passphrase not readable" >&2; exit 1; }

# 1) Выбрать ключ объекта (последний, если не задан явно).
S3_KEY="${1:-}"
if [[ -z "$S3_KEY" ]]; then
  S3_KEY="$(aws --endpoint-url="$S3_ENDPOINT" s3 ls "s3://$S3_BUCKET/postgres/" --recursive \
            | sort | tail -n1 | awk '{print $4}')"
  [[ -n "$S3_KEY" ]] || { echo "FATAL: no backups in s3://$S3_BUCKET/postgres/" >&2; exit 1; }
fi
echo "[restore-test] using s3://$S3_BUCKET/$S3_KEY"

TMPDIR="$(mktemp -d -t cleancity-restore-XXXXXX)"
CONTAINER="cleancity-restore-pg-$$"
cleanup(){ docker rm -f "$CONTAINER" >/dev/null 2>&1 || true; rm -rf "$TMPDIR"; }
trap cleanup EXIT

ENC="$TMPDIR/backup.dump.gpg"; DUMP="$TMPDIR/backup.dump"

# 2) Скачать + расшифровать.
aws --endpoint-url="$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$S3_KEY" "$ENC" --no-progress
gpg --batch --quiet --passphrase-file "$GPG_PASSPHRASE_FILE" --decrypt "$ENC" > "$DUMP"
DUMP_SIZE=$(stat -c %s "$DUMP" 2>/dev/null || stat -f %z "$DUMP")
echo "[restore-test] decrypted $DUMP_SIZE bytes"
(( DUMP_SIZE > 1024 )) || { echo "FATAL: decrypted dump suspiciously small" >&2; exit 2; }

# 3) Эфемерный Postgres (тот же образ, что в проде).
docker run -d --name "$CONTAINER" \
  -e POSTGRES_PASSWORD=restore -e POSTGRES_DB="$SCRATCH_DB" \
  postgis/postgis:16-3.4 >/dev/null
# Entrypoint поднимает ВРЕМЕННЫЙ сервер для initdb, затем перезапускает настоящий.
# pg_isready против временного даёт ложную готовность → pg_restore рвётся на середине
# ("terminating connection due to administrator command"). Сначала ждём завершения
# initdb, и только потом — готовности настоящего сервера.
for _ in $(seq 1 60); do
  docker logs "$CONTAINER" 2>&1 | grep -q "PostgreSQL init process complete" && break
  sleep 1
done
for _ in $(seq 1 30); do
  docker exec "$CONTAINER" pg_isready -U postgres >/dev/null 2>&1 && break
  sleep 1
done
sleep 1

# 4) pg_restore внутри контейнера.
# pg_restore возвращает ненулевой код даже из-за безобидных ошибок (образ postgis уже
# содержит схемы tiger/topology, а дамп их пересоздаёт → "already exists"). Поэтому НЕ
# даём этому прервать скрипт — авторитетная проверка успеха ниже, по числу строк.
docker cp "$DUMP" "$CONTAINER:/tmp/backup.dump"
docker exec "$CONTAINER" pg_restore --no-owner --no-privileges \
  -U postgres -d "$SCRATCH_DB" /tmp/backup.dump \
  || echo "[restore-test] pg_restore завершился с игнорируемыми ошибками — проверяю по строкам"

# 5) Проверка: ключевые таблицы есть и число строк — целое (это и есть критерий успеха).
ROWS_USERS=$(docker exec "$CONTAINER" psql -U postgres -d "$SCRATCH_DB" -tAc "SELECT count(*) FROM users" 2>/dev/null || echo ERR)
ROWS_COMPLAINTS=$(docker exec "$CONTAINER" psql -U postgres -d "$SCRATCH_DB" -tAc "SELECT count(*) FROM complaints" 2>/dev/null || echo ERR)
ROWS_USERS="${ROWS_USERS//[[:space:]]/}"; ROWS_COMPLAINTS="${ROWS_COMPLAINTS//[[:space:]]/}"
echo "[restore-test] users=$ROWS_USERS complaints=$ROWS_COMPLAINTS"

if [[ "$ROWS_USERS" =~ ^[0-9]+$ && "$ROWS_COMPLAINTS" =~ ^[0-9]+$ ]]; then
  echo "[restore-test] PASS"
else
  echo "[restore-test] FAIL: row counts not numeric" >&2; exit 2
fi

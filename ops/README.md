# ops/ — deploy playbook для Day 18

Содержимое папки и порядок установки на боевую ВМ (Yandex Cloud).
Запускается **один раз** при первом деплое. Все секреты создаются на сервере
и не коммитятся.

## Что лежит в папке

| Файл                  | Назначение                                                | Куда деплоим                       |
|-----------------------|-----------------------------------------------------------|------------------------------------|
| `backup.sh`           | pg_dump → gpg AES256 → s3://cleancity-backups             | `/opt/cleancity/backup.sh` (0755)  |
| `backup.env.example`  | Шаблон конфигурации backup.sh                             | `/etc/cleancity/backup.env` (0600) |
| `alert.sh`            | Telegram-обёртка для cron-алертов                         | `/opt/cleancity/alert.sh` (0755)   |
| `alert.env.example`   | Шаблон с TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID              | `/etc/cleancity/alert.env` (0600)  |
| `healthcheck.sh`      | curl /health каждые 5 мин + cooldown через state-файл     | `/opt/cleancity/healthcheck.sh` (0755) |
| `cleancity.cron`      | Расписание: healthcheck */5 мин, backup в 03:00 UTC       | `/etc/cron.d/cleancity` (0644)     |
| `install-cron.sh`     | Скрипт-инсталлер, выполняет всё ниже                      | запускается с sudo один раз        |

## Подготовка перед запуском install-cron.sh

На сервере должны быть:
- `postgresql-client` (для `pg_dump`)
- `gnupg` (для `gpg --symmetric`)
- `awscli` v2 (для upload в Yandex Object Storage)
- `curl`

```bash
sudo apt-get update
sudo apt-get install -y postgresql-client gnupg curl
# AWS CLI v2 — отдельной командой через installer (не из apt)
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp && sudo /tmp/aws/install
aws --version  # должно показать 2.x
```

Также нужны от Жасмин (готовы заранее):
- `TELEGRAM_BOT_TOKEN` (от @BotFather)
- `TELEGRAM_CHAT_ID` (id чата куда писать алерты)
- `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY` для service-account
  в Yandex Cloud с правами на бакет `cleancity-backups`
- Postgres-пароль (тот же, что в `.env` backend'а)

## Запуск установки

Из директории, куда склонирован репозиторий на сервере:

```bash
sudo bash ops/install-cron.sh
```

Скрипт спросит значения интерактивно и сам:
1. Создаст `/opt/cleancity/` и `/etc/cleancity/` с правильными правами.
2. Скопирует скрипты в `/opt/cleancity/`.
3. Сгенерирует случайную gpg-passphrase и положит в `/etc/cleancity/backup-gpg.pass` (mode 0600).
4. Создаст `/etc/cleancity/backup.env` и `/etc/cleancity/alert.env` по введённым значениям.
5. Установит `/etc/cron.d/cleancity`.
6. Сделает тестовый прогон healthcheck.sh и (опционально) backup.sh.

## Что важно проверить после установки

```bash
# Cron подцепил расписание
sudo systemctl status cron
sudo cat /etc/cron.d/cleancity

# Healthcheck отрабатывает (backend должен быть запущен)
sudo /opt/cleancity/healthcheck.sh && echo OK

# Принудительный backup (опционально, прогон на пустой БД — ОК)
sudo /opt/cleancity/backup.sh

# Telegram-алерт работает
sudo /opt/cleancity/alert.sh "deploy smoke test from $(hostname)"

# Логи появляются
sudo tail -f /var/log/cleancity-healthcheck.log
sudo tail -f /var/log/cleancity-backup.log
```

## Retention бэкапов

В консоли Yandex Object Storage на бакете `cleancity-backups` настроить
lifecycle-rule: «удалять объекты старше 30 дней, prefix `postgres/`».
Это **дешевле и надёжнее**, чем шелл-скрипт с `aws s3 rm`.

## Восстановление из бэкапа (restore drill)

Раз в месяц прогон на staging-окружении (см. SPEC § 9.4):

```bash
# 1) Скачиваем последний бэкап
aws --endpoint-url=https://storage.yandexcloud.net s3 cp \
    s3://cleancity-backups/postgres/2026/05/cleancity_20260513T030000Z.dump.gpg \
    /tmp/restore.dump.gpg

# 2) Расшифровываем
gpg --batch --yes --passphrase-file /etc/cleancity/backup-gpg.pass \
    --decrypt /tmp/restore.dump.gpg > /tmp/restore.dump

# 3) Применяем (на ОТДЕЛЬНУЮ staging-БД, не на prod!)
pg_restore -h staging-db.local -U cleancity -d cleancity_staging \
    --no-owner --no-privileges --clean --if-exists /tmp/restore.dump
```

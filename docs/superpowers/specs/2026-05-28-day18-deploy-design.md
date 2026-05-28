# Day 18 — Деплой CleanCity на Yandex Cloud

**Дата:** 2026-05-28
**Скоуп:** защищаемый минимум (см. раздел «Что не входит» ниже)
**Цель:** боевой стенд `clean--city.ru`, на котором мобилка (APK через хостинг, без магазинов), web-админка и backend работают на реальных данных. Достаточно для дипломной защиты и для презентации администрации Сочи.

## Контекст

- APK НЕ публикуется в магазины (ни Google Play, ни RuStore). Раздача — через лендинг на корневом домене + QR-код.
- В коде уже есть `S3StorageService` (`backend/.../storage/`), переключение local↔S3 по `STAGE=PROD` + наличию S3-конфига. Кода писать не надо.
- SMTP в backend через `SmtpEmailService` с фоллбеком на `LoggingEmailService`. Включается через env.
- Push-уведомления работают через polling каналом (FCM/Firebase не используется, см. memory `project_cleancity_notifications`).
- Релизный keystore и подписанная сборка — хвост Day 13, делается в этот же день перед заливкой.

## Архитектура

Одна VM в Yandex Cloud (Ubuntu 24.04, 2 vCPU / 4 GB RAM / 30 GB SSD, public IP) с `docker-compose.prod.yml` и 4 контейнерами.

| Контейнер | Образ / содержимое | Порты вне |
|---|---|---|
| `caddy` | официальный `caddy:2` | 80, 443 |
| `backend` | тот же образ, что dev (из `Dockerfile`) | — |
| `web-admin` | nginx-alpine со статикой `web-admin/dist` | — |
| `db` | `postgis/postgis:16-3.4` | — |

Внешние ресурсы (вне VM):
- Yandex Object Storage bucket `cleancity-photos-prod` — фото жалоб, публично читаемый (без листинга).
- Yandex Object Storage bucket `cleancity-backups` — приватные дампы БД, lifecycle 30 дней.

DNS — REG.RU, три A-записи на public IP VM:
- `clean--city.ru` → публичный лендинг с QR на APK
- `admin.clean--city.ru` → web-админка
- `api.clean--city.ru` → backend API

Cloudflare/WAF в этот день НЕ ставим (можно добавить позже за 10 минут).

## Компоненты и их конфигурация

### Caddyfile

```
clean--city.ru {
    root * /srv/landing
    file_server
}

admin.clean--city.ru {
    reverse_proxy web-admin:80
}

api.clean--city.ru {
    reverse_proxy backend:8080
    request_body { max_size 10MB }
}
```

Caddy сам получает Let's Encrypt сертификаты на старте — нужны лишь корректные A-записи.

### Прод-секреты

Файл `/opt/cleancity/.env.prod` (НЕ в git, права 600, владелец root):

```
STAGE=PROD
DB_PASSWORD=<pwgen>
JWT_SECRET=<32 байта base64>
STORAGE_S3_BUCKET=cleancity-photos-prod
STORAGE_S3_ENDPOINT=https://storage.yandexcloud.net
STORAGE_S3_ACCESS_KEY=<IAM ключ cleancity-photos>
STORAGE_S3_SECRET_KEY=<IAM секрет cleancity-photos>
STORAGE_S3_PUBLIC_URL_BASE=https://cleancity-photos-prod.storage.yandexcloud.net
EMAIL_SMTP_HOST=smtp.yandex.ru
EMAIL_SMTP_USER=noreply@clean--city.ru
EMAIL_SMTP_PASSWORD=<app password Я.Почты>
TELEGRAM_BOT_TOKEN=<токен @BotFather>
TELEGRAM_ALERT_CHAT_ID=<chat_id>
BACKUP_S3_BUCKET=cleancity-backups
BACKUP_S3_ACCESS_KEY=<IAM ключ cleancity-backups>
BACKUP_S3_SECRET_KEY=<IAM секрет cleancity-backups>
ADMIN_BOOTSTRAP_EMAIL=<email>
ADMIN_BOOTSTRAP_PASSWORD=<разовый, сменить после первого логина>
```

### Yandex IAM service accounts

Два раздельных аккаунта с разными ключами:

1. **`cleancity-photos`** — роль `storage.editor` только на bucket `cleancity-photos-prod`. Backend читает/пишет фото.
2. **`cleancity-backups`** — роль `storage.uploader` только на bucket `cleancity-backups` (только PutObject). При компрометации VM атакующий не сможет удалить старые бэкапы.

### SMTP

Яндекс 360 для бизнеса (бесплатный tier) — подключение почты `noreply@clean--city.ru` к домену, генерация app-password.

### Landing (статика)

`/opt/cleancity/landing/`:
- `index.html` — одна страница без фреймворков: логотип, абзац о проекте, QR-картинка, кнопка «Скачать APK» (`href="/cleancity.apk"`), краткая инструкция «разрешите установку из неизвестных источников» для Android.
- `cleancity.apk` — релизный подписанный APK (заливается scp при выкатке).
- `qr.png` — QR с содержимым `https://clean--city.ru/cleancity.apk`, сгенерирован один раз через `qrencode`.

## Поток данных

### Регистрация и работа жителя

1. Житель сканирует QR на `clean--city.ru` → телефон скачивает `cleancity.apk` → ставит → открывает.
2. App → `POST api.clean--city.ru/v1/auth/register` → запись в Postgres → `SmtpEmailService` шлёт письмо с кодом через Яндекс SMTP.
3. Создание жалобы с фото:
   - app: `POST api.clean--city.ru/v1/photos` (multipart) → backend через `S3StorageService` кладёт в bucket `cleancity-photos-prod` → возвращает publicUrl `https://cleancity-photos-prod.storage.yandexcloud.net/<uuid>`.
   - app: `POST /v1/complaints` с photoUrl + координатами → запись в БД.
4. Push об изменении статуса: app каждые ~60с поллит `GET /v1/notifications/unread` (без FCM).

### Работа админа

1. `admin.clean--city.ru` → Caddy → React SPA вызывает `api.clean--city.ru/v1/*`.
2. Смена статуса жалобы → backend пишет в `complaint_status_history` → следующий polling мобилки увидит уведомление.

### Бэкапы (cron на VM, 03:00 МСК ежедневно)

1. `pg_dump -Fc cleancity` → `cleancity-YYYYMMDD-HHMMSS.dump`.
2. `aws --endpoint-url=https://storage.yandexcloud.net s3 cp <файл> s3://cleancity-backups/`.
3. Локальный файл удаляется.
4. Bucket `cleancity-backups` имеет lifecycle 30 дней — старые удаляются автоматически (это конфиг bucket'а, не cron'а).

### Healthcheck (cron на VM, каждые 5 минут)

1. `curl -fsS https://api.clean--city.ru/v1/health`.
2. Если 200 — тишина. Иначе — POST в Telegram Bot API с алертом «API DOWN, status=…».
3. Дедуп через `/tmp/cleancity-down-flag`: после первого алерта не спамим каждые 5 минут; флаг сбрасывается при первом 200.

## Чек-лист задач

### Подготовка (на Mac)

1. Сгенерить релизный signing key (хвост Day 13, если не сделан): `keytool -genkeypair … -keystore release.keystore`. Пароли сохранить в `~/Keys/cleancity/`.
2. Собрать релизный APK c `API_BASE_URL=https://api.clean--city.ru`: `./gradlew composeApp:assembleRelease`.
3. Проверить APK: `aapt dump badging` + grep по строкам на `clean--city.ru` (убедиться, что нет dev-URL).
4. Сгенерить QR: `qrencode -o qr.png -s 8 "https://clean--city.ru/cleancity.apk"`.
5. Написать `landing/index.html` (~80 строк, без фреймворков, мобильная адаптация через viewport+flex).
6. Подготовить `docker-compose.prod.yml`, `Caddyfile`, `scripts/backup.sh`, `scripts/healthcheck.sh` (адаптировать существующие `ops/backup.sh`/`ops/alert.sh` под прод-пути).

### Yandex Cloud (консоль)

7. Создать VM Ubuntu 24.04, 2 vCPU / 4 GB / 30 GB SSD, public IP, SSH-ключ.
8. Создать 2 bucket'а: `cleancity-photos-prod` (public-read только GetObject, без листинга) и `cleancity-backups` (private).
9. На `cleancity-backups` — lifecycle rule 30 дней.
10. Создать 2 IAM service account'а (`cleancity-photos` / `cleancity-backups`) с раздельными ключами и минимальными ролями.

### REG.RU

11. Три A-записи: `@`, `admin`, `api` → public IP VM. TTL 600.
12. Подождать резолва (~10-15 минут), проверить через `dig +short`.

### На VM (SSH)

13. `apt update && apt install -y docker.io docker-compose-plugin awscli qrencode`.
14. `mkdir -p /opt/cleancity/{landing,backups,scripts}`.
15. Скопировать с Mac: `docker-compose.prod.yml`, `Caddyfile`, `.env.prod`, `landing/`, `cleancity.apk`, `scripts/backup.sh`, `scripts/healthcheck.sh`.
16. `.env.prod` → права 600, root:root.
17. `docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d`. Caddy при первом запуске возьмёт сертификаты.
18. Flyway-миграции запустятся автоматически при старте backend.
19. Backend на старте создаст seed-админа из `ADMIN_BOOTSTRAP_*` (через `bootstrapInitialAdmin`).
20. Установить cron'ы:
    ```
    0 3 * * * /opt/cleancity/scripts/backup.sh
    */5 * * * * /opt/cleancity/scripts/healthcheck.sh
    ```

## Тестирование (acceptance criteria)

| # | Проверка | Команда / действие | Ожидаем |
|---|---|---|---|
| 1 | HTTPS работает на 3 поддоменах | `curl -I https://clean--city.ru`, `…/admin`, `…/api/v1/health` | 200 + валидный TLS |
| 2 | Лендинг открывается | браузер | страница с QR и кнопкой |
| 3 | APK скачивается | клик «Скачать» | файл `cleancity.apk` корректного размера |
| 4 | QR работает | сканирование с экрана | открывается ссылка на APK |
| 5 | Установка | Android открывает APK | ставится, иконка в меню |
| 6 | Mobile + prod backend | регистрация → код на почту → логин → создать жалобу с фото | жалоба в админке, фото открывается по S3 URL |
| 7 | Push-канал | админ меняет статус → жду 60с на телефоне | приходит уведомление |
| 8 | Бэкап | `bash scripts/backup.sh` руками | в bucket появился `.dump` ≥ 10 KB |
| 9 | Healthcheck alert | `docker compose stop backend`, жду 5-10 мин | в Telegram «API DOWN» |
| 10 | Восстановление после стопа | `docker compose start backend` | через 5 мин — healthcheck молчит |

День 18 закрыт, когда все 10 проверок зелёные.

## Что НЕ входит (отложено)

- Восстановление БД из бэкапа в отдельную staging-БД — не делаем, только проверяем что дамп лежит в bucket'е.
- GPG-шифрование бэкапов — bucket приватный, IAM-ключ только-на-запись, этого достаточно.
- Telegram-алерты на ERROR-логи в backend — только healthcheck.
- Cloudflare proxy / WAF — добавляется позже, не блокер защиты.
- Seed демо-данных (80 жалоб, фото и т. п.) — это Day 19.

## Риски и митигации

| # | Риск | Митигация |
|---|---|---|
| 1 | DNS не разрезолвился → Let's Encrypt не выдаст сертификат | После A-записей подождать 10-15 мин, проверить `dig +short api.clean--city.ru` ДО запуска `docker compose up`. |
| 2 | APK захардкожен на dev URL → телефон не видит прод | Пункт 2-3 чек-листа: пересборка с `API_BASE_URL=https://api.clean--city.ru`, грeп строк через `aapt dump badging`. |
| 3 | IAM-ключ бэкапов с правами `storage.editor` → атакующий стирает бэкапы при компрометации VM | Явно роль `storage.uploader`, проверить `aws s3 rm` → AccessDenied. |
| 4 | Яндекс SMTP блокирует первую массовую отправку | До защиты прогнать 3-5 регистраций вручную, посмотреть доставку. |
| 5 | Public-read на photos-bucket выдаёт листинг файлов | На bucket'е отключить anonymous `storage.objects.list`, проверить `aws s3 ls --no-sign-request` → AccessDenied. |

## Готовность к работе

Время на день: ~6-8 часов одним непрерывным заходом. Блокеры — оплата Yandex Cloud (нужна карта РФ) и доступ к REG.RU для смены DNS.

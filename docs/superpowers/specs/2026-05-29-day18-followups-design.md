# Day-18 follow-ups: verify-email fix + SMTP + backup restore

**Дата:** 2026-05-29
**Контекст:** боевой стенд CleanCity развёрнут (`clean--city.ru`, api/`admin` поддомены, Caddy + docker-compose.prod). Закрываем три follow-up'а после деплоя: почта пока на логах, ссылка verify-email ведёт в 404, бэкап ни разу не восстанавливали.
**Workflow:** код идёт прямо в `main` (без feature-веток).

---

## Unit 1 — Web auth pages на backend (фикс ссылок из писем)

### Проблема
`AuthService.sendVerifyEmail` (и аналоги для reset/invite) строят ссылку как
`$baseUrl/<path>?token=…`, где `baseUrl = https://api.clean--city.ru`. В `deploy/Caddyfile`
поддомен `api.clean--city.ru` reverse-proxy'ит на `backend:8080`. Значит клик по ссылке —
это **браузерный GET** `api.clean--city.ru/<path>`, а на backend есть только JSON-эндпоинты
`POST /auth/verify-email`, `POST /auth/reset-password`, `POST /auth/admin/accept-invite`.
Результат: ссылка из письма → **404**. Баг затрагивает все три типа ссылок.

### Решение
Новая корневая группа маршрутов `webAuthRoutes(service)`, отдающая три брендированные
самодостаточные HTML-страницы. Тот же origin (`api.clean--city.ru`) → **CORS не нужен**
(согласуется с комментарием в Caddyfile). Существующие JSON-эндпоинты не меняются —
добавляем только тонкий HTML/JS-слой поверх них.

**Новые файлы:**
- `backend/src/main/kotlin/com/example/cleancity/web/WebAuthRoutes.kt`
- `backend/src/main/kotlin/com/example/cleancity/web/HtmlPages.kt` (палитра из EmailTemplates: `#5DDE8A` / `#0d2b1a`)

**Маршруты:**
- `GET /verify-email?token=` → страница с кнопкой **«Подтвердить email»**; inline-JS POST'ит
  `{token}` на существующий `/auth/verify-email`. Успех → «Email подтверждён — откройте
  приложение и войдите.» Кнопка (а не авто-verify на GET) намеренно обходит **prefetch**
  почтовых сканеров, который иначе сжёг бы одноразовый токен.
- `GET /reset-password?token=` → форма (новый пароль + подтверждение) → JS POST на
  `/auth/reset-password`. Успех → «Пароль обновлён — войдите заново.»
- `GET /accept-invite?token=` → форма (установка пароля) → JS POST на
  `/auth/admin/accept-invite`. Успех → «Аккаунт активирован.»
- Отсутствующий/пустой `token` в query → дружелюбная страница ошибки.
- Невалидный/истёкший токен всплывает после POST (400 → «Ссылка недействительна или истекла.»).

Мобильный путь (`VerifyEmailScreenModel`) не трогаем — он работает независимо.

### Тесты
Backend route-тесты: GET каждого пути → 200 + содержит token + ожидаемый control; пустой
token → страница ошибки. Ручной: клик по реальной ссылке end-to-end после деплоя.

---

## Unit 2 — Включение SMTP (Yandex 360)

Код полностью разведён: `buildEmailService()` автоматически переключается с
`LoggingEmailService` на `SmtpEmailService`, как только заданы `SMTP_HOST` + `SMTP_USER`.
`application.conf` маппит `SMTP_*` env → `email.smtp_*`; `docker-compose.prod.yml` пробрасывает
`SMTP_HOST/PORT/USER/PASSWORD/EMAIL_FROM` из `.env.prod` в контейнер backend. Задача = **конфиг + ops**, без изменений кода.

**Домен `clean--city.ru` зарегистрирован в REG.RU** → все DNS-записи правятся в панели REG.RU.
Yandex 360 — это почтовый провайдер, на который указываем почту домена.

### Часть Жасмин (REG.RU DNS + Yandex 360)
1. Подключить домен `clean--city.ru` в Yandex 360.
2. Добавить в **DNS-панель REG.RU** записи, которые выдаст Yandex: верификационный **TXT**,
   **MX** (`mx.yandex.net.` приоритет 10), **SPF** (`v=spf1 redirect=_spf.yandex.net`) и **DKIM**
   (ключ из Yandex 360) — чтобы письма не падали в спам.
3. Создать ящик `noreply@clean--city.ru`.
4. Создать **app-password** для SMTP этого ящика; передать его мне в сессии (по правилу
   «никаких секретов в git/коммитах» он попадает только в `.env.prod`).

### Часть сервера (я)
1. SSH на прод, прописать в `/opt/cleancity/.env.prod`:
   `SMTP_HOST=smtp.yandex.ru`, `SMTP_PORT=465`, `SMTP_USER=noreply@clean--city.ru`,
   `SMTP_PASSWORD=<app-pw>`, `EMAIL_FROM=CleanCity Сочи <noreply@clean--city.ru>`.
2. Пересоздать backend (`docker compose --env-file /opt/cleancity/.env.prod -f docker-compose.prod.yml up -d backend`).
3. Проверить в логах строку `EmailService: using SmtpEmailService`.
4. Дёрнуть реальный `resend-verification` на тестовый ящик → письмо пришло **и** ссылка
   открывается (зависит от задеплоенного Unit 1).

---

## Unit 3 — Тест восстановления бэкапа

`ops/backup.sh` делает `pg_dump -Fc` (custom-формат) → `gpg --symmetric AES256`
(passphrase-файл) → Yandex Object Storage. Скрипта восстановления нет.

**Новый `ops/restore-test.sh`** (он же документированная DR-процедура):
1. Скачать backup-объект из S3 (`aws --endpoint-url=… s3 cp s3://<bucket>/<key> …`).
2. Расшифровать: `gpg --batch --quiet --passphrase-file <file> --decrypt … > restore.dump`.
3. `pg_restore --no-owner --no-privileges` в **одноразовую scratch-БД** (эфемерный контейнер
   `postgres`, **никогда не прод**).
4. Проверка: `SELECT count(*)` по ключевым таблицам (users, complaints, …) — числа вменяемые.
5. Удалить scratch-БД + temp-файлы. Вывести **PASS/FAIL**.
6. Скрипт **отказывается работать**, если имя целевой БД совпадает с прод-БД.

Прогнать один раз сейчас = закрытие задачи 4.

---

## Критический порядок
1. **Unit 1 первым** — `docker compose build backend` + `up -d`. Без GET-страниц даже рабочий
   SMTP шлёт письма со ссылками, которые всё равно 404.
2. **Unit 2 следом** — SMTP env + пересоздание backend; теперь реальные письма уходят и ссылки открываются.
3. **Unit 3** — независим; можно в любой момент (лучше до того, как полагаться на прод-данные).

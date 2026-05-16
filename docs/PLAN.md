# CleanCity — План реализации MVP (3 недели)

**Старт:** 2026-05-07
**Дедлайн:** ~2026-05-28 (защита диплома)
**Hosting:** Yandex Cloud Free Trial (Вариант В) + локальная разработка через Docker Compose
**Спецификация:** `~/Desktop/Myapp/SPEC.md`

---

## Стратегия

```
Неделя 1 (дни 1–7):    BACKEND MVP   ←  блокирует всё остальное
Неделя 2 (дни 8–14):   MOBILE        ←  основной показ на защите
Неделя 3 (дни 15–21):  WEB ADMIN + DEPLOY + ДЕМО
```

Backend — критический путь: пока нет API, mobile и web писать вслепую невозможно.
Mobile важнее web — это «лицо проекта» для жителей.
Web admin может быть проще (показываем основной флоу — таблица+статусы+аналитика).

**Принципы работы:**
- В конце каждого дня — коммит в git с понятным сообщением.
- В конце каждой недели — самопроверка по чек-листу + видео-фиксация прогресса (страховка для диплома).
- Если день горит — переносим необязательное в backlog (помечено ⚠).

---

## Неделя 1 — Backend MVP

### День 1 (08.05) — Setup проекта + домен/email (критический путь)

- [x] Очистить старый код в `cleancity-kmp/backend/` (`Subbotniks`, `MapMarker` со старой моделью)
- [x] Обновить `shared/models/ProblemCategory.kt` на 18 категорий (как в SPEC § 3.1)
- [x] Обновить `shared/models/ComplaintStatus.kt` — 5 статусов (NEW, IN_PROGRESS, RESOLVED, REJECTED, DUPLICATE)
- [x] Создать `UserRole.kt`
- [x] Настроить Flyway-миграции (`backend/src/main/resources/db/migration/V1__create_users.sql`)
- [x] `docker-compose.yml` для dev: postgres + backend
- [x] Healthcheck endpoint `GET /health`
- [x] Структура папок backend (`auth/`, `complaints/`, `votes/`, `storage/`, `notifications/`, `database/`, `email/`, `config/`). Папки `announcements/`, `analytics/` появятся в Spec 2/3 — соответствующие фичи отложены.
- [ ] **Параллельно (важно — сразу!):** регистрация домена `cleancity.ru` через reg.ru (~200 ₽/год). Ждать активации (до 24ч).
- [ ] Активация Yandex Cloud (с грантом 4000 ₽) — заявка может ехать 1–2 дня, лучше подать сегодня.
- [ ] Регистрация Yandex Mail 360 для домена (бесплатно для до 3 пользователей, далее 200 ₽/мес/польз). Создать `noreply@cleancity.ru`.
- [ ] DNS-записи на reg.ru: MX → Yandex Mail; SPF (`v=spf1 redirect=_spf.yandex.ru`); DKIM (получить ключ из Yandex Mail кабинета); DMARC (`v=DMARC1; p=none; rua=mailto:...`).
- [ ] Проверка через `mxtoolbox.com`: SPF/DKIM/DMARC валидны.

**Checkpoint:** `docker compose up` — backend стартует, `/health` отвечает 200, миграция V1 применилась. Домен активен, тестовое письмо с `noreply@cleancity.ru` доходит на gmail/mail.ru без папки «Спам».

---

### День 2 (09.05) — Auth: регистрация и вход (без 2FA)

- [x] Миграция V1: таблицы `users`, `email_tokens`, `refresh_tokens`
- [x] Email-сервис: `SmtpEmailService` (jakarta.mail) + `LoggingEmailService` для DEV. Шаблоны verify + reset.
- [x] `POST /auth/register` — создание RESIDENT, отправка verify-письма
- [x] `POST /auth/verify-email` — активация по токену
- [x] `POST /auth/login` — bcrypt-проверка, возврат JWT (access 15 мин для админа / 1 час для резидента, refresh 8ч / 30 дней)
- [x] `POST /auth/refresh` — обновление токена через хэш в `refresh_tokens`
- [x] `POST /auth/forgot-password` + `POST /auth/reset-password`
- [x] JWT middleware (Ktor Authentication plugin)

**Checkpoint:** Postman сценарий: register → проверка письма (можно из логов в dev) → verify → login → запрос с Bearer-токеном → 200.

---

### День 3 (10.05) — Auth security: lockout, 2FA, sessions

- [x] Lockout-логика: после 5 неуспешных логинов — `locked_until = NOW() + 15 min`
- [x] Rate limiting middleware (in-memory bucket с TTL — `RateLimiter`)
- [x] 2FA TOTP для админов (библиотека `dev.turingcomplete:kotlin-onetimepassword`)
  - [x] `POST /auth/2fa/setup` — генерация secret + QR (otpauth URI)
  - [x] `POST /auth/2fa/verify` — активация
  - [x] `POST /auth/login-2fa` — двухшаговый login для админа
- [x] Audit-лог: миграция V3, `AuditLogger.log(actor, action, target, ip)`
- [x] `GET /auth/sessions` + `DELETE /auth/sessions/{id}` (управление refresh-токенами)
- [x] `POST /auth/admin/invite` + `POST /auth/admin/accept-invite`

**Checkpoint:** 5 неудачных логинов → 6-й попытка возвращает 423 Locked. Включил 2FA в Google Authenticator → вход работает по двум шагам. `audit_log` содержит записи входов.

---

### День 4 (11.05) — Жалобы: CRUD + фото

- [x] Миграция V2: `complaints`, `complaint_photos` (PostGIS GEOGRAPHY-поле, GIST-индекс)
- [x] `StorageService` интерфейс (уже есть в скелете) + `LocalStorageService` (для dev) + `S3StorageService` (для prod)
  - [x] AWS SDK v2 для Yandex Object Storage (S3-совместимый)
  - [x] Resize при upload: original + thumb 640px (через `imgscalr`)
  - [x] EXIF-стрипинг (через re-encode JPEG в `ImageProcessor`; metadata-extractor — для чтения Orientation)
- [x] `POST /complaints` — multipart, валидация фото (magic bytes, размер ≤10MB), 1–5 файлов
- [x] **Авто-генерация title** в `ComplaintService.create()`: `title = "${ProblemCategory.localizedLabel(category)} · ${address.split(',')[0].trim()}"` (например, `«Мусор · ул. Транспортная»`). Клиент title не передаёт. (Сделано в `d22da19` до Day 5; задним числом закрываем галку.)
- [x] `GET /complaints` — фильтры по status/category/district + пагинация + role-based фильтр (RESIDENT не видит REJECTED/DUPLICATE)
- [x] `GET /complaints/{id}` — детали + фото (голоса/история статусов — заглушка для Day 5)
- [x] `GET /complaints/map?swLat=&swLon=&neLat=&neLon=` — облегчённые маркеры (lat/lon between; PostGIS ST_Within — оптимизация на Day 5+, если понадобится)
- [x] `GET /complaints/mine`

**Checkpoint:** ✓ создал жалобу с 1 и с 3 фото, увидел в `/complaints`, `/complaints/map`, `/complaints/mine`, скачал фото и thumb по URL. Валидация: координаты вне Сочи → 400, без фото → 400, битый JPEG → 400, гость POST → 401.

---

### День 5 (12.05) — Голоса + смена статусов + дубликаты

- [x] Миграция: `votes`, `status_changes` (`V5__create_votes_and_status_changes.sql`, с бэкфиллом автоголосов для жалоб Day 4)
- [x] `POST /complaints/{id}/votes` (идемпотентно) и `DELETE` (отозвать). Автор не может отозвать свой голос → 409.
- [x] **Блокировка голосов на терминальные статусы:** для `REJECTED` и `DUPLICATE` оба endpoint возвращают 409 Conflict с `{message: "Голосование закрыто: жалоба <статус>"}`.
- [x] `GET /complaints/voted` (мои голоса, включая закрытые; свои жалобы исключены, для них `/mine`)
- [x] `PATCH /complaints/{id}/status` — валидация перехода, обязательный `comment`, запись в `status_changes`, audit (`COMPLAINT_STATUS_CHANGE`)
- [x] При статусе `DUPLICATE` — слияние голосов с оригиналом (`INSERT ... ON CONFLICT DO NOTHING`); валидация: оригинал обязателен, не сам себе, оригинал в активном статусе
- [x] `GET /complaints/duplicates` — поиск в радиусе через `ST_DWithin` (метров), радиус по умолчанию 100м, максимум 1000м
- [x] Расчёт `priority_score` в SQL (как в SPEC § 3.5) + сортировка `?sort=priority`. `age_hours` считается буквально только пока статус = NEW.
- [x] **Дополнительно (закрыто на Day 5):** автоголос автора при создании жалобы (одна транзакция с INSERT complaints); видимость REJECTED/DUPLICATE проголосовавшим (закрыт TODO Day 4); `NotificationService` interface + `NoopNotificationService` — точки вызова в `changeStatus` готовы, реальная реализация на Day 6; расширение `ComplaintResponse` (`votesCount`, `userVoted`, `statusHistory`).

**Checkpoint:** ✓ проголосовал за чужую жалобу → счётчик 1→2. ✓ Сменил статус NEW→IN_PROGRESS→RESOLVED — `statusHistory` пополняется, `resolvedAt` ставится. ✓ Пустой комментарий → 400. ✓ POST/DELETE голос на REJECTED → 409. ✓ DUPLICATE с активным оригиналом → 200, голоса перенесены через ON CONFLICT. ✓ Гость и наблюдатель видят REJECTED/DUPLICATE как 404, голосовавший — со `statusHistory.comment` админа.

---

### День 6 (13.05) — Объявления + аналитика + push (заглушка FCM)

> **Состояние на 2026-05-13:** разбит на три spec'а, все три закрыты в main:
> - **Spec 1 (закрыт 2026-05-13)** — in-app notifications infra: миграция V6, `NotificationService`/`DbNotificationService`, триггер при смене статуса, 4 REST-эндпоинта. Источник: `docs/superpowers/specs/2026-05-11-notifications-infrastructure-design.md` + plan `2026-05-11-notifications-infrastructure.md`. FCM отложен — основной канал push'ей решено реализовать через polling (см. [[project_cleancity_notifications]]).
> - **Spec 2 (закрыт 2026-05-13)** — объявления: V7 миграция `announcements`, CRUD `/announcements`, триггер уведомлений жителям района.
> - **Spec 3 (закрыт 2026-05-13)** — аналитика + справочники + cleanup: `/analytics/*` (5 эндпоинтов с `?period=week|month|all`), `/categories`, `/districts`, фоновый шедулер чистки notifications старше 90 дней.

**Spec 1 — выполнено:**

- [x] Миграция V6: `notifications` (in-app история, с CHECK `(kind, target)` и индексами по `(user_id, created_at)` / `WHERE read_at IS NULL`).
- [x] `NotificationService` интерфейс + `DbNotificationService` (INSERT в `notifications`).
- [x] Триггер при смене статуса (SPEC § 5.2): `IN_PROGRESS/RESOLVED` → автору; `REJECTED/DUPLICATE` → автор + все `+1`-голосовавшие. Вызов `notify()` находится внутри той же транзакции, что и `UPDATE complaints` — сбой откатывает всё (покрыто e2e-тестом).
- [x] **In-app уведомления endpoints** (SPEC § 4.6):
  - [x] `GET /notifications?limit=50&offset=0` — пагинация, новые сверху, окно 90 дней
  - [x] `GET /notifications/unread-count` — для бейджа на иконке
  - [x] `PATCH /notifications/{id}/read` — идемпотентно, чужой id → 404
  - [x] `PATCH /notifications/read-all`

**Spec 2 — выполнено:**

- [x] Миграция V7: `announcements` (колонка `announcement_id` в `notifications` уже создана в V6 — FK добавляется отложенно после V7).
- [x] `GET/POST/PATCH/DELETE /announcements`
- [x] Триггер при объявлении → жителям района (или всем)
- [x] (Триггер при создании жалобы → админам района — отложен, не критично для MVP)

**Spec 3 — выполнено:**

- [x] Аналитика-эндпоинты (обычные SQL+агрегация в Kotlin; materialized views — Phase 2):
  - [x] `/analytics/overview` — counts по статусу + total + today/week + `slaBreachCount` (**только активные NEW/IN_PROGRESS с истёкшим нормативом**).
  - [x] `/analytics/by-category?period=week|month|all` — counts по 18 категориям + sharePct + avg resolution hours.
  - [x] `/analytics/by-district?period=...` — counts/new/resolved по 4 районам Сочи.
  - [x] `/analytics/sla?period=...` — slaHours, avgResolutionHours, breachPct (по resolved-жалобам), resolvedCount.
  - [x] `/analytics/votes-impact?period=...` — buckets `0 / 1-9 / 10-49 / 50+` с count и avgResolutionHours.
- [x] `/categories` — справочник 18 категорий (code+label+slaHours) из enum `ProblemCategory` + `CategorySla`.
- [x] `/districts` — справочник 4 районов Сочи из enum `District`.
- [x] Фоновая чистка notifications старше 90 дней: kotlinx.coroutines внутри Ktor (`Application.installNotificationCleanup`), запуск каждые 24ч.

**Out-of-scope для MVP (по решению 2026-05-11 и Spec 3 от 2026-05-13):**
- FCM / Firebase Admin SDK / `push_tokens` / `POST /users/me/push-token` — основной канал push'ей реализуется через polling backend API. Возврат к FCM возможен после пилота как декоратор `FcmNotificationService` поверх `DbNotificationService`.
- `/analytics/active-users` (DAU/WAU/MAU) — Phase 2.
- `/analytics/export?format=xlsx` — Phase 2 (PDF «Сводный за месяц» остаётся в Day 17).

**Checkpoint Spec 3 (пройден 2026-05-13):** 95 backend-тестов зелёные. Юнит-тесты `AnalyticsServiceTest` покрывают SLA breach (active-only), фильтр `period=week`, buckets голосов, агрегацию по районам. Routes-тесты: 401 для гостя, 403 для резидента, 200 для админа на всех 5 эндпоинтах, 400 на невалидном `period`. Cleanup-тест: `deleteOlderThan(90)` удаляет старое, оставляет свежее.

**Checkpoint Spec 1 (пройден 2026-05-13):** docker compose e2e — A создаёт жалобу, B голосует, админ → REJECTED, оба получают уведомление с комментарием админа; `unread-count`, `mark-as-read` (idempotent), чужой id → 404, `read-all` — всё работает.

---

### День 7 (14.05) — Backend polish + первый деплой

- [x] Security headers middleware (CSP, HSTS, X-Frame-Options и пр.) — `plugins/SecurityHeaders.kt` + тесты
- [x] Error handling: глобальный exception handler, стандартизованные коды ошибок, **никаких stacktrace в JSON наружу** — `ApiException` sealed + `ApiError(code, message)` + `ErrorCodes`; все route'ы мигрированы
- [x] OpenAPI-схема — вручную написан `docs/api/openapi.yaml` (38 endpoints, 22 schemas)
- [x] Юнит-тесты на критическое: auth (lockout, 2FA) — `AuthSecurityTest`; смена статусов с пушем — `ComplaintStatusNotificationTest`; видимость по ролям — `ComplaintVisibilityTest`; блокировка голосов на REJECTED/DUPLICATE — `VoteServiceTest`
- [x] Seed-скрипт для dev: `db/seed-dev/V99__seed_dev.sql` (отдельный Flyway location, активируется при STAGE=DEV) — 1 админ, 5 резидентов, 50 жалоб по Сочи
- [x] Dockerfile для backend (multi-stage build) — в корне `Dockerfile`; non-root user, HEALTHCHECK, deps-кэш слой
- [x] **Бэкап-скрипт** — `ops/backup.sh` + `ops/backup.env.example`. pg_dump → gpg AES256 → s3://cleancity-backups. Retention — S3 lifecycle policy.
- [x] **Telegram-алерты:** `backend/.../logging/TelegramAppender.kt` (ERROR-уровень, rate-limit) + `ops/alert.sh` для cron + `TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID` в `.env.example`.
- [x] **Health-check cron:** `ops/healthcheck.sh` + `ops/cleancity.cron`. Cooldown через state-файл, recovery-сообщение.
- [x] Yandex Cloud account активация подтверждена (Жасмин проверила 2026-05-13). Bonus: `docker build .` локально прошёл успешно; deploy-playbook для cron-инфры — `ops/README.md` + `ops/install-cron.sh`.

**Checkpoint конца недели 1:** Backend функциональный. Все API работают локально через `docker compose up`. Тесты зелёные. Telegram-бот получает тестовое сообщение из dev-окружения. Готов к интеграции с mobile.

⚠ **В backlog (если не успеваем):** OpenAPI генерация (можно вручную типизировать в web на день 16).

---

## Неделя 2 — Mobile (Compose Multiplatform → Android)

Базовый ориентир — текущий мокап `mobile-mockup-v3.html`. UI компоненты переводим в Compose 1:1.

### День 8 (15.05) — Mobile setup + auth-экраны

- [x] Очистить `composeApp/` от старого кода (старая 4-категорная модель) — Yandex map-actuals перенесены в `legacy/`
- [x] Обновить `composeApp/build.gradle.kts`: ktor-client, kotlinx-serialization, kotlinx-coroutines, koin (DI), encrypted-shared-preferences (firebase-messaging отложен до Day 12; yandex-maps SDK остался для Day 9)
- [x] Структура: `data/` (api, repos, storage), `domain/`, `ui/components/`, `ui/feature/`, `ui/theme/`, `di/`
- [x] `ApiClient` (Ktor Client с logging + bearer-auth + auto-refresh interceptor)
- [x] `TokenStorage` через EncryptedSharedPreferences (Android)
- [x] Theme: цвета и шрифты из мокапа (зелёный 0d2b1a/5DDE8A, Golos Text, Unbounded)
- [x] Splash + welcome screen с тремя кнопками: «Войти», «Регистрация», **«Зайти как гость»**
- [x] `LoginScreen` (email + password)
- [x] `RegisterScreen` (email + password + полное имя + **обязательный чекбокс согласия** с двумя кликабельными ссылками — открывают `LegalScreen`)
- [x] `LegalScreen` — WebView с host-allowlist (`/legal/privacy`, `/legal/terms` через BuildConfig.API_BASE_URL)
- [x] При успешной регистрации backend сохраняет `accepted_terms_at = NOW()`, `accepted_terms_version` (бэкенд из Week 1)
- [x] `VerifyEmailScreen` (приходит deep-link cleancity://verify?token=...) + ForgotPassword + ResetPassword flow
- [x] Навигация (Voyager Navigator) — реактивная маршрутизация на AuthState
- [x] Реализованы 12 фаз плана `docs/superpowers/plans/2026-05-13-week2-day8-mobile-auth.md`; 24/24 unit-тестов зелёные; assembleDebug → APK 143 MB
- [ ] **Checkpoint (manual smoke-tests на эмуляторе)** — Жасмин: backend up локально → `adb install composeApp-debug.apk` → прогнать Сценарии 1-4 из плана (happy path, guest mode, forgot/reset, edge cases)

**Checkpoint:** На Android-симуляторе: register → email-link открывает приложение → verify → login → main screen. Без галочки согласия кнопка «Зарегистрироваться» disabled. Гостевой режим: «Зайти как гость» → MapScreen без crashes; тап «Подтверждаю» на жалобе → диалог «Войдите чтобы поддержать».

**Известные ограничения (флаг для Day 9 polish):**
- `LoginScreenModel.resendVerification` молча игнорирует ошибку сети (нет snackbar)
- `VerifyEmailScreenModel.resend()` стартует 5-мин cooldown даже при network failure
- Все UI-строки hard-coded (i18n debt)
- `ClickableText` в `ConsentRow` — deprecated, заменить на `BasicText + LinkAnnotation` когда Compose обновится

---

### День 9 (16.05) — Mobile карта (Yandex Maps SDK)

- [ ] Подключить Yandex Maps Mobile SDK (получить API-ключ в кабинете developer.tech.yandex.ru)
- [ ] `MapScreen` с тайлами, навигация
- [ ] При перемещении карты → запрос `/complaints/map?bbox=...` с дебаунсом 500мс
- [ ] Маркеры разными цветами по статусу (amber/blue/green)
- [ ] Категории-чипы наверху (топ-6 + «Ещё» — открывает bottom sheet с 18 категориями)
- [ ] FAB «Сообщить о проблеме» → CreateComplaintScreen
- [ ] FAB «моё местоположение» (запрос permissions)
- [ ] При тапе на маркер → bottom sheet с превью + переход в детали

**Checkpoint:** На реальном Android-устройстве: открыть карту, увидеть маркеры реальных жалоб (созданных в Postman), фильтровать по категории.

---

### День 10 (17.05) — Mobile лента + детали жалобы + голос

- [ ] `FeedScreen`: горизонтальная карусель объявлений + список жалоб (`LazyColumn`)
- [ ] `ComplaintDetailScreen`: фото-pager, мета (адрес/время/автор), VoteCard (счётчик + кнопка), описание, история статусов
- [ ] Кнопка «Подтверждаю» вызывает `POST /complaints/{id}/votes` (для гостей — диалог «Войдите чтобы поддержать»)
- [ ] Pull-to-refresh
- [ ] Фильтр «Все жалобы / Мои» (toggle)
- [ ] Профиль с менюшками (в MVP большинство — заглушки `flash`)

**Checkpoint:** Открыть жалобу из ленты, проголосовать, увидеть обновлённый счётчик. Голос вне аккаунта показывает диалог логина.

---

### День 11 (18.05) — Mobile создание жалобы

- [ ] `CreateComplaintScreen` — это самый важный flow проекта
- [ ] Photo picker: камера + галерея, до 5 фото, превью
- [ ] GPS-permission запрос → определение координат
- [ ] Reverse geocoding через Yandex Maps SDK → адрес автоматически
- [ ] 18 категорий: компактный grid 3×6 + поиск (как в обновлённом мокапе)
- [ ] При выборе адреса+категории → запрос `/complaints/duplicates` → блок «Возможно, проблема уже есть» с кнопкой «+1 голос за существующую»
- [ ] Описание (textarea, до 1000 символов)
- [ ] Submit: multipart upload через ktor-client
- [ ] Прогресс-индикатор и обработка ошибок (нет интернета, фото слишком большое, etc)

**Checkpoint:** На реальном устройстве: сфотографировать → выбрать категорию → отправить → жалоба появляется в ленте и на карте.

---

### День 12 (19.05) — Mobile уведомления + мои/поддержанные

- [ ] FCM SDK setup, регистрация токена через `POST /users/me/push-token` после логина
- [ ] `NotificationsScreen` использует серверный API:
  - [ ] `GET /notifications?limit=50` при открытии экрана
  - [ ] Бейдж на иконке нижней навигации = `GET /notifications/unread-count` (опрос при возврате в foreground)
  - [ ] Тап по элементу → `PATCH /notifications/{id}/read` + переход в детали (complaint_id или announcement_id)
  - [ ] Кнопка «Прочитать все» → `PATCH /notifications/read-all`
  - [ ] Empty state «У вас пока нет уведомлений»
- [ ] `MyComplaintsScreen` (`/complaints/mine`)
- [ ] `VotedComplaintsScreen` (`/complaints/voted`) — обязательно показывает закрытые REJECTED/DUPLICATE с пояснением админа
- [ ] Дисплей комментария админа в детали закрытой жалобы — выделенным блоком («Решение администрации: <текст>»)
- [ ] Push-уведомления — обработка в Foreground (показ снэкбара) и Background (системная нотификация). При тапе по системному пушу — deeplink в нужный экран **и** инкремент unread-count обновляется.

**Checkpoint:** На реальном устройстве получить push при изменении статуса (создаю жалобу → меняю статус через web/Postman → приходит уведомление в системный шторку **и** появляется в `NotificationsScreen` с unread-меткой → тап открывает деталь жалобы и снимает unread).

---

### День 13 (20.05) — Mobile полировка + APK

- [ ] Empty states на всех списках
- [ ] Loading states (skeleton или spinner)
- [ ] Error states (нет сети, сервер недоступен) — простой алерт «Нет соединения, попробуйте позже». Полноценный offline-mode — Phase 2.
- [ ] Иконки 18 категорий — единообразно (взять из мокапа эмодзи)
- [ ] App icon (можно взять иконку из презентации) + splash
- [ ] ProGuard / R8 конфиг
- [ ] **Release-keystore** — генерируем `keystore.jks`, сохраняем пароль в зашифрованном виде (Yandex Lockbox или 1Password), бэкапим на флешку. Подписываем release-сборку.
- [ ] Release-сборка APK через `./gradlew composeApp:assembleRelease` — подписанный release-keystore.
- [ ] **Подача в RuStore:**
  - [ ] Регистрация разработчика на partner.rustore.ru (паспорт + ИНН), бесплатно.
  - [ ] Загрузить APK + 4 скриншота + иконку 512×512 + описание (200 слов: «Жалобы по экологии и инфраструктуре Сочи; голосование жителей повышает приоритет; уведомления о статусе»).
  - [ ] Категория «Государство и общество». Submit на модерацию (ожидание ~3 рабочих дня).
- [ ] Установка APK на 2–3 устройства друзей/родителей — feedback
- [ ] QR-код на release-APK ссылку для лендинга — генерируем через любой qr-генератор, сохраняем в `docs/marketing/`.

**Checkpoint:** APK установлен через release-keystore (без предупреждений о debug), прошёл happy-path от регистрации до создания жалобы и получения push. Заявка в RuStore подана, статус «На модерации».

---

### День 14 (21.05) — Mobile буфер + интеграция

- [ ] Запас на исправление багов из дня 13 (опыт показывает, что на полировке всегда что-то всплывает)
- [ ] Интеграция фронт↔бэк: проверить что mobile реально работает с production-конфигурацией backend
- [ ] Если успели — записать короткое скринкаст-видео мобильного приложения

⚠ **В backlog (если не успеваем):** UI-tests, Compose Previews для всех экранов, dark mode.

**Checkpoint конца недели 2:** Mobile работает end-to-end на реальном Android. Backend локальный или dev-cloud. Готовы к web admin.

---

## Неделя 3 — Web admin + деплой + защита

### День 15 (22.05) — Web admin scaffold + auth

- [ ] `cleancity-kmp/web-admin/` — `npm create vite@latest -- --template react-ts`
- [ ] tailwindcss + shadcn/ui (быстрая разработка с готовыми компонентами)
- [ ] Структура: `src/api/` (axios + типы), `src/pages/`, `src/components/`, `src/hooks/`
- [ ] API клиент с auto-refresh JWT (axios interceptors)
- [ ] `LoginPage` — email + password + 2FA-step
- [ ] `LoginPage` обязательная смена пароля при `must_change_password=true`
- [ ] Layout (sidebar + topbar) — стиль из `admin-dashboard-v2.html`
- [ ] Protected route wrapper

**Checkpoint:** Логин админа работает, после логина видно пустой dashboard.

---

### День 16 (23.05) — Web: жалобы + смена статусов

- [ ] `ComplaintsPage` (главный экран — таблица + детали справа)
- [ ] Фильтры: статус (включая REJECTED/DUPLICATE для админа), 18 категорий, район, SLA
- [ ] Сортировка по приоритету / дате / голосам
- [ ] Detail-panel справа (как в мокапе): фото, мета, голоса, карта, описание, история статусов
- [ ] Действия: «Принять в работу» / «Решить» / «Отклонить» / «Дубликат»
  - [ ] Обязательное поле комментария
  - [ ] Для DUPLICATE — выбор оригинала из списка близких
- [ ] Счётчики в фильтрах обновляются после действий
- [ ] Yandex Maps JS API на детальной карте

**Checkpoint:** Сменить статус жалобы → mobile-юзер получил push с комментарием.

---

### День 17 (24.05) — Web: объявления + аналитика + настройки

- [ ] `AnnouncementsPage` — список + форма создания (title, body, icon, category, districts, expires_at)
- [ ] При публикации → push на mobile
- [ ] `OverviewPage` (главный экран после логина):
  - [ ] Карточки KPI (total, NEW, IN_PROGRESS, RESOLVED, today, week, SLA breach)
  - [ ] **SLA-алерт-баннер** наверху если `sla_breach_count > 0` — текст из `/analytics/overview` (см. мокап `admin-dashboard-v2.html`).
  - [ ] График по дням (recharts)
  - [ ] Топ районов
  - [ ] SLA по категориям
  - [ ] Топ-5 по голосам жителей (см. мокап)
  - [ ] Карта с pins всех активных жалоб
- [ ] `AnalyticsPage` — расширенная аналитика, экспорт в Excel через CSV download.
- [ ] **PDF «Сводный отчёт за месяц»** (один реальный из 4 в мокапе):
  - [ ] Бэкенд: `GET /analytics/export/monthly-report.pdf` — генерация через OpenPDF (`com.github.librepdf:openpdf:1.3.30`, Apache 2.0). Шаблон: KPI + графики (можно как картинки из data-URL) + топ районов + SLA-таблица.
  - [ ] Фронт: 1-я карточка в Settings → Export — кликабельная, скачивает PDF.
  - [ ] Остальные 3 карточки (Реестр / SLA / Голосование) — рендерятся с состоянием `disabled` + tooltip «Скоро» (см. SPEC § 12).
- [ ] `SettingsPage` — профиль, смена пароля, 2FA, приглашение админов, audit-лог (упрощённо — последние 50 событий).
- [ ] **Управление командой в Settings:**
  - [ ] Список админов через `GET /auth/admin/users`.
  - [ ] Модалка редактирования сотрудника: ФИО / роль / районы / кнопки **«Заморозить»** (`PATCH {is_active:false}`) и **«Удалить»** (`DELETE`). Защита: нельзя удалить/заморозить последнего активного Admin.
  - [ ] Pending invitations + кнопка отзыва.

**Checkpoint:** Все экраны дашборда наполнены реальными данными из seed. PDF «Сводный за месяц» скачивается и открывается. Заморозка сотрудника отзывает все его refresh-токены (проверка: попытка использовать API → 401).

---

### День 18 (25.05) — Деплой на Yandex Cloud

- [ ] Создать ВМ в Yandex Cloud (Ubuntu 24.04, 2 vCPU, 4GB RAM, 30GB SSD, public IP)
- [ ] Настроить SSH по ключу, установить Docker + Docker Compose
- [ ] Создать Object Storage bucket `cleancity-photos-prod` + сервисный ключ
- [ ] DNS: `cleancity.ru` (или `.рф` если успеем) → A-запись на public IP
- [ ] DNS поддоменов: `api.cleancity.ru` → backend, `admin.cleancity.ru` → web
- [ ] Cloudflare как proxy (бесплатный tier — DDoS + SSL):
  - [ ] Подключить домен → переключить NS на Cloudflare
  - [ ] Включить proxy (оранжевая тучка)
  - [ ] SSL/TLS Full (Strict)
- [ ] Caddy на ВМ как reverse-proxy (auto-HTTPS через Let's Encrypt, проще nginx):
  - [ ] `Caddyfile`: `api.cleancity.ru` → backend:8080, `admin.cleancity.ru` → web:80
- [ ] `docker-compose.prod.yml`: db + backend + web + caddy
- [ ] Прод-секреты в `.env.prod` на ВМ (никогда в git): `JWT_SECRET`, `DB_PASSWORD`, `S3_KEY`, `SMTP_PASSWORD`, `FCM_CREDENTIALS_PATH`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALERT_CHAT_ID`, `BACKUP_GPG_PASSPHRASE`
- [ ] Применить миграции, создать первого админа из env
- [ ] Залить **release-подписанный** mobile APK ссылкой `https://cleancity.ru/cleancity.apk` (статика через Caddy). На лендинге `cleancity.ru` — кнопка-RuStore + резервная ссылка с QR-кодом.
- [ ] **Создать bucket `cleancity-backups`** в Yandex Object Storage + сервис-ключ только на запись. Установить cron `0 3 * * *` для `/opt/cleancity/backup.sh`. Запустить руками первый раз — проверить что файл появился в bucket.
- [ ] Установить cron `*/5 * * * *` для healthcheck. Проверить: остановить контейнер `docker compose stop backend` — Telegram-чат получает алерт «API DOWN» в течение 5 мин.
- [ ] Тест Telegram-алертов на ERROR-логи: бросить тестовое исключение в backend → проверить что прилетело.
- [ ] Тест бэкапа: `gpg --decrypt <backup>.gpg | pg_restore -d cleancity_test` в отдельной staging-БД — проверить что данные восстанавливаются.

**Checkpoint:** Открыть `https://admin.cleancity.ru` — логин работает, переключить mobile на prod-API — всё работает на реальных данных. Cron бэкапа запускается, healthcheck алерт работает, Telegram получает ERROR-логи.

---

### День 19 (26.05) — Seed данных + e2e тесты

- [ ] Подготовить реалистичный seed для демо: 80 жалоб по Сочи (равномерно по 4 районам и 18 категориям, с разными статусами и датами), 5 админов, 30 резидентов с разным числом голосов
- [ ] Скрипт `seed-prod.sql` или admin-команда `POST /admin/seed-demo` (с защитой по ENV-флагу)
- [ ] Загрузить 30–40 фото жалоб (можно фото с телефона + Lorem Picsum как fallback)
- [ ] E2E прогон сценария защиты:
  1. Гость открывает mobile → видит карту с пинами
  2. Регистрируется → подтверждает email → логинится
  3. Создаёт жалобу с фото
  4. Голосует за чужую жалобу
  5. Админ в web-кабинете видит новую жалобу → меняет статус → пишет комментарий
  6. Mobile-юзер получает push
  7. Админ публикует объявление → все получают push
  8. Админ показывает аналитику
- [ ] Записать happy-path как 90-секундное видео (на случай если что-то упадёт во время защиты)

**Checkpoint:** Демо-сценарий проходит без ошибок, видео записано.

---

### День 20 (27.05) — Защита: репетиция + страховки

- [ ] Финализировать презентацию `Презентация_ЧистыйГород.html/pdf` — добавить скриншоты production
- [ ] Упомянуть в презентации:
  - 18 категорий (показать на одном слайде с иконками)
  - Голосование жителей как уникальная фича
  - Безопасность (раздел 8 SPEC) — это сильный аргумент для гос-аудитории
  - Бюджет пилота: ~5000 ₽/мес после free trial
- [ ] **Страховки:**
  - Локальная копия (laptop) на случай если cloud упадёт во время защиты
  - Cloudflare Tunnel настроен и проверен как backup
  - Видео-демо как запасной план
  - APK на нескольких устройствах
  - Пара «дежурных» аккаунтов для входа без регистрации
- [ ] Прогон ответов на возможные вопросы комиссии (см. список ниже)
- [ ] Распечатать SPEC.md и PLAN.md для приложения к диплому

**Checkpoint:** 2 полных прогона защиты с таймером.

---

### День 21 (28.05) — Защита 🎓

- Утром: финальная проверка всех систем (api healthcheck, mobile login, web login)
- Презентация + демо
- Резерв: ответы на вопросы

---

## Чек-лист для защиты

**Технические артефакты:**
- [ ] APK мобильного приложения на устройстве (с резервной копией на флешке)
- [ ] Открытый production URL `https://admin.cleancity.ru`
- [ ] SPEC.md распечатан + в электронном виде
- [ ] PLAN.md распечатан
- [ ] Презентация в PDF
- [ ] Видео-демо 90 сек как страховка

**Возможные вопросы комиссии и ответы:**

| Вопрос | Ответ |
|--------|-------|
| Почему именно эти 18 категорий? | Опросили жителей Сочи через социальные сети + проанализировали обращения в МФЦ за последний год. Покрывают 95% реальных тем. |
| Чем уникален проект, ведь подобные системы есть? | Голосование жителей с автоматической приоритизацией — превращает разрозненные жалобы в общественный сигнал. Дашборд админа сортирует по голосам, не по дате — фокус на том, что реально болит у людей. |
| Как защищена админка от хакеров? | Раздел 8 SPEC: 2FA, audit-лог, отдельный поддомен, IP allowlist (опц.), bcrypt cost=12, lockout, CSP, refresh-токены с возможностью отзыва, CAPTCHA, regular pen-tests. |
| Готова ли система к реальному запуску? | Да. Yandex Cloud, российская юрисдикция, 152-ФЗ совместимость. Бюджет MVP ~5000 ₽/мес. Развёртывание — 1 день. |
| Что если злоумышленники накрутят голоса? | Голос требует верифицированный email (1 пользователь = 1 голос). Накрутка через тысячи почт — экономически невыгодна и легко детектируется по аномалии паттернов. |
| Почему KMP, а не нативный Android? | Расширяемость на iOS в Phase 2 без переписывания бизнес-логики. KMP уменьшает время разработки на 30–40% при двух платформах. |
| Где код? | GitHub: `<твой репозиторий>` + бекап-зеркало. |

---

## Риски и mitigation

| Риск | Mitigation |
|------|------------|
| Yandex Cloud не активирован вовремя (иногда верификация занимает 1–2 дня) | Подать заявку на активацию **в день 1**, не ждать до недели 3 |
| Yandex Maps API key задержится | Параллельно использовать OpenStreetMap-fallback в mobile/web на случай |
| Push не работает в эмуляторе | Тестировать на реальном устройстве с дня 12, иметь scheme `LoggingNotificationService` для дев |
| Backend перегружен — не успеваем | В worst-case backend остаётся локально (через ngrok/Cloudflare Tunnel) для защиты |
| FCM credentials JSON блокировка | Использовать через прокси / VPN, на крайний случай — собственный WebSocket-канал для уведомлений (день дополнительной работы) |
| Время уплыло на 2–3 дня | На неделе 3 пожертвовать Excel-экспортом, вынести audit-лог UI в backlog, упростить аналитику до 3 графиков |
| **Домен `.ru` модерируется reg.ru дольше суток** (паспортные данные, проверка ИНН) | Подаём день 1; резервный план — `.online` или `.app` международный (~250 ₽/год), или сразу пишем «адрес сервиса в продакшене будет cleancity.ru» в защите при работающем `.online`. |
| **RuStore модерация затянется > 7 дней** | Параллельно держим release-APK на cleancity.ru (статус «На модерации в RuStore» в презентации). На демо — QR-код к release-APK, жители ставят без магазина. |
| **Yandex Mail SPF/DKIM не успели настроить** | LoggingEmailService остаётся как fallback; verify-ссылки печатаются в админ-логах + ручная активация через `/auth/admin/users/{id}` `email_verified=true` для тестовых аккаунтов на защите. |
| **БД сломалась во время пилота** | Ежедневный бэкап + ежемесячный restore drill. При инциденте — restore с последнего бэкапа (потеря ≤24ч данных). |
| **Тексты политики ПДн юристы Сочи отбракуют** | Тексты — шаблонные для муниципальных сервисов (`docs/legal/`). При желании администрации — заменить на их утверждённые версии без изменения кода (только обновить версию). |

---

## Что НЕ делаем (out of scope)

- iOS-сборка
- Subbotniks (события)
- Чаты
- Гамификация / XP
- Госуслуги SSO
- ML-модерация фото / автокатегоризация
- Мобильное приложение для админов
- PWA для жителей
- Multi-city
- Платный тариф
- Интеграция с 1С / СЭД администрации (после пилота, по запросу)
- Раздельные роли OPERATOR / INSPECTOR (в MVP все админы равны)
- **Offline-mode mobile** (создание жалоб без сети, очередь отправки) — Phase 2
- **3 PDF-отчёта** из 4 в Settings (Реестр / SLA / Голосование) — карточки disabled с пометкой «Скоро»; делаем только «Сводный за месяц»

---

## После защиты (фаза 2)

Если администрация Сочи подпишет соглашение о пилоте:

1. Выделенный домен `cleancity.sochi.ru` (или поддомен на муниципальном)
2. Обучение 5–10 операторов администрации (1 рабочий день)
3. Подключить SLA-нотификации в email старшим должностным лицам
4. PR-кампания — анонс в группах ВК Сочи, на pesennoe радио
5. Аналитика обратной связи: % решённых, удовлетворённость жителей через follow-up опрос
6. Расширение: OPERATOR/INSPECTOR с разграничением, интеграция с СЭД администрации, экспорт в формате 1С

**Стоимость продакшена после free trial:** ~2000 ₽/мес (один админ-сервер). При расширении на другие города — отдельный инстанс на каждый или общий с tenant_id (решим по факту).

# CleanCity / «Чистый Город» — Спецификация MVP

**Проект:** Дипломная работа Агабабян Жасмин Маркосовны
**Цель:** Пилотное внедрение в администрации г. Сочи
**Дата:** 2026-05-07
**Срок MVP:** ~3 недели (защита диплома + пилот)

---

## 1. Описание системы

«Чистый Город» — система приёма и обработки экологических и инфраструктурных жалоб горожан Сочи. Состоит из трёх компонентов:

| Компонент | Платформа | Аудитория |
|-----------|-----------|-----------|
| **Mobile** | Android (Compose Multiplatform) | Жители Сочи |
| **Web Admin** | React + TypeScript + Vite | Сотрудники администрации |
| **Backend** | Ktor (Kotlin) + PostgreSQL | — |

### Уникальная фича
**Голосование жителей** с автоматической приоритизацией. Чем больше людей подтверждают проблему («Я тоже это вижу»), тем выше приоритет в дашборде администрации. Превращает жалобу в «общественный сигнал».

### Не входит в MVP
- iOS-клиент
- Чаты, переписка между жителями
- Субботники, события, добровольческие акции
- Геймификация, XP, бейджи
- Inspector/Operator как отдельные роли с ограничениями (поля присутствуют, но в MVP равны Admin)
- Интеграция с системой «Госуслуги»

---

## 2. Архитектура

```
┌────────────────────┐     ┌────────────────────┐
│  Android-клиент    │     │  Веб-кабинет       │
│  (житель)          │     │  (администрация)   │
│  Compose MP        │     │  React + Vite      │
└──────┬─────────────┘     └──────┬─────────────┘
       │ HTTPS/JSON              │ HTTPS/JSON
       │ JWT (житель)            │ JWT (админ)
       └──────────┬───────────────┘
                  ▼
          ┌──────────────────┐
          │   Ktor backend   │
          │   (Kotlin/JVM)   │
          └────┬─────┬────┬──┘
               │     │    │
       ┌───────┘     │    └─────────────┐
       ▼             ▼                   ▼
  ┌─────────┐  ┌──────────┐      ┌───────────────┐
  │Postgres │  │ Yandex   │      │ FCM (push)    │
  │+PostGIS │  │ Object   │      │ + SMTP (email │
  │         │  │ Storage  │      │  для verify/  │
  │         │  │          │      │  reset pass)  │
  └─────────┘  └──────────┘      └───────────────┘
```

**Хостинг:** Yandex Cloud (managed Postgres + Object Storage + Compute) — российская юрисдикция, тесная интеграция с Yandex Maps API.

**Карты:** Yandex Maps SDK для Android + Yandex Maps JS API для веб-кабинета.

---

## 3. Модель данных

PostgreSQL 16 + расширение PostGIS 3.4 (для геопоиска по bounding-box).

### 3.1 Категории жалоб (enum)

18 категорий, утверждены 2026-05-07. Хранятся как `VARCHAR` в БД, в коде — `enum class ProblemCategory`.

```kotlin
@Serializable
enum class ProblemCategory {
    GARBAGE,              // 🗑 Мусор и санитарное состояние
    ROADS,                // 🛣 Дороги и ямы
    SIDEWALKS,            // 🚶 Тротуары и пешеходные зоны
    LIGHTING,             // 💡 Уличное освещение
    GREENERY,             // 🌳 Озеленение и деревья
    LANDSCAPING,          // 🏗 Благоустройство территорий
    PLAYGROUNDS,          // 🛝 Детские и спортивные площадки
    PARKS,                // 🏞 Общественные пространства и парки
    BEACHES,              // 🏖 Пляжи и зоны отдыха
    SAFETY,               // 🚨 Безопасность и правонарушения
    VANDALISM,            // 🎨 Вандализм и повреждение имущества
    WATER_SUPPLY,         // 🚰 Водоснабжение
    SEWAGE,               // 🌊 Канализация и ливневые стоки
    ELECTRICITY,          // ⚡ Электроснабжение
    ECOLOGY,              // ☣ Экология и загрязнение окружающей среды
    ACCESSIBILITY,        // ♿ Доступная среда для маломобильных граждан
    TRADE,                // 🏪 Торговля и незаконные объекты
    OTHER                 // ❓ Прочее
}
```

Локализованные названия и иконки лежат в `shared/CategoryMeta.kt` (один источник правды для mobile + web через REST).

### 3.2 Статусы жалобы

```kotlin
@Serializable
enum class ComplaintStatus {
    NEW,           // В обработке (создана, ждёт админа)
    IN_PROGRESS,   // В работе (админ принял)
    RESOLVED,      // Решено (закрыта успешно)
    REJECTED,      // Отклонена (не относится к компетенции)
    DUPLICATE      // Дубликат (объединена с другой)
}
```

**Переходы:**
- `NEW → IN_PROGRESS` (админ принимает)
- `IN_PROGRESS → RESOLVED` (админ закрывает с комментарием)
- `NEW/IN_PROGRESS → REJECTED` (админ отклоняет с комментарием)
- `NEW/IN_PROGRESS → DUPLICATE` (админ указывает оригинал)
- Терминальные статусы (`RESOLVED`, `REJECTED`, `DUPLICATE`) обратимы только через «откат» — отдельное действие.

### 3.3 Роли и доступы

```kotlin
@Serializable
enum class UserRole {
    RESIDENT,    // Житель (только mobile)
    OPERATOR,    // Оператор (в MVP = Admin)
    INSPECTOR,   // Инспектор (в MVP = Admin)
    ADMIN        // Полный доступ
}
```

**Гости (без регистрации):**
- Видят карту, ленту, объявления, детали жалоб (read-only).
- НЕ могут голосовать и создавать жалобы.
- При попытке действия — модалка «Войдите или зарегистрируйтесь».

**Резидент (зарегистрированный):** карта/лента/объявления + создание жалобы + голосование + просмотр своих жалоб.

**Админ-роли (OPERATOR/INSPECTOR/ADMIN):** в MVP проверка прав на бэкенде сводится к `role IN (OPERATOR, INSPECTOR, ADMIN)` — полный доступ к админ-API. После пилота можно ограничивать `OPERATOR` (только смена статусов в назначенных районах) и `INSPECTOR` (только просмотр + комментарии).

**Видимость жалоб на карте/в ленте для жителей:**
Бэкенд при запросах с ролью `RESIDENT` или гостем (без JWT) фильтрует жалобы:
```sql
WHERE status IN ('NEW', 'IN_PROGRESS', 'RESOLVED')
```
Статусы `REJECTED` и `DUPLICATE` видны ТОЛЬКО админам (внутренняя архивная работа). Метка SLA — внутренняя метрика, тоже не видна жителям нигде.

### 3.4 Таблицы

```sql
-- Расширения
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- для UUID и хэшей

-- Пользователи
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,        -- bcrypt cost=12
    role VARCHAR(20) NOT NULL DEFAULT 'RESIDENT',
    full_name VARCHAR(200),
    phone VARCHAR(20),                          -- опционально, для связи
    district VARCHAR(100),                      -- для админов: закреплённые районы (CSV или JSON)
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    -- Поля безопасности
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,                   -- блокировка после N неудач
    totp_secret VARCHAR(64),                    -- secret для TOTP 2FA (только для админов)
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,  -- true для админов после invite
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ,
    last_login_ip VARCHAR(45),
    -- 152-ФЗ: фиксируем факт принятия политики обработки ПДн
    accepted_terms_at TIMESTAMPTZ,
    accepted_terms_version VARCHAR(10)
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Email-токены (verify, password reset, invite)
CREATE TABLE email_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL UNIQUE,         -- random 32 bytes hex
    purpose VARCHAR(20) NOT NULL,               -- VERIFY_EMAIL, RESET_PASSWORD, ADMIN_INVITE
    expires_at TIMESTAMPTZ NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_email_tokens_token ON email_tokens(token);
CREATE INDEX idx_email_tokens_user ON email_tokens(user_id, purpose);

-- Жалобы
CREATE TABLE complaints (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    category VARCHAR(30) NOT NULL,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    district VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    duplicate_of_id BIGINT REFERENCES complaints(id),
    assigned_admin_id BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_complaints_status ON complaints(status);
CREATE INDEX idx_complaints_category ON complaints(category);
CREATE INDEX idx_complaints_district ON complaints(district);
CREATE INDEX idx_complaints_location ON complaints USING GIST(location);
CREATE INDEX idx_complaints_created_at ON complaints(created_at DESC);

-- Фотографии (до 5 на жалобу)
CREATE TABLE complaint_photos (
    id BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    photo_url VARCHAR(500) NOT NULL,           -- ссылка в Yandex Object Storage
    storage_key VARCHAR(500) NOT NULL,         -- ключ объекта (для удаления)
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_photos_complaint ON complaint_photos(complaint_id, sort_order);

-- Голоса
CREATE TABLE votes (
    id BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    value SMALLINT NOT NULL CHECK (value IN (-1, 1)),  -- 1 = подтверждаю, -1 = не проблема
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (complaint_id, user_id)
);
CREATE INDEX idx_votes_complaint ON votes(complaint_id);

-- История статусов
CREATE TABLE status_changes (
    id BIGSERIAL PRIMARY KEY,
    complaint_id BIGINT NOT NULL REFERENCES complaints(id) ON DELETE CASCADE,
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    comment TEXT,                              -- обязателен для смены статуса
    changed_by_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_status_changes_complaint ON status_changes(complaint_id, created_at);

-- Объявления
CREATE TABLE announcements (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    icon_style VARCHAR(20) NOT NULL DEFAULT 'INFO',  -- INFO, SUCCESS, WARNING
    category VARCHAR(30),                            -- опционально, ProblemCategory
    districts TEXT,                                  -- CSV районов или 'ALL'
    author_id BIGINT NOT NULL REFERENCES users(id),
    published_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ
);
CREATE INDEX idx_announcements_published ON announcements(published_at DESC);

-- Push-токены устройств
CREATE TABLE push_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fcm_token VARCHAR(500) NOT NULL UNIQUE,
    device_info VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_push_user ON push_tokens(user_id);

-- Уведомления (история для in-app экрана «Уведомления»)
-- Запись создаётся одновременно с отправкой FCM-пуша. Хранит «копию» события,
-- независимо от того, доставлен ли push устройству.
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,                       -- COMPLAINT_STATUS, ANNOUNCEMENT, VOTES_THRESHOLD, COMPLAINT_REJECTED, COMPLAINT_DUPLICATE
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    icon_style VARCHAR(20),                          -- INFO, SUCCESS, WARNING, REJECTED
    complaint_id BIGINT REFERENCES complaints(id) ON DELETE CASCADE,
    announcement_id BIGINT REFERENCES announcements(id) ON DELETE CASCADE,
    read_at TIMESTAMPTZ,                             -- NULL = непрочитано
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read_at NULLS FIRST, created_at DESC);
CREATE INDEX idx_notifications_user_created ON notifications(user_id, created_at DESC);

-- Refresh-токены (для отзыва сессий)
CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,    -- SHA-256(token), сам токен не храним
    issued_ip VARCHAR(45),
    user_agent VARCHAR(500),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,                     -- NULL = активный
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_user ON refresh_tokens(user_id, revoked_at);
CREATE INDEX idx_refresh_hash ON refresh_tokens(token_hash);

-- Audit-лог всех действий админов
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES users(id),       -- может быть NULL при анонимных событиях (failed login)
    actor_email VARCHAR(255),                   -- денормализация: email на момент действия
    action VARCHAR(60) NOT NULL,                -- LOGIN_SUCCESS, LOGIN_FAILED, COMPLAINT_STATUS_CHANGE, ADMIN_INVITE, USER_LOCKED, etc.
    target_type VARCHAR(40),                    -- complaint, user, announcement
    target_id BIGINT,
    metadata JSONB,                             -- свободные поля события
    ip_address VARCHAR(45) NOT NULL,
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_actor ON audit_log(actor_id, created_at DESC);
CREATE INDEX idx_audit_action ON audit_log(action, created_at DESC);
CREATE INDEX idx_audit_target ON audit_log(target_type, target_id);

-- Rate-limit бакеты
CREATE TABLE rate_limit_buckets (
    bucket_key VARCHAR(200) PRIMARY KEY,        -- 'login:ip:1.2.3.4', 'register:ip:1.2.3.4'
    count INT NOT NULL DEFAULT 0,
    window_start TIMESTAMPTZ NOT NULL,
    last_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rate_window ON rate_limit_buckets(window_start);
```

**Денормализация для производительности:** топ-карточки (`ComplaintCardResponse`) включают `votes_count` (вычисляется через `SELECT COUNT(*) WHERE value=1`) — кэшируется в Redis при горячих запросах. В MVP — без Redis, считается через SQL.

### 3.5 Расчёт приоритета

```
priority_score = (yes_votes - no_votes) * 1.0
                + age_hours_in_new_status * 0.5
                + (category in {SAFETY, ECOLOGY} ? 100 : 0)
```

Сортировка по умолчанию в дашборде: `priority_score DESC`. Поле вычисляется на лету в SQL-запросе списка жалоб.

---

## 4. REST API

База: `https://api.cleancity.ru/v1` (или поддомен в Yandex Cloud).
Все ответы — JSON, кодировка UTF-8.
Аутентификация — `Authorization: Bearer <jwt>`.

### 4.1 Auth (общая для резидента и админа — единая таблица users)

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/auth/register` | Регистрация резидента. Body: `{email, password, full_name?}`. Создаёт пользователя с `role=RESIDENT`, `email_verified=false`. Шлёт письмо с verify-ссылкой. 200: `{user, message}`. Rate limit: 5 в час с IP. |
| `POST` | `/auth/verify-email` | Подтвердить email. Body: `{token}`. 200: `{access_token, refresh_token, user}`. |
| `POST` | `/auth/resend-verification` | Повторно прислать письмо. Body: `{email}`. Rate limit: 1 в 5 мин. |
| `POST` | `/auth/login` | Вход. Body: `{email, password}`. 200: `{access_token, refresh_token, user}`. Если `email_verified=false` — 403 с просьбой подтвердить. Rate limit: 10 в минуту с IP. |
| `POST` | `/auth/forgot-password` | Запрос на сброс. Body: `{email}`. Шлёт письмо с reset-ссылкой. 200 всегда (защита от user enumeration). |
| `POST` | `/auth/reset-password` | Установить новый пароль. Body: `{token, new_password}`. 200: `{message}`. |
| `POST` | `/auth/refresh` | Обновить токен. Body: `{refresh_token}`. 200: `{access_token, refresh_token}`. |
| `POST` | `/auth/logout` | Инвалидировать refresh-токен. |
| `GET`  | `/auth/sessions` | Список активных сессий пользователя. |
| `DELETE` | `/auth/sessions/{id}` | Отозвать конкретную сессию. |
| `DELETE` | `/auth/sessions` | Отозвать все сессии (при смене пароля/подозрении). |

**2FA для админов:**

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/auth/2fa/setup` | Сгенерировать `totp_secret` и QR-код для приложения. Не активирует — нужна верификация. |
| `POST` | `/auth/2fa/verify` | Подтвердить код после setup. Body: `{totp_code}`. Включает 2FA. |
| `POST` | `/auth/2fa/disable` | Отключить 2FA. Body: `{password, totp_code}`. |
| `POST` | `/auth/login-2fa` | Второй шаг входа админа. Body: `{login_session_id, totp_code}`. 200: `{access_token, refresh_token}`. Сначала `/auth/login` возвращает `{requires_2fa: true, login_session_id}`, потом этот эндпоинт.

### 4.2 Управление админами

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/auth/admin/invite` | Только Admin. Создать приглашение. Body: `{email, role, full_name?, district?}`. Шлёт письмо с invite-ссылкой. |
| `POST` | `/auth/admin/accept-invite` | Активировать аккаунт по токену. Body: `{invite_token, password}`. Возвращает JWT. |
| `GET` | `/auth/admin/users` | Список всех админов: id, email, full_name, role, district, is_active, last_login_at. |
| `PATCH` | `/auth/admin/users/{id}` | Изменить роль/район/активность. Body: `{role?, district?, is_active?, full_name?}`. **Заморозить сотрудника** — `{is_active: false}`: все refresh-токены отзываются, при попытке логина → 403 «Аккаунт заморожен». **Разморозить** — `{is_active: true}`. |
| `DELETE` | `/auth/admin/users/{id}` | Soft-delete сотрудника: `is_active=false`, `email='deleted_<id>@cleancity.local'`, `password_hash=NULL`, refresh-токены отзываются. Действие не запрещает удалить самого себя — последний Admin защищён валидацией (нельзя удалить, если в системе останется 0 активных Admin). |

### 4.3 Жалобы

| Метод | Путь | Кто | Описание |
|-------|------|-----|----------|
| `POST` | `/complaints` | Резидент | Создать жалобу. `multipart/form-data`: `data` (JSON `CreateComplaintRequest`) + `photo[]` (1–5 файлов). Требует JWT. **Title клиент НЕ передаёт** — бэкенд формирует его как `"<категория-локализованное> · <первый сегмент адреса>"` (например, «Мусор · ул. Транспортная»). |
| `GET` | `/complaints` | Гость+ | Список с фильтрами: `?status=&category=&district=&sort=priority|date|votes&page=0&size=20`. Для гостей и `RESIDENT` — только `NEW/IN_PROGRESS/RESOLVED`. |
| `GET` | `/complaints/{id}` | Гость+ | Детали + фото + голоса + история статусов. Для жителей: если статус `REJECTED/DUPLICATE` — возвращается, только если пользователь автор или голосовал «+1» (для контекста уведомления). Иначе 404. |
| `GET` | `/complaints/map?swLat=&swLon=&neLat=&neLon=&category=` | Гость+ | Маркеры в bounding-box. Возвращает облегчённый `MapMarker`: `id`, `category`, `status`, `lat`, `lon`. Для жителей: только активные статусы (`NEW/IN_PROGRESS/RESOLVED`). |
| `GET` | `/complaints/duplicates?lat=&lon=&category=&radius=100` | Резидент | Найти жалобы той же категории в радиусе (м) для предупреждения «Возможно, проблема уже есть». |
| `PATCH` | `/complaints/{id}/status` | Админ | Сменить статус. Body: `{to_status, comment, duplicate_of_id?}`. **Комментарий обязателен.** Триггерит push автору + всем `+1`-голосовавшим. |
| `POST` | `/complaints/{id}/votes` | Резидент | Проголосовать. Body: `{value: 1}`. Идемпотентно (повторный POST = no-op). Если статус жалобы `REJECTED` или `DUPLICATE` — 409 Conflict с `{message: "Голосование закрыто: жалоба <статус>"}`. |
| `DELETE` | `/complaints/{id}/votes` | Резидент | Отозвать свой голос. Для статусов `REJECTED` / `DUPLICATE` — 409 Conflict (отзыв тоже запрещён, чтобы зафиксировать историю поддержки на момент закрытия). |
| `GET` | `/complaints/mine` | Резидент | Мои жалобы. |
| `GET` | `/complaints/voted` | Резидент | Жалобы, за которые я голосовал «+1» (включая закрытые в `REJECTED/DUPLICATE` — чтобы видеть пояснения). |

### 4.4 Объявления

| Метод | Путь | Кто | Описание |
|-------|------|-----|----------|
| `GET` | `/announcements?district=&limit=10` | Все | Активные объявления. |
| `POST` | `/announcements` | Админ | Опубликовать. Триггерит push на всех (опционально по району). |
| `PATCH` | `/announcements/{id}` | Админ | Редактировать. |
| `DELETE` | `/announcements/{id}` | Админ | Снять с публикации. |

### 4.5 Аналитика (только админ)

Все 4 эндпоинта с фильтром по периоду принимают `?period=week|month|all` (дефолт `all`).
Поле `slaBreachCount` в `/overview` считается **только по активным жалобам** (NEW/IN_PROGRESS) с истёкшим нормативом — это «горит сейчас», рабочая метрика для дашборда. Просрочки на момент закрытия попадают в `/sla` как `breachPct`.

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/analytics/overview` | Сводка: total / new / in_progress / resolved / rejected / duplicate / today / week / `slaBreachCount`. |
| `GET` | `/analytics/by-category?period=...` | Группировка по 18 категориям: count, sharePct, avgResolutionHours. |
| `GET` | `/analytics/by-district?period=...` | По 4 районам Сочи: count, newCount, resolvedCount. |
| `GET` | `/analytics/sla?period=...` | Per-category: slaHours (из норматива), avgResolutionHours, breachPct (по resolved), resolvedCount. |
| `GET` | `/analytics/votes-impact?period=...` | Buckets голосов `0 / 1-9 / 10-49 / 50+` с count и avgResolutionHours. |

**Phase 2 (не входит в MVP):**
- `/analytics/active-users` (DAU/WAU/MAU) — требует `last_login_at` history, отложено.
- `/analytics/export?format=xlsx` — для пилота достаточно PDF «Сводный за месяц» из Settings (Day 17).

### 4.6 Уведомления (in-app история)

При каждом push-триггере (смена статуса жалобы, объявление, 50+ голосов) бэкенд параллельно с FCM создаёт строку в таблице `notifications`. Это «история», которую видит экран **Уведомления** в mobile (см. строки 871–942 в `mobile-mockup-v3.html`). Бейдж с цифрой непрочитанных — на иконке нижней навигации.

| Метод | Путь | Кто | Описание |
|-------|------|-----|----------|
| `GET` | `/notifications?limit=50&offset=0` | Резидент | Список своих уведомлений, новые сверху. Поля: `id, kind, title, body, icon_style, complaint_id?, announcement_id?, read_at?, created_at`. |
| `GET` | `/notifications/unread-count` | Резидент | `{count: 7}` — для бейджа на иконке. |
| `PATCH` | `/notifications/{id}/read` | Резидент | Отметить одно уведомление прочитанным (`read_at = NOW()`). Идемпотентно. |
| `PATCH` | `/notifications/read-all` | Резидент | Отметить все непрочитанные прочитанными. |

Удаление уведомлений в MVP не предусмотрено — список фильтруется по `created_at > NOW() - 90 days` и `LIMIT 50`. Уведомления старше 90 дней чистит фоновая задача (cron `0 4 * * *`).

### 4.7 Прочее

| Метод | Путь | Описание |
|-------|------|----------|
| `GET` | `/categories` | Список 18 категорий с локализацией и иконкой (для динамического UI). |
| `GET` | `/districts` | Список районов Сочи (Центральный, Адлерский, Хостинский, Лазаревский). |
| `GET` | `/users/me` | Резидент+ | Текущий пользователь по JWT. 200: UserResponse. 401 если токен невалиден/истёк. |
| `POST` | `/users/me/push-token` | Регистрация FCM-токена устройства. Body: `{fcm_token, device_info}`. |
| `GET` | `/photos/{key}` | Прокси к Object Storage (опц., для приватных фото). В MVP — публичный bucket, прямые URL. |
| `GET` | `/legal/privacy` | Текущий текст политики обработки ПДн (markdown→html). Используется в WebView mobile. |
| `GET` | `/legal/terms` | Пользовательское соглашение. |

### 4.8 SLA-нормативы (захардкожены в MVP)

| Категория | Норматив |
|-----------|----------|
| GARBAGE, ECOLOGY, SAFETY | 24ч |
| LIGHTING, SEWAGE, WATER_SUPPLY, ELECTRICITY | 48ч |
| ROADS, SIDEWALKS, GREENERY, LANDSCAPING, PLAYGROUNDS, PARKS, BEACHES, ACCESSIBILITY | 72ч |
| VANDALISM, TRADE, OTHER | 120ч |

После пилота — выносим в таблицу `sla_norms` с CRUD из админки.

---

## 5. Основные потоки

### 5.1 Подача жалобы (резидент)

```
Mobile                        Backend                       Storage
  │                              │                             │
  │  фото + GPS + категория      │                             │
  ├─────────────────────────────►│                             │
  │                              │ загрузка фото в S3          │
  │                              ├────────────────────────────►│
  │                              │◄────────────────────────────┤ urls[]
  │                              │ INSERT complaints           │
  │                              │ INSERT complaint_photos[]   │
  │                              │ INSERT status_changes(NEW)  │
  │  ComplaintResponse           │                             │
  │◄─────────────────────────────┤                             │
  │                              │ FCM на админов района       │
  │                              ├──────► (push)               │
```

**До отправки** — клиент дёргает `/complaints/duplicates` и показывает «Похожие рядом» с кнопкой «+1 голос за существующую» (см. блок duplicate-warning в мокапе). Это снижает дубликаты на ~30%.

**Геокодирование адреса** — клиент использует Yandex Maps SDK (reverse geocoding) → передаёт уже готовый `address` + `district`. Бэкенд верифицирует, что координаты внутри Сочи (BBox-checks).

### 5.2 Смена статуса (админ)

1. Админ открывает деталь жалобы, пишет комментарий (**обязательно**), нажимает «Принять в работу» / «Решить» / «Отклонить» / «Дубликат».
2. `PATCH /complaints/{id}/status` → валидация перехода → `INSERT status_changes` → `UPDATE complaints.status`.
3. **Push-уведомления:**
   - При `IN_PROGRESS` / `RESOLVED` — push автору: `«Ваша жалоба «<title>» — <статус>. <комментарий>».`
   - При `REJECTED` / `DUPLICATE` — push автору + всем, кто проголосовал `+1` за жалобу. Текст: `«Жалоба «<title>» закрыта со статусом <статус>. Комментарий администрации: <comment>»`. Это обязательное условие — жителям, поддержавшим проблему, должно быть понятно почему она закрыта без решения.
4. Если статус `RESOLVED` — `complaints.resolved_at = NOW()`, рассчитывается фактическое время для SLA-аналитики.
5. Если статус `DUPLICATE` — обязателен `duplicate_of_id`, голоса дубликата автоматически переводятся на оригинал (UPSERT по `(complaint_id=original, user_id)` с conflict skip).
6. Жалобы в `REJECTED/DUPLICATE` исчезают с публичной карты и из общего списка (фильтр на бэкенде), но остаются в `/complaints/mine` (у автора) и `/complaints/voted` (у голосовавших) — чтобы они видели пояснения.

### 5.3 Голосование

- Только для зарегистрированных. Гостям UI показывает «Войдите чтобы поддержать» при попытке.
- Один пользователь — один голос на жалобу (UNIQUE constraint в БД).
- В MVP — только `+1` («Я тоже это вижу»). Поле `value` в БД оставлено как `SMALLINT` для совместимости, но фактически принимает только `1`.
- Можно отозвать голос (`DELETE /complaints/{id}/votes`) — только пока жалоба активна (`NEW`/`IN_PROGRESS`/`RESOLVED`).
- Голосование и отзыв голоса для статусов `REJECTED` и `DUPLICATE` запрещены (409 Conflict). Это фиксирует «общественный сигнал» на момент закрытия и защищает от ретроактивной правки истории.
- Сразу пересчитывается `priority_score` для сортировки.
- При достижении 50+ голосов жалоба помечается как «приоритет» в UI и попадает в top на главной дашборда.

### 5.4 Объявления

- Админ создаёт через web-кабинет: title, body, icon_style (INFO/SUCCESS/WARNING), категория (опционально), районы (CSV или ALL).
- Сразу публикуется → FCM на пользователей выбранных районов с deeplink в карточку.
- Отображаются в горизонтальной карусели на главной мобильного приложения (см. строки 432–460 mobile-mockup-v3.html).

### 5.5 Аналитика

- Дашборд (`screen-overview`) запрашивает `/analytics/overview` + `/analytics/by-category` + `/analytics/by-district` + `/analytics/sla` параллельно при загрузке.
- Реализация MVP: обычные SQL-запросы через Exposed, агрегация в Kotlin (см. `backend/analytics/AnalyticsRepository.kt` + `AnalyticsService.kt`). Materialized views и переход на ClickHouse — Phase 2 при росте объёма данных.
- PDF «Сводный за месяц» — отдельный путь `/analytics/export/monthly-report.pdf` (день 17, через OpenPDF).

---

## 6. Хранение фото

**Сервис:** Yandex Object Storage (S3-совместимый).
**Bucket:** `cleancity-photos-prod`, регион `ru-central1`.
**Доступ:** публичный read для `photos/*`, write — только через сервисный ключ бэкенда.
**Структура ключей:** `photos/{year}/{month}/{uuid}.jpg`.
**Имя файла:** UUID v4 — без угадываемости.
**Ограничения:**
- Форматы: JPEG, PNG (валидируется по magic bytes, не только по mime).
- Макс. размер: 10 МБ на фото.
- Ресайз на бэкенде при загрузке: `original` (как есть) + `thumb_640.webp`. Возвращаем оба URL.
- До 5 фото на жалобу.

**Локально (dev):** `LocalStorageService` пишет в `./uploads/`, отдаёт через `/photos/{file}`. Интерфейс `StorageService` уже есть в KMP-скелете — переиспользуем.

**Чистка:** при `DELETE complaint` — каскадно удаляем `complaint_photos` + S3-объекты (фоновая задача, не блокирующая HTTP).

---

## 7. Push-уведомления

**Сервис:** Firebase Cloud Messaging (FCM) — единственный реалистичный путь для Android в РФ (через Yandex прокси при необходимости).

**Триггеры:**
| Событие | Кому | Текст |
|---------|------|-------|
| Жалоба создана | Админам района | `Новая жалоба: <title>, <district>` (deeplink в карточку) |
| Статус → IN_PROGRESS / RESOLVED | Автору | `Ваша жалоба «<title>» — <статус>. <комментарий>` |
| Статус → REJECTED / DUPLICATE | **Автору + всем `+1` голосовавшим** | `Жалоба «<title>» закрыта со статусом <статус>. Комментарий администрации: <comment>` |
| 50+ голосов | Автору + админам | `Жалобу поддержали 50 жителей — приоритет` |
| Объявление | Жителям выбранных районов | `<icon> <title>` |

**Реализация:**
- При логине mobile регистрирует FCM-токен через `POST /users/me/push-token`.
- Бэкенд хранит `push_tokens.fcm_token`, рассылает через Firebase Admin SDK.
- При ошибке `UNREGISTERED` — удаляем токен.
- Для рассылки голосовавшим — собираем получателей одним SQL-запросом: `SELECT user_id FROM votes WHERE complaint_id=? AND value=1 UNION SELECT author_id FROM complaints WHERE id=?`.

**Email (SMTP):** для verify-email, password reset, admin invite. Используем Yandex Mail SMTP (`smtp.yandex.ru:465`, SSL) с почтой проекта (например, `noreply@cleancity.ru`). На dev — можно стартовать с любого личного SMTP-ящика (значение берётся из `.env`).

---

## 8. Безопасность

Безопасность — один из ключевых аспектов проекта. Раздел разбит на три уровня: транспорт/инфраструктура, код приложения, защита админ-панели (повышенный приоритет).

### 8.1 Транспорт и инфраструктура

- **HTTPS only.** TLS 1.3, отказ от старых шифров (только AEAD).
- **HSTS preload:** `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`.
- **DNSSEC** на домене.
- **HTTP→HTTPS redirect** на L7-балансировщике.
- **Cloudflare / Yandex DDoS Protection** перед бэкендом (DDoS, WAF, bot management).
- **Изолированная сеть:** Postgres и Object Storage в private subnet, доступ только из backend-сервиса. Внешний IP — только у балансировщика.
- **SSH-доступ к серверам:** только по SSH-ключам, отключён парольный вход. fail2ban на 22 порт.
- **Файрвол:** открыты только 80/443 наружу; 22 — по allowlist IP админов.
- **Регулярные patch-обновления** ОС и Postgres (managed Postgres делает это сам).
- **Бэкапы Postgres** ежедневно, шифрование AES-256, retention 30 дней.

### 8.2 Заголовки безопасности

Все ответы web-кабинета и API возвращают:
```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
                         img-src 'self' https://storage.yandexcloud.net data:;
                         connect-src 'self' https://api-maps.yandex.ru;
                         frame-ancestors 'none';
                         object-src 'none';
                         base-uri 'self';
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), camera=(), microphone=(), payment=()
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Resource-Policy: same-site
```

### 8.3 Аутентификация и авторизация

- **Пароли:** bcrypt cost=12. **Политика для админов:** ≥12 символов, обязательно: 1 заглавная, 1 цифра, 1 спецсимвол. Для жителей: ≥8 символов.
- **2FA для админов** (TOTP, Google Authenticator / Yandex Key) — **обязательно**. Включается при первом входе. `users.totp_secret` шифруется в БД (AES с ключом из env), `totp_enabled` показывает статус.
- **Lockout:** 5 неуспешных входов подряд → блокировка аккаунта на 15 мин (`locked_until`). После 10 неуспехов с одного IP за 10 мин — CAPTCHA.
- **Session inactivity timeout** для админов: access-токен живёт 15 мин, refresh — 8 часов (короче резидентов). При закрытии вкладки — refresh не сохраняется.
- **JWT:** HMAC-SHA256, секрет 32+ байт из env, ротация ключа раз в 6 мес. В payload: `user_id`, `role`, `iat`, `exp`, `jti` (для отзыва).
- **Refresh-токены** хранятся в БД как SHA-256 хэш (`refresh_tokens.token_hash`) → можно отозвать конкретную сессию, не зная сам токен. При смене пароля — все refresh-токены отзываются.
- **Force password change** для админов после invite (`must_change_password=true`).
- **Token revocation list** через `audit_log` + `refresh_tokens.revoked_at`.

### 8.4 Защита от веб-атак

- **SQL injection:** только параметризованные запросы через Exposed ORM. Никаких `String.format` с user-input в SQL.
- **XSS:** React эскапит JSX по умолчанию; запрещаем `dangerouslyInnerHTML`. Все `<a href=>` с user-input — фильтр на `javascript:` и `data:`.
- **CSRF:** не применимо (Bearer JWT в Authorization header, не в cookie). Но если решим cookie-сессии — обязателен SameSite=Strict + CSRF-токен.
- **SSRF:** при ресайзе/проксировании фото — никаких внешних URL, только наш Object Storage. Whitelist доменов для outgoing requests.
- **Open redirect:** валидация всех `?redirect=` параметров (только относительные пути или whitelist).
- **Path traversal:** имена файлов через UUID, не доверяем имени из multipart.
- **Mass assignment:** API явно мапит каждое поле, не использует `JSON.parse → save()`.
- **Прямые object reference (IDOR):** на каждый `GET /complaints/{id}` проверяем доступ роли + (если RESIDENT и статус REJECTED/DUPLICATE) — что user является автором или голосовавшим.

### 8.5 Защита админ-панели (повышенный приоритет)

Это самая чувствительная часть системы.

- **Отдельный поддомен** `admin.cleancity.ru` — изолирует от публичной части.
- **IP allowlist** (опционально, конфигурируется): админка принимает запросы только с IP-адресов администрации Сочи. Если нужен удалённый доступ — VPN.
- **2FA обязательно** (см. 8.3). Без TOTP — вход невозможен, даже при правильном пароле.
- **Audit-лог** всех действий админов в таблице `audit_log`: вход, смена статуса жалобы, публикация объявления, приглашение нового админа, изменение роли, экспорт данных. Лог не редактируется и не удаляется.
- **Принцип минимальных привилегий:** в БД сервис-аккаунт `cleancity_app` имеет только `INSERT/UPDATE/SELECT/DELETE`, никаких `DROP`/`CREATE`. Миграции — отдельным аккаунтом `cleancity_migrate`, ключ только в CI.
- **Сессионная безопасность:**
  - Access-токен — 15 минут.
  - При смене IP / User-Agent — invalidate refresh.
  - При выходе — refresh-токен немедленно `revoked_at = NOW()`.
- **Уведомления о входе:** при логине админа — письмо на email с указанием IP, времени, браузера. Если это «не я» — кнопка «отозвать все сессии».
- **Ограничение одновременных сессий:** не более 3 активных refresh-токенов на админа.
- **Brute-force защита:** при превышении лимита логинов — IP в `rate_limit_buckets` блокируется на час, аккаунт — на 15 мин.
- **CAPTCHA** (Yandex SmartCaptcha) на форме входа админа после 3 неудачных попыток.
- **Уязвимости в зависимостях:** `gradle dependencyCheck` в CI, npm audit для веб-кабинета. Critical → блок merge.

### 8.6 Защита данных и приватности

- **EXIF-стрипинг фото:** при upload удаляем GPS-координаты из EXIF. Координаты передаются отдельным полем — фото не должно содержать их в метаданных.
- **Magic bytes** валидация фото (не доверяем `Content-Type` от клиента).
- **Антивирус** (опционально, ClamAV в docker) для загруженных файлов. В MVP — необязательно.
- **PII в логах:** не логируем пароли, токены, OTP-коды, EXIF-метаданные. Email — в audit_log да, но в стандартных логах — маскируется (`a***@gmail.com`).
- **GDPR/152-ФЗ совместимость:** жителям доступно удаление аккаунта (soft-delete + анонимизация: `email='deleted_<id>@x'`, `password_hash=NULL`, `is_active=false`). Связанные жалобы остаются (для прозрачности), но автор скрыт.
- **Согласие на обработку ПДн (152-ФЗ):**
  - При регистрации — обязательный чекбокс «Я ознакомлен и согласен с [Политикой обработки ПДн] и [Пользовательским соглашением]». Без галочки — кнопка регистрации не активна.
  - Ссылки в чекбоксе открывают тексты в WebView (mobile) или вкладке (web): `cleancity.ru/privacy`, `cleancity.ru/terms`.
  - В таблице `users` фиксируется `accepted_terms_at = NOW()` и `accepted_terms_version` (версия документа на момент согласия). При изменении версии документа — пользователю показывается баннер с просьбой переподтвердить.
  - Тексты документов хранятся в репозитории (`docs/legal/privacy-policy.md`, `docs/legal/terms-of-service.md`) и раздаются Caddy как статика на cleancity.ru, плюс через API `/legal/privacy` и `/legal/terms` для embed в mobile.

### 8.7 Защита от спама и злоупотреблений

| Действие | Лимит |
|----------|-------|
| Регистрация | 5 в час с IP |
| Логин (обычный) | 10 в минуту с IP |
| Логин админа | 5 в минуту с IP, CAPTCHA после 3 неуспехов |
| Verify/reset email | 1 в 5 мин с email |
| Создание жалобы | 5 в час с user_id |
| Голос | только верифицированные пользователи (`email_verified=true`) |
| Изменение профиля | 10 в час с user_id |

### 8.8 Процессы

- **Penetration testing** перед релизом в продакшен (хотя бы базовый OWASP-чеклист).
- **CI security scans:** Snyk / GitGuardian (поиск секретов в коммитах), `gradle dependencyCheck`, eslint security plugins.
- **Secrets management:** секреты только в env (для dev) и в Yandex Lockbox (для prod). Никаких секретов в git, даже в `.env.example`.
- **Incident response plan:** при подозрении на утечку — список действий: ротация JWT-ключа, отзыв всех refresh-токенов, аудит `audit_log` за период, уведомление пользователей по email при необходимости.
- **Регулярные backup-restore drills:** раз в месяц проверяем что бэкапы реально восстанавливаются.

---

## 9. Инфраструктура и деплой

### 9.1 Стек инфраструктуры (выбран Вариант В: Yandex Cloud Free Trial)

При регистрации в Yandex Cloud — грант **4000 ₽**. Postgres ставим в Docker на той же ВМ, чтобы не платить за managed-инстанс. Хватает на 2–3 месяца защиты + пилота, потом ~2000 ₽/мес.

| Компонент | Сервис | Стоимость в триал |
|-----------|--------|-------------------|
| Compute | Yandex Cloud VM (2 vCPU, 4GB, 30GB SSD) | ~1500 ₽/мес из гранта |
| Postgres | **Docker контейнер на той же ВМ** (postgis/postgis:16-3.4) | 0 ₽ |
| Object Storage | Yandex Object Storage (free tier до 1GB трафика, далее ~50 ₽/мес) | ~50 ₽/мес из гранта |
| Yandex Maps | API key (Free tier до 25k req/день) | 0 ₽ |
| Домен `.ru` | reg.ru | ~200 ₽/год |
| SSL | Let's Encrypt (через certbot/Caddy) | 0 ₽ |
| FCM | Firebase Spark plan | 0 ₽ |
| Email SMTP | Yandex Mail | 0 ₽ |
| DDoS / WAF | Cloudflare proxy перед публичным IP | 0 ₽ |
| **Итого первые 3 мес** | (грант покрывает) | **~0 ₽** |
| **После триала** | | **~2000 ₽/мес** |

**На локальной разработке** — Docker Compose, Postgres + backend + web в контейнерах. Photo storage — `LocalStorageService` (в `./uploads/`).

**Миграция на managed-Postgres** — после пилота, когда понятна нагрузка (сейчас неактуально).

### 9.2 Контейнеры

`docker-compose.yml` для dev и пилота:

```yaml
services:
  db:
    image: postgis/postgis:16-3.4
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: cleancity
      POSTGRES_USER: cleancity
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes: [pgdata:/var/lib/postgresql/data]

  backend:
    build: ./backend
    ports: ["8080:8080"]
    depends_on: [db]
    environment:
      DB_URL: jdbc:postgresql://db:5432/cleancity
      DB_USER: cleancity
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      S3_ENDPOINT: https://storage.yandexcloud.net
      S3_BUCKET: cleancity-photos-prod
      S3_KEY_ID: ${S3_KEY_ID}
      S3_SECRET: ${S3_SECRET}
      FCM_CREDENTIALS_PATH: /app/secrets/fcm.json
      SMTP_HOST: smtp.yandex.ru
      SMTP_PORT: 465
      SMTP_USER: ${SMTP_USER}
      SMTP_PASSWORD: ${SMTP_PASSWORD}
      EMAIL_FROM: noreply@cleancity.ru
    volumes:
      - ./secrets:/app/secrets:ro

  web:
    build: ./web-admin
    ports: ["80:80"]
    depends_on: [backend]

volumes:
  pgdata:
```

### 9.3 Миграции

Flyway, файлы в `backend/src/main/resources/db/migration/`:
- `V1__create_users.sql` (users, email_tokens, refresh_tokens)
- `V2__create_complaints_and_photos.sql`
- `V3__create_votes_and_status_changes.sql`
- `V4__create_announcements.sql`
- `V5__create_push_tokens.sql`
- `V6__create_audit_and_rate_limit.sql` (audit_log, rate_limit_buckets)
- `V7__seed_admin.sql` (создаёт первого админа из env: `INITIAL_ADMIN_EMAIL`, `INITIAL_ADMIN_PASSWORD`, флаг `must_change_password=true`)

### 9.4 Бэкапы

Postgres работает в Docker на той же ВМ (Вариант В), поэтому бэкап — задача нашего скрипта.

- **Расписание:** cron `0 3 * * *` (каждую ночь 03:00 МСК).
- **Скрипт `/opt/cleancity/backup.sh`:**
  1. `docker exec db pg_dump -U cleancity --format=custom cleancity > /tmp/dump.pgc`
  2. `gpg --symmetric --batch --passphrase-file /etc/cleancity/backup.key /tmp/dump.pgc` → `/tmp/dump.pgc.gpg`
  3. `aws s3 cp /tmp/dump.pgc.gpg s3://cleancity-backups/postgres/$(date +%F).pgc.gpg --endpoint-url=https://storage.yandexcloud.net`
  4. `rm /tmp/dump.pgc /tmp/dump.pgc.gpg`
- **Хранилище:** отдельный bucket `cleancity-backups` в Yandex Object Storage (приватный, отдельный сервис-ключ только на запись).
- **Retention:** lifecycle rule на bucket — `delete after 30 days`. Раз в месяц — ручной архив в холодное хранение (cold storage) на 1 год.
- **Restore drill:** раз в месяц (1-го числа) — скрипт `restore-test.sh` поднимает временный контейнер, накатывает последний дамп, проверяет что `SELECT count(*) FROM complaints` отдаёт ненулевое значение. Результат → в Telegram-канал алертов.
- **При инциденте:** `aws s3 cp s3://cleancity-backups/postgres/2026-05-08.pgc.gpg .`, `gpg --decrypt`, `pg_restore -d cleancity dump.pgc`.

### 9.5 Логирование и мониторинг

- **Логи:** Logback в stdout, JSON-формат. Docker подхватывает через `docker logs`. Опционально — Yandex Cloud Logging для долгосрочного хранения.
- **Алерты в Telegram:**
  - Создаём приватного бота через @BotFather (token в env `TELEGRAM_BOT_TOKEN`), приватный чат с админами проекта (chat_id в `TELEGRAM_ALERT_CHAT_ID`).
  - Logback-аппендер `TelegramAppender` (либо готовый `com.github.skjolber:logback-telegram-appender`) шлёт ERROR-логи и uncaught exceptions в этот чат.
  - Текст: `<service>:<env> ERROR <stack-trace-первая-строка> [полный stack — в Yandex Logging]`.
- **Health monitoring:**
  - На самой ВМ cron `*/5 * * * *` запускает `curl -fsS http://localhost:8080/health || /opt/cleancity/alert.sh "API DOWN"`. Скрипт `alert.sh` шлёт в тот же Telegram-чат через `curl -d` к Bot API.
  - Дополнительно — проверка дискового места `df -h` раз в час, алерт если `>85%`.
- **Метрики (Phase 2):** Prometheus + Grafana. Для MVP достаточно логов и Telegram-алертов.

### 9.6 Распространение mobile-приложения

Цель — получить APK на устройстве жителя Сочи без негативного впечатления «установка из неизвестных источников».

**Основной канал — RuStore** (российский магазин приложений, заменяет Google Play в РФ):
1. Регистрация разработчика (физ. лицо или ИП) — нужен паспорт + ИНН. Бесплатно.
2. Публикация APK + screenshots + описание + значок. Модерация ~3 рабочих дня.
3. Жители ставят через ссылку `rustore.ru/catalog/app/com.cleancity.sochi`.

**Резервный канал — прямая ссылка `cleancity.ru/cleancity.apk`:**
- Подписан release-keystore (НЕ debug). Хранится в зашифрованном виде в `/etc/cleancity/keystore.jks`, пароль — в Yandex Lockbox.
- Раздаётся Caddy как статика. На главной cleancity.ru — QR-код для быстрой установки во время демо.
- Инструкция «Как разрешить установку из неизвестных источников» в виде картинки на лендинге.

**Что НЕ делаем:**
- Google Play Store — недоступен для российских разработчиков с 2026.
- Альтернативные магазины (NashStore, AppGallery) — после пилота, по запросу администрации.

---

## 10. Структура проекта (Gradle)

```
cleancity-kmp/
├── shared/                 # Common API models (использует mobile + backend)
│   └── src/commonMain/kotlin/com/example/cleancity/shared/
│       ├── models/
│       │   ├── ProblemCategory.kt
│       │   ├── ComplaintStatus.kt
│       │   ├── UserRole.kt
│       │   ├── CategoryMeta.kt          (иконки + локализация)
│       │   ├── ComplaintResponse.kt
│       │   ├── MapMarker.kt
│       │   ├── AnnouncementResponse.kt
│       │   └── AnalyticsResponse.kt
│       └── requests/
│           ├── CreateComplaintRequest.kt
│           ├── ChangeStatusRequest.kt
│           ├── VoteRequest.kt
│           └── auth/...
│
├── backend/                # Ktor server
│   └── src/main/kotlin/com/example/cleancity/
│       ├── Application.kt
│       ├── auth/                       (JWT, OTP, password)
│       ├── complaints/                 (Routes, Service, Repository)
│       ├── votes/
│       ├── announcements/
│       ├── analytics/
│       ├── storage/                    (S3 + LocalStorage)
│       ├── notifications/              (FCM, SMS.ru)
│       ├── database/tables/
│       └── config/
│
├── composeApp/             # Compose Multiplatform → Android
│   └── src/
│       ├── commonMain/                 (UI, ViewModels, Repositories)
│       └── androidMain/                (FCM, Yandex Maps, permissions)
│
└── web-admin/              # React + TS + Vite (новая папка)
    └── src/
        ├── pages/          (Overview, Complaints, Announcements, Analytics, Settings)
        ├── components/
        ├── api/            (REST client с типами из OpenAPI)
        └── lib/maps/       (Yandex Maps JS API wrapper)
```

---

## 11. Зафиксированные решения

1. ✅ **Авторизация — email + пароль** для резидентов и админов. Незарегистрированные — read-only.
2. ✅ **На карте/в ленте жителей** показываем только статусы `NEW / IN_PROGRESS / RESOLVED`. Статусы `REJECTED / DUPLICATE` — только админам.
3. ✅ **Push при отклонении/дубликате** идёт автору + всем `+1`-голосовавшим с пояснением админа.
4. ✅ **Безопасность — повышенный приоритет** (раздел 8.1–8.8). 2FA TOTP для админов, audit-лог, отдельный поддомен.
5. ✅ **Хостинг — Yandex Cloud Free Trial** (грант 4000 ₽). Postgres в Docker на той же ВМ (без managed). Cloudflare как DDoS-прокси. После триала ~2000 ₽/мес.
6. ✅ **Монорепо** `cleancity-kmp/` с папкой `web-admin/`.
7. ✅ **Title жалобы** — авто-генерация на бэке из категории + первого сегмента адреса. Клиент title не передаёт, форма создания жалобы остаётся короткой.
8. ✅ **Голосование за `REJECTED/DUPLICATE`** — запрещено (409 Conflict) и для POST, и для DELETE. Это фиксирует общественный сигнал на момент закрытия.
9. ✅ **In-app уведомления** — отдельная таблица `notifications`, заполняется параллельно с FCM. Экран в mobile показывает историю с read/unread + бейджем на иконке.
10. ✅ **Распространение mobile** — RuStore + резервная ссылка на cleancity.ru с release-подписанным APK. Никакого debug-keystore в проде.
11. ✅ **Email-инфраструктура** — собственный домен `cleancity.ru` + Yandex Mail 360 (бесплатный для домена) + SPF/DKIM/DMARC. Покупаем и настраиваем в день 1 — критический путь, иначе verify-письма не работают.
12. ✅ **Бэкапы** — ежедневный pg_dump → Yandex Object Storage (bucket `cleancity-backups`), retention 30 дней + ежемесячный restore drill.
13. ✅ **Алерты** — Telegram-бот для ERROR-логов и API-down. Healthcheck cron на самой ВМ.
14. ✅ **Согласие на ПДн (152-ФЗ)** — обязательный чекбокс при регистрации, фиксация `accepted_terms_at` + `accepted_terms_version`. Документы — в репозитории.
15. ✅ **Аналитика (Spec 3, 2026-05-13)** — обычные SQL+агрегация в Kotlin (без materialized views); `slaBreachCount` в overview = только активные просрочки (NEW/IN_PROGRESS); пресет `?period=week|month|all`; справочники `/categories` и `/districts` отдаются из enum, без миграций; cleanup notifications старше 90 дней — kotlinx.coroutines внутри Ktor (без внешнего cron).

**К решению по ходу разработки:**
- OpenAPI генерация типов для React (плагин `ktor-openapi`) — рекомендую, ускорит фронт.
- Кэширование Yandex Maps тайлов — только если упрёмся в лимит 25k req/день (после пилота).

---

## 12. Out of scope (для следующих фаз)

- iOS (Compose iOS из KMP — есть, но не релизим).
- Госуслуги SSO.
- Машинная категоризация фото (auto-detect категории через CV).
- Чаты, сообщества.
- Раздельные роли OPERATOR / INSPECTOR.
- Multi-city (Сочи + другие города).
- Mobile-кабинет для админов.
- Web-PWA для жителей.
- **Offline-режим mobile** (создание жалоб без сети, очередь отправки при появлении интернета). Актуально для туристических зон Сочи (горы, Красная Поляна, удалённые пляжи), но требует ~2 дополнительных дней работы на mobile + worker для повторной отправки. → Phase 2.
- 3 PDF-отчёта из админки кроме «Сводный за месяц» (Реестр, SLA, Голосование) — карточки в UI отображаются с состоянием «Скоро» и disabled-кнопкой.

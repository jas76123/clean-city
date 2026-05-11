# Spec 1 — In-app notifications infrastructure (CleanCity Day 6, часть 1 из 3)

**Дата:** 2026-05-11
**Целевой день в PLAN.md:** Day 6 (13.05)
**Связанные спеки:** Spec 2 — Объявления (announcements + триггер push); Spec 3 — Аналитика
**Переопределяет SPEC:** §7 «Push-уведомления» — основной канал теперь polling backend API, FCM откладывается как optional provider после защиты

---

## 1. Цель и границы

### Цель
Дать горожанину механизм получать уведомления о статусе **его** жалоб без зависимости от внешней инфраструктуры (Google Play Services / Firebase). Mobile (Day 12) будет опрашивать backend, рендерить ленту уведомлений, бейдж непрочитанных и in-app banner при foreground.

### Что входит в Spec 1
- Миграция `V6__create_notifications.sql` — таблица `notifications` с CHECK-ограничениями и индексами под чтение
- Enum `NotificationKind` + DTO `NotificationDto` в `shared/models/`
- Расширение `NotificationService` interface до низкоуровневого `notify(...)` (batch INSERT)
- Реализация `DbNotificationService` (только запись в БД, без внешних HTTP-вызовов)
- Util-объект `NotificationTexts` — формирование текстов по событию (вынесено из бизнес-логики)
- Замена `NoopNotificationService` на `DbNotificationService` в DI
- Рефактор `ComplaintService.changeStatus(...)` на новый `notify(...)` API
- 4 endpoint'а: `GET /notifications`, `GET /notifications/unread-count`, `PATCH /notifications/{id}/read`, `PATCH /notifications/read-all`
- Заготовки под Spec 2: enum-значение `ANNOUNCEMENT`, колонка `notifications.announcement_id` (без FK; FK добавит миграция Spec 2)

### Что НЕ входит в Spec 1
- ❌ Таблица `push_tokens` и endpoint `POST /users/me/push-token` — откладывается до FCM-spec после защиты
- ❌ Firebase Admin SDK / FCM / любая внешняя push-инфраструктура
- ❌ Триггер «новое объявление → жителям» — переезжает в Spec 2 (нельзя без таблицы `announcements`)
- ❌ Триггер «50+ голосов» — backlog после защиты
- ❌ Триггер «новая жалоба → админам района» — backlog после защиты
- ❌ Фоновая задача очистки 90 дней — заменена фильтром `WHERE created_at > NOW() - INTERVAL '90 days'` в SELECT (фактическое удаление — оптимизация после пилота)

---

## 2. Контекст и фиксированные решения

1. **Канал доставки:** polling backend API из mobile, **не FCM**. На защите push не должен зависеть от Google Play Services / VPN. Backend на Yandex Cloud полностью контролируем.
2. **Точка расширения для FCM:** интерфейс `NotificationService` — будущая `FcmNotificationService` подключается feature-флагом / профилем; бизнес-логика не меняется.
3. **Авторизация:** все 4 endpoint'а требуют JWT (RESIDENT или Admin). Все запросы фильтруются по `currentUserId` из JWT. Юзер не может читать или менять чужие уведомления.
4. **Утечка существования ID:** PATCH `/notifications/{id}/read` для чужого `{id}` возвращает **404, не 403** — чтобы атакующий не мог enumerate чужие notification IDs.
5. **Транзакционность:** `notify(...)` вызывается **в той же транзакции** что `UPDATE complaints` и `INSERT status_changes`. Если INSERT в `notifications` упадёт — вся смена статуса откатывается. Статус не должен меняться «втихую» без уведомления.
6. **Идемпотентность read:** повторный `PATCH /{id}/read` не меняет `read_at` (`COALESCE(read_at, NOW())`). Обратное действие (unread) не поддерживается.
7. **Триггеры в Spec 1:** только смена статуса жалобы (per SPEC §5.2):
   - `IN_PROGRESS` / `RESOLVED` → автор
   - `REJECTED` / `DUPLICATE` → автор + все, кто проголосовал `+1`
   - `NEW` уведомление не создаёт (это создание жалобы, не смена статуса)

---

## 3. Схема БД

### Миграция `V6__create_notifications.sql`

```sql
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    title VARCHAR(300) NOT NULL,
    body TEXT NOT NULL,
    icon_style VARCHAR(20),
    complaint_id BIGINT REFERENCES complaints(id) ON DELETE CASCADE,
    announcement_id BIGINT,  -- FK добавит миграция Spec 2 одновременно с announcements
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT notifications_kind_check
        CHECK (kind IN ('COMPLAINT_STATUS', 'ANNOUNCEMENT')),
    CONSTRAINT notifications_target_check
        CHECK (
            (kind = 'COMPLAINT_STATUS' AND complaint_id IS NOT NULL AND announcement_id IS NULL)
            OR (kind = 'ANNOUNCEMENT' AND announcement_id IS NOT NULL AND complaint_id IS NULL)
        )
);

CREATE INDEX idx_notifications_user_created
    ON notifications(user_id, created_at DESC);

CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id)
    WHERE read_at IS NULL;
```

### Решения по схеме (отличия от SPEC §3.4 lines 283–296)
- **CHECK `kind`** — закрытое множество значений; защита от опечатки в строковом enum в коде
- **CHECK `target`** — гарантирует ровно один FK-таргет для каждого `kind`; невалидное состояние не запишется даже багом в коде
- **`announcement_id BIGINT` без FK** — миграция Spec 2 добавит `ADD CONSTRAINT ... FOREIGN KEY ... REFERENCES announcements(id) ON DELETE CASCADE`; иначе миграция Spec 1 невыполнима без таблицы `announcements`
- **Два индекса:** полный `(user_id, created_at DESC)` под список + частичный `(user_id) WHERE read_at IS NULL` под `unread-count`. Частичный компактнее, точнее матчит запрос
- **ON DELETE CASCADE** на `user_id` и `complaint_id` — удаление сущности удаляет привязанные уведомления (152-ФЗ право на удаление + отсутствие висящих ссылок)

### Семантика полей
- `read_at IS NULL` — непрочитано; `NOT NULL` — таймстамп прочтения
- Нет endpoint'а unmark — прочитанное не возвращается в непрочитанное
- `icon_style` — `INFO` | `SUCCESS` | `WARNING` (для будущих kind может расшириться)

---

## 4. Интерфейс `NotificationService`

### Файл `notifications/NotificationService.kt` (расширяется)

```kotlin
package com.example.cleancity.notifications

import com.example.cleancity.shared.models.NotificationKind

interface NotificationService {
    /**
     * Базовый низкоуровневый метод. Делает batch INSERT в notifications.
     * Если recipientUserIds пуст — no-op.
     *
     * complaintId / announcementId — взаимоисключающие в рамках одного kind:
     *   - kind=COMPLAINT_STATUS → complaintId != null, announcementId == null
     *   - kind=ANNOUNCEMENT     → announcementId != null, complaintId == null
     * CHECK на стороне БД дублирует это правило.
     */
    fun notify(
        recipientUserIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String? = null,
        complaintId: Long? = null,
        announcementId: Long? = null
    )
}
```

### Файл `notifications/DbNotificationService.kt` (новый)

```kotlin
class DbNotificationService(
    private val repository: NotificationRepository
) : NotificationService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun notify(
        recipientUserIds: List<Long>,
        kind: NotificationKind,
        title: String,
        body: String,
        iconStyle: String?,
        complaintId: Long?,
        announcementId: Long?
    ) {
        if (recipientUserIds.isEmpty()) return
        repository.insertBatch(
            userIds = recipientUserIds.distinct(),
            kind = kind,
            title = title,
            body = body,
            iconStyle = iconStyle,
            complaintId = complaintId,
            announcementId = announcementId
        )
        log.info(
            "Notified {} users: kind={} complaintId={} announcementId={}",
            recipientUserIds.size, kind, complaintId, announcementId
        )
    }
}
```

### Файл `notifications/NotificationTexts.kt` (новый)

Вся локализация и форматирование текстов уведомлений живёт здесь. Бизнес-сервис передаёт сущности, получает готовый `(title, body, iconStyle)`.

```kotlin
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.ComplaintStatus.*

object NotificationTexts {
    data class StatusChangeText(val title: String, val body: String, val iconStyle: String)

    fun statusChange(
        complaintTitle: String,
        toStatus: ComplaintStatus,
        adminComment: String
    ): StatusChangeText = when (toStatus) {
        IN_PROGRESS -> StatusChangeText(
            title = "Ваша жалоба принята в работу",
            body  = "«$complaintTitle» — в работе. $adminComment",
            iconStyle = "INFO"
        )
        RESOLVED -> StatusChangeText(
            title = "Ваша жалоба решена",
            body  = "«$complaintTitle» — решена. $adminComment",
            iconStyle = "SUCCESS"
        )
        REJECTED -> StatusChangeText(
            title = "Жалоба отклонена",
            body  = "«$complaintTitle» закрыта со статусом «Отклонена». " +
                    "Комментарий администрации: $adminComment",
            iconStyle = "WARNING"
        )
        DUPLICATE -> StatusChangeText(
            title = "Жалоба отмечена как дубликат",
            body  = "«$complaintTitle» закрыта со статусом «Дубликат». " +
                    "Комментарий администрации: $adminComment",
            iconStyle = "WARNING"
        )
        NEW -> error("NEW не триггерит уведомление")
    }
}
```

### Enum `shared/models/NotificationKind.kt` (новый)

```kotlin
@Serializable
enum class NotificationKind { COMPLAINT_STATUS, ANNOUNCEMENT }
```

`ANNOUNCEMENT` — заготовка под Spec 2. В Spec 1 значение в enum есть, но физически вставить такую строку нельзя: CHECK `notifications_target_check` требует `announcement_id IS NOT NULL`, а пока таблицы `announcements` нет — валидного значения для `announcement_id` взять негде. Defense-in-depth: даже багом нельзя случайно создать сиротское ANNOUNCEMENT-уведомление до выкатки Spec 2.

### Удаляется
`NoopNotificationService` — больше не нужен. `DbNotificationService` дешёвый в тестах: для unit-тестов подменяется мок `NotificationRepository`.

### Trade-off — почему `List<Long>` а не `Long`
Списком получателей `recipientUserIds` позволяет одним SQL `INSERT ... SELECT FROM unnest($1)` записать всю рассылку. Для «REJECTED жалоба с 50 поддержавшими» — один запрос вместо 50.

---

## 5. REST API

База: `/notifications`. Все 4 endpoint'а требуют JWT.

### 5.1 `GET /notifications?limit=50&offset=0`

```
Query: limit (1..100, default 50), offset (>=0, default 0)

SELECT id, kind, title, body, icon_style,
       complaint_id, announcement_id, read_at, created_at
FROM notifications
WHERE user_id = :currentUserId
  AND created_at > NOW() - INTERVAL '90 days'
ORDER BY created_at DESC
LIMIT :limit OFFSET :offset

Response 200: {
  "items": [NotificationDto],
  "total": number,    // COUNT(*) с теми же фильтрами без LIMIT/OFFSET
  "hasMore": boolean  // items.size == limit
}
```

### 5.2 `GET /notifications/unread-count`

```
SELECT COUNT(*) FROM notifications
WHERE user_id = :currentUserId
  AND read_at IS NULL
  AND created_at > NOW() - INTERVAL '90 days'

Response 200: { "count": number }
```

### 5.3 `PATCH /notifications/{id}/read`

```
UPDATE notifications
SET read_at = COALESCE(read_at, NOW())
WHERE id = :id AND user_id = :currentUserId
RETURNING id

0 rows → 404 Not Found (защита от enumeration чужих ID)
≥1 row → 204 No Content
```

### 5.4 `PATCH /notifications/read-all`

```
UPDATE notifications
SET read_at = NOW()
WHERE user_id = :currentUserId AND read_at IS NULL

Response 200: { "markedCount": number }  -- кол-во затронутых строк
```

### DTO `NotificationDto` (shared/models)

```kotlin
@Serializable
data class NotificationDto(
    val id: Long,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val iconStyle: String?,
    val complaintId: Long?,
    val announcementId: Long?,
    val readAt: Instant?,
    val createdAt: Instant
)
```

---

## 6. Поток «смена статуса жалобы»

### Изменение в `ComplaintService.changeStatus(...)`

Транзакция остаётся та же, что в Day 5; меняется только последняя строка с вызовом `NotificationService`.

```kotlin
// Было (Day 5, NoopNotificationService):
notificationService.notifyStatusChange(
    complaintId, complaintTitle, toStatus, comment, recipientIds
)

// Станет:
val text = NotificationTexts.statusChange(complaint.title, toStatus, comment)
notificationService.notify(
    recipientUserIds = recipientIds,
    kind = NotificationKind.COMPLAINT_STATUS,
    title = text.title,
    body = text.body,
    iconStyle = text.iconStyle,
    complaintId = complaintId
)
```

### Логика получателей `recipientIds` (уже есть в Day 5, не меняется)

- `toStatus ∈ {IN_PROGRESS, RESOLVED}` → `[complaint.authorId]`
- `toStatus ∈ {REJECTED, DUPLICATE}` → `[complaint.authorId] ∪ SELECT user_id FROM votes WHERE complaint_id = :id AND value = 1`
- `DbNotificationService.notify` дедуплицирует через `.distinct()` (защита от случая автор+голосовавший=один пользователь)

### Транзакционная гарантия

Один Postgres-транзакционный блок:
1. `UPDATE complaints SET status = ?, updated_at = NOW(), resolved_at = ?, duplicate_of_id = ? WHERE id = ?`
2. `INSERT INTO status_changes (complaint_id, from_status, to_status, comment, changed_by_id)`
3. (для DUPLICATE) перенос голосов с conflict skip
4. `INSERT INTO notifications (user_id, kind, ...) SELECT user_id, 'COMPLAINT_STATUS', ... FROM unnest(:userIds)`

Если шаг 4 упадёт (например, недоступна БД на середине транзакции) — всё откатывается, статус не сменился. Это правильно: «жалоба перешла в RESOLVED но автор об этом не узнал» — недопустимое состояние.

### NotificationKind для разных статусов

В Spec 1 все 4 ветки status change → `NotificationKind.COMPLAINT_STATUS` (различия — в тексте, не в kind). Мобильный клиент при рендере смотрит на `iconStyle` (`SUCCESS`/`WARNING`/`INFO`) для цвета.

---

## 7. Тестирование

### 7.1 `NotificationRepositoryTest` (integration, Testcontainers Postgres)

| Сценарий | Ожидание |
|---|---|
| `insertBatch(userIds=[1,2,3], kind=COMPLAINT_STATUS, complaintId=10)` | 3 строки в `notifications`, поля корректны |
| `insertBatch(userIds=[], ...)` | no-op, 0 строк |
| Прямой INSERT `kind='COMPLAINT_STATUS' complaint_id=NULL` | `SQLException` (CHECK constraint работает) |
| Прямой INSERT `kind='ANNOUNCEMENT' complaint_id=10 announcement_id=NULL` | `SQLException` |

### 7.2 `NotificationRoutesTest` (Ktor `testApplication`)

| Сценарий | Ожидание |
|---|---|
| `GET /notifications` без JWT | 401 |
| `GET /notifications` от A: 3 свои + 2 чужие в БД | 200, видно только 3 |
| `GET /notifications?limit=2` (5 в БД) | 200, items=2, total=5, hasMore=true |
| `GET /notifications` после прямого INSERT с `created_at = NOW() - INTERVAL '91 days'` | старая строка не возвращается (фильтр работает) |
| `GET /notifications/unread-count` (3 unread + 2 read) | 200, `{count: 3}` |
| `PATCH /notifications/{id}/read` своё unread | 204, `read_at` заполнен |
| `PATCH /notifications/{id}/read` своё уже read | 204, `read_at` не изменился |
| `PATCH /notifications/{id}/read` чужое | **404** (не 403) |
| `PATCH /notifications/read-all` (3 unread) | 200, `{markedCount: 3}` |

### 7.3 `ComplaintStatusNotificationTest` (integration end-to-end)

- Создать жалобу автором A → подавляющий тест: при создании уведомления **не** появляются (Spec 1 не делает триггер «новая жалоба»)
- B голосует +1 → нет уведомлений
- Админ `PATCH /complaints/{id}/status` toStatus=REJECTED, comment="Не подтверждено инспектором"
- В `notifications`: 2 строки (A и B), `kind=COMPLAINT_STATUS`, `complaint_id=...`, `body` содержит «Не подтверждено инспектором», `iconStyle=WARNING`
- `GET /notifications` от A → видит свою; от B → видит свою; от C (не автор, не голосовал) → не видит
- **Дедупликация автора:** в Day 5 был добавлен автоголос автора при создании жалобы. Сценарий: A создаёт → A автоматически имеет vote — REJECTED триггерит уведомление автору (A) + голосовавшим (A). В `notifications` для A должна быть ровно **одна** строка, не две (`.distinct()` в `DbNotificationService.notify`)
- Тест rollback: мок `NotificationRepository.insertBatch` кидает `SQLException` → статус жалобы остался прежним, `status_changes` без новой записи

### 7.4 `NotificationTextsTest` (pure unit)

- 4 кейса `statusChange(IN_PROGRESS|RESOLVED|REJECTED|DUPLICATE, ...)` — каждый возвращает правильный `title` / `body` (с подставленным `complaintTitle` и `adminComment`) / `iconStyle`
- `statusChange(NEW, ...)` → `IllegalStateException`

---

## 8. Порядок реализации

1. Миграция `V6__create_notifications.sql`; локально `docker compose up` подхватывает; вручную проверить `\d notifications` и CHECK constraints
2. `shared/models/NotificationKind.kt` + `shared/models/NotificationDto.kt`
3. `notifications/NotificationRepository.kt` (interface + Postgres impl) + `NotificationRepositoryTest`
4. `notifications/NotificationTexts.kt` + `NotificationTextsTest`
5. Расширение `notifications/NotificationService.kt` interface; новый `notifications/DbNotificationService.kt`; удаление `NoopNotificationService`
6. DI-binding: замена `NoopNotificationService` на `DbNotificationService` в `Application.kt`
7. Рефактор `ComplaintService.changeStatus(...)` под новый `notify(...)` API; убрать старый метод `notifyStatusChange` из интерфейса
8. `ComplaintStatusNotificationTest` (end-to-end + rollback)
9. `notifications/NotificationRoutes.kt` + регистрация в `Application.kt`
10. `NotificationRoutesTest`
11. Smoke-проверка через curl/Postman: register A → register B → A создаёт жалобу → B голосует +1 → admin PATCH status=REJECTED → `GET /notifications` от A и B показывает уведомление, `/unread-count` = 1, `PATCH /{id}/read` → 0

---

## 9. Чего эта спека НЕ предписывает (out-of-scope strict)

- Изменения в `votes/` (50+ threshold) — backlog
- Изменения в `ComplaintService.create()` (триггер админам района) — backlog
- Любой FCM / Firebase / push_tokens / external HTTP
- Любой scheduler / cron / фоновая задача
- Любые изменения в mobile (это Day 12; backend здесь готовит для него API)
- Любые изменения в web admin (web admin будет триггерить смену статуса через тот же PATCH endpoint — Spec 1 не меняет контракт PATCH)

---

## 10. Связь со следующими спеками

- **Spec 2 (Announcements)** добавит: таблицу `announcements`, миграцию `ALTER TABLE notifications ADD CONSTRAINT fk_announcement_id FOREIGN KEY ...`, CRUD endpoints, триггер `notifyAnnouncementPublished` через тот же `notify(...)` API с `kind=ANNOUNCEMENT`. Никаких изменений в Spec 1 кода не требует.
- **Spec 3 (Аналитика)** — независим, не пересекается.
- **FCM-spec (после защиты)** добавит: `FcmNotificationService` как декоратор/композит вокруг `DbNotificationService` (сначала INSERT, потом FCM-вызов асинхронно), таблицу `push_tokens`, endpoint регистрации токена. Подключается через DI-флаг, бизнес-логика не меняется.

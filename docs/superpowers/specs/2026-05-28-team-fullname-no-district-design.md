# Команда: добавить ФИО в инвайт, убрать колонку «Район»

**Дата:** 2026-05-28
**Статус:** дизайн утверждён, готов к плану реализации
**Контекст:** follow-up к [Day 17C — SettingsPage](2026-05-28-day17c-settings-team-audit-design.md). Колонка «Район» в таблице команды всегда показывает «Все районы», т.к. поле `users.district` для admin/operator никогда не заполняется. ФИО в DTO команды есть, но в auth-флоу нигде не вводится — колонка тоже всегда пуста («—»).

## Цель

1. Сделать так, чтобы в таблице команды отображалось реальное ФИО сотрудника.
2. Убрать неинформативную колонку «Район» из таблицы команды.

## Объём (scope)

- **Frontend:** `InviteMemberDialog` принимает ФИО; `TeamTable` без колонки «Район».
- **Shared DTO:** `AdminInviteRequest.fullName: String` (обязательное); `TeamMemberDto.district` удалено.
- **Backend:** `AuthService.inviteAdmin` принимает и сохраняет ФИО; шаблон письма приглашения персонализируется.
- **Тесты:** обновляются существующие, добавляется кейс «blank fullName → 400».

### Не входит

- Колонка `users.district` в БД и связанная логика рассылки announcements жителям (`AnnouncementRepository.recipientIdsForDistricts`) — **не трогаем**.
- Редактирование ФИО после активации инвайта.
- Изменение роли / района сотрудника через UI.
- Миграция данных для существующих admin/operator без ФИО (старые записи продолжат показывать «—», обратная совместимость сохраняется).

## Изменения по слоям

### Shared DTO (Kotlin)

`shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/auth/AuthRequests.kt`

```kotlin
@Serializable
data class AdminInviteRequest(
    val email: String,
    val fullName: String,   // NEW: обязательное, валидация на бэке
    val role: String        // "ADMIN" | "OPERATOR"
)
```

`shared/src/commonMain/kotlin/com/example/cleancity/shared/responses/admin/TeamMemberDto.kt`

```kotlin
@Serializable
data class TeamMemberDto(
    val id: Long,
    val email: String,
    val fullName: String?,
    val role: String,
    // val district: String?,   // REMOVED
    val status: TeamStatus,
    val createdAt: String,
    val lastLoginAt: String?,
    val invitedAt: String?
)
```

### Backend

**`AuthService.inviteAdmin`** (`backend/.../auth/AuthService.kt`)

Новый параметр `fullName: String`. Валидация в начале метода:
- `fullName.isBlank()` → бросить `InvalidFullNameException` (новый класс в `ApiExceptions.kt`, маппится в 400 с кодом `INVALID_FULL_NAME`)
- Триммим перед сохранением: `users.create(..., fullName = fullName.trim(), ...)`

**`AuthRoutes.kt`** `POST /admin/invite` (строка ~243)

Пробросить `req.fullName` в `service.inviteAdmin(actorId, req.email, req.fullName, targetRole, ip, ua)`.

**`EmailTemplates.adminInvite`** (`backend/.../email/EmailTemplates.kt`)

Новый параметр `recipientName: String` (вызывающая сторона — `AuthService.inviteAdmin` — теперь имеет ФИО). Использовать в приветствии: `Здравствуйте, {recipientName}!`.

**`UserRepository.toTeamMember`** (`backend/.../auth/UserRepository.kt:~215`)

Убрать маппинг `district = this[Users.district]` из конструктора `TeamMemberDto`.

### Frontend (web-admin)

**`web-admin/src/pages/settings/InviteMemberDialog.tsx`**

- Новое поле `fullName: string` в state, инпут с `<Label>ФИО</Label>` между Email и Роль.
- Сигнатура `onSubmit`: `(email: string, fullName: string, role: 'ADMIN' | 'OPERATOR') => void`.
- Кнопка «Пригласить» disabled, пока `!email.trim() || !fullName.trim()`.
- При закрытии диалога сброс `fullName` так же, как `email` и `role`.

**`web-admin/src/pages/settings/TeamSection.tsx`**

Пробросить новое поле в API-вызов `inviteTeamMember`.

**`web-admin/src/api/admin.ts`**

`inviteTeamMember(email, role)` → `inviteTeamMember(email, fullName, role)`; в `api.post('/auth/admin/invite', { email, fullName, role })`.

**`web-admin/src/api/types.ts`**

Удалить `district` из `TeamMemberDto`.

**`web-admin/src/pages/settings/TeamTable.tsx`**

- Удалить `<TableHead>Район</TableHead>` (строка 69).
- Удалить `<TableCell>{m.district ?? 'Все районы'}</TableCell>` (строка 84).

### Тесты

**Бэк:**
- Найти и обновить тесты `inviteAdmin` (AuthServiceTest / интеграционные на `POST /admin/invite`):
  - существующие — добавить `fullName = "Иван Иванов"` в фикстуры
  - новый кейс: `POST /admin/invite` с пустым `fullName` → `400 INVALID_FULL_NAME`
- `EmailTemplates`: тест на персонализированный subject/body (если есть юнит-тесты шаблонов)

**Веб-админка:**
- `InviteMemberDialog.test.tsx`: 
  - кнопка disabled пока ФИО пустое
  - `onSubmit` вызывается с тремя аргументами (email, fullName, role)
  - сброс fullName при закрытии
- `TeamTable.test.tsx`: 
  - убрать ожидания колонки «Район» и значения district из ассертов

## Обратная совместимость

- Существующие admin/operator без `fullName` (например, bootstrap-админ `admin@cleancity.dev`) — продолжат отображаться с «—» в колонке ФИО. Ничего не ломается.
- БД-колонка `users.district` остаётся как есть. Используется в рассылке announcements жителям по районам.
- DTO для residents (если где-то отдельно) не трогается; `district` убирается **только из `TeamMemberDto`**.

## Acceptance criteria

1. На странице `/settings` → «Команда» нет колонки «Район».
2. Диалог «Пригласить сотрудника» содержит обязательное поле «ФИО»; кнопка «Пригласить» неактивна, пока поле пустое.
3. После приглашения нового сотрудника с ФИО «Тест Тестов»:
   - В таблице «Ожидают» в колонке ФИО видно «Тест Тестов».
   - На email приходит письмо с приветствием «Здравствуйте, Тест Тестов!».
4. `POST /auth/admin/invite` без `fullName` (или с пустым) возвращает `400 INVALID_FULL_NAME`.
5. Существующий bootstrap-админ продолжает работать; в его строке таблицы ФИО показано как «—».
6. Рассылка announcements жителям по выбранным районам продолжает работать (smoke).

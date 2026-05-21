# Удаление аккаунта + чистка профиля — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Убрать дублирующий пункт «Мои жалобы» из профиля и добавить самостоятельное удаление аккаунта жителем (soft-delete + анонимизация, требование 152-ФЗ).

**Architecture:** Новый эндпоинт `DELETE /auth/me` (только роль `RESIDENT`) анонимизирует пользователя и отзывает refresh-токены. Жалобы остаются, имя автора подменяется на «Удалённый пользователь» на уровне репозитория жалоб. Мобильный клиент вызывает эндпоинт из профиля через диалог подтверждения и локально завершает сессию.

**Tech Stack:** Kotlin, Ktor (backend), Exposed + PostgreSQL/H2(тесты), Compose Multiplatform + Voyager + Koin (mobile), Ktor client.

**Спецификация:** `docs/superpowers/specs/2026-05-21-account-deletion-design.md`

**Команды тестов:**
- Backend: `./gradlew :backend:test`
- Mobile (common-тесты): `./gradlew :composeApp:testDebugUnitTest`

---

## File Structure

**Backend (изменяются):**
- `backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt` — новое событие аудита.
- `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt` — метод анонимизации.
- `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt` — оркестрация удаления.
- `backend/src/main/kotlin/com/example/cleancity/auth/AuthRoutes.kt` — роут `DELETE /auth/me`.
- `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRepository.kt` — подмена имени автора.
- `docs/SPEC.md` — строка эндпоинта.

**Backend (создаются):**
- `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt`
- `backend/src/test/kotlin/com/example/cleancity/auth/AccountDeletionRoutesTest.kt`

**Mobile (изменяются):**
- `composeApp/.../ui/feature/profile/ProfileScreen.kt` — убрать «Мои жалобы», добавить UI удаления.
- `composeApp/.../ui/feature/profile/ProfileScreenModel.kt` — логика удаления.
- `composeApp/.../data/network/AuthApi.kt` — метод `deleteAccount`.
- `composeApp/.../data/repository/AuthRepository.kt` — метод `deleteAccount`.
- `composeApp/.../di/AppModule.kt` — снять регистрацию `MyComplaintsScreenModel`.
- `composeApp/.../commonTest/.../data/network/FakeAuthApi.kt` — поддержка нового метода.

**Mobile (удаляются — мёртвый код):**
- `composeApp/.../ui/feature/mycomplaints/MyComplaintsScreen.kt`
- `composeApp/.../ui/feature/mycomplaints/MyComplaintsScreenModel.kt`
- `composeApp/.../commonTest/.../ui/feature/mycomplaints/MyComplaintsScreenModelTest.kt`
- `composeApp/.../commonTest/.../ui/feature/mycomplaints/FakeMineComplaintsApi.kt`

**Mobile (создаются):**
- `composeApp/.../commonTest/.../ui/feature/profile/ProfileScreenModelTest.kt`
- `composeApp/.../commonTest/.../ui/feature/profile/FakeProfileComplaintsApi.kt`

---

## Task 1: Убрать «Мои жалобы» из профиля и удалить мёртвый код

Чисто фронтовое изменение. Автотеста нет (удаление кода) — проверка через компиляцию.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreen.kt`
- Delete: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModel.kt`
- Delete: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModelTest.kt`
- Delete: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/FakeMineComplaintsApi.kt`

- [ ] **Step 1: Убрать импорты `MyComplaintsScreen` и `ListAlt` из `ProfileScreen.kt`**

Удалить эти две строки (импорты):

```kotlin
import androidx.compose.material.icons.filled.ListAlt
```
```kotlin
import com.example.cleancity.ui.feature.mycomplaints.MyComplaintsScreen
```

- [ ] **Step 2: Убрать `onMyComplaintsClick` из вызова `LoadedView` в `Content()`**

Найти блок `is ProfileState.Loaded -> LoadedView(` и удалить из него строку:

```kotlin
                    onMyComplaintsClick = { navigator.push(MyComplaintsScreen()) },
```

- [ ] **Step 3: Убрать параметр `onMyComplaintsClick` из сигнатуры `LoadedView`**

В объявлении `private fun LoadedView(` удалить строку:

```kotlin
    onMyComplaintsClick: () -> Unit,
```

- [ ] **Step 4: Убрать проброс `onMyComplaintsClick` в вызове `ProfileMenu` внутри `LoadedView`**

В теле `LoadedView`, в вызове `ProfileMenu(`, удалить строку:

```kotlin
            onMyComplaintsClick = onMyComplaintsClick,
```

- [ ] **Step 5: Убрать параметр `onMyComplaintsClick` из сигнатуры `ProfileMenu` и пункт меню**

В объявлении `private fun ProfileMenu(` удалить строку:

```kotlin
    onMyComplaintsClick: () -> Unit,
```

В теле `ProfileMenu` удалить строку с пунктом меню:

```kotlin
        MenuItemRow(icon = Icons.Default.ListAlt, label = "Мои жалобы", onClick = onMyComplaintsClick)
```

- [ ] **Step 6: Удалить четыре файла мёртвого кода**

```bash
rm composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreen.kt
rm composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModel.kt
rm composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/MyComplaintsScreenModelTest.kt
rm composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints/FakeMineComplaintsApi.kt
```

- [ ] **Step 7: Снять регистрацию `MyComplaintsScreenModel` в `AppModule.kt`**

Удалить строку импорта:

```kotlin
import com.example.cleancity.ui.feature.mycomplaints.MyComplaintsScreenModel
```

Удалить блок-фабрику (целиком, включая `factory {` и закрывающую `}`):

```kotlin
    factory {
        MyComplaintsScreenModel(complaintsApi = get<ComplaintsApiContract>())
    }
```

- [ ] **Step 8: Скомпилировать и прогнать common-тесты**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Компиляция проходит, ссылок на `mycomplaints` не осталось, упавших тестов нет.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/mycomplaints \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/mycomplaints
git commit -m "refactor: убрать дублирующий пункт «Мои жалобы» из профиля"
```

---

## Task 2: Backend — событие аудита + анонимизация пользователя

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt` (создать)

- [ ] **Step 1: Написать падающий тест анонимизации**

Создать `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt`:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UserRepositoryTest {

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:user-repo-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
        }
    }

    @Test
    fun `softDeleteAndAnonymize wipes email password and name`() {
        initDb()
        val repo = UserRepository()
        val user = repo.create(
            email = "victim@cleancity.local",
            passwordHash = "real-bcrypt-hash",
            role = UserRole.RESIDENT,
            fullName = "Иван Петров",
        )

        repo.softDeleteAndAnonymize(user.id)

        val after = repo.findById(user.id)!!
        assertEquals("deleted_${user.id}@cleancity.local", after.email)
        assertEquals("", after.passwordHash)
        assertNull(after.fullName)
        assertFalse(after.isActive)
    }

    @Test
    fun `softDeleteAndAnonymize makes original email unfindable`() {
        initDb()
        val repo = UserRepository()
        val user = repo.create(
            email = "victim2@cleancity.local",
            passwordHash = "real-bcrypt-hash",
            role = UserRole.RESIDENT,
            fullName = "Иван Петров",
        )

        repo.softDeleteAndAnonymize(user.id)

        assertNull(repo.findByEmail("victim2@cleancity.local"))
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что не компилируется/падает**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.UserRepositoryTest"`
Expected: FAIL — компиляция падает, `softDeleteAndAnonymize` не существует.

- [ ] **Step 3: Добавить событие `ACCOUNT_DELETED` в `AuditAction`**

В `AuditLog.kt`, в `enum class AuditAction`, заменить последнюю строку (добавить запятую и новое значение):

Было:
```kotlin
    ADMIN_INVITE_ACCEPTED,
    COMPLAINT_STATUS_CHANGE
}
```

Стало:
```kotlin
    ADMIN_INVITE_ACCEPTED,
    COMPLAINT_STATUS_CHANGE,
    ACCOUNT_DELETED
}
```

- [ ] **Step 4: Добавить `softDeleteAndAnonymize` в `UserRepository`**

В `UserRepository.kt` добавить метод внутри класса `UserRepository` (например, после `enableTotp`):

```kotlin
    /**
     * Soft-delete + анонимизация аккаунта (152-ФЗ). Email заменяется на
     * deleted_<id>@cleancity.local, пароль и имя обнуляются, is_active=false.
     * password_hash ставится в пустую строку (колонка NOT NULL) — этого
     * достаточно: ни один пароль не верифицируется против пустого хеша.
     */
    fun softDeleteAndAnonymize(userId: Long) = transaction {
        Users.update({ Users.id eq userId }) {
            it[Users.isActive] = false
            it[Users.email] = "deleted_${userId}@cleancity.local"
            it[Users.passwordHash] = ""
            it[Users.fullName] = null
        }
    }
```

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.UserRepositoryTest"`
Expected: PASS — оба теста зелёные.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/database/tables/AuditLog.kt \
        backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt
git commit -m "feat(backend): анонимизация аккаунта при удалении (152-ФЗ)"
```

---

## Task 3: Backend — сервис удаления + роут `DELETE /auth/me`

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthRoutes.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/auth/AccountDeletionRoutesTest.kt` (создать)

- [ ] **Step 1: Написать падающий роут-тест**

Создать `backend/src/test/kotlin/com/example/cleancity/auth/AccountDeletionRoutesTest.kt`:

```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.AuditLog
import com.example.cleancity.database.tables.EmailTokens
import com.example.cleancity.database.tables.RefreshTokens
import com.example.cleancity.database.tables.Users
import com.example.cleancity.email.EmailService
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.testutils.installApiErrorHandling
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionRoutesTest {

    private val jwtConfig = JwtConfig(
        secret = "test-secret-at-least-32-bytes-long-for-tests-only-1234567890",
        issuer = "cleancity-test",
        audience = "cleancity-test-api"
    )
    private val json = Json { ignoreUnknownKeys = true }

    private class NoopEmail : EmailService {
        override suspend fun send(to: String, subject: String, htmlBody: String, plainBody: String?) = Unit
    }

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:acc-del-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver"
        )
        transaction {
            SchemaUtils.drop(RefreshTokens, EmailTokens, AuditLog, Users)
            SchemaUtils.create(Users, EmailTokens, RefreshTokens, AuditLog)
        }
    }

    private fun seedUser(email: String, role: UserRole): Long = transaction {
        Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = "bcrypt-hash"
            it[Users.role] = role.name
            it[Users.fullName] = "Тест Тестов"
            it[Users.emailVerified] = true
            it[Users.isActive] = true
            it[Users.createdAt] = OffsetDateTime.now(ZoneOffset.UTC)
            it[Users.passwordChangedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }[Users.id]
    }

    private fun bearerFor(userId: Long, role: UserRole): String =
        jwtConfig.issueAccessToken(userId = userId, role = role).token

    private fun ApplicationTestBuilder.appWithAuth() {
        val service = AuthService(
            users = UserRepository(),
            tokens = TokenRepository(),
            email = NoopEmail(),
            jwt = jwtConfig,
            baseUrl = "http://localhost:8080",
            termsVersion = "test-v1",
            totp = TotpService(),
            audit = DbAuditLogger()
        )
        application {
            install(ContentNegotiation) { json(json) }
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(jwtConfig.verifier)
                    validate { c ->
                        if (c.payload.subject != null &&
                            c.payload.getClaim("type").asString() == "access"
                        ) JWTPrincipal(c.payload) else null
                    }
                }
            }
            installApiErrorHandling()
            routing { authRoutes(service, RateLimiter()) }
        }
    }

    @Test
    fun `DELETE auth me without token returns 401`() {
        initDb()
        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/me")
            assertEquals(HttpStatusCode.Unauthorized, resp.status)
        }
    }

    @Test
    fun `DELETE auth me as non-resident returns 403`() {
        initDb()
        val adminId = seedUser("admin@cleancity.local", UserRole.ADMIN)
        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/me") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(adminId, UserRole.ADMIN)}")
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }

    @Test
    fun `DELETE auth me as resident returns 204 and anonymizes account`() {
        initDb()
        val residentId = seedUser("resident@cleancity.local", UserRole.RESIDENT)
        testApplication {
            appWithAuth()
            val resp = client.delete("/auth/me") {
                header(HttpHeaders.Authorization, "Bearer ${bearerFor(residentId, UserRole.RESIDENT)}")
            }
            assertEquals(HttpStatusCode.NoContent, resp.status)

            transaction {
                val row = Users.selectAll().where { Users.id eq residentId }.first()
                assertEquals("deleted_${residentId}@cleancity.local", row[Users.email])
                assertEquals("", row[Users.passwordHash])
                assertNull(row[Users.fullName])
                assertFalse(row[Users.isActive])

                val audited = AuditLog.selectAll()
                    .where { AuditLog.action eq AuditAction.ACCOUNT_DELETED.name }
                    .count()
                assertTrue(audited >= 1)
            }
        }
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.AccountDeletionRoutesTest"`
Expected: FAIL — компиляция падает (`deleteOwnAccount` не существует); даже после фикса компиляции роут `/auth/me` отсутствует → запросы возвращают 404 вместо 401/403/204.

- [ ] **Step 3: Добавить `deleteOwnAccount` в `AuthService`**

В `AuthService.kt` добавить метод внутри класса `AuthService` (например, после `revokeSession`):

```kotlin
    /**
     * Самостоятельное удаление аккаунта жителем (152-ФЗ): анонимизация
     * персональных данных + отзыв всех refresh-токенов + запись в audit_log.
     */
    fun deleteOwnAccount(userId: Long, ip: String?, userAgent: String?) {
        users.softDeleteAndAnonymize(userId)
        tokens.revokeAllUserRefreshTokens(userId)
        audit.log(AuditAction.ACCOUNT_DELETED, userId, "user", userId.toString(), ip, userAgent)
    }
```

(`AuditAction` уже импортирован в `AuthService.kt`.)

- [ ] **Step 4: Добавить роут `DELETE /auth/me` в `AuthRoutes.kt`**

Внутри блока `authenticate("auth-jwt") {` добавить роут сразу после блока `delete("/sessions/{id}") { ... }`:

```kotlin
            delete("/me") {
                val userId = call.requireUserId()
                if (call.requireRole() != UserRole.RESIDENT) {
                    throw ForbiddenException("Сотрудники удаляются администратором")
                }
                service.deleteOwnAccount(userId, call.clientIp(), call.userAgentSafe())
                call.respond(HttpStatusCode.NoContent)
            }
```

(`delete`, `HttpStatusCode`, `ForbiddenException`, `UserRole`, `requireUserId`, `requireRole`, `clientIp`, `userAgentSafe` уже доступны в этом файле.)

- [ ] **Step 5: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.AccountDeletionRoutesTest"`
Expected: PASS — все 3 теста зелёные.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt \
        backend/src/main/kotlin/com/example/cleancity/auth/AuthRoutes.kt \
        backend/src/test/kotlin/com/example/cleancity/auth/AccountDeletionRoutesTest.kt
git commit -m "feat(backend): эндпоинт DELETE /auth/me для удаления аккаунта"
```

---

## Task 4: Backend — скрыть имя автора у удалённых пользователей

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRepository.kt:361-362`
- Test: `backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintVisibilityTest.kt` (добавить тест)

- [ ] **Step 1: Написать падающий тест в `ComplaintVisibilityTest`**

В файл `ComplaintVisibilityTest.kt` добавить импорт (рядом с прочими `org.jetbrains.exposed.sql.*`):

```kotlin
import org.jetbrains.exposed.sql.update
```

И новый тест-метод внутри класса `ComplaintVisibilityTest`:

```kotlin
    @Test
    fun `list shows Удалённый пользователь only for deleted authors`() {
        val activeAuthor = seedUser("active@x.ru")
        val deletedAuthor = seedUser("gone@x.ru")
        seedComplaint(activeAuthor, ComplaintStatus.NEW, "ул. Живая")
        seedComplaint(deletedAuthor, ComplaintStatus.NEW, "ул. Тихая")

        // имитируем softDeleteAndAnonymize для автора
        transaction {
            Users.update({ Users.id eq deletedAuthor }) {
                it[Users.isActive] = false
                it[Users.fullName] = null
                it[Users.email] = "deleted_${deletedAuthor}@cleancity.local"
            }
        }

        val resp = service.list(Viewer.Guest, PublicListFilter())
        val byAddress = resp.items.associateBy { it.address }
        assertEquals("Удалённый пользователь", byAddress["ул. Тихая"]!!.authorName)
        assertNull(byAddress["ул. Живая"]!!.authorName)
    }
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :backend:test --tests "com.example.cleancity.complaints.ComplaintVisibilityTest"`
Expected: FAIL — для «ул. Тихая» `authorName` равен `null` (из обнулённого `full_name`), а не «Удалённый пользователь».

- [ ] **Step 3: Подменить имя автора в `ComplaintRepository.toComplaintRow()`**

В `ComplaintRepository.kt` заменить начало метода `toComplaintRow()`.

Было:
```kotlin
    private fun ResultRow.toComplaintRow(): ComplaintRow {
        val authorName = runCatching { this[Users.fullName] }.getOrNull()
        return ComplaintRow(
```

Стало:
```kotlin
    private fun ResultRow.toComplaintRow(): ComplaintRow {
        // Запрос жалоб делает LEFT JOIN Users, поэтому is_active доступен.
        // У удалённого (анонимизированного) автора скрываем имя.
        val authorActive = runCatching { this[Users.isActive] }.getOrNull()
        val authorName = if (authorActive == false) {
            "Удалённый пользователь"
        } else {
            runCatching { this[Users.fullName] }.getOrNull()
        }
        return ComplaintRow(
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.complaints.ComplaintVisibilityTest"`
Expected: PASS — все тесты класса зелёные (новый + старые не сломаны).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/example/cleancity/complaints/ComplaintRepository.kt \
        backend/src/test/kotlin/com/example/cleancity/complaints/ComplaintVisibilityTest.kt
git commit -m "feat(backend): скрывать имя автора удалённого аккаунта в жалобах"
```

---

## Task 5: Обновить `docs/SPEC.md`

**Files:**
- Modify: `docs/SPEC.md` (§4.1, таблица Auth)

- [ ] **Step 1: Добавить строку эндпоинта в таблицу §4.1**

В `docs/SPEC.md`, в §4.1 (`### 4.1 Auth ...`), найти строку с `DELETE` | `/auth/sessions` (отзыв всех сессий) и сразу после неё добавить новую строку:

```markdown
| `DELETE` | `/auth/me` | Удаление собственного аккаунта (152-ФЗ). Только `RESIDENT`. Soft-delete + анонимизация: `is_active=false`, `email='deleted_<id>@cleancity.local'`, `password_hash=''`, `full_name=NULL`. Отзывает все refresh-токены. Жалобы остаются, автор скрыт («Удалённый пользователь»). 204 при успехе. Для не-резидентов — 403. |
```

- [ ] **Step 2: Commit**

```bash
git add docs/SPEC.md
git commit -m "docs: эндпоинт DELETE /auth/me в SPEC §4.1"
```

---

## Task 6: Mobile — клиентский метод `deleteAccount`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/AuthApi.kt`
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeAuthApi.kt`

- [ ] **Step 1: Добавить `deleteAccount` в контракт и реализацию `AuthApi`**

В `AuthApi.kt` добавить импорт рядом с другими `io.ktor.client.request.*`:

```kotlin
import io.ktor.client.request.delete
```

В `interface AuthApiContract` добавить метод (например, после `logout`):

```kotlin
    suspend fun deleteAccount()
```

В `class AuthApi` добавить реализацию (например, после `logout`):

```kotlin
    override suspend fun deleteAccount() {
        client.delete("/auth/me")
    }
```

- [ ] **Step 2: Обновить `FakeAuthApi`, чтобы он реализовывал новый метод**

В `FakeAuthApi.kt` добавить в конструктор класса новый параметр (последним):

```kotlin
class FakeAuthApi(
    var registerResult: Result<UserResponse>? = null,
    var verifyResult: Result<AuthResponse>? = null,
    var loginResult: Result<LoginResponse>? = null,
    var refreshResult: Result<AuthResponse>? = null,
    var deleteAccountResult: Result<Unit> = Result.success(Unit),
) {
```

Добавить счётчик вызовов рядом с другими (`logoutCalls` и т.п.):

```kotlin
    var deleteAccountCalls = 0
```

В объекте `asAuthApi()` добавить реализацию метода (например, после `logout`):

```kotlin
        override suspend fun deleteAccount() {
            deleteAccountCalls++
            deleteAccountResult.getOrThrow()
        }
```

- [ ] **Step 3: Скомпилировать common-тесты**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — `FakeAuthApi` снова полностью реализует `AuthApiContract`, существующие тесты зелёные.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/AuthApi.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/FakeAuthApi.kt
git commit -m "feat(mobile): клиентский метод AuthApi.deleteAccount"
```

---

## Task 7: Mobile — логика удаления в репозитории и ViewModel

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/repository/AuthRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreenModel.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/profile/FakeProfileComplaintsApi.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreenModelTest.kt` (создать)

- [ ] **Step 1: Создать фейк ComplaintsApi для теста профиля**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/profile/FakeProfileComplaintsApi.kt`:

```kotlin
package com.example.cleancity.ui.feature.profile

import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.domain.photo.PhotoBytes
import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.DuplicateCandidatesResponse
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.VoteResponse
import com.example.cleancity.shared.requests.CreateComplaintRequest

/** Заглушка: ProfileScreenModelTest проверяет только deleteAccount, load() не вызывается. */
class FakeProfileComplaintsApi : ComplaintsApiContract {
    override suspend fun mine(page: Int, size: Int): ComplaintListResponse = error("not used")
    override suspend fun voted(page: Int, size: Int): ComplaintListResponse = error("not used")
    override suspend fun getMapMarkers(
        swLat: Double, swLon: Double, neLat: Double, neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse = error("not used")
    override suspend fun list(
        page: Int, size: Int, sort: String,
        category: ProblemCategory?, district: String?,
    ): ComplaintListResponse = error("not used")
    override suspend fun getById(id: Long): ComplaintResponse = error("not used")
    override suspend fun vote(id: Long): VoteResponse = error("not used")
    override suspend fun unvote(id: Long): VoteResponse = error("not used")
    override suspend fun findDuplicates(
        latitude: Double, longitude: Double, category: ProblemCategory, radiusMeters: Int?,
    ): DuplicateCandidatesResponse = error("not used")
    override suspend fun create(
        request: CreateComplaintRequest, photos: List<PhotoBytes>,
    ): ComplaintResponse = error("not used")
}
```

> Проверка: сверь список методов с актуальным `ComplaintsApiContract` — если сигнатуры разойдутся, компилятор укажет на недостающие/лишние методы; приведи фейк в соответствие.

- [ ] **Step 2: Написать падающий тест `ProfileScreenModelTest`**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreenModelTest.kt`:

```kotlin
package com.example.cleancity.ui.feature.profile

import com.example.cleancity.data.network.ApiError
import com.example.cleancity.data.network.ApiException
import com.example.cleancity.data.network.FakeAuthApi
import com.example.cleancity.data.network.FakeUserApi
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.FakeTokenStorage
import com.example.cleancity.domain.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProfileScreenModelTest {

    @BeforeTest fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    private fun buildModel(authApi: FakeAuthApi): Pair<ProfileScreenModel, AuthRepository> {
        val repo = AuthRepository(authApi.asAuthApi(), FakeUserApi().asUserApi(), FakeTokenStorage())
        return ProfileScreenModel(FakeProfileComplaintsApi(), repo) to repo
    }

    @Test fun `deleteAccount success switches auth state to Anonymous`() = runTest {
        val authApi = FakeAuthApi(deleteAccountResult = Result.success(Unit))
        val (model, repo) = buildModel(authApi)

        model.deleteAccount()
        testScheduler.advanceUntilIdle()

        assertEquals(1, authApi.deleteAccountCalls)
        assertEquals(AuthState.Anonymous, repo.state.value)
    }

    @Test fun `deleteAccount failure surfaces error state`() = runTest {
        val authApi = FakeAuthApi(
            deleteAccountResult = Result.failure(ApiException(ApiError("SERVER", "boom"), 500)),
        )
        val (model, _) = buildModel(authApi)

        model.deleteAccount()
        testScheduler.advanceUntilIdle()

        assertIs<DeleteAccountState.Error>(model.deleteState.value)
    }
}
```

> Проверка: `ProfileScreenModel` конструируется как `ProfileScreenModel(complaintsApi, authRepo)` — сверь порядок аргументов с актуальным конструктором в `ProfileScreenModel.kt` и при расхождении поправь вызов `buildModel`.

- [ ] **Step 3: Запустить тест — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ProfileScreenModelTest"`
Expected: FAIL — компиляция падает: `model.deleteAccount()`, `model.deleteState`, `DeleteAccountState` ещё не существуют.

- [ ] **Step 4: Добавить `deleteAccount` в `AuthRepository`**

В `AuthRepository.kt` добавить метод сразу после `logout()`:

```kotlin
    /**
     * Удаление аккаунта (152-ФЗ). При успехе локально завершает сессию так же,
     * как logout. При ошибке сессия НЕ трогается — аккаунт всё ещё существует.
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        authApi.deleteAccount()
        storage.clear()
        tokenInvalidator.invalidate()
        _state.value = AuthState.Anonymous
    }
```

- [ ] **Step 5: Добавить состояние и метод удаления в `ProfileScreenModel`**

В `ProfileScreenModel.kt` добавить новый sealed-интерфейс на уровне файла (рядом с `ProfileState`):

```kotlin
sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState
    data object InProgress : DeleteAccountState
    data class Error(val message: String) : DeleteAccountState
}
```

Внутри класса `ProfileScreenModel` добавить поле состояния (рядом с `_state`):

```kotlin
    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()
```

И два метода (например, после `logout()`):

```kotlin
    fun deleteAccount() {
        if (_deleteState.value == DeleteAccountState.InProgress) return
        _deleteState.value = DeleteAccountState.InProgress
        screenModelScope.launch {
            authRepo.deleteAccount().onFailure {
                _deleteState.value = DeleteAccountState.Error(
                    it.message ?: "Не удалось удалить профиль"
                )
            }
            // При успехе AuthState → Anonymous, App.kt пересоберёт root и снимет
            // этот экран — отдельное Success-состояние не нужно.
        }
    }

    fun dismissDeleteError() {
        _deleteState.value = DeleteAccountState.Idle
    }
```

(`MutableStateFlow`, `StateFlow`, `asStateFlow`, `screenModelScope`, `launch` уже импортированы в `ProfileScreenModel.kt`.)

- [ ] **Step 6: Запустить тест — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "*ProfileScreenModelTest"`
Expected: PASS — оба теста зелёные.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/repository/AuthRepository.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/profile/FakeProfileComplaintsApi.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreenModelTest.kt
git commit -m "feat(mobile): логика удаления аккаунта в AuthRepository и ProfileScreenModel"
```

---

## Task 8: Mobile — UI удаления аккаунта в профиле

UI без автотеста (Compose UI-тесты в проекте не настроены) — проверка через компиляцию и ручной smoke.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt`

- [ ] **Step 1: Добавить импорты в `ProfileScreen.kt`**

Добавить недостающие импорты (рядом с существующими `androidx.compose.material3.*` и `androidx.compose.runtime.*`):

```kotlin
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

- [ ] **Step 2: Добавить состояние диалога и реакцию на `deleteState` в `Content()`**

В `ProfileScreen.Content()`, после строки `val scope = rememberCoroutineScope()`, добавить:

```kotlin
        val deleteState by model.deleteState.collectAsState()
        var showDeleteDialog by remember { mutableStateOf(false) }

        LaunchedEffect(deleteState) {
            val s = deleteState
            if (s is DeleteAccountState.Error) {
                snackbarHost.showSnackbar(s.message)
                model.dismissDeleteError()
            }
        }
```

- [ ] **Step 3: Прокинуть `onDeleteAccountClick` в `LoadedView` из `Content()`**

В вызове `is ProfileState.Loaded -> LoadedView(`, добавить аргумент (после `onLogoutClick`):

```kotlin
                    onDeleteAccountClick = { showDeleteDialog = true },
```

- [ ] **Step 4: Добавить диалог подтверждения и оверлей загрузки в `Content()`**

Внутри корневого `Box` в `Content()`, после блока `SnackbarHost(...) { ... }`, добавить:

```kotlin
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Удалить профиль?") },
                    text = {
                        Text(
                            "Действие необратимо. Ваши жалобы останутся в системе, " +
                                "но без указания автора."
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                model.deleteAccount()
                            }
                        ) { Text("Удалить", color = Red) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") }
                    },
                )
            }
            if (deleteState is DeleteAccountState.InProgress) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
```

- [ ] **Step 5: Добавить параметр `onDeleteAccountClick` в `LoadedView`**

В сигнатуру `private fun LoadedView(` добавить параметр (последним, после `onLogoutClick`):

```kotlin
    onDeleteAccountClick: () -> Unit,
```

В теле `LoadedView`, в вызове `ProfileMenu(`, добавить проброс (после `onLogoutClick`):

```kotlin
            onDeleteAccountClick = onDeleteAccountClick,
```

- [ ] **Step 6: Добавить параметр и пункт меню в `ProfileMenu`**

В сигнатуру `private fun ProfileMenu(` добавить параметр (последним, после `onLogoutClick`):

```kotlin
    onDeleteAccountClick: () -> Unit,
```

В теле `ProfileMenu`, сразу после `MenuItemRow(... "Выйти" ...)`, добавить новый пункт:

```kotlin
        MenuItemRow(
            icon = Icons.Default.DeleteForever,
            label = "Удалить профиль",
            tint = Red,
            iconBg = RedLight,
            onClick = onDeleteAccountClick,
        )
```

- [ ] **Step 7: Скомпилировать и прогнать common-тесты**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — компиляция проходит, все тесты зелёные.

- [ ] **Step 8: Ручной smoke на эмуляторе/устройстве**

Запустить приложение, войти жителем, открыть «Профиль»:
- Пункт «Мои жалобы» отсутствует; есть красный пункт «Удалить профиль».
- Тап → диалог «Удалить профиль?»; «Отмена» закрывает без действий.
- Тап «Удалить» → кратко спиннер → приложение на гостевом экране.
- Повторный вход под старым email невозможен (неверные учётные данные).

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/profile/ProfileScreen.kt
git commit -m "feat(mobile): удаление профиля из экрана профиля (152-ФЗ)"
```

---

## Финальная проверка

- [ ] **Полный прогон тестов**

Run: `./gradlew :backend:test :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL — все тесты обоих модулей зелёные.

- [ ] **Сверка с критериями готовности спека** (`docs/superpowers/specs/2026-05-21-account-deletion-design.md`):
  1. В профиле нет «Мои жалобы»; файлы `mycomplaints/*` удалены; проект собирается.
  2. Житель удаляет аккаунт через диалог; после — гостевой экран; повторный вход невозможен.
  3. Жалоба удалённого пользователя показывает автора «Удалённый пользователь».
  4. `DELETE /auth/me` → 403 для сотрудников.
  5. Все тесты зелёные.

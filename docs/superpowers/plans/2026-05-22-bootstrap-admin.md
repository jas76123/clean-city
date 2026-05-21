# Bootstrap admin — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** При старте backend автоматически создавать первого администратора из `INITIAL_ADMIN_EMAIL`/`INITIAL_ADMIN_PASSWORD`, если в системе ещё нет ни одного админа.

**Architecture:** Правила пароля выносятся из приватного `AuthService.validatePassword` в общий `object PasswordPolicy`. Новая функция `bootstrapInitialAdmin` (пакет `auth/`) вызывается из `Application.module()` после `configureDatabase()`: проверяет конфиг и `UserRepository.hasAnyAdmin()`, в PROD валидирует пароль (fail-fast), создаёт ADMIN.

**Tech Stack:** Kotlin, Ktor, Exposed, Postgres (H2 in-memory в тестах), bcrypt (`PasswordHasher`), kotlin.test.

**Базовый каталог:** все пути — относительно `~/Desktop/Myapp/cleancity-kmp/`.

**Спецификация:** `docs/superpowers/specs/2026-05-22-bootstrap-admin-design.md`

---

## Справка по существующему коду (проверено)

- `backend/.../auth/AuthService.kt`:
  - строки 13-14: `private const val MIN_PASSWORD_LENGTH_RESIDENT = 8` и `MIN_PASSWORD_LENGTH_ADMIN = 12` — используются ТОЛЬКО в `validatePassword`.
  - строка 28: `class WeakPasswordException(msg: String) : IllegalArgumentException(msg)` — пакет `com.example.cleancity.auth`.
  - приватный `validatePassword(password, role)` (~строка 379).
- `UserRepository.create(email, passwordHash, role, fullName, isActive, emailVerified, mustChangePassword, acceptedTermsVersion)` — все параметры кроме первых двух с дефолтами. `email` лочкейзится внутри. Возвращает `UserRow`.
- `UserRow` поля: `id, email, passwordHash, role: UserRole, fullName, emailVerified, isActive, ...`.
- `UserRole` (enum): `RESIDENT, OPERATOR, INSPECTOR, ADMIN`.
- `PasswordHasher.hash(plain): String` — bcrypt, объект.
- Тесты — H2 in-memory, паттерн в `UserRepositoryTest.kt`: `Database.connect("jdbc:h2:mem:...;MODE=PostgreSQL")` + `SchemaUtils.create(Users)`.
- Backend-тесты: `./gradlew :backend:test`. Один класс: `./gradlew :backend:test --tests "<FQCN>"`.
- `Application.module()` начинается с `configureDatabase()`. Хелперы вида `buildStorage`/`buildEmailService` — приватные extension-функции на `Application`.
- `application.conf` — блок `app { stage; base_url; terms_version }`.
- `.env.example` строки 64-66 — блок `# Первый администратор` с устаревшим комментарием «применяется миграцией V7» (миграции с этим нет).

---

## Карта файлов

| Файл | Действие | Ответственность |
|------|----------|-----------------|
| `backend/src/main/kotlin/com/example/cleancity/auth/PasswordPolicy.kt` | создать | правила сложности пароля (общие) |
| `backend/src/test/kotlin/com/example/cleancity/auth/PasswordPolicyTest.kt` | создать | тесты `PasswordPolicy` |
| `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt` | изменить | `validatePassword` делегирует в `PasswordPolicy`, удалить локальные константы |
| `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt` | изменить | новый `hasAnyAdmin()` |
| `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt` | изменить | тесты `hasAnyAdmin()` |
| `backend/src/main/kotlin/com/example/cleancity/auth/AdminBootstrap.kt` | создать | `bootstrapInitialAdmin(...)` |
| `backend/src/test/kotlin/com/example/cleancity/auth/AdminBootstrapTest.kt` | создать | тесты `bootstrapInitialAdmin` |
| `backend/src/main/kotlin/com/example/cleancity/Application.kt` | изменить | хелпер `bootstrapAdmin()` + вызов в `module()` |
| `backend/src/main/resources/application.conf` | изменить | блок `app.initial_admin` |
| `.env.example` | изменить | поправить устаревший комментарий |

---

## Task 1: Вынести PasswordPolicy

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/auth/PasswordPolicy.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/auth/PasswordPolicyTest.kt`
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`

- [ ] **Step 1: Написать падающий тест**

Create `backend/src/test/kotlin/com/example/cleancity/auth/PasswordPolicyTest.kt`:
```kotlin
package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordPolicyTest {

    @Test
    fun `valid admin password passes`() {
        PasswordPolicy.validate("Secret123!xyz", UserRole.ADMIN)
    }

    @Test
    fun `admin password shorter than 12 is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("Ab1!", UserRole.ADMIN)
        }
        assertTrue(ex.message!!.contains("12"))
    }

    @Test
    fun `admin password without digit is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("Abcdefgh!xyz", UserRole.ADMIN)
        }
        assertEquals("Password must contain a digit", ex.message)
    }

    @Test
    fun `admin password without uppercase is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("secret123!xyz", UserRole.ADMIN)
        }
        assertEquals("Password must contain an uppercase letter", ex.message)
    }

    @Test
    fun `admin password without special char is rejected`() {
        val ex = assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("Secret123xyzAB", UserRole.ADMIN)
        }
        assertEquals("Password must contain a special character", ex.message)
    }

    @Test
    fun `resident password of 8 chars passes without char-class rules`() {
        PasswordPolicy.validate("simple12", UserRole.RESIDENT)
    }

    @Test
    fun `resident password shorter than 8 is rejected`() {
        assertFailsWith<WeakPasswordException> {
            PasswordPolicy.validate("short", UserRole.RESIDENT)
        }
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.PasswordPolicyTest"`
Expected: FAIL — компиляция падает, `PasswordPolicy` не существует.

- [ ] **Step 3: Создать PasswordPolicy**

Create `backend/src/main/kotlin/com/example/cleancity/auth/PasswordPolicy.kt`:
```kotlin
package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole

/**
 * Правила сложности пароля. Единый источник для регистрации (AuthService)
 * и bootstrap-админа (AdminBootstrap).
 */
object PasswordPolicy {
    const val MIN_LENGTH_RESIDENT = 8
    const val MIN_LENGTH_ADMIN = 12

    /** Бросает [WeakPasswordException], если пароль не удовлетворяет правилам роли. */
    fun validate(password: String, role: UserRole) {
        val minLen = if (role == UserRole.RESIDENT) MIN_LENGTH_RESIDENT else MIN_LENGTH_ADMIN
        if (password.length < minLen) {
            throw WeakPasswordException("Password must be at least $minLen characters long")
        }
        if (role != UserRole.RESIDENT) {
            if (password.none { it.isDigit() }) {
                throw WeakPasswordException("Password must contain a digit")
            }
            if (password.none { it.isUpperCase() }) {
                throw WeakPasswordException("Password must contain an uppercase letter")
            }
            if (password.none { !it.isLetterOrDigit() }) {
                throw WeakPasswordException("Password must contain a special character")
            }
        }
    }
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.PasswordPolicyTest"`
Expected: PASS, 7 тестов зелёные.

- [ ] **Step 5: Переключить AuthService на PasswordPolicy**

В `backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt`:

1. Удалить две строки с константами (строки 13-14):
```kotlin
private const val MIN_PASSWORD_LENGTH_RESIDENT = 8
private const val MIN_PASSWORD_LENGTH_ADMIN = 12
```

2. Заменить тело приватного метода `validatePassword`. Было:
```kotlin
    private fun validatePassword(password: String, role: UserRole) {
        val minLen = if (role == UserRole.RESIDENT) MIN_PASSWORD_LENGTH_RESIDENT else MIN_PASSWORD_LENGTH_ADMIN
        if (password.length < minLen) {
            throw WeakPasswordException("Password must be at least $minLen characters long")
        }
        if (role != UserRole.RESIDENT) {
            require(password.any { it.isDigit() }) { throw WeakPasswordException("Password must contain a digit") }
            require(password.any { it.isUpperCase() }) { throw WeakPasswordException("Password must contain an uppercase letter") }
            require(password.any { !it.isLetterOrDigit() }) { throw WeakPasswordException("Password must contain a special character") }
        }
    }
```
Стало:
```kotlin
    private fun validatePassword(password: String, role: UserRole) =
        PasswordPolicy.validate(password, role)
```

3. Проверить grep'ом, что `MIN_PASSWORD_LENGTH_RESIDENT` / `MIN_PASSWORD_LENGTH_ADMIN` больше нигде в `AuthService.kt` не используются (ожидается: 0 вхождений после удаления). `PasswordPolicy` — тот же пакет, импорт не нужен.

- [ ] **Step 6: Прогнать весь backend-тест (регрессия)**

Run: `./gradlew :backend:test`
Expected: PASS — все тесты, включая `AuthServiceTest`, `AuthSecurityTest`, `PasswordPolicyTest`. Правила пароля не изменились, поэтому регрессий быть не должно.

- [ ] **Step 7: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add backend/src/main/kotlin/com/example/cleancity/auth/PasswordPolicy.kt \
  backend/src/test/kotlin/com/example/cleancity/auth/PasswordPolicyTest.kt \
  backend/src/main/kotlin/com/example/cleancity/auth/AuthService.kt
git commit -m "refactor(backend): вынести правила пароля в PasswordPolicy"
```

---

## Task 2: UserRepository.hasAnyAdmin

**Files:**
- Modify: `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt`

- [ ] **Step 1: Написать падающие тесты**

В `backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt`:

1. Добавить импорт (рядом с прочими `import kotlin.test.*`):
```kotlin
import kotlin.test.assertTrue
```

2. Добавить два теста внутрь класса `UserRepositoryTest`:
```kotlin
    @Test
    fun `hasAnyAdmin is false on empty db and with only residents`() {
        initDb()
        val repo = UserRepository()
        assertFalse(repo.hasAnyAdmin())
        repo.create(email = "r@cleancity.local", passwordHash = "h", role = UserRole.RESIDENT)
        assertFalse(repo.hasAnyAdmin())
    }

    @Test
    fun `hasAnyAdmin is true when an admin-role user exists`() {
        initDb()
        val repo = UserRepository()
        repo.create(email = "a@cleancity.local", passwordHash = "h", role = UserRole.ADMIN)
        assertTrue(repo.hasAnyAdmin())
    }
```
(`initDb()`, `assertFalse`, `UserRole` уже доступны в этом тест-файле.)

- [ ] **Step 2: Запустить тесты — убедиться, что падают**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.UserRepositoryTest"`
Expected: FAIL — компиляция падает, метод `hasAnyAdmin` не существует.

- [ ] **Step 3: Реализовать hasAnyAdmin**

В `backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt` добавить метод в класс `UserRepository` (например, сразу после `findById`):
```kotlin
    /**
     * true, если в системе есть хотя бы один пользователь с админ-ролью
     * (ADMIN / OPERATOR / INSPECTOR), независимо от is_active.
     */
    fun hasAnyAdmin(): Boolean = transaction {
        !Users.selectAll().where {
            (Users.role eq UserRole.ADMIN.name) or
                (Users.role eq UserRole.OPERATOR.name) or
                (Users.role eq UserRole.INSPECTOR.name)
        }.empty()
    }
```
`eq` уже используется в этом файле (`findByEmail`); `or` доступен в том же scope `where { }`. `Query.empty()` — метод Exposed (делает `LIMIT 1`). Доп. импорты не нужны.

- [ ] **Step 4: Запустить тесты — убедиться, что проходят**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.UserRepositoryTest"`
Expected: PASS — все тесты `UserRepositoryTest`, включая 2 новых.

- [ ] **Step 5: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add backend/src/main/kotlin/com/example/cleancity/auth/UserRepository.kt \
  backend/src/test/kotlin/com/example/cleancity/auth/UserRepositoryTest.kt
git commit -m "feat(backend): UserRepository.hasAnyAdmin"
```

---

## Task 3: AdminBootstrap

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/auth/AdminBootstrap.kt`
- Test: `backend/src/test/kotlin/com/example/cleancity/auth/AdminBootstrapTest.kt`

- [ ] **Step 1: Написать падающий тест**

Create `backend/src/test/kotlin/com/example/cleancity/auth/AdminBootstrapTest.kt`:
```kotlin
package com.example.cleancity.auth

import com.example.cleancity.database.tables.Users
import com.example.cleancity.shared.models.UserRole
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdminBootstrapTest {

    private val log = LoggerFactory.getLogger(AdminBootstrapTest::class.java)

    private fun initDb() {
        Database.connect(
            "jdbc:h2:mem:admin-bootstrap-${System.nanoTime()};DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.drop(Users)
            SchemaUtils.create(Users)
        }
    }

    private fun userCount(): Long = transaction { Users.selectAll().count() }

    @Test
    fun `creates admin on empty db in DEV`() {
        initDb()
        val repo = UserRepository()
        bootstrapInitialAdmin(repo, "boot@cleancity.local", "AnyPass123!ok", "DEV", log)
        val admin = repo.findByEmail("boot@cleancity.local")
        assertNotNull(admin)
        assertEquals(UserRole.ADMIN, admin.role)
        assertTrue(admin.emailVerified)
        assertTrue(admin.isActive)
    }

    @Test
    fun `creates admin in DEV even with weak password`() {
        initDb()
        val repo = UserRepository()
        bootstrapInitialAdmin(repo, "boot@cleancity.local", "weak", "DEV", log)
        assertNotNull(repo.findByEmail("boot@cleancity.local"))
    }

    @Test
    fun `throws on weak password in PROD and creates no user`() {
        initDb()
        val repo = UserRepository()
        assertFailsWith<WeakPasswordException> {
            bootstrapInitialAdmin(repo, "boot@cleancity.local", "weak", "PROD", log)
        }
        assertEquals(0L, userCount())
    }

    @Test
    fun `skips when an admin already exists`() {
        initDb()
        val repo = UserRepository()
        repo.create(email = "existing@cleancity.local", passwordHash = "h", role = UserRole.ADMIN)
        bootstrapInitialAdmin(repo, "boot@cleancity.local", "AnyPass123!ok", "DEV", log)
        assertNull(repo.findByEmail("boot@cleancity.local"))
        assertEquals(1L, userCount())
    }

    @Test
    fun `skips when email or password is blank`() {
        initDb()
        val repo = UserRepository()
        bootstrapInitialAdmin(repo, null, "AnyPass123!ok", "DEV", log)
        bootstrapInitialAdmin(repo, "boot@cleancity.local", null, "DEV", log)
        bootstrapInitialAdmin(repo, "", "", "DEV", log)
        assertEquals(0L, userCount())
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.AdminBootstrapTest"`
Expected: FAIL — компиляция падает, `bootstrapInitialAdmin` не существует.

- [ ] **Step 3: Реализовать AdminBootstrap**

Create `backend/src/main/kotlin/com/example/cleancity/auth/AdminBootstrap.kt`:
```kotlin
package com.example.cleancity.auth

import com.example.cleancity.shared.models.UserRole
import org.slf4j.Logger

/**
 * Создаёт первого администратора из INITIAL_ADMIN_EMAIL/PASSWORD при старте backend,
 * если в системе ещё нет ни одного админа. Идемпотентно — после появления любого
 * админа bootstrap больше не срабатывает.
 *
 * См. docs/superpowers/specs/2026-05-22-bootstrap-admin-design.md
 *
 * @param stage окружение ("DEV" / "PROD"); в PROD пароль валидируется PasswordPolicy (fail-fast).
 */
fun bootstrapInitialAdmin(
    users: UserRepository,
    email: String?,
    password: String?,
    stage: String,
    log: Logger,
) {
    if (email.isNullOrBlank() || password.isNullOrBlank()) {
        log.info("INITIAL_ADMIN не сконфигурирован — bootstrap пропущен")
        return
    }
    if (users.hasAnyAdmin()) {
        log.info("Админ уже существует — bootstrap пропущен")
        return
    }
    if (stage.uppercase() == "PROD") {
        PasswordPolicy.validate(password, UserRole.ADMIN)
    }
    users.create(
        email = email,
        passwordHash = PasswordHasher.hash(password),
        role = UserRole.ADMIN,
        fullName = "Администратор",
        isActive = true,
        emailVerified = true,
        mustChangePassword = false,
    )
    log.info("Bootstrap-админ создан: ${email.lowercase()}")
}
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :backend:test --tests "com.example.cleancity.auth.AdminBootstrapTest"`
Expected: PASS, 5 тестов зелёные.

- [ ] **Step 5: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add backend/src/main/kotlin/com/example/cleancity/auth/AdminBootstrap.kt \
  backend/src/test/kotlin/com/example/cleancity/auth/AdminBootstrapTest.kt
git commit -m "feat(backend): bootstrapInitialAdmin — авто-создание первого админа"
```

---

## Task 4: Подключить bootstrap к старту приложения

**Files:**
- Modify: `backend/src/main/resources/application.conf`
- Modify: `backend/src/main/kotlin/com/example/cleancity/Application.kt`
- Modify: `.env.example`

- [ ] **Step 1: Добавить конфиг initial_admin**

В `backend/src/main/resources/application.conf`, внутри блока `app { ... }`, после строк с `terms_version`, добавить:
```
    initial_admin {
        email = ${?INITIAL_ADMIN_EMAIL}
        password = ${?INITIAL_ADMIN_PASSWORD}
    }
```
(`${?VAR}` — если переменная окружения не задана, ключ просто отсутствует, `propertyOrNull` вернёт `null`.)

- [ ] **Step 2: Добавить хелпер и вызов в Application.kt**

В `backend/src/main/kotlin/com/example/cleancity/Application.kt`:

1. Добавить импорт рядом с другими `import com.example.cleancity.auth.*`:
```kotlin
import com.example.cleancity.auth.bootstrapInitialAdmin
```

2. В функции `Application.module()` — сразу после первой строки `configureDatabase()` добавить вызов:
```kotlin
    configureDatabase()
    bootstrapAdmin()
```

3. Добавить приватный хелпер рядом с `buildStorage` / `buildEmailService` (в конце файла):
```kotlin
private fun Application.bootstrapAdmin() {
    val email = environment.config.propertyOrNull("app.initial_admin.email")?.getString()
    val password = environment.config.propertyOrNull("app.initial_admin.password")?.getString()
    val stage = environment.config.propertyOrNull("app.stage")?.getString()?.uppercase() ?: "DEV"
    bootstrapInitialAdmin(UserRepository(), email, password, stage, environment.log)
}
```
`UserRepository` уже импортирован в `Application.kt`. `environment.log` — SLF4J Logger Ktor-приложения.

- [ ] **Step 3: Поправить устаревший комментарий в .env.example**

В `.env.example` заменить строку-комментарий (строка 64):
```
# ----- Первый администратор (применяется миграцией V7) -------------------
```
на:
```
# ----- Первый администратор -----------------------------------------------
# Создаётся автоматически при старте backend, ЕСЛИ в системе нет ни одного
# админа. В PROD пароль обязан проходить правила (≥12, цифра, заглавная,
# спецсимвол), иначе backend не стартует. В DEV правила не проверяются.
```
Строки `INITIAL_ADMIN_EMAIL=...` и `INITIAL_ADMIN_PASSWORD=...` под комментарием оставить как есть.

- [ ] **Step 4: Собрать backend и прогнать тесты**

Run: `./gradlew :backend:test`
Expected: PASS — компиляция проходит, все тесты зелёные (изменения в `Application.kt`/conf не ломают существующие тесты — они не поднимают полный модуль).

- [ ] **Step 5: Commit**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
git add backend/src/main/resources/application.conf \
  backend/src/main/kotlin/com/example/cleancity/Application.kt .env.example
git commit -m "feat(backend): вызывать bootstrap admin при старте + конфиг initial_admin"
```

---

## Task 5: Ручной чекпоинт

**Files:** нет (проверка).

- [ ] **Step 1: Пересоздать БД и проверить bootstrap**

Текущая dev-БД содержит тест-админа (id 16) → bootstrap пропустится. Чтобы проверить работу на пустой системе:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
docker compose down -v          # снести том БД
docker compose up -d --build    # backend применит миграции + bootstrap
```

- [ ] **Step 2: Проверить лог bootstrap**

Run: `docker compose logs backend | grep -i bootstrap`
Expected: строка `Bootstrap-админ создан: admin@cleancity.local` (значение — из `INITIAL_ADMIN_EMAIL` в `.env`).

- [ ] **Step 3: Проверить логин bootstrap-админом**

Run:
```bash
curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cleancity.local","password":"admin_dev_change_me"}' \
  -w "\nHTTP %{http_code}\n"
```
Expected: HTTP 200, в ответе `"requires2fa":false` и блок `auth` с токенами.
(Пароль — текущее значение `INITIAL_ADMIN_PASSWORD` из `.env`; в DEV слабый пароль допустим.)

- [ ] **Step 4: Проверить идемпотентность**

Run: `docker compose restart backend` затем `docker compose logs backend | grep -i bootstrap | tail -1`
Expected: при повторном старте — `Админ уже существует — bootstrap пропущен`.

---

## Self-review (выполнено при написании плана)

- **Покрытие спеки:** `PasswordPolicy` + вынос → Task 1; `hasAnyAdmin` → Task 2; `bootstrapInitialAdmin` (все 5 веток поведения: нет конфига / есть админ / PROD-валидация / создание / поля) → Task 3 + тесты; подключение в `Application` + `application.conf` + `.env.example` → Task 4; ручной чекпоинт → Task 5. Все решения брейншторма (seed «пустой системы», PROD-only валидация, fail-fast, поля админа) отражены в коде Task 3.
- **Плейсхолдеров нет** — каждый шаг содержит полный код или точную команду.
- **Согласованность типов:** `bootstrapInitialAdmin(users, email, password, stage, log)` — одна сигнатура в Task 3 (определение), Task 3 (тесты), Task 4 (вызов). `hasAnyAdmin(): Boolean` — Task 2 (определение + тесты), Task 3 (вызов внутри `bootstrapInitialAdmin`). `PasswordPolicy.validate(password, role)` — Task 1 (определение), Task 1 (`AuthService` делегат), Task 3 (вызов). `WeakPasswordException` — существующий класс пакета `auth`, переиспользуется без изменений.
- **Зависимости задач линейны:** Task 3 зависит от Task 1 (`PasswordPolicy`) и Task 2 (`hasAnyAdmin`); Task 4 — от Task 3. Порядок 1→2→3→4→5 корректен.

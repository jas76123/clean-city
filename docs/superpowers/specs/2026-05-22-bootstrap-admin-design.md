# Bootstrap admin — дизайн

**Дата:** 2026-05-22
**Контекст:** backend CleanCity (`backend/`), Ktor + Exposed + Postgres.

## Задача

При старте backend автоматически создавать первого администратора из переменных
окружения `INITIAL_ADMIN_EMAIL` / `INITIAL_ADMIN_PASSWORD`, если в системе ещё нет
ни одного админа. Сейчас эти ключи есть в `.env` / `.env.example`, но **ни одна
строчка кода их не использует** — bootstrap-механизма нет, и первого админа
приходится заводить прямым SQL-инсертом.

## Решения брейншторма

- **Условие создания.** Seed «пустой системы»: админ создаётся, только если в
  таблице `users` нет ни одной строки с ролью ADMIN / OPERATOR / INSPECTOR
  (независимо от `is_active`). Как только появился любой админ — bootstrap больше
  не срабатывает никогда.
- **Валидация пароля.** Только в PROD: `INITIAL_ADMIN_PASSWORD` прогоняется через
  правила пароля админа (≥12 символов, цифра, заглавная буква, спецсимвол), слабый
  пароль → fail-fast при старте. В DEV валидации нет — подходит любой непустой
  пароль (текущий `admin_dev_change_me` работает как есть).
- **Размещение логики.** Отдельная функция `bootstrapInitialAdmin` в пакете `auth/`
  (startup-concern, не запросная операция; не раздувает `AuthService`).
- **Целевое улучшение.** Правила пароля выносятся из приватного
  `AuthService.validatePassword` в общий `object PasswordPolicy` — им пользуются и
  `AuthService`, и `AdminBootstrap` (без дублирования).

## Компоненты и файлы

### Новые файлы

- `backend/src/main/kotlin/com/example/cleancity/auth/PasswordPolicy.kt`
  `object PasswordPolicy` с `validate(password: String, role: UserRole)` (бросает
  `WeakPasswordException`) и константами `MIN_PASSWORD_LENGTH_RESIDENT = 8`,
  `MIN_PASSWORD_LENGTH_ADMIN = 12`. Логика переносится 1-в-1 из текущего приватного
  `AuthService.validatePassword`.

- `backend/src/main/kotlin/com/example/cleancity/auth/AdminBootstrap.kt`
  `fun bootstrapInitialAdmin(users: UserRepository, email: String?, password: String?, stage: String, log: org.slf4j.Logger)`.

### Изменяемые файлы

- `AuthService.kt` — приватный `validatePassword` становится тонким делегатом
  `PasswordPolicy.validate(password, role)`; локальные константы длины удаляются.
  Внешнее поведение не меняется.
- `UserRepository.kt` — новый метод `hasAnyAdmin(): Boolean` — есть ли в `users`
  хоть одна строка с ролью в (ADMIN, OPERATOR, INSPECTOR), `is_active` не учитывается.
- `Application.kt` — приватный хелпер `Application.bootstrapAdmin()` (по образцу
  `buildStorage` / `buildEmailService`), вызывается в `module()` после
  `configureDatabase()`. Читает `app.initial_admin.email`,
  `app.initial_admin.password`, `app.stage` и зовёт `bootstrapInitialAdmin(...)`.
- `application.conf` — добавить блок:
  ```
  app {
      ...
      initial_admin {
          email = ${?INITIAL_ADMIN_EMAIL}
          password = ${?INITIAL_ADMIN_PASSWORD}
      }
  }
  ```
- `.env.example` — задокументировать ключи `INITIAL_ADMIN_EMAIL` /
  `INITIAL_ADMIN_PASSWORD` с плейсхолдер-значениями и комментарием.

## Поведение `bootstrapInitialAdmin`

При старте backend, по порядку:

1. **Конфиг не задан.** Если `email` или `password` пустые/`null` →
   `log.info("INITIAL_ADMIN не сконфигурирован — bootstrap пропущен")` → выход.
   Не ошибка.
2. **Система не пустая.** `users.hasAnyAdmin()` вернул `true` →
   `log.info("Админ уже существует — bootstrap пропущен")` → выход.
3. **Валидация пароля (только PROD).** Если `stage == "PROD"` →
   `PasswordPolicy.validate(password, UserRole.ADMIN)`. Слабый пароль →
   `WeakPasswordException` пробрасывается наружу → backend падает со старта
   (fail-fast, как `DatabaseConfig`). В DEV шаг пропускается.
4. **Создание.**
   `users.create(email = email, passwordHash = PasswordHasher.hash(password), role = UserRole.ADMIN, fullName = "Администратор", isActive = true, emailVerified = true, mustChangePassword = false)`
   → `log.info("Bootstrap-админ создан: <email>")`.

**Порядок шагов важен:** валидация пароля идёт после `hasAnyAdmin()` — если админ
уже есть, bootstrap всё равно no-op, и падать на слабом `INITIAL_ADMIN_PASSWORD`,
который не будет использован, не нужно.

### Поля создаваемого админа

| Поле | Значение | Причина |
|------|----------|---------|
| `role` | `ADMIN` | в MVP ADMIN/OPERATOR/INSPECTOR равноправны (`UserRole.kt`) |
| `emailVerified` | `true` | иначе login отдаёт `AUTH_EMAIL_UNVERIFIED` |
| `isActive` | `true` | админ должен сразу логиниться |
| `mustChangePassword` | `false` | флоу принудительной смены фронт не поддерживает; в PROD пароль меняется вручную через Settings |
| `fullName` | `"Администратор"` | топбар web-admin покажет осмысленное имя |
| `totpEnabled` | `false` (дефолт) | 2FA настраивается позже вручную |
| `acceptedTermsVersion` | `null` (дефолт `create`) | как у админов до accept-invite |

## Обработка ошибок

- Нет env → пропуск, info-лог. Не ошибка.
- PROD + слабый пароль → `WeakPasswordException` наружу → startup падает (намеренный
  fail-fast с понятным сообщением).
- Ошибка БД при `create` → пробрасывается, startup падает (БД нерабочая — глушить
  нечего).
- Исключения bootstrap **не оборачиваются** в try-catch: либо намеренный fail-fast,
  либо реальная поломка инфраструктуры.
- **Гонка** двух инстансов: оба теоретически пройдут `hasAnyAdmin()=false` —
  страхует UNIQUE-констрейнт на `users.email` (второй инсерт упадёт). Для диплома
  (один инстанс) не проблема; отдельной блокировки не вводим.

## Тестирование

Юнит-тесты на H2 in-memory — паттерн как в `UserRepositoryTest`
(`Database.connect("jdbc:h2:mem:...;MODE=PostgreSQL")` + `SchemaUtils.create(Users)`).

- **`PasswordPolicyTest`** (чистый юнит, без БД):
  - валидный admin-пароль проходит;
  - каждое нарушение admin-правил (длина / цифра / заглавная / спецсимвол) бросает
    `WeakPasswordException` с ожидаемым сообщением;
  - правила резидента: длина 8, без требований к классам символов.
- **`AdminBootstrapTest`** (H2):
  - пустая БД + валидный конфиг + DEV → админ создан (`role = ADMIN`,
    `emailVerified`, `isActive`);
  - пустая БД + слабый пароль + DEV → админ всё равно создан (в DEV без валидации);
  - пустая БД + слабый пароль + PROD → `WeakPasswordException`, юзер не создан;
  - в БД уже есть админ → no-op (второй юзер не появился);
  - пустой `email` или `password` → no-op.
- **`UserRepositoryTest`** — добавить кейс на `hasAnyAdmin()` (false на пустой БД и
  при наличии только RESIDENT; true при наличии ADMIN-роли).
- **Регрессия:** существующие `AuthServiceTest` / `AuthSecurityTest` остаются
  зелёными после выноса `PasswordPolicy`.

Backend-тесты прогоняются `./gradlew :backend:test` (это отдельный gradle-модуль
`:backend`, не common — таска `composeApp:testDebugUnitTest` к нему не относится).

## Ручной чекпоинт

Текущая dev-БД уже содержит тест-админа (id 16) → bootstrap там пропустится.
Чтобы проверить работу:

1. `docker compose down -v` — снести том БД.
2. `docker compose up -d` — backend применит миграции и выполнит bootstrap.
3. В логах backend — `Bootstrap-админ создан: admin@cleancity.local`.
4. Логин этим админом из web-admin (`:5173`) работает.

## Что НЕ входит (YAGNI)

- Принудительная смена пароля при первом входе bootstrap-админа — флоу
  `must_change_password` фронтом не поддерживается (см. дизайн Day 15).
- Валидация формата email для `INITIAL_ADMIN_EMAIL` — только проверка на непустоту.
- Распределённая блокировка от гонки инстансов — UNIQUE-констрейнта достаточно.
- Аудит-запись о создании bootstrap-админа — у события нет actor'а; ограничиваемся
  app-логом.

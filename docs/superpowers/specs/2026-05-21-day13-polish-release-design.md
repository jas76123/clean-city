# Day 13 — Mobile полировка + release-сборка

**Дата:** 2026-05-21
**Статус:** дизайн утверждён, готов к плану

## Контекст и решения

Day 13 в `docs/PLAN.md` смешивает два разнородных пласта работы:
in-app полировку (designable код) и release/дистрибуцию (ops-шаги, часть из
которых — реальные асинхронные действия: регистрация в RuStore по паспорту/ИНН,
~3 дня модерации). Брейншторм 2026-05-21 разделил их.

Решения брейншторма:

- **Scope спеки:** in-app полировка **+** release-сборка (генерация keystore,
  подписанный release-APK, ProGuard/R8). Подача в RuStore — отдельный ops-чеклист.
- **Loading-состояние:** единый spinner-компонент сейчас; skeleton-плейсхолдеры —
  в backlog как необязательный апгрейд.
- **ProGuard/R8:** `minifyEnabled = true` + `-dontobfuscate`. R8 убирает мёртвый
  код, но не переименовывает классы — читаемые стек-трейсы, serialization и
  рефлексия почти не ломаются. `shrinkResources = false` (минимизируем риск
  скрытых крашей до интеграции и защиты).
- **Хранение keystore:** локально вне репозитория (`~/keys/cleancity/`), пароли в
  `keystore.properties` (в `.gitignore`), резервная копия на USB-флешку. Без
  внешних сервисов.
- **Стратегия публикации:** Day 13 даёт подписанный release-APK для демо/защиты и
  установки на устройства друзей (прямая ссылка/QR). Подача в RuStore переезжает
  в отдельный чеклист — после готовности веб-админки (Day 15+), потому что до неё
  обрабатывать жалобы реальных пользователей некому.
- **Legal:** упоминание FCM/Firebase в `privacy-policy.md` пока **оставляем** —
  FCM планируется в Day 14-буфере, если останется время. Yandex MapKit terms и
  доступность legal-документов проверяем в рамках Day 13.

### Что уже на месте (проверено)

- `configChanges` с `orientation` уже в `AndroidManifest.xml` — баг поворота
  экрана из Day 9 закрыт.
- Раздел объявлений администрации построен: бэкенд (`AnnouncementRoutes.kt`,
  миграция `V7__create_announcements.sql`), `AnnouncementsApi.kt`, отображение в
  `FeedScreen`/`AnnouncementCard`, уведомления в `NotificationCard`. Создание
  объявления требует роль администратора. UI создания — это веб-админка (Day 15+);
  до неё объявления создаются прямым API-вызовом.
- Согласие на обработку ПДн при регистрации есть: `ConsentRow`, `RegisterScreen`
  → `LegalScreen` (WebView на `/legal/privacy` и `/legal/terms`), при регистрации
  отправляется `acceptedTerms`.
- Политика обработки ПДн и Пользовательское соглашение существуют
  (`backend/src/main/resources/legal/`), ссылаются на 152-ФЗ.

## Scope

### 1. Общие state-компоненты (рефакторинг)

Сейчас `FeedScreen`, `NotificationsScreen`, `MyComplaintsScreen` дублируют UI
состояний приватными composable (`LoadingState`/`ErrorState`/`EmptyState`/
`CenteredSpinner`).

- Новый файл `composeApp/src/commonMain/.../ui/components/ScreenState.kt`:
  - `LoadingState(modifier)` — `CircularProgressIndicator` по центру.
  - `ErrorState(message, onRetry, modifier)` — иконка + текст + кнопка «Повторить».
  - `EmptyState(emoji, title, subtitle, action?, modifier)` — эмодзи-иллюстрация
    + заголовок + подзаголовок + опциональная кнопка действия.
- Три экрана мигрируют на общие компоненты, приватные копии удаляются.
- `EmptyFeedRow` с вариантами mode/guest → `EmptyState` с параметрами; Feed
  передаёт нужные title/subtitle.
- Error-тексты: сетевая ошибка → «Нет соединения, попробуйте позже»; прочее →
  «Что-то пошло не так» + «Повторить». Если в `ErrorMapper` уже есть маппинг
  исключений в текст — переиспользовать. Offline-режима нет (Phase 2).

### 2. Единообразные иконки 18 категорий

- `CategoryEmoji.emoji()` остаётся единственным источником (SVG/vector ломаются
  на Android — известное ограничение проекта).
- Аудит 18 эмодзи: заменить слабые (`OTHER` сейчас «…»), убрать визуальные
  дубли при наличии.
- Новый composable `CategoryIcon(category, size)` — эмодзи в едином круглом
  контейнере (фикс-размер, brand-тон фона). Используется везде, где встречаются
  категории: `CategorySheet`, `CategoryFilterChips`, карточки жалоб, экран
  деталей, экран создания. Один компонент → гарантированная консистентность.

### 3. App icon + splash

- Сейчас launcher-иконки нет — приложение на дефолтной Android-иконке (в
  `<application>` нет `android:icon`).
- Из `~/Desktop/Myapp/clean_city_logo_refined (1).svg` сделать adaptive icon:
  `mipmap-anydpi-v26/ic_launcher.xml` (foreground — логотип, background —
  brand-цвет) + density-ресурсы. Прописать `android:icon` и `android:roundIcon`
  в манифест.
- `SplashScreen.kt` (Compose) остаётся. Добавить простую `windowBackground`-тему,
  чтобы убрать белую вспышку при холодном старте.

### 4. ProGuard / R8

- `composeApp/build.gradle.kts`, release buildType: `isMinifyEnabled = true`,
  `proguardFiles(...)`.
- `composeApp/proguard-rules.pro`: `-dontobfuscate` + keep-правила для
  kotlinx-serialization (`@Serializable`-классы), Ktor, Koin, Yandex MapKit,
  Voyager.
- `isShrinkResources = false` (resource shrink — в backlog).

### 5. Release-keystore + подписанный APK

- Сгенерировать `keystore.jks` через `keytool`, хранить в `~/keys/cleancity/`
  (вне репозитория).
- `keystore.properties` в корне репозитория (**добавить в `.gitignore`**) —
  путь к storeFile, пароли store/key, alias.
- `build.gradle.kts` читает `keystore.properties` → `signingConfigs.release`,
  применяется к release buildType. Если файла нет — подпись пропускается
  (сборка у других не падает).
- Резервная копия `keystore.jks` + `keystore.properties` на USB-флешку.
- Сборка: `./gradlew composeApp:assembleRelease` → подписанный APK.
- Проверка: `apksigner verify`, установка на Samsung A33 без warning о debug.
- **Дистрибуция:** прямая ссылка/QR на APK (хостинг — Yandex Object Storage или
  статика бэкенда). QR сохраняется в `docs/marketing/`. Не RuStore.

### 6. Legal / compliance check

- **Yandex MapKit terms.** Сверить с актуальными условиями MapKit API: атрибуция
  Яндекса на карте (рендерится SDK — не скрывать), обязательная ссылка на условия
  использования Яндекса в приложении. Чего не хватает — добавить в `AboutScreen`.
- **Доступность legal-документов.** `LegalWebView` грузит документы с
  `currentApiBase()` — при недоступном бэкенде должен показывать понятную ошибку,
  а не белый экран. Legal-ссылка добавляется в `AboutScreen` (не только при
  регистрации).
- **Консистентность контактов.** `AboutScreen` указывает поддержку
  `a.ja5m@yandex.ru`, а legal-документы — `info@cleancity.ru` / `support@…` /
  `privacy@…`. Привести к единому актуальному контакту.
- **FCM в политике.** Упоминание FCM/Firebase в `privacy-policy.md` (п. 2.1, 5)
  пока оставляем — FCM планируется в Day 14-буфере. ⚠️ Если FCM так и не
  реализуется — политику надо привести в соответствие с реальностью (канал
  доставки — polling) **до** подачи в RuStore.

### 7. Тесты и проверка

- Полный unit-suite зелёный: `./gradlew composeApp:testDebugUnitTest`.
- State-компоненты — без отдельных тестов (чистый UI).
- Ручной happy-path smoke на подписанном release-APK: регистрация → создание
  жалобы → лента/карта/уведомления.

## Вне scope

- **Подача в RuStore** (регистрация разработчика по паспорту/ИНН, загрузка APK +
  скриншоты + описание, модерация) — отдельный ops-чеклист после готовности
  веб-админки.
- Skeleton-плейсхолдеры загрузки — backlog.
- `shrinkResources`, полная обфускация — backlog.
- Offline-режим — Phase 2.
- UI создания объявлений администрацией — веб-админка, Day 15+.

## Follow-ups в PLAN.md

- В Day 14-буфер добавить задачу: «настроить FCM SDK + системные push, если
  останется время; иначе — привести `privacy-policy.md` в соответствие с
  polling-каналом до подачи в RuStore».

## Чеклист готовности (Checkpoint)

- Все списки имеют единообразные empty/loading/error состояния.
- 18 категорий рендерятся через единый `CategoryIcon`.
- У приложения свой adaptive launcher icon, нет белой вспышки на старте.
- Release-APK собирается с R8 (`-dontobfuscate`), подписан release-keystore,
  устанавливается на реальное устройство без debug-предупреждений.
- `keystore.jks` и пароли вне репозитория, есть бэкап на флешке.
- Legal-документы открываются из приложения и доступны из `AboutScreen`;
  при недоступном бэкенде — корректная ошибка.
- Yandex MapKit terms сверены, недостающая атрибуция/ссылка добавлены.
- Полный unit-suite зелёный.

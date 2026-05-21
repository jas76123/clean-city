# Day 12 — Mobile уведомления + мои жалобы (polling-версия)

**Дата:** 2026-05-21
**Статус:** дизайн утверждён, готов к плану

## Контекст и решения

Day 12 в `docs/PLAN.md` изначально включал FCM SDK и системные push-уведомления.
По решению от 2026-05-11 канал доставки push'ей — polling backend API; FCM отложен.
Бейдж непрочитанных на иконке нижней навигации уже работает с Day 10
(`UnreadCountStore`, polling `GET /notifications/unread-count`).

Решения, принятые на брейншторме 2026-05-21:

- **Push в Day 12:** только polling-экраны. FCM SDK и системные push в шторке —
  отдельный мини-этап в Day 14-буфере, если останется время.
- **`VotedComplaintsScreen` не делаем.** Пользователь узнаёт об отклонении
  поддержанной жалобы через уведомление → тап → детали жалобы. Карточка
  «Подтверждено» в профиле остаётся обычным счётчиком (не кликабельна).
- **Тап по `ANNOUNCEMENT`-уведомлению** переключает на вкладку «Лента», где
  объявления показаны секцией (как в `docs/mockups/mobile-mockup-v3.html`).
  Точного перехода в конкретное объявление нет — отдельного экрана объявления
  на мобильном не существует.

## Scope

Делаем:

- `NotificationsScreen` — полная реализация (заменяет заглушку).
- `MyComplaintsScreen` — список `GET /complaints/mine`.
- Блок «Решение администрации» в деталях закрытой жалобы.
- Правка слогана на `RegisterScreen`.
- Unit-тесты новых ScreenModel'ей и методов API.

Не делаем:

- `VotedComplaintsScreen` — выкинут.
- FCM SDK, регистрация push-токена, системные push, foreground/background
  обработка, deeplink из системного пуша — отложено в Day 14-буфер.

## Архитектура

Следуем существующим паттернам проекта: Voyager `Screen` + `ScreenModel`,
Koin DI, Ktor `*Api`-контракты, модели в `shared`. Никаких новых архитектурных
слоёв не вводится.

### 1. Слой данных — `NotificationsApi`

Сейчас `NotificationsApiContract` умеет только `unreadCount()`. Добавляем три
метода. Модели ответов уже существуют в `shared`
(`NotificationListResponse`, `NotificationResponse`, `MarkAllReadResponse`).

| Метод | Запрос |
|-------|--------|
| `list(limit: Int = 50): NotificationListResponse` | `GET /notifications?limit=50` |
| `markRead(id: Long)` | `PATCH /notifications/{id}/read` |
| `markAllRead(): MarkAllReadResponse` | `PATCH /notifications/read-all` |

`ComplaintsApi.mine(page, size)` уже существует — переиспользуется без изменений.

### 2. `NotificationsScreen` + `NotificationsScreenModel`

Заменяет текущую заглушку
`composeApp/.../ui/feature/notifications/NotificationsScreen.kt`.

**Состояния:** `Loading` / `Error(message, retry)` / `Empty` / `Loaded(items)`.

**Модель:**

- `load()` — вызывается при открытии экрана и при возврате приложения в
  foreground.
- `refresh()` — для pull-to-refresh.
- `onItemClick(notification)`:
  1. Оптимистично помечает элемент прочитанным в локальном state.
  2. Декрементит `UnreadCountStore`, чтобы бейдж обновился немедленно.
  3. Вызывает `markRead(id)`; при ошибке сети — откат локального состояния
     и snackbar.
  4. Эмитит navigation intent: `COMPLAINT_STATUS` → детали жалобы;
     `ANNOUNCEMENT` → переключение вкладки.
- `markAllRead()` — оптимистично помечает все прочитанными, обнуляет
  `UnreadCountStore`, вызывает API; при ошибке — откат + snackbar.

**UI:**

- Шапка «Уведомления» + действие «Прочитать все» — видно только когда есть
  непрочитанные.
- `LazyColumn` карточек `NotificationCard`: иконка по `kind`/`iconStyle`,
  заголовок (`title`), текст (`body`), относительное время («N мин назад»),
  маркер непрочитанного (точка + фон-тинт). Визуал — по экрану
  `screen-notifications` в `docs/mockups/mobile-mockup-v3.html`.
- Pull-to-refresh.
- Empty state: «У вас пока нет уведомлений».
- Error view с кнопкой «Повторить».

**Навигация при тапе:**

- `COMPLAINT_STATUS` (есть `complaintId`) → `navigator.push(ComplaintDetailScreen(complaintId))`.
- `ANNOUNCEMENT` (есть `announcementId`) → переключение на вкладку «Лента»
  через `LocalTabNavigator.current` (экран уведомлений находится внутри
  `TabNavigator`, отдельная шина не нужна).

### 3. `MyComplaintsScreen` + `MyComplaintsScreenModel`

- Модель грузит `GET /complaints/mine` (`ComplaintsApi.mine`), пагинация
  load-more при скролле — по паттерну `FeedScreen`.
- Переиспользует `ComplaintCard` из ленты.
- Состояния: `Loading` / `Error(retry)` / `Empty` / `Loaded`.
- Empty state: «Вы пока не создавали жалоб».
- Тап по карточке → `navigator.push(ComplaintDetailScreen(id))`.
- Точка входа: пункт меню «Мои жалобы» в `ProfileScreen`. Текущая заглушка
  `onMyComplaintsClick = { /* shell-level ... Day 13 */ }` заменяется на
  `navigator.push(MyComplaintsScreen())` внутри навигатора `ProfileTab`.

### 4. Блок «Решение администрации» в `ComplaintDetailScreen`

При статусе `REJECTED` или `DUPLICATE` — выделенный callout-блок над
существующим таймлайном `statusHistory`.

- Текст = `comment` последней записи `statusHistory` (терминальный переход).
- Заголовок блока: «Решение администрации».
- Для `DUPLICATE` при наличии `duplicateOfId` — добавить строку
  «Дубликат жалобы #N».
- Существующий таймлайн (`StatusHistoryRow`) остаётся без изменений ниже.

### 5. Правка слогана `RegisterScreen`

`RegisterScreen.kt:42`:

```
AuthSub("За 30 секунд — и вы можете влиять на состояние Сочи.")
```

→

```
AuthSub("За 30 секунд — и вы можете влиять на состояние города.")
```

## Обработка ошибок

- Нет сети при загрузке списка → `Error`-view с «Повторить».
- Оптимистичные действия (`markRead`, `markAllRead`) при ошибке API
  откатываются, показывается snackbar.
- `UnreadCountStore` синхронизируется при каждой отметке прочитанным, чтобы
  бейдж не «отставал» до следующего опроса.

## Тестирование

Unit-тесты по паттерну существующего suite (текущее состояние 82/82 зелёные):

- `NotificationsScreenModel` — `load`, оптимистичная отметка прочитанным,
  `markAllRead`, ошибка сети + откат, navigation intents для обоих `kind`.
- `MyComplaintsScreenModel` — `load`, пагинация, empty, ошибка.
- Новые методы `NotificationsApi` (`list`, `markRead`, `markAllRead`).

Smoke на реальном Samsung A33 5G.

## Чекпоинт

На реальном устройстве: создать жалобу → сменить статус через web/Postman →
уведомление приходит в `NotificationsScreen` с unread-меткой → тап открывает
деталь жалобы и снимает unread, бейдж на навигации уменьшается. Пункт меню
«Мои жалобы» открывает реальный список жалоб пользователя. У закрытой
(`REJECTED`/`DUPLICATE`) жалобы виден блок «Решение администрации».

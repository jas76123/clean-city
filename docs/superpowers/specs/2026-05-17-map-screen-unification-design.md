# Map Screen — Visual Unification (Day 9 polish)

## Цель

Привести экран `MapScreen` к единой стилистике приложения: одна палитра, одна типографика, согласованные пропорции. Все локальные `Color(0xFF…)` константы заменяются на token'ы из `ui/theme`. Особый приоритет — кастомный user-location маркер вместо дефолтной чёрно-оранжевой стрелки Yandex MapKit.

Поведение не меняется: backend-запросы, кластеризация, FAB-обработчики, permission-flow — все как есть.

## Канон

| Источник | Что берём |
|---|---|
| `ui/theme/Color.kt` | Полная палитра (Green-spectrum, Accent, Gray-spectrum, status colors) |
| `ui/theme/Type.kt` | Unbounded для display/title, Golos Text для body/label |
| `docs/mockups/mobile-mockup-v3.html` | Геометрия (FAB 52×52 r16, chip pill, legend r12, padding 10×12) |
| Day 9 commit `438901a` | FAB-группа уже приведена к моку — расширяем тот же подход |

TopAppBar в моке отсутствует, но **остаётся** (по решению Жасмин): перекрашивается в `Green700`/white вместо дефолтного M3 white. Меню «Выйти» сохраняется.

## Pixel scale

Используем 8pt grid Material 3. Допустимые значения spacing/size в dp:
`4, 8, 12, 16, 20, 24, 32, 40, 52, 56, 64, 72`.

Радиусы:
`8 (chip), 12 (legend/sheet card), 16 (FAB), 100% (status dot, user dot, cluster, pill chip)`.

Strokes:
`1, 2, 3` (1 — accuracy circle, 2 — cluster/pin outline, 3 — user dot white ring).

Шрифты — только через `MaterialTheme.typography.*` (никаких прямых `TextStyle`). Цифры в кластере и FAB — `titleLarge` (Unbounded SemiBold 16sp) или `labelMedium` (Golos SemiBold 12sp) в зависимости от контекста.

## Изменения по компонентам

### 1. `MapScreen.kt` — TopAppBar

```kotlin
TopAppBar(
    title = { Text("Чистый Город") },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,         // Green700
        titleContentColor = Color.White,
        actionIconContentColor = Color.White,
    ),
    actions = { /* без изменений */ },
)
```

DropdownMenu и Snackbar — без изменений, наследуют тему.

### 2. `MapFabGroup.kt`

Удалить локальные `ACCENT`, `ACCENT_ON`, `SURFACE`, `ICON_TINT`. Использовать токены:

| Элемент | Token |
|---|---|
| Primary FAB «Сообщить» container | `colorScheme.secondary` (Accent #5DDE8A) |
| Primary FAB content | `colorScheme.onSecondary` (Green900) |
| Secondary FAB container | `colorScheme.surface` (white) |
| Secondary FAB icon + spinner color | `Gray700` (из `theme/Color.kt`) — по моку |
| Размер | 52dp, `RoundedCornerShape(16.dp)` (без изменений) |
| Elevation | `defaultElevation = 6.dp` (без изменений) |
| Spacing внутри Column | `Arrangement.spacedBy(12.dp)` (без изменений) |
| Padding группы | start 16dp, end 8dp, top/bottom 16dp (без изменений) |

### 3. `MapLegend.kt`

Удалить локальные `AMBER`, `BLUE`, `GREEN`. Использовать токены из `theme/Color.kt`:
`Amber`, `Blue`, `AccentDark`. Точки 10dp `CircleShape`. Текст `labelMedium`.
Карточка `surface` фон, `RoundedCornerShape(12.dp)`, `shadowElevation = 4.dp`, padding 12×10.

### 4. `CategoryFilterChips.kt`

Удалить локальные `CHIP_SELECTED_BG`, `CHIP_SELECTED_FG`. Использовать:
- `selectedContainerColor = Green700` (из `theme/Color.kt`)
- `selectedLabelColor = Color.White`
- `selectedTrailingIconColor = Color.White`

Inactive chip-цвета берёт `FilterChipDefaults` (наследуют тему — серый текст, прозрачный фон, тонкая обводка).

### 5. `YandexMapHost.android.kt` — placemark colors

`statusColor()` переписать через токены — импорт `com.example.cleancity.ui.theme.*`:

```kotlin
private fun statusColor(status: ComplaintStatus): Int = when (status) {
    ComplaintStatus.NEW         -> Amber.toArgb()
    ComplaintStatus.IN_PROGRESS -> Blue.toArgb()
    ComplaintStatus.RESOLVED    -> AccentDark.toArgb()
    ComplaintStatus.REJECTED,
    ComplaintStatus.DUPLICATE   -> Gray400.toArgb()
}
```

Геометрия `createPinBitmap` не меняется (трапеция уже починена в `3efdc3d`). Размер pin 56×72px остаётся.

### 6. `YandexMapHost.android.kt` — cluster

`createClusterBitmap` переписать:
- Размер 64dp (≈192px при density 3, было 80px фикс) — пересчёт через density передаваемый в функцию.
- Фон `Color.White.toArgb()`
- Обводка `Green700.toArgb()`, ширина 2dp в пикселях
- Текст `Green900.toArgb()`, шрифт Unbounded (через `Typeface`-резолв из ресурса), size ≈ sizePx * 0.34, SemiBold

Поскольку native Canvas требует px и Typeface, добавляем параметр `density: Float` и `Typeface` (грузим один раз через `ResourcesCompat.getFont` или передаём из Composable).

### 7. Новый файл `UserLocationDecorator.android.kt` — главное

Реализует `UserLocationObjectListener`. Один экземпляр на MapView, регистрируется на UserLocationLayer.

```kotlin
class UserLocationDecorator(
    private val context: Context,
    private val accentArgb: Int,        // Accent #5DDE8A
) : UserLocationObjectListener {

    private val handler = Handler(Looper.getMainLooper())
    private var pinView: UserLocationView? = null
    private val dotBitmap = createDotBitmap(accentArgb, dpToPx(16f))
    private var ringScale = 1.0f
    private var ringTickStartMs = 0L

    override fun onObjectAdded(view: UserLocationView) {
        pinView = view
        view.arrow.setIcon(transparent1x1())                  // прячем стрелку heading
        view.accuracyCircle.fillColor = withAlpha(accentArgb, 0.10f)
        view.accuracyCircle.strokeColor = withAlpha(accentArgb, 0.40f)
        view.accuracyCircle.strokeWidth = 1f

        view.pin.useCompositeIcon().apply {
            setIcon("ring", ringBitmap(accentArgb, dpToPx(32f)), IconStyle())
            setIcon("dot",  dotBitmap, IconStyle())
        }
        startPulse()
    }

    override fun onObjectRemoved(view: UserLocationView) {
        stopPulse()
        pinView = null
    }

    override fun onObjectUpdated(view: UserLocationView, event: ObjectEvent) { /* no-op */ }

    private fun startPulse() {
        ringTickStartMs = System.currentTimeMillis()
        handler.post(pulseTick)
    }
    private fun stopPulse() { handler.removeCallbacks(pulseTick) }

    private val pulseTick = object : Runnable {
        override fun run() {
            val v = pinView ?: return
            val t = ((System.currentTimeMillis() - ringTickStartMs) % 1200L) / 1200f
            val scale = 1.0f + 0.6f * t       // 1.0 → 1.6
            val alpha = (1f - t) * 0.6f       // 0.6 → 0
            v.pin.useCompositeIcon().setIconStyle(
                "ring",
                IconStyle().setScale(scale).setVisible(alpha > 0.02f),
            )
            handler.postDelayed(this, 80L)
        }
    }
}
```

(Псевдокод — финальные API-имена/scale-управление уточняются в плане; ringScale через IconStyle.scale, opacity если не доступен напрямую — через перегенерацию ring bitmap каждый тик с нужной альфой.)

Регистрация в `YandexMapHost.android.kt` сразу после `createUserLocationLayer`:

```kotlin
val layer = MapKitFactory.getInstance().createUserLocationLayer(view.mapWindow).apply {
    isVisible = true
    setObjectListener(UserLocationDecorator(ctx, Accent.toArgb()))
}
```

**Fallback при сложностях с API пульсации в MapKit 4.25:** статичный `dotBitmap + ringBitmap` без таймера. Поведение и визуал сохраняются, теряется только анимация.

## Что НЕ трогаем

- `LocationProvider.android.kt` — поведение FAB
- `MapScreenModel.kt` — state machine
- `CategorySheet.kt`, `MarkerPreviewSheet.kt` — отдельные компоненты
- Backend, навигация, permission-flow

## Risks / open questions

- **Compose Resources Font → Android Typeface**: для рисования кластера в Canvas нужен `android.graphics.Typeface`. Compose Resource `Res.font.unbounded_semibold` не даёт Typeface напрямую. Решение: добавить копию шрифтов в `composeApp/src/androidMain/res/font/`, грузить через `ResourcesCompat.getFont`. Дублирование 6 файлов шрифтов — приемлемая цена.
- **MapKit pulse animation**: если `IconStyle.setScale` не вызывает перерисовку без `setIcon`, переключаем на перегенерацию bitmap каждый тик (дороже, но рабоче).
- **TopAppBar Green700 + DropdownMenu**: меню рисуется на `surface` (white) и должно остаться читаемым с белым контейнером. Проверить контраст текста меню (Gray900 на white — ok).

## Acceptance criteria

1. На эмуляторе виден экран карты с зелёным TopAppBar и белым заголовком.
2. FAB «прицел» — белый с серо-зелёной иконкой, FAB «Сообщить» — Accent-зелёный с тёмным «+».
3. Метки жалоб используют цвета `Amber/Blue/AccentDark/Gray400` (визуально близко к текущему, RESOLVED заметно теплее).
4. Кластеры — белые с зелёной 2dp обводкой и тёмно-зелёной цифрой шрифтом Unbounded.
5. User-location: зелёная точка с белой обводкой и пульсирующим ring'ом (или статичным в fallback). Дефолтная Yandex-стрелка не видна.
6. В коде `MapScreen.kt`, `MapFabGroup.kt`, `MapLegend.kt`, `CategoryFilterChips.kt`, `YandexMapHost.android.kt` нет ни одной локальной `Color(0xFF…)` константы — всё через `theme/Color.kt` или `MaterialTheme.colorScheme`.
7. `:composeApp:testDebugUnitTest` зелёные (38/38), приложение не падает при cold start.

## Out of scope

- Search bar (по моку есть — будет в Day 11 вместе с CreateComplaint).
- Bottom navigation (по моку есть — будет в Day 10 с FeedScreen).
- Анимация переходов между screens.
- iOS-side actual (там карта-заглушка).

# Map Screen Visual Unification — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Привести экран `MapScreen` к единому design system (палитра + типографика + пропорции из `ui/theme` и мокапа `mobile-mockup-v3.html`), заменить дефолтный Yandex user-location marker на кастомный «зелёная точка + пульс».

**Architecture:** Точечный рефакторинг 5 существующих файлов экрана + 1 новый Android-only декоратор для `UserLocationLayer`. Без миграций, без новых модулей, без изменений поведения. Каждая задача — самостоятельный коммит.

**Tech Stack:** Kotlin Multiplatform / Compose Multiplatform / Material 3 / Yandex MapKit Mobile SDK 4.25 / Android Canvas.

**TDD note:** Юнит-тесты для Canvas-рендеринга и MapKit-интеграции в проекте отсутствуют и не добавляются — рефакторинг визуальный, тестируется визуально на AVD (см. Task 9). Существующий `MapScreenModelTest` (38 кейсов) обязан остаться зелёным после каждой задачи.

**Spec:** `docs/superpowers/specs/2026-05-17-map-screen-unification-design.md`

---

## Task 1: TopAppBar → Green700

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreen.kt`

- [ ] **Step 1: Добавить импорты в MapScreen.kt**

Найти блок импортов и добавить:

```kotlin
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
```

(`Color` уже может быть импортирован транзитивно — проверить, не дублировать.)

- [ ] **Step 2: Заменить TopAppBar — добавить colors=**

Текущий код в `MapScreen.kt` (около строки 66-80) заменить с добавлением `colors`:

```kotlin
TopAppBar(
    title = { Text("Чистый Город") },
    colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primary,
        titleContentColor = Color.White,
        actionIconContentColor = Color.White,
    ),
    actions = {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Выйти") },
                onClick = { menuOpen = false; onLogout() },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
            )
        }
    },
)
```

Добавить импорт `androidx.compose.material3.MaterialTheme`, если ещё нет.

- [ ] **Step 3: Скомпилировать**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Прогнать существующие unit-тесты**

```bash
./gradlew :composeApp:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, 38 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreen.kt
git commit -m "$(cat <<'EOF'
style(map): repaint TopAppBar to Green700/white

Aligns the map top bar with the app's green brand palette.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: MapFabGroup → theme tokens, секондари FAB → Gray700 icon

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MapFabGroup.kt`

- [ ] **Step 1: Заменить файл целиком**

Полный новый текст:

```kotlin
package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.Gray700

@Composable
fun MapFabGroup(
    onLocationClick: () -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocating: Boolean = false,
) {
    Column(
        modifier = modifier.padding(
            PaddingValues(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Box(modifier = Modifier.align(Alignment.End)) {
            FloatingActionButton(
                onClick = onLocationClick,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Gray700,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(52.dp),
            ) {
                if (isLocating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Gray700,
                    )
                } else {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Моё местоположение",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreateClick,
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            shape = RoundedCornerShape(16.dp),
            text = { Text("Сообщить") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }
}
```

Локальных `Color(0xFF…)` в файле больше нет.

- [ ] **Step 2: Скомпилировать и тесты**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, 38 tests passed.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MapFabGroup.kt
git commit -m "$(cat <<'EOF'
refactor(map): route MapFabGroup colors through theme tokens

Drops local ACCENT/ACCENT_ON/SURFACE/ICON_TINT constants; uses
colorScheme.secondary/onSecondary for the primary FAB and surface +
Gray700 for the location FAB (per mockup), so one palette change in
theme/Color.kt now flows everywhere.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: MapLegend → theme tokens

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MapLegend.kt`

- [ ] **Step 1: Заменить файл целиком**

```kotlin
package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.theme.AccentDark
import com.example.cleancity.ui.theme.Amber
import com.example.cleancity.ui.theme.Blue

@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LegendRow(color = Amber, label = "В обработке")
            LegendRow(color = Blue, label = "В работе")
            LegendRow(color = AccentDark, label = "Решено")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color = color, shape = CircleShape),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
```

- [ ] **Step 2: Скомпилировать и тесты**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, 38 tests passed.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MapLegend.kt
git commit -m "$(cat <<'EOF'
refactor(map): route MapLegend dots through theme tokens

Drops local AMBER/BLUE/GREEN; legend dots now share the same Amber /
Blue / AccentDark values as the placemarks (Task 5 enforces that).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: CategoryFilterChips → theme tokens

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategoryFilterChips.kt`

- [ ] **Step 1: Удалить локальные константы**

В верху файла после импортов удалить:

```kotlin
private val CHIP_SELECTED_BG = Color(0xFF1F5233)
private val CHIP_SELECTED_FG = Color(0xFFFFFFFF)
```

- [ ] **Step 2: Добавить импорты**

```kotlin
import androidx.compose.ui.graphics.Color
import com.example.cleancity.ui.theme.Green700
```

(Color уже импортирован — удалить вместе с константами в Step 1 и заново импортировать в Step 2 нельзя; убедиться, что импорт `Color` присутствует ровно один раз.)

- [ ] **Step 3: Заменить тело `chipColors()`**

Текущее:

```kotlin
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = CHIP_SELECTED_BG,
    selectedLabelColor = CHIP_SELECTED_FG,
    selectedTrailingIconColor = CHIP_SELECTED_FG,
)
```

Заменить на:

```kotlin
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Green700,
    selectedLabelColor = Color.White,
    selectedTrailingIconColor = Color.White,
)
```

- [ ] **Step 4: Скомпилировать и тесты**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, 38 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategoryFilterChips.kt
git commit -m "$(cat <<'EOF'
refactor(map): route CategoryFilterChips colors through theme tokens

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Placemark status colors → theme tokens

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt`

- [ ] **Step 1: Добавить импорты**

В блок импортов добавить:

```kotlin
import androidx.compose.ui.graphics.toArgb
import com.example.cleancity.ui.theme.AccentDark
import com.example.cleancity.ui.theme.Amber
import com.example.cleancity.ui.theme.Blue
import com.example.cleancity.ui.theme.Gray400
```

- [ ] **Step 2: Заменить функцию `statusColor`**

Найти (около строки 167-172):

```kotlin
private fun statusColor(status: ComplaintStatus): Int = when (status) {
    ComplaintStatus.NEW -> 0xFFF59E0B.toInt()
    ComplaintStatus.IN_PROGRESS -> 0xFF3B82F6.toInt()
    ComplaintStatus.RESOLVED -> 0xFF10B981.toInt()
    ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> 0xFF9CA3AF.toInt()
}
```

Заменить на:

```kotlin
private fun statusColor(status: ComplaintStatus): Int = when (status) {
    ComplaintStatus.NEW -> Amber.toArgb()
    ComplaintStatus.IN_PROGRESS -> Blue.toArgb()
    ComplaintStatus.RESOLVED -> AccentDark.toArgb()
    ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> Gray400.toArgb()
}
```

- [ ] **Step 3: Скомпилировать и тесты**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, 38 tests passed.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt
git commit -m "$(cat <<'EOF'
refactor(map): route placemark status colors through theme tokens

NEW=Amber, IN_PROGRESS=Blue, RESOLVED=AccentDark, REJECTED/DUPLICATE=
Gray400 — same tokens MapLegend uses, so the dots and pins can never
drift apart again. RESOLVED becomes a touch warmer (#3AB868 vs prior
#10B981) — intentional, matches mockup.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Скопировать Unbounded SemiBold в androidMain/res/font

**Files:**
- Create: `composeApp/src/androidMain/res/font/unbounded_semibold.ttf`

- [ ] **Step 1: Создать директорию и скопировать шрифт**

```bash
mkdir -p ~/Desktop/Myapp/cleancity-kmp/composeApp/src/androidMain/res/font
cp ~/Desktop/Myapp/cleancity-kmp/composeApp/src/commonMain/composeResources/font/unbounded_semibold.ttf \
   ~/Desktop/Myapp/cleancity-kmp/composeApp/src/androidMain/res/font/unbounded_semibold.ttf
ls -la ~/Desktop/Myapp/cleancity-kmp/composeApp/src/androidMain/res/font/
```

Expected: один файл `unbounded_semibold.ttf` ~30-100kb.

- [ ] **Step 2: Скомпилировать (проверка, что resource подцепился)**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
./gradlew :composeApp:processDebugResources --console=plain 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. В `composeApp/build/generated/source/resValues/.../R.java` (или Kotlin-эквивалент) появится `R.font.unbounded_semibold`.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/res/font/unbounded_semibold.ttf
git commit -m "$(cat <<'EOF'
chore(android): bundle Unbounded SemiBold as Android font resource

Needed for native Canvas drawing in the map cluster bitmap (Task 7).
Compose Resources expose fonts only for Compose Text; ResourcesCompat
requires res/font/. Duplicates one TTF — acceptable.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Cluster bitmap — токены + Unbounded шрифт + 64dp размер

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt`

- [ ] **Step 1: Добавить импорты**

```kotlin
import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.res.ResourcesCompat
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green900
```

(Также добавить package `composeApp.R` import: `import com.example.cleancity.R` — но KMP-проект Android target генерит R как `<applicationId>.R`. Проверить applicationId в `composeApp/build.gradle.kts`. Если applicationId = `com.example.cleancity`, импорт `com.example.cleancity.R`. Если иной — подставить.)

- [ ] **Step 2: Прочитать applicationId**

```bash
grep -nE "applicationId|namespace" ~/Desktop/Myapp/cleancity-kmp/composeApp/build.gradle.kts | head -5
```

Использовать значение `namespace` (или `applicationId`) для R-импорта на следующих шагах.

- [ ] **Step 3: Получить density и Typeface в Composable**

В функции `YandexMapHost` (commonMain expect не трогаем, тут actual в androidMain) сразу после `val mapViewState = remember { mutableStateOf<MapView?>(null) }` (около строки 46-47) добавить:

```kotlin
val context = LocalContext.current
val density = LocalDensity.current.density
val clusterTypeface = remember(context) {
    ResourcesCompat.getFont(context, R.font.unbounded_semibold) ?: Typeface.DEFAULT_BOLD
}
```

- [ ] **Step 4: Передать typeface и density в createClusterBitmap**

Найти в файле (около строки 113-115):

```kotlin
val clusterListener = ClusterListener { cluster ->
    cluster.appearance.setIcon(
        ImageProvider.fromBitmap(createClusterBitmap(cluster.size)),
    )
```

Заменить на:

```kotlin
val clusterListener = ClusterListener { cluster ->
    cluster.appearance.setIcon(
        ImageProvider.fromBitmap(
            createClusterBitmap(cluster.size, density, clusterTypeface),
        ),
    )
```

- [ ] **Step 5: Переписать `createClusterBitmap`**

Найти (около строки 215-238):

```kotlin
private fun createClusterBitmap(count: Int, sizePx: Int = 80): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF374151.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF111827.toInt()
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.4f
        isFakeBoldText = true
    }
    val r = sizePx / 2f - 4f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, fill)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, stroke)
    canvas.drawText(count.toString(), sizePx / 2f, sizePx / 2f + text.textSize / 3f, text)
    return bitmap
}
```

Заменить на:

```kotlin
private fun createClusterBitmap(
    count: Int,
    density: Float,
    typeface: Typeface,
): Bitmap {
    val sizePx = (64f * density).toInt()         // 64dp
    val strokePx = 2f * density                  // 2dp
    val padPx = strokePx / 2f + 1f
    val radius = sizePx / 2f - padPx

    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Green700.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Green900.toArgb()
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.34f
        this.typeface = typeface
    }

    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, fill)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, radius, stroke)
    canvas.drawText(
        count.toString(),
        sizePx / 2f,
        sizePx / 2f - (text.descent() + text.ascent()) / 2f,
        text,
    )
    return bitmap
}
```

Добавить импорт в этом же файле: `import androidx.compose.ui.graphics.toArgb` (если ещё нет от Task 5).

- [ ] **Step 6: Скомпилировать и тесты**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest --console=plain
```

Expected: `BUILD SUCCESSFUL`, 38 tests passed.

Если ругается на `R.font.unbounded_semibold` — проверить namespace в `composeApp/build.gradle.kts` (Step 2) и заменить импорт `com.example.cleancity.R` на правильный.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt
git commit -m "$(cat <<'EOF'
style(map): restyle cluster bubble — Green700 ring, Unbounded number

64dp white circle with 2dp Green700 outline and Green900 cluster count
in Unbounded SemiBold, matching the brand identity used by TopAppBar,
FAB and pins. Drops hardcoded slate colors.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: UserLocationDecorator — кастомная точка + пульсация

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/UserLocationDecorator.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt`

- [ ] **Step 1: Создать UserLocationDecorator.android.kt**

Полный текст нового файла:

```kotlin
package com.example.cleancity.ui.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider

/**
 * Замещает дефолтную чёрно-оранжевую стрелку Yandex MapKit на брендовую точку:
 *   - сплошная Accent-точка 16dp с белой обводкой 3dp,
 *   - мягкий пульсирующий ring (накачка от 1.0 до 1.6 по scale, fade-out по alpha),
 *   - accuracy-circle перекрашен в Accent.
 *
 * Один экземпляр на MapView. Регистрируется через [com.yandex.mapkit.user_location.UserLocationLayer.setObjectListener].
 * Жизненный цикл пульсации: запускается в onObjectAdded, останавливается в onObjectRemoved.
 */
class UserLocationDecorator(
    private val density: Float,
    private val accentArgb: Int,
) : UserLocationObjectListener {

    private val handler = Handler(Looper.getMainLooper())
    private var view: UserLocationView? = null
    private var startedAtMs = 0L

    private val dotBitmap: Bitmap = createDot(accentArgb, density)
    private val transparent1x1: Bitmap =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun onObjectAdded(userLocationView: UserLocationView) {
        view = userLocationView
        userLocationView.arrow.setIcon(ImageProvider.fromBitmap(transparent1x1))
        userLocationView.accuracyCircle.fillColor = withAlpha(accentArgb, 0.10f)
        userLocationView.accuracyCircle.strokeColor = withAlpha(accentArgb, 0.40f)
        userLocationView.accuracyCircle.strokeWidth = 1f

        val composite = userLocationView.pin.useCompositeIcon()
        composite.setIcon("dot", ImageProvider.fromBitmap(dotBitmap), IconStyle())
        composite.setIcon(
            "ring",
            ImageProvider.fromBitmap(createRing(accentArgb, density, alpha = 0.6f)),
            IconStyle(),
        )

        startedAtMs = System.currentTimeMillis()
        handler.post(pulseTick)
    }

    override fun onObjectRemoved(userLocationView: UserLocationView) {
        handler.removeCallbacks(pulseTick)
        view = null
    }

    override fun onObjectUpdated(
        userLocationView: UserLocationView,
        event: com.yandex.mapkit.map.ObjectEvent,
    ) {
        // нечего обновлять: пульсация привязана к таймеру, локация — к MapKit
    }

    private val pulseTick = object : Runnable {
        override fun run() {
            val v = view ?: return
            val t = ((System.currentTimeMillis() - startedAtMs) % PULSE_PERIOD_MS).toFloat() /
                PULSE_PERIOD_MS
            val alpha = (1f - t) * 0.6f
            val newRing = createRing(accentArgb, density * (1f + 0.6f * t), alpha)
            v.pin.useCompositeIcon().setIcon(
                "ring",
                ImageProvider.fromBitmap(newRing),
                IconStyle(),
            )
            handler.postDelayed(this, PULSE_FRAME_MS)
        }
    }

    companion object {
        private const val PULSE_PERIOD_MS = 1200L
        private const val PULSE_FRAME_MS = 80L

        private fun withAlpha(argb: Int, alpha: Float): Int {
            val a = (alpha.coerceIn(0f, 1f) * 255).toInt() and 0xFF
            return (argb and 0x00FFFFFF) or (a shl 24)
        }

        private fun createDot(accentArgb: Int, density: Float): Bitmap {
            val sizePx = (16f * density).toInt().coerceAtLeast(24)
            val strokePx = 3f * density
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = sizePx / 2f
            val r = sizePx / 2f - strokePx / 2f - 1f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentArgb
                style = Paint.Style.FILL
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = strokePx
            }
            canvas.drawCircle(cx, cx, r, fill)
            canvas.drawCircle(cx, cx, r, stroke)
            return bitmap
        }

        /**
         * @param scaledDensity density × pulse-scale; ring растёт со временем
         * @param alpha       0..1, наружный fade
         */
        private fun createRing(accentArgb: Int, scaledDensity: Float, alpha: Float): Bitmap {
            val sizePx = (32f * scaledDensity).toInt().coerceAtLeast(32)
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val cx = sizePx / 2f
            val r = sizePx / 2f - 1f
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accentArgb, alpha * 0.4f)
                style = Paint.Style.FILL
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = withAlpha(accentArgb, alpha)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawCircle(cx, cx, r, fill)
            canvas.drawCircle(cx, cx, r, stroke)
            return bitmap
        }
    }
}
```

- [ ] **Step 2: Использовать decorator в YandexMapHost.android.kt factory**

Найти текущий блок (после Task 5/7 он уже такой):

```kotlin
MapKitFactory.getInstance()
    .createUserLocationLayer(view.mapWindow)
    .apply { isVisible = true }
```

Заменить на:

```kotlin
MapKitFactory.getInstance()
    .createUserLocationLayer(view.mapWindow)
    .apply {
        isVisible = true
        setObjectListener(UserLocationDecorator(density, Accent.toArgb()))
    }
```

Добавить импорт `import com.example.cleancity.ui.theme.Accent` в верх файла (если ещё нет).

`density` — уже доступен в Composable (объявлен в Task 7, Step 3). Если внутри `factory = { ctx -> ... }` он не виден — захватить через локальную переменную перед AndroidView:

```kotlin
val localDensity = density           // capture for use inside factory lambda
```

и использовать `localDensity` внутри factory.

- [ ] **Step 3: Скомпилировать**

```bash
./gradlew :composeApp:compileDebugKotlinAndroid --console=plain 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`.

Если жалуется на `view.arrow`/`view.pin`/`view.accuracyCircle` — проверить через `javap`:

```bash
TMP=$(mktemp -d); cd $TMP; \
unzip -q ~/.gradle/caches/8.11.1/transforms/*/transformed/maps.mobile-4.25.0-full-api.jar \
  com/yandex/mapkit/user_location/UserLocationView.class; \
/usr/bin/javap -p com/yandex/mapkit/user_location/UserLocationView.class; \
cd / && rm -rf $TMP
```

Если методы называются `getArrow()`/`getPin()`/`getAccuracyCircle()` — в Kotlin использовать `view.arrow`/`view.pin`/`view.accuracyCircle` (как property). Уже так.

Если в этой версии другие имена — заменить и пересобрать.

- [ ] **Step 4: Прогнать тесты**

```bash
./gradlew :composeApp:testDebugUnitTest --console=plain 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, 38 tests passed.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/UserLocationDecorator.android.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt
git commit -m "$(cat <<'EOF'
feat(map): brand user-location dot with pulsing accent ring

Replaces the default Yandex chrome arrow with a 16dp Accent dot, white
3dp ring, transparent heading-arrow, accent-tinted accuracy circle, and
a soft pulse (1.0→1.6 scale, alpha 0.6→0, period 1.2s) driven by a
Handler tied to the MapView lifecycle.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Verification on emulator

**Files:** none

- [ ] **Step 1: Собрать APK**

```bash
cd ~/Desktop/Myapp/cleancity-kmp
./gradlew :composeApp:assembleDebug --console=plain 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`. APK по адресу `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

- [ ] **Step 2: Поднять эмулятор (если не запущен) и установить APK**

```bash
SDK=/Users/jasminagababyan/Library/Android/sdk
"$SDK/platform-tools/adb" devices
"$SDK/platform-tools/adb" install -r \
  ~/Desktop/Myapp/cleancity-kmp/composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

Expected: `Success`.

- [ ] **Step 3: Выдать permission, выставить локацию Сочи, запустить**

```bash
SDK=/Users/jasminagababyan/Library/Android/sdk
ADB="$SDK/platform-tools/adb"
"$ADB" shell pm grant com.example.cleancity android.permission.ACCESS_FINE_LOCATION
"$ADB" shell pm grant com.example.cleancity android.permission.ACCESS_COARSE_LOCATION
"$ADB" emu geo fix 39.723 43.585
"$ADB" shell am force-stop com.example.cleancity
"$ADB" shell am start -W -n com.example.cleancity/.MainActivity
```

Expected: `Status: ok`.

- [ ] **Step 4: Скриншот стартового экрана**

```bash
sleep 4
SDK=/Users/jasminagababyan/Library/Android/sdk
"$SDK/platform-tools/adb" shell screencap -p /sdcard/verify-1.png
"$SDK/platform-tools/adb" pull /sdcard/verify-1.png /tmp/verify-1.png
```

Проверить (через Read tool):
- TopAppBar — зелёный с белым «Чистый Город».
- Active chip «Все» — Green700 фон, белый текст.
- FAB «прицел» — белый с серой иконкой, FAB «Сообщить» — Accent-зелёный.
- Legend (левый низ) — белая карточка с тремя точками (Amber/Blue/AccentDark).
- Метки/кластеры — белые с зелёной обводкой и зелёной цифрой Unbounded.

- [ ] **Step 5: Тап на FAB прицел, скриншот**

```bash
SDK=/Users/jasminagababyan/Library/Android/sdk
ADB="$SDK/platform-tools/adb"
"$ADB" shell input tap 990 2047    # координаты FAB на Medium_Phone AVD
sleep 4
"$ADB" shell screencap -p /sdcard/verify-2.png
"$ADB" pull /sdcard/verify-2.png /tmp/verify-2.png
```

Проверить:
- Камера переехала в центр Сочи.
- В центре виден зелёный pulsing dot (Accent), без чёрно-оранжевой стрелки Yandex.
- Accuracy circle тоже зелёный.

Если FAB не сработал — снять `uiautomator dump` и взять реальные bounds:

```bash
"$ADB" shell uiautomator dump /sdcard/ui.xml && "$ADB" pull /sdcard/ui.xml /tmp/ui.xml
grep -oE 'content-desc="[^"]*Моё[^"]*"[^/]*bounds="[^"]*"' /tmp/ui.xml
```

- [ ] **Step 6: Проверить logcat на падения**

```bash
SDK=/Users/jasminagababyan/Library/Android/sdk
"$SDK/platform-tools/adb" logcat -d -t 500 2>/dev/null | \
  grep -iE "cleancity.*FATAL|AndroidRuntime.*cleancity"
```

Expected: пустой вывод. Если есть стек — открыть YandexMapHost / UserLocationDecorator на упомянутой строке и читать спек / openness API.

- [ ] **Step 7: Финальный коммит (если нужны правки от verify)**

Если правки не понадобились — этап без коммита. Если потребовались — отдельный коммит `fix(map): ...` с пояснением.

- [ ] **Step 8: Mark spec acceptance criteria as met**

Открыть `docs/superpowers/specs/2026-05-17-map-screen-unification-design.md`, в секции «Acceptance criteria» убедиться, что все 7 пунктов выполнены. Если какой-то не выполнен — вернуться к соответствующей задаче.

---

## Self-Review checklist (для писавшего план)

**Spec coverage:**
- §1 Базовый принцип — Task 1-5, 7 (TopAppBar, FAB, Legend, Chips, Pin colors, Cluster).
- §2 TopAppBar — Task 1.
- §3 MapFabGroup — Task 2.
- §4 MapLegend — Task 3.
- §5 CategoryFilterChips — Task 4.
- §6 Placemark statusColor — Task 5.
- §6 Cluster — Task 6 (font) + Task 7 (bitmap).
- §7 UserLocationDecorator — Task 8.
- §«НЕ трогаем» — никаких задач (assertion).
- §Risks — Task 7 Step 6 (font), Task 8 Step 3 (MapKit API) дают fallback-инструкции.
- §Acceptance criteria — Task 9.

**Type/method consistency:**
- `createClusterBitmap(count, density, typeface)` — сигнатура одинакова в Step 5 и в caller Step 4.
- `UserLocationDecorator(density: Float, accentArgb: Int)` — одинаково в файле создания (Task 8 Step 1) и регистрации (Task 8 Step 2).
- `withAlpha`, `createDot`, `createRing` — все объявлены и используются внутри одного файла.

**No placeholders:** проверено — каждая правка содержит полный код, нет «similar to», «TODO», и т.п.

# Map Complaint Location Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Перестать схлопывать координаты жалоб в центр района при создании и дать доступ ко всем жалобам, стоящим в одной точке, через список-шторку на карте.

**Architecture:** Два независимых фикса в Android-приложении `composeApp`. Фикс A — решение «снапить ли координаты на топоним» переносится в Android-провайдер геокодинга (`AndroidMapSearchProvider`), который для неточных топонимов возвращает `null`-координаты; общий код уже корректно оставляет точку пользователя. Фикс B — Android-хост карты отдаёт наверх id жалоб кластера и его bbox, а тестируемый `MapScreenModel` (commonMain) по размеру bbox решает: показать список-шторку (совпадающие точки) или зумить (обычный кластер).

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Voyager (ScreenModel), Yandex MapKit (Android), kotlin.test + kotlinx-coroutines-test.

## Global Constraints

- **ЗАПРЕЩЕНО менять существующие данные.** Никаких миграций, `DELETE`, `UPDATE`, пересчёта координат старых жалоб, изменений схемы БД или бэкенда. Только код клиента Android и UI-слой.
- Платформа — только **Android**. У `composeApp` единственный таргет `androidTarget`, единственный `actual fun YandexMapHost` — в `androidMain`. iOS/desktop таргетов нет.
- Тесты пишутся в `composeApp/src/commonTest`, фреймворк `kotlin.test`, асинхронные — `kotlinx.coroutines.test.runTest`.
- Команда прогона unit-тестов: `./gradlew :composeApp:testDebugUnitTest`.
- Команда компиляции (для проверки UI/Android-кода без тестов): `./gradlew :composeApp:compileDebugKotlinAndroid`.
- Следовать существующим паттернам: `data class` UiState с `copy`, `_state.update { ... }`, приватные хелперы в файле компонента.

---

## File Structure

| Файл | Ответственность | Задача |
|------|-----------------|--------|
| `composeApp/src/commonMain/.../domain/map/BoundingBox.kt` | + `spanMeters()` — размер bbox в метрах | 1 |
| `composeApp/src/commonTest/.../domain/map/BoundingBoxTest.kt` | тесты `spanMeters()` | 1 |
| `composeApp/src/androidMain/.../map/AndroidMapSearchProvider.kt` | снап только для точного топонима | 2 |
| `composeApp/src/commonMain/.../map/MapSearchProvider.kt` | KDoc к `ReverseGeocodeResult` | 2 |
| `composeApp/src/commonMain/.../map/MapUiState.kt` | + `selectedClusterIds` | 3 |
| `composeApp/src/commonMain/.../map/MapScreenModel.kt` | `onClusterTap(ids, bbox)`, `closeClusterSheet()`, порог | 3 |
| `composeApp/src/commonTest/.../map/MapScreenModelTest.kt` | тесты зум/список | 3 |
| `composeApp/src/commonMain/.../map/YandexMapHost.kt` | сигнатура `onClusterTap` (expect) | 4 |
| `composeApp/src/androidMain/.../map/YandexMapHost.android.kt` | `userData = id`, сбор ids, новый коллбек | 4 |
| `composeApp/src/commonMain/.../map/components/MarkerSheetFormat.kt` | общие `formatCoord` + `localizedLabel` | 5 |
| `composeApp/src/commonMain/.../map/components/MarkerPreviewSheet.kt` | убрать приватные копии хелперов | 5 |
| `composeApp/src/commonMain/.../map/components/MarkerListSheet.kt` | новый компонент-список | 5 |
| `composeApp/src/commonMain/.../map/MapScreen.kt` | прокидка `onClusterTap`, рендер `MarkerListSheet` | 6 |

---

## Task 1: `BoundingBox.spanMeters()`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/BoundingBox.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/map/BoundingBoxTest.kt`

**Interfaces:**
- Consumes: ничего.
- Produces: `fun BoundingBox.spanMeters(): Double` — диагональ bbox (SW↔NE) в метрах по гаверсинусу. Для совпадающих углов возвращает `0.0`.

- [ ] **Step 1: Написать падающий тест**

Создать `composeApp/src/commonTest/kotlin/com/example/cleancity/domain/map/BoundingBoxTest.kt`:

```kotlin
package com.example.cleancity.domain.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class BoundingBoxTest {

    @Test
    fun `spanMeters of identical corners is zero`() {
        val bbox = BoundingBox(43.4660, 39.9242, 43.4660, 39.9242)
        assertTrue(bbox.spanMeters() < 0.001, "ожидали ~0, получили ${bbox.spanMeters()}")
    }

    @Test
    fun `spanMeters of 0_001 degree latitude is about 111 meters`() {
        val bbox = BoundingBox(43.0, 39.0, 43.001, 39.0)
        val m = bbox.spanMeters()
        assertTrue(m in 109.0..114.0, "ожидали ~111 м, получили $m")
    }

    @Test
    fun `spanMeters of wide bbox is hundreds of meters or more`() {
        val bbox = BoundingBox(43.40, 39.90, 43.60, 40.10)
        assertTrue(bbox.spanMeters() > 25.0, "широкий bbox должен быть > порога")
    }
}
```

- [ ] **Step 2: Запустить тест — убедиться, что падает**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.map.BoundingBoxTest"`
Expected: FAIL компиляции — `spanMeters` не определён (`unresolved reference`).

- [ ] **Step 3: Реализовать `spanMeters()`**

В `BoundingBox.kt` добавить импорты и метод. Импорты сверху файла (рядом с существующими `import kotlin.math.pow`, `import kotlin.math.round`):

```kotlin
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
```

Внутри `data class BoundingBox { ... }`, после `suggestedZoom()`:

```kotlin
    // Диагональ bbox (юго-западный ↔ северо-восточный угол) в метрах, гаверсинус.
    // Для совпадающих углов = 0. Используется, чтобы отличить «жалобы в одной точке»
    // (span ≈ 0, зум их не разведёт → список) от обычного кластера (большой span → зум).
    fun spanMeters(): Double {
        val earthRadiusM = 6_371_000.0
        fun rad(deg: Double) = deg * PI / 180.0
        val dLat = rad(neLat - swLat)
        val dLon = rad(neLon - swLon)
        val a = sin(dLat / 2).pow(2) +
            cos(rad(swLat)) * cos(rad(neLat)) * sin(dLon / 2).pow(2)
        return 2 * earthRadiusM * asin(minOf(1.0, sqrt(a)))
    }
```

- [ ] **Step 4: Запустить тест — убедиться, что проходит**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.domain.map.BoundingBoxTest"`
Expected: PASS (3 теста).

- [ ] **Step 5: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/BoundingBox.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/domain/map/BoundingBoxTest.kt
git commit -m "feat(map): BoundingBox.spanMeters() для различения совпадающих точек и кластеров"
```

---

## Task 2: Фикс A — снап только для точного топонима

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/AndroidMapSearchProvider.kt:102-134`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapSearchProvider.kt:29-38`

**Interfaces:**
- Consumes: `ReverseGeocodeResult(address, district, latitude?, longitude?)` (без изменений сигнатуры).
- Produces: при reverse-geocode `latitude/longitude` непустые **только** если топоним уровня дома/точной точки; иначе `null`. Общий код (`MapPickerScreenModel:57-58`, `CreateComplaintScreenModel`) уже делает `r.latitude ?: it.currentLat`, поэтому при `null` сохраняется точка пользователя — менять его НЕ нужно.

> Примечание: у `composeApp` нет источника `androidUnitTest`, а код завязан на нативный Yandex SDK — поэтому задача проверяется компиляцией + ручным сценарием (Step 4–5), без автотеста. Решение умышленно тривиальное.

- [ ] **Step 1: Внести правку в `AndroidMapSearchProvider.kt`**

В `reverseGeocode` → `onSearchResponse`, заменить блок вычисления `toponymPoint` (текущие стр. 121–123) и сборку результата. Сейчас:

```kotlin
                    // Координата toponym'а — на ней реально стоит дом/улица из адреса.
                    // Если её нет (редко), state останется на исходных координатах.
                    val toponymPoint = obj.geometry.firstOrNull()?.point

                    if (cont.isActive) cont.resume(
                        Result.success(
                            ReverseGeocodeResult(
                                address = address,
                                district = district,
                                latitude = toponymPoint?.latitude,
                                longitude = toponymPoint?.longitude,
                            ),
                        ),
                    )
```

Заменить на:

```kotlin
                    // Снап на координату toponym'а допустим ТОЛЬКО для точного адреса
                    // (дом / точная точка). Для уровня района/локалитета Yandex отдаёт
                    // геометрию = центр района — снап туда схлопывает разные жалобы в одну
                    // точку. В таком случае возвращаем null-координаты, и вызывающий код
                    // оставляет точку, которую пользователь поставил на карте.
                    val precise = house != null || toponym?.precision in setOf(
                        ToponymObjectMetadata.Precision.EXACT,
                        ToponymObjectMetadata.Precision.NUMBER,
                    )
                    val toponymPoint = if (precise) obj.geometry.firstOrNull()?.point else null

                    if (cont.isActive) cont.resume(
                        Result.success(
                            ReverseGeocodeResult(
                                address = address,
                                district = district,
                                latitude = toponymPoint?.latitude,
                                longitude = toponymPoint?.longitude,
                            ),
                        ),
                    )
```

(`house` и `toponym` уже объявлены выше в этом же `onSearchResponse`; `ToponymObjectMetadata` уже импортирован — стр. 16.)

- [ ] **Step 2: Обновить KDoc в `MapSearchProvider.kt`**

Заменить комментарий над полями `latitude/longitude` в `data class ReverseGeocodeResult` (стр. 32–35) на:

```kotlin
    // Координаты найденного toponym'а. Заполняются ТОЛЬКО для точного адреса
    // (дом / EXACT / NUMBER) — тогда снап ставит маркер ровно на здание и дубликат-чек
    // ищет вокруг дома. Для грубого уровня (район/локалитет) здесь null, чтобы НЕ затирать
    // точку пользователя центром района. См. AndroidMapSearchProvider.reverseGeocode.
    val latitude: Double? = null,
    val longitude: Double? = null,
```

- [ ] **Step 3: Скомпилировать**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (тип `ToponymObjectMetadata.Precision` резолвится).

- [ ] **Step 4: Прогнать существующие тесты (регресс)**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS — `MapPickerScreenModelTest` и `CreateComplaint*`-тесты зелёные (фейковый провайдер отдаёт `null`-координаты, точка сохраняется как и раньше).

- [ ] **Step 5: Ручная проверка (эмулятор/устройство) и коммит**

Сценарии:
- Пин во дворе без дома → создать жалобу → сохранённые координаты совпадают с пином, НЕ с центром района.
- Пин на конкретном доме → координаты снапятся на дом (регресс «снап на здание» не сломан).

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/AndroidMapSearchProvider.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapSearchProvider.kt
git commit -m "fix(map): не снапить координаты жалобы на центр района при неточном топониме"
```

---

## Task 3: `MapScreenModel.onClusterTap` — зум или список

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapUiState.kt:9-23`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt`
- Test: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`

**Interfaces:**
- Consumes: `BoundingBox.spanMeters()` (Task 1); `MapScreenModel.zoomTo(lat, lon, zoom)`, `BoundingBox.suggestedZoom()` (существуют).
- Produces:
  - `MapUiState.selectedClusterIds: List<Long>?` — id жалоб для шторки-списка, `null` когда закрыта.
  - `MapScreenModel.onClusterTap(ids: List<Long>, bbox: BoundingBox)` — при `ids` непустом и `bbox.spanMeters() < 25.0` выставляет `selectedClusterIds = ids`; иначе зумит к центру bbox.
  - `MapScreenModel.closeClusterSheet()` — `selectedClusterIds = null`.

- [ ] **Step 1: Добавить поле в `MapUiState`**

В `MapUiState.kt` в `data class MapUiState(...)` добавить поле (например, после `selectedMarkerId`):

```kotlin
    val selectedClusterIds: List<Long>? = null,
```

- [ ] **Step 2: Написать падающие тесты**

В `MapScreenModelTest.kt` добавить тесты (в конец класса, перед закрывающей `}`):

```kotlin
    @Test
    fun `onClusterTap with identical coords opens list and does not zoom`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()
        val cameraBefore = model.state.value.cameraPosition

        // три жалобы в одной точке: span = 0
        model.onClusterTap(listOf(1L, 2L, 3L), BoundingBox(43.4660, 39.9242, 43.4660, 39.9242))

        assertEquals(listOf(1L, 2L, 3L), model.state.value.selectedClusterIds)
        assertEquals(cameraBefore, model.state.value.cameraPosition, "камера не должна двигаться")
        model.close()
    }

    @Test
    fun `onClusterTap with wide bbox zooms and opens no list`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()

        model.onClusterTap(listOf(1L, 2L), BoundingBox(43.40, 39.90, 43.60, 40.10))

        assertEquals(null, model.state.value.selectedClusterIds)
        assertEquals(43.5, model.state.value.cameraPosition.latitude, "зум к центру bbox")
        assertEquals(40.0, model.state.value.cameraPosition.longitude)
        model.close()
    }

    @Test
    fun `onClusterTap with empty ids falls back to zoom`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()

        model.onClusterTap(emptyList(), BoundingBox(43.4660, 39.9242, 43.4660, 39.9242))

        assertEquals(null, model.state.value.selectedClusterIds)
        model.close()
    }

    @Test
    fun `closeClusterSheet clears selectedClusterIds`() = runTest(dispatcher) {
        val model = newModel()
        advanceUntilIdle()
        model.onClusterTap(listOf(1L, 2L), BoundingBox(43.4660, 39.9242, 43.4660, 39.9242))

        model.closeClusterSheet()

        assertEquals(null, model.state.value.selectedClusterIds)
        model.close()
    }
```

- [ ] **Step 3: Запустить тесты — убедиться, что падают**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest"`
Expected: FAIL компиляции — `onClusterTap` / `closeClusterSheet` / `selectedClusterIds` не определены.

- [ ] **Step 4: Реализовать в `MapScreenModel.kt`**

Добавить top-level приватную константу (рядом с классом, например над `class MapScreenModel`):

```kotlin
// Если все точки кластера умещаются в круг меньше этого радиуса, зум их не разведёт —
// показываем список вместо приближения. Главный случай — точно совпадающие координаты.
private const val CLUSTER_SPLIT_THRESHOLD_M = 25.0
```

Добавить методы в класс (рядом с `onMarkerClick` / `closeMarkerSheet`):

```kotlin
    fun onClusterTap(ids: List<Long>, bbox: BoundingBox) {
        if (ids.isNotEmpty() && bbox.spanMeters() < CLUSTER_SPLIT_THRESHOLD_M) {
            _state.update { it.copy(selectedClusterIds = ids) }
        } else {
            val midLat = (bbox.swLat + bbox.neLat) / 2.0
            val midLon = (bbox.swLon + bbox.neLon) / 2.0
            zoomTo(midLat, midLon, bbox.suggestedZoom())
        }
    }

    fun closeClusterSheet() {
        _state.update { it.copy(selectedClusterIds = null) }
    }
```

- [ ] **Step 5: Запустить тесты — убедиться, что проходят**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest"`
Expected: PASS (все тесты, включая новые 4).

- [ ] **Step 6: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapUiState.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "feat(map): onClusterTap решает зум или список по размеру кластера"
```

---

## Task 4: Сигнатура `onClusterTap` — пробросить id жалоб из хоста

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.kt:9-17`
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt:50-58, 145-202`

**Interfaces:**
- Consumes: `MapScreenModel.onClusterTap(ids, bbox)` (Task 3) — будет подключён в Task 6.
- Produces: `YandexMapHost(..., onClusterTap: (ids: List<Long>, bbox: BoundingBox) -> Unit, ...)`. Хост проставляет `placemark.userData = marker.id` и при тапе по кластеру собирает `ids` из `userData`.

> UI/нативный код Yandex — проверка компиляцией, без unit-теста.

- [ ] **Step 1: Изменить expect-сигнатуру**

В `YandexMapHost.kt` заменить строку:

```kotlin
    onClusterTap: (BoundingBox) -> Unit,
```

на:

```kotlin
    onClusterTap: (ids: List<Long>, bbox: BoundingBox) -> Unit,
```

- [ ] **Step 2: Обновить actual-сигнатуру**

В `YandexMapHost.android.kt` в `actual fun YandexMapHost(...)` заменить параметр:

```kotlin
    onClusterTap: (BoundingBox) -> Unit,
```

на:

```kotlin
    onClusterTap: (ids: List<Long>, bbox: BoundingBox) -> Unit,
```

- [ ] **Step 3: Проставлять `userData` на placemark**

В блоке `markers.forEach { marker -> ... }` (стр. ~181-188) добавить `userData`:

```kotlin
            val placemark = collection.addPlacemark().apply {
                geometry = Point(marker.latitude, marker.longitude)
                userData = marker.id
                setIcon(
                    ImageProvider.fromBitmap(createPinBitmap(statusColor(marker.status), density)),
                    pinIconStyle,
                )
            }
```

- [ ] **Step 4: Собрать ids в `ClusterTapListener`**

В `ClusterTapListener { c -> ... }` (стр. 156-172) заменить тело так, чтобы собрать id и вызвать новый коллбек:

```kotlin
                ClusterTapListener { c ->
                    val placemarks = c.placemarks
                    if (placemarks.isEmpty()) return@ClusterTapListener true
                    val ids = placemarks.mapNotNull { it.userData as? Long }
                    var minLat = Double.MAX_VALUE
                    var maxLat = -Double.MAX_VALUE
                    var minLon = Double.MAX_VALUE
                    var maxLon = -Double.MAX_VALUE
                    placemarks.forEach { p ->
                        val pt = p.geometry
                        if (pt.latitude < minLat) minLat = pt.latitude
                        if (pt.latitude > maxLat) maxLat = pt.latitude
                        if (pt.longitude < minLon) minLon = pt.longitude
                        if (pt.longitude > maxLon) maxLon = pt.longitude
                    }
                    onClusterTap(ids, BoundingBox(minLat, minLon, maxLat, maxLon))
                    true
                },
```

- [ ] **Step 5: Скомпилировать**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: FAIL — `MapScreen.kt` ещё передаёт старую лямбду `onClusterTap = { bbox -> ... }`. Это ожидаемо и чинится в Task 6. Если других ошибок нет (только несоответствие лямбды в `MapScreen.kt`) — переходим дальше.

- [ ] **Step 6: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt
git commit -m "feat(map): хост карты отдаёт id жалоб кластера в onClusterTap"
```

---

## Task 5: Компонент `MarkerListSheet` + общие хелперы форматирования

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerSheetFormat.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerPreviewSheet.kt:46-60`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerListSheet.kt`

**Interfaces:**
- Consumes: `MapMarker` (`id`, `category.localizedLabel`, `status`, `latitude`, `longitude`).
- Produces:
  - `internal fun formatCoord(value: Double): String` и `internal fun ComplaintStatus.localizedLabel(): String` в `MarkerSheetFormat.kt` (тот же пакет `...map.components`).
  - `@Composable fun MarkerListSheet(markers: List<MapMarker>, onDismiss: () -> Unit, onOpenDetail: (Long) -> Unit)` — `ModalBottomSheet` со списком кликабельных строк.

> Compose UI — проверка компиляцией + ручным сценарием.

- [ ] **Step 1: Вынести хелперы в `MarkerSheetFormat.kt`**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerSheetFormat.kt`:

```kotlin
package com.example.cleancity.ui.feature.map.components

import com.example.cleancity.shared.models.ComplaintStatus
import kotlin.math.roundToLong

internal fun formatCoord(value: Double): String {
    val rounded = (value * 10000).roundToLong()
    val whole = rounded / 10000
    val frac = (rounded % 10000).let { if (it < 0) -it else it }
    val fracStr = frac.toString().padStart(4, '0')
    return "$whole.$fracStr"
}

internal fun ComplaintStatus.localizedLabel(): String = when (this) {
    ComplaintStatus.NEW -> "Новая"
    ComplaintStatus.IN_PROGRESS -> "В работе"
    ComplaintStatus.RESOLVED -> "Решено"
    ComplaintStatus.REJECTED -> "Отклонено"
    ComplaintStatus.DUPLICATE -> "Дубликат"
}
```

- [ ] **Step 2: Убрать приватные копии из `MarkerPreviewSheet.kt`**

В `MarkerPreviewSheet.kt` удалить приватные функции `formatCoord` (стр. 46-52) и `ComplaintStatus.localizedLabel()` (стр. 54-60) — теперь они берутся из `MarkerSheetFormat.kt` (тот же пакет, импорт не нужен). Удалить ставший лишним импорт `import kotlin.math.roundToLong`. `import com.example.cleancity.shared.models.ComplaintStatus` тоже больше не нужен в этом файле — удалить.

- [ ] **Step 3: Создать `MarkerListSheet.kt`**

Создать `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerListSheet.kt`:

```kotlin
package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.MapMarker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerListSheet(
    markers: List<MapMarker>,
    onDismiss: () -> Unit,
    onOpenDetail: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Text(
                text = "${markers.size} жалоб в этой точке",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn {
                items(markers, key = { it.id }) { marker ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetail(marker.id) }
                            .padding(vertical = 12.dp),
                    ) {
                        Text(
                            text = "${marker.category.localizedLabel} · ${marker.status.localizedLabel()}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "Координаты: ${formatCoord(marker.latitude)}, ${formatCoord(marker.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
```

- [ ] **Step 4: Скомпилировать**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL для новых файлов (ошибка несоответствия лямбды в `MapScreen.kt` из Task 4 ещё остаётся — она чинится в Task 6; других ошибок быть не должно).

- [ ] **Step 5: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerSheetFormat.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerPreviewSheet.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerListSheet.kt
git commit -m "feat(map): MarkerListSheet — список жалоб в одной точке, общие хелперы форматирования"
```

---

## Task 6: Подключить список в `MapScreen`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreen.kt:45, 98-102, 146-158`

**Interfaces:**
- Consumes: `MapScreenModel.onClusterTap(ids, bbox)` и `closeClusterSheet()` (Task 3), `MapUiState.selectedClusterIds` (Task 3), `MarkerListSheet(...)` (Task 5), новая сигнатура `YandexMapHost.onClusterTap` (Task 4), `ComplaintDetailScreen(complaintId: Long)` (существует).
- Produces: рабочий экран — тап по совпадающим точкам открывает `MarkerListSheet`, по обычному кластеру зумит.

- [ ] **Step 1: Прокинуть `onClusterTap` в модель**

В `MapScreen.kt` заменить лямбду в `YandexMapHost(...)` (стр. 98-102):

```kotlin
                    onClusterTap = { bbox ->
                        val midLat = (bbox.swLat + bbox.neLat) / 2.0
                        val midLon = (bbox.swLon + bbox.neLon) / 2.0
                        model.zoomTo(midLat, midLon, bbox.suggestedZoom())
                    },
```

на:

```kotlin
                    onClusterTap = model::onClusterTap,
```

Если после удаления остался неиспользуемый импорт `suggestedZoom`/`BoundingBox` — оставить как есть, если он используется в других местах файла; иначе удалить неиспользуемые импорты, на которые укажет компилятор.

- [ ] **Step 2: Добавить импорт и рендер `MarkerListSheet`**

Добавить импорт рядом с `import ...components.MarkerPreviewSheet` (стр. 45):

```kotlin
import com.example.cleancity.ui.feature.map.components.MarkerListSheet
```

После блока `state.selectedMarkerId?.let { ... }` (заканчивается на стр. 158) добавить:

```kotlin
                state.selectedClusterIds?.let { ids ->
                    MarkerListSheet(
                        markers = state.markers.filter { it.id in ids },
                        onDismiss = { model.closeClusterSheet() },
                        onOpenDetail = { id ->
                            model.closeClusterSheet()
                            navigator.push(ComplaintDetailScreen(id))
                        },
                    )
                }
```

- [ ] **Step 3: Скомпилировать весь модуль**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (несоответствие лямбды из Task 4 устранено).

- [ ] **Step 4: Прогнать все unit-тесты**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS — все тесты, включая `BoundingBoxTest` и новые в `MapScreenModelTest`.

- [ ] **Step 5: Ручная проверка (эмулятор/устройство)**

- Создать 2–3 жалобы с одинаковыми координатами → тап по точке на карте открывает `MarkerListSheet` со всеми; тап по строке открывает детальный экран.
- Кластер из жалоб на разных улицах → тап по-прежнему зумит, список не появляется.

- [ ] **Step 6: Коммит**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreen.kt
git commit -m "feat(map): показывать MarkerListSheet для жалоб в одной точке"
```

---

## Self-Review (выполнено при написании плана)

**Spec coverage:**
- Фикс A (не снапить на центр района) → Task 2. ✓
- Фикс B (список для совпадающих точек, зум для обычных кластеров) → Tasks 1, 3, 4, 5, 6. ✓
- «ЗАПРЕЩЕНО менять данные» → Global Constraints + ни одна задача не трогает БД/бэкенд. ✓
- iOS вне scope → Global Constraints (один Android-таргет). ✓
- Тесты из секции «Тестирование» спека → `BoundingBoxTest` (Task 1), `MapScreenModelTest` новые кейсы (Task 3), регресс `MapPickerScreenModelTest` (Task 2 Step 4), ручные сценарии (Task 2/6). ✓

**Type consistency:**
- `onClusterTap(ids: List<Long>, bbox: BoundingBox)` — одинаково в expect (Task 4 Step 1), actual (Task 4 Step 2), модели (Task 3 Step 4), вызове (Task 6 Step 1). ✓
- `selectedClusterIds: List<Long>?` — объявлено (Task 3 Step 1), читается (Task 6 Step 2). ✓
- `closeClusterSheet()`, `spanMeters()`, `formatCoord`, `localizedLabel` — имена единообразны во всех задачах. ✓

**Placeholder scan:** плейсхолдеров нет; код приведён полностью в каждом шаге.

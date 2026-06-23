# Дизайн: точные координаты жалоб и доступ к перекрытым маркерам

Дата: 2026-06-23
Платформа: Android (мобильное приложение «Чистый Город», `composeApp`)
Карта: Yandex MapKit

## Контекст и проблема

На карте три жалобы в районе Орёл-Изумруд (все с координатами ≈ 43.4660, 39.9242)
визуально слились в одну точку. Тап открывает только одну жалобу («Мусор · Решено»),
до остальных двух добраться невозможно. Тап по кластеру лишь приближает камеру —
но при совпадающих координатах зум их не разводит, и пользователь зацикливается.

Расследование выявило **две связанные причины**:

- **Баг A (первопричина) — координаты схлопываются в центр района при создании.**
  При создании жалобы точка, поставленная пользователем на карте, проходит
  reverse-geocode (Yandex Geocoder), и координаты заменяются на координаты найденного
  топонима (`MapPickerScreenModel.kt:57-58`, ранее также `CreateComplaintScreenModel`).
  Замысел верный — «снап на здание», чтобы маркер и дубликат-чек совпадали с адресом.
  Но когда в точке нет конкретного дома (двор, пустырь, парк), Yandex возвращает
  топоним **уровня района**, и его геометрия — это **центр района** (≈ 43.4660, 39.9242).
  Точная точка пользователя затирается центроидом, и все «безадресные» жалобы района
  сваливаются в одну координату.

- **Баг B (симптом) — нет доступа к нескольким жалобам в одной точке.**
  Маркеры кладутся в кластеризованную коллекцию (`clusterPlacemarks(60.0, 15)`).
  При совпадающих координатах жалобы либо схлопываются в кластер (тап → бесконечный
  зум), либо накладываются друг на друга (тап ловит только верхний placemark).
  Наблюдение заказчика: схлопывание возникает практически только при **точно
  совпадающих** координатах — то есть это прямое следствие бага A.

## Цели

1. **Баг A:** при создании жалобы сохранять точку, которую пользователь поставил
   пальцем, если геокодер не нашёл адрес уровня дома/точной точки. Снап на топоним
   оставить только для точных адресов.
2. **Баг B:** дать доступ ко всем жалобам, стоящим в одной (или почти одной) точке,
   через список-шторку. Обычные кластеры (жалобы на разных улицах) продолжают
   работать как раньше — зумом.

## Не-цели (вне scope)

- **iOS** — карта реализована только под Android (`androidMain`), iOS-таргета у экрана
  карты нет. Не трогаем.
- **Spiderfy** (веерное разъезжание маркеров) — эффектно, но дорого на Yandex MapKit;
  список-шторка решает ту же задачу надёжнее.
- **Серверная агрегация/дедупликация маркеров** — не требуется.
- **Восстановление позиций уже схлопнутых жалоб** — исходные точки безвозвратно
  потеряны (затёрты центроидом при создании). Баг B обеспечивает к ним доступ;
  отдельная ручная чистка данных при желании — вне этого спека.

## ЗАПРЕЩЕНО (жёсткое требование заказчика)

- **Не стирать и не изменять существующие данные.** Никаких миграций, `DELETE`,
  `UPDATE`, пересчёта координат старых жалоб, чисток таблиц жалоб/пользователей/
  чего-либо ещё. Все изменения — только в коде клиента (Android) и UI-слое.
- База данных остаётся как есть. Старые жалобы становятся доступны через новый
  список-шторку (баг B), но их записи не модифицируются.

---

## Фикс A — не снапить на неточный топоним

**Принцип:** решение «снапить или нет» принимается в одном месте — в Android-провайдере
геокодинга. Общий код (commonMain) уже корректно обрабатывает `null`-координаты, поэтому
его менять не нужно.

### Изменения

**`composeApp/src/androidMain/.../AndroidMapSearchProvider.kt`** (`reverseGeocode`, ~стр. 102–134):

- Из `ToponymObjectMetadata` достать точность: `metadata.precision`
  (значения Yandex: `EXACT`, `NUMBER`, `RANGE`, `NEAR`, `OTHER`) и/или проверить
  наличие компонента `Address.Component.Kind.HOUSE` в адресе.
- Определить топоним как **точный**, если выполняется хотя бы одно:
  - `precision ∈ { EXACT, NUMBER }`, **или**
  - в адресе присутствует компонент `HOUSE`.
- Если точный → вернуть `latitude/longitude` топонима (как сейчас).
- Если **не** точный (уровень района/локалитета/улицы без дома) → вернуть
  `latitude = null, longitude = null`. Текстовый `address` и `district` возвращаются
  как прежде, независимо от координат.

**`composeApp/src/commonMain/.../map/MapSearchProvider.kt`** (`ReverseGeocodeResult`):

- Сигнатура не меняется (`latitude/longitude` уже nullable). Обновить KDoc:
  координаты возвращаются только для топонима уровня дома/точной точки; для более
  грубых уровней — `null`, чтобы не затирать точку пользователя центром района.

**`MapPickerScreenModel.kt:57-58`** и **`CreateComplaintScreenModel.kt` (reverseGeocode, ~стр. 245-273)**:

- **Изменений нет.** Оба уже делают `r.latitude ?: it.currentLat` — при `null`
  оставляют точку пользователя. Это и есть требуемое поведение «молча сохранить точку»
  (заказчик выбрал вариант без подсказки в UI).

### Поведение после фикса

- Пин на конкретном доме → снап на дом (как раньше): маркер и дубль-чек точны.
- Пин во дворе/на пустыре → адрес показывается («Сочи, Орёл-Изумруд р-н»),
  но координаты остаются там, где пользователь поставил палец. Жалобы больше не
  сваливаются в центр района.

---

## Фикс B — список жалоб в перекрытой точке

**Принцип:** хост-карта (Android) отдаёт наверх id жалоб кластера и его bbox.
Решение «зум или список» принимает `MapScreenModel` (тестируемый commonMain) по
размеру bbox.

### Поток данных

```
тап по кластеру (Yandex ClusterTapListener)
  → хост: ids = placemarks.userData (Long), bbox = граница точек кластера
  → onClusterTap(ids, bbox)                      // новая сигнатура
  → MapScreenModel.onClusterTap(ids, bbox):
        bbox.spanMeters() < CLUSTER_SPLIT_THRESHOLD_M ?
            да  → selectedClusterIds = ids        // показать список-шторку
            нет → zoomTo(центр, suggestedZoom)     // зум, как сейчас
```

Поскольку схлопывание происходит почти всегда при **точно совпадающих** координатах,
`spanMeters()` в этом случае ≈ 0, и условие срабатывает тривиально. Порог ~25 м —
страховка для «почти совпадающих» точек у одного дома.

### Изменения по файлам

**`composeApp/src/androidMain/.../YandexMapHost.android.kt`:**
- При создании placemark (стр. ~182): `placemark.userData = marker.id`.
- В `ClusterTapListener` (стр. 155–172): собрать
  `ids = c.placemarks.mapNotNull { it.userData as? Long }`, посчитать bbox (уже
  считается), вызвать `onClusterTap(ids, bbox)`. Если `ids` пуст — fallback на зум.

**`YandexMapHost` (expect-декларация, commonMain) и `MapScreen.kt:98-102`:**
- Сменить сигнатуру `onClusterTap` на `(ids: List<Long>, bbox: BoundingBox) -> Unit`.
- В `MapScreen` прокинуть в `model.onClusterTap(ids, bbox)` (убрать локальный расчёт
  зума — он переезжает в модель).

**`composeApp/src/commonMain/.../map/MapScreenModel.kt`:**
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
fun closeClusterSheet() { _state.update { it.copy(selectedClusterIds = null) } }
```
- Константа `CLUSTER_SPLIT_THRESHOLD_M = 25.0`.

**`MapUiState`:** добавить `selectedClusterIds: List<Long>? = null`.

**`composeApp/src/commonMain/.../domain/map/BoundingBox.kt`:** добавить хелпер
`spanMeters(): Double` — диагональ bbox в метрах (гаверсинус либо грубая оценка
`δlat·111320` и `δlon·111320·cos(lat)`, взять максимум/диагональ). Чистая функция,
покрывается unit-тестом.

**Новый `composeApp/src/commonMain/.../map/components/MarkerListSheet.kt`:**
- `ModalBottomSheet` со списком. Для каждого `id` берём
  `markers.firstOrNull { it.id == id }`, рисуем строку «Категория · Статус» +
  координаты (тот же стиль, что `MarkerPreviewSheet`). Тап по строке →
  `onOpenDetail(id)`. Заголовок: «N жалоб в этой точке».
- `MarkerPreviewSheet` остаётся без изменений — для одиночного маркера.

**`MapScreen.kt`:** рядом с блоком `selectedMarkerId` (стр. 146-158) добавить:
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

### Граничные случаи

- `ids` пуст → шторку не показываем, зум (fallback).
- В кластере 1 id → список с одной строкой (теоретический случай; корректно).
- Жалоба из `ids` не найдена в `state.markers` (рассинхрон) → строка пропускается
  (`filter`).

---

## Обработка ошибок

- **Геокодер вернул ошибку/пусто (фикс A):** поведение как сейчас — `onFailure`
  оставляет точку пользователя, `address = null`. Фикс A это не меняет.
- **`userData` не `Long` (фикс B):** `mapNotNull { it.userData as? Long }` молча
  отфильтрует — кластер не упадёт.
- Никаких новых сетевых вызовов и точек отказа не вводится.

## Тестирование

**Unit (commonMain, без эмулятора):**
- `BoundingBox.spanMeters()`: нулевой span (совпадающие точки) ≈ 0; известные
  расстояния в пределах допуска.
- `MapScreenModel.onClusterTap`:
  - совпадающие координаты (span 0) → `selectedClusterIds` выставлен, зума нет;
  - разнесённый bbox (> порога) → вызван зум, `selectedClusterIds == null`;
  - пустой `ids` → зум, без списка;
  - `closeClusterSheet()` → `selectedClusterIds == null`.
- Существующие тесты `MapPickerScreenModelTest` остаются зелёными (фейковый провайдер
  отдаёт `null`-координаты → точка сохраняется).

**Ручная проверка фикса A (эмулятор/устройство):**
- Поставить пин во дворе без адреса → создать жалобу → координаты совпадают с пином,
  не с центром района.
- Поставить пин на доме → координаты снапятся на дом (регресс не сломан).

**Ручная проверка фикса B:**
- Создать 2–3 жалобы с одинаковыми координатами → на карте тап по точке открывает
  список со всеми, каждая открывается в детальный экран.
- Кластер из жалоб на разных улицах → тап по-прежнему зумит.

## Затронутые файлы (сводка)

| Файл | Изменение |
|------|-----------|
| `androidMain/.../AndroidMapSearchProvider.kt` | Снап только для точного топонима (precision/HOUSE) |
| `commonMain/.../map/MapSearchProvider.kt` | KDoc к `ReverseGeocodeResult` |
| `androidMain/.../YandexMapHost.android.kt` | `userData = id`, `onClusterTap(ids, bbox)` |
| `commonMain/.../map/YandexMapHost` (expect) | Сигнатура `onClusterTap` |
| `commonMain/.../map/MapScreen.kt` | Прокидка `onClusterTap`, рендер `MarkerListSheet` |
| `commonMain/.../map/MapScreenModel.kt` | Логика зум/список, `selectedClusterIds` |
| `commonMain/.../map/MapUiState` | Поле `selectedClusterIds` |
| `commonMain/.../domain/map/BoundingBox.kt` | Хелпер `spanMeters()` |
| `commonMain/.../map/components/MarkerListSheet.kt` | Новый компонент-список |

Без изменений: `MapPickerScreenModel.kt`, `CreateComplaintScreenModel.kt`, бэкенд,
схема БД, данные.

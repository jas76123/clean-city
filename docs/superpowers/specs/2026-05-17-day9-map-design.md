# Day 9 — Mobile MapScreen (Yandex MapKit + Backend)

**Status:** approved 2026-05-17 (brainstorm-сессия)
**Owner:** Жасмин
**Plan code-ref:** `docs/PLAN.md` §«День 9 (16.05) — Mobile карта (Yandex Maps SDK)»

## 1. Цель

Реализовать первый продуктовый экран мобильного приложения после auth — карту Сочи с маркерами реальных жалоб из backend, фильтром по категориям, кластеризацией и точками входа в создание жалобы и геолокацию.

Это первый экран, с которым взаимодействует и гость, и резидент после входа. Day 10 (лента/детали) и Day 11 (CreateComplaintScreen) опираются на навигационные хуки, заложенные здесь.

## 2. Контекст и текущее состояние

**Что уже готово (закрытые дни):**
- Backend `/complaints/map?swLat=&swLon=&neLat=&neLon=&category=` (Day 7) — возвращает `MapMarker { id, category, status, latitude, longitude }`, для роли `RESIDENT`/гость фильтрует `status IN ('NEW','IN_PROGRESS','RESOLVED')`.
- Mobile auth-flow (Day 8) — Koin DI, `AuthState`-реактивный Navigator, `MainPlaceholderScreen` как заглушка, которая в Day 9 заменяется на `MapScreen`.
- Yandex MapKit SDK подключён в `composeApp/build.gradle.kts` (`libs.mapkit.android`), API-ключ через `BuildConfig.YANDEX_MAPS_API_KEY` из `local.properties`, `MapKitFactory.setApiKey` + `MapKitFactory.initialize` в `CleanCityApplication.onCreate`.
- Legacy `composeApp/src/androidMain/.../legacy/YandexMapView.android.kt` и `MapSearchProvider.android.kt` — закомментированный код от старой 4-категорной модели. **Удаляем в Day 9** (не реюзим, переписываем под backend и 18 категорий).

**KMP-таргеты:** только `androidTarget`. iOS-исходников и iosMain-директории нет — `expect/actual` используем только там, где иначе нельзя (нативная обёртка MapView). iOS-стабы не делаем.

## 3. Scope Day 9

**Входит:**
- `MapScreen` — карта Сочи с тайлами Yandex MapKit, навигация, lifecycle.
- Стартовый запрос markers для дефолтного bbox Сочи на `onCreate` ScreenModel.
- Bbox-запросы `/complaints/map` при движении камеры, debounce 500мс, отмена inflight через `collectLatest`.
- Маркеры разных цветов по статусу (NEW=amber, IN_PROGRESS=blue, RESOLVED=green).
- Кластеризация (`ClusterizedPlacemarkCollection`, MapKit), `clusterRadius = 60`, распад на zoom ≥ 15.
- Категории-чипы: `[Все]` + топ-6 + `[Ещё ▾]`. Кнопка `Ещё` открывает modal bottom-sheet со всеми 18 категориями (single-select).
- Bottom-sheet превью при тапе на маркер: показывает то, что есть в `MapMarkerDto` (категория-локализованная + статус + координаты + disabled-кнопка «Открыть детально» с пометкой «Day 10»).
- FAB «📍 Моё местоположение» — lazy permission, при grant — `FusedLocationProviderClient.getLastLocation()` → camera move.
- FAB «➕ Сообщить о проблеме» — навигация на `CreateComplaintPlaceholderScreen` (текст «В разработке — Day 11», back возвращает на MapScreen с сохранённым state).
- Топ-бар «Чистый Город» + меню «⋮» с пунктом Logout.
- Обработка ошибок: 5xx/network → snackbar, markers не очищаются. 401 — обрабатывается существующим `AuthInterceptor`.

**Не входит (deferred):**
- Поиск адреса (`SearchManager.suggest` / `submit`) — нужен только в Day 11 (CreateComplaintScreen).
- Pull-to-refresh — лишний UX-элемент: bbox-запрос и так триггерится движением камеры.
- BottomNavigation (Карта/Лента/Создать/Уведомления/Профиль) — Day 10, когда появится FeedScreen.
- Подгрузка деталей жалобы в превью-sheet (фото, votes_count, адрес, описание) — Day 10 вместе с `ComplaintDetailScreen`.
- iOS-таргет — никогда (Android-only релиз).

## 4. Архитектура

### 4.1 Файловая структура

```
composeApp/src/
├── commonMain/kotlin/com/example/cleancity/
│   ├── data/
│   │   ├── api/ComplaintsApi.kt              CREATE  interface + KtorComplaintsApi(httpClient, tokenStorage), suspend getMapMarkers(bbox, category): MapMarkersResponse
│   │   └── model/
│   │       ├── MapMarkerDto.kt               CREATE  data class id, category: ComplaintCategory, status: ComplaintStatus, latitude, longitude (+ @Serializable)
│   │       ├── MapMarkersResponse.kt         CREATE  data class markers: List<MapMarkerDto>
│   │       ├── ComplaintCategory.kt          CREATE  enum с 18 значениями (соответствует backend) + displayName(): String, hexColor неактуален для category — это для статуса
│   │       └── ComplaintStatus.kt            CREATE  enum NEW, IN_PROGRESS, RESOLVED, REJECTED, DUPLICATE + pinColor(): Long
│   ├── platform/
│   │   └── LocationProvider.kt               CREATE  expect class LocationProvider { suspend fun getLastKnownLocation(): Result<Location> }; data class Location(lat, lon)
│   ├── ui/permission/
│   │   └── LocationPermission.kt             CREATE  @Composable expect fun rememberLocationPermission(): LocationPermissionController (status, launchRequest)
│   ├── di/
│   │   └── AppModule.kt                      MODIFY  + factory<MapScreenModel>, single<ComplaintsApi> { KtorComplaintsApi(get(), get()) }, single<LocationProvider>
│   └── ui/feature/map/
│       ├── MapScreen.kt                      CREATE  Voyager Screen, TopAppBar + chips + YandexMapHost + FABs + sheets
│       ├── MapScreenModel.kt                 CREATE  Voyager ScreenModel, StateFlow<MapUiState>, debounced bbox channel
│       ├── MapUiState.kt                     CREATE  data class (см. §5.1)
│       ├── YandexMapHost.kt                  CREATE  @Composable expect fun (camera, markers, onCameraMoved, onMarkerClick, onClusterTap)
│       └── components/
│           ├── CategoryFilterChips.kt        CREATE  LazyRow с 8 chip + onClick
│           ├── CategorySheet.kt              CREATE  ModalBottomSheet с LazyColumn 18 категорий, radio
│           ├── MarkerPreviewSheet.kt         CREATE  BottomSheet с category+status+lat/lon + disabled-кнопка
│           └── MapFabGroup.kt                CREATE  Column из 2 FAB (location mini + create extended)
├── androidMain/kotlin/com/example/cleancity/
│   ├── MainActivity.kt                       MODIFY  + MapKitFactory.getInstance().onStart/onStop в onStart/onStop
│   ├── platform/LocationProvider.android.kt  CREATE  actual class LocationProvider(context) — обёртка над FusedLocationProviderClient.lastLocation
│   ├── ui/permission/LocationPermission.android.kt  CREATE  actual @Composable rememberLocationPermission() — внутри rememberLauncherForActivityResult(RequestPermission)
│   └── ui/feature/map/
│       └── YandexMapHost.android.kt          CREATE  AndroidView обёртка MapView + ClusterizedPlacemarkCollection + DisposableEffect lifecycle
├── composeApp/src/androidMain/AndroidManifest.xml  MODIFY  + ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION
└── composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/
    └── MapScreenModelTest.kt                 CREATE  unit-тесты ScreenModel + FakeComplaintsApi (см. §7)
```

**Удалить:**
- `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/YandexMapView.android.kt`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/MapSearchProvider.android.kt`
- (Директория `legacy/` остаётся, если в ней есть другие parked-файлы; иначе тоже удалить.)

### 4.2 expect/actual

`YandexMapHost.kt` (commonMain):
```kotlin
@Composable
expect fun YandexMapHost(
    cameraPosition: CameraPosition,
    markers: List<MapMarkerDto>,
    onCameraMoved: (BoundingBox) -> Unit,   // вызывается на onCameraIdle
    onMarkerClick: (markerId: String) -> Unit,
    onClusterTap: (BoundingBox) -> Unit,    // для zoom-in на кластер
    modifier: Modifier = Modifier,
)
```

`YandexMapHost.android.kt` — `AndroidView` с `MapView`, рисует pin'ы битмапом (`createPinBitmap(color)` как в legacy), регистрирует `CameraListener`, `MapObjectTapListener`, `ClusterListener`, lifecycle через `DisposableEffect`.

iOS — нет (нет iosMain).

### 4.3 DI

В `AppModule`:
```kotlin
single<ComplaintsApi> { KtorComplaintsApi(get(), get()) }   // HttpClient, AuthTokenStorage
factory { MapScreenModel(get()) }                            // ComplaintsApi
```

## 5. Data flow и state

### 5.1 MapUiState

```kotlin
data class MapUiState(
    val cameraPosition: CameraPosition = SOCHI_CENTER,        // 43.5855, 39.7231, zoom=12
    val markers: List<MapMarkerDto> = emptyList(),
    val selectedCategory: ComplaintCategory? = null,          // null = "Все"
    val selectedMarkerId: String? = null,
    val isCategorySheetOpen: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastKnownLocation: Location? = null,                  // если резидент уже запросил и получил
)
// PermissionStatus (Granted/Denied/NotRequested) НЕ хранится в MapUiState —
// его источник истины — Composable rememberLocationPermission() (§5.4),
// передаётся в screenModel через колбэк только в момент тапа FAB.

object SochiDefaults {
    val CENTER = CameraPosition(43.5855, 39.7231, zoom = 12f)
    val BBOX = BoundingBox(swLat = 43.40, swLon = 39.55, neLat = 43.75, neLon = 40.05)
}
```

### 5.2 Поток bbox-запросов

```
YandexMapHost (Android) → onCameraIdle(VisibleRegion)
   ↓
MapScreenModel.onCameraMoved(bbox)
   ↓ MutableSharedFlow<BoundingBox>.emit(bbox)
   ↓ .debounce(500.milliseconds)
   ↓ .combine(selectedCategoryFlow) { bbox, cat -> bbox to cat }
   ↓ .mapLatest { (bbox, cat) -> api.getMapMarkers(bbox, cat) }   // collectLatest отменяет inflight
   ↓ uiState.update { it.copy(markers = response.markers, isLoading = false, error = null) }
```

**Стартовый запрос:** в `init` ScreenModel'а делаем явный `onCameraMoved(SochiDefaults.BBOX)` — единый кодовый путь, никакого специального case для cold-start.

**Смена категории:** `selectedCategoryFlow` — отдельный `StateFlow`, обновление триггерит немедленный запрос (combine + последний bbox), debounce не применяется к смене категории (реакция на user action).

**Tap на чип, уже выбранный:** `selectedCategory = null` (сброс на «Все»).

### 5.3 Tap-сценарии

- **Tap маркера** → `onMarkerClick(id)` → `state.copy(selectedMarkerId = id)` → `MarkerPreviewSheet` показывает данные из `state.markers.first { it.id == id }`. Кнопка «Открыть детально» — `enabled = false`, tooltip «Скоро — Day 10».
- **Tap кластера** → `onClusterTap(bbox)` → `state.copy(cameraPosition = cameraFor(bbox, deltaZoom = +1.5f))` → MapView перемещается, кластер распадается.
- **Tap пустого места карты** → закрыть открытый sheet (если есть).

### 5.4 Геолокация (FAB 📍)

Две expect/actual точки расширения:

```kotlin
// commonMain — данные
expect class LocationProvider {
    suspend fun getLastKnownLocation(): Result<Location>
}
data class Location(val lat: Double, val lon: Double)

// commonMain — UI-permission
@Composable
expect fun rememberLocationPermission(): LocationPermissionController

class LocationPermissionController(
    val status: PermissionStatus,                              // Granted | Denied | NotRequested
    val launchRequest: () -> Unit,                             // показать системный диалог
)
```

Android-actual `rememberLocationPermission` — обёртка над `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` + `ContextCompat.checkSelfPermission` при первом вызове. Без Accompanist (не подключён, не подключаем).

`MapScreen` (Composable):
1. Получает `permission = rememberLocationPermission()`, передаёт `permission.status` в `MapScreenModel` через колбэк.
2. Тап FAB «📍» → `screenModel.onLocationFabClicked(permission.status, permission.launchRequest)`.
3. ScreenModel:
   - `NotRequested` → вызывает `permission.launchRequest()` (через колбэк) → ждёт перерисовку с новым `status`.
   - `Granted` → `viewModelScope.launch { locationProvider.getLastKnownLocation().onSuccess { state.copy(cameraPosition = CameraPosition(it.lat, it.lon, zoom = 15f)) } }`.
   - `Denied` → snackbar «Разрешите геолокацию в настройках».

Permission запрашиваем **только по тапу FAB** (lazy), не на старте экрана.

### 5.5 Error handling

| Сценарий | Поведение |
|---|---|
| HTTP 5xx, IOException, timeout | snackbar «Не удалось загрузить жалобы», `state.error` установлен, `state.markers` НЕ очищается (показываем последние известные) |
| HTTP 401 (резидент) | существующий `AuthInterceptor` редиректит на LoginScreen |
| Пустой `markers: []` | не ошибка, карта без пинов, без snackbar |
| Permission denied для location | snackbar «Разрешите геолокацию в настройках» |

## 6. UI

### 6.1 Layout

```
┌─────────────────────────────────────┐
│ TopAppBar: "Чистый Город"      ⋮   │   меню → Logout
├─────────────────────────────────────┤
│ [Все] [Мусор] [Дороги] [Освещ.]    │   CategoryFilterChips (LazyRow)
│ [Озел.] [Тротуары] [Благоустр.] [Ещё ▾] │
├─────────────────────────────────────┤
│                                     │
│         🗺  YandexMapHost           │
│         (markers + clusters)        │
│                                     │
│                            ┌──┐    │
│                            │📍│    │   FAB location (small)
│                            └──┘    │
│                            ┌──┐    │
│                            │ ➕│    │   FAB create (extended)
│                            └──┘    │
└─────────────────────────────────────┘
```

При тапе маркера снизу выезжает `MarkerPreviewSheet` (Material 3 `ModalBottomSheet`).

### 6.2 Категории-чипы

Топ-6 в видимой строке (упорядочены по ожидаемой частоте обращений в SPEC):
1. Мусор и санитарное состояние
2. Дороги и ямы
3. Уличное освещение
4. Озеленение и деревья
5. Тротуары и пешеходные зоны
6. Благоустройство территорий

`[Ещё ▾]` → `CategorySheet` (modal bottom sheet, LazyColumn все 18 категорий, radio-выбор, кнопка «Применить»). Если выбрана категория из «Ещё», лейбл чипа `Ещё` меняется на её название.

Single-select (backend API принимает один `category`). Повторный тап на выделенный чип сбрасывает фильтр на «Все».

### 6.3 Маркеры

- Круглый pin 32dp, белая обводка 3dp, цвета:
  - `NEW` → amber `#F59E0B`
  - `IN_PROGRESS` → blue `#3B82F6`
  - `RESOLVED` → green `#10B981`
- Рисуем `Bitmap` через `Canvas` в `createPinBitmap(color)` (адаптация helper'а из legacy-кода).

### 6.4 Кластеры

- `ClusterizedPlacemarkCollection`, `clusterRadius = 60.0`, `minZoom = 15`.
- Кластер-иконка: круг 48dp белый с цифрой количества (тёмно-серый текст), окантовка 3dp цвета доминирующего статуса в кластере.
- Tap кластера → camera move на `cluster.appearance.geometry.boundingBox` с `+1.5` zoom.

### 6.5 Loading states

- **Первый запрос (markers пуст):** полупрозрачный overlay 50% с `CircularProgressIndicator` в центре карты.
- **Последующие:** тонкий `LinearProgressIndicator` под TopAppBar, карта остаётся интерактивной.

### 6.6 MapKit lifecycle

- `MapKitFactory.initialize` — уже в `CleanCityApplication.onCreate`.
- В `MainActivity`: `override fun onStart() { super.onStart(); MapKitFactory.getInstance().onStart() }` и аналогично `onStop()`.
- Lifecycle самого `MapView` внутри `YandexMapHost.android.kt` — через `DisposableEffect(lifecycleOwner)` с наблюдателем `Lifecycle.Event.ON_START/ON_STOP` → `mapView.onStart()/onStop()`.

### 6.7 Manifest

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<!-- android.permission.INTERNET уже есть -->
```

## 7. Тестирование

### 7.1 Unit-тесты (commonTest, kotlin.test + Turbine + kotlinx-coroutines-test)

Файл `MapScreenModelTest.kt`. `FakeComplaintsApi` — in-memory реализация интерфейса с возможностями: вернуть конкретный список, выбросить ошибку, ответить с задержкой.

Сценарии:
1. `onCameraMoved дебаунсит 500мс` — эмит 5 bbox за 200мс, проверяем что `FakeComplaintsApi.getMapMarkers` вызван **1 раз** с последним bbox.
2. `onCameraMoved отменяет inflight запрос` — fake возвращает bbox#1 с задержкой 800мс, в 400мс эмитим bbox#2; проверяем что в `state.markers` итоговый ответ для bbox#2, не bbox#1.
3. `смена selectedCategory триггерит немедленный запрос` (без debounce).
4. `onMarkerClick устанавливает selectedMarkerId`.
5. `closeSheet сбрасывает selectedMarkerId`.
6. `error не очищает markers` — после успеха кладём 5 markers, fake кидает IOException на следующий запрос, проверяем `state.markers.size == 5` и `state.error != null`.
7. `повторный тап на выделенную категорию сбрасывает selectedCategory на null`.

### 7.2 Не тестируем юнитами

- `YandexMapHost` (нативный interop, моки MapKit нерентабельны).
- Stateless UI-компоненты (`CategoryFilterChips`, `CategorySheet`, `MarkerPreviewSheet`, `MapFabGroup`).
- Permission flow (фреймворочно-зависимый).

Эти зоны покрываются ручным smoke-тестом на эмуляторе (§8).

### 7.3 Backend integration test — optional

`ComplaintsApiIntegrationTest` против docker-compose backend на `:8081` — создать через POST 3 жалобы с координатами Сочи, дёрнуть `GET /complaints/map` с bbox охватывающим город, проверить, что вернулись все 3 с корректными координатами и категориями.

Выполняем только если остаётся время после §7.1 и §8.

## 8. Checkpoint (smoke на Medium_Phone AVD против docker-compose backend на :8081)

| # | Сценарий | Ожидание |
|---|----------|----------|
| 1 | Запустить приложение → авторизоваться → попасть на MapScreen | Карта Сочи отрисовалась, маркеры дефолтного bbox видны (если в БД есть жалобы) |
| 2 | Создать через Postman 3 жалобы разных категорий и статусов в центре Сочи → сдвинуть камеру / перезайти | После debounce 500мс и успешного ответа API маркеры появились с правильными цветами |
| 3 | Tap чип «Мусор» → камера не двигается | Маркеры мгновенно перерисовываются — только категория «Мусор» |
| 4 | Tap «Ещё» → выбрать категорию из 18 → «Применить» | Лейбл чипа «Ещё» меняется на выбранную, фильтр применился |
| 5 | Tap маркера | BottomSheet с категорией+статусом+координатами, кнопка «Открыть детально» disabled |
| 6 | Zoom-out до zoom 12 → точки сгруппированы в кластеры с цифрами | Кластеры видны вместо отдельных маркеров |
| 7 | Tap кластера | Камера зумится внутрь, кластер распадается на отдельные маркеры |
| 8 | Tap FAB «📍» → grant permission → камера летит к локации эмулятора | Камера переместилась (эмулятор шлёт фейк-координаты — это OK для smoke) |
| 9 | Deny permission | Snackbar «Разрешите геолокацию в настройках» |
| 10 | Tap FAB «➕» → открывается `CreateComplaintPlaceholderScreen` → back | Возврат на MapScreen, состояние сохранено |
| 11 | Гостевой режим (выход → «Зайти как гость») | MapScreen открывается, маркеры только `NEW/IN_PROGRESS/RESOLVED` (backend сам фильтрует) |
| 12 | Поворот экрана при открытом sheet | Sheet и `selectedMarkerId` восстановлены |

## 9. Известные ограничения / deferred

| Что | Куда отложено | Почему |
|---|---|---|
| Поиск адреса (SearchBar наверху карты, см. mockup) | Day 11 | Реализуется вместе с `CreateComplaintScreen` (нужен SearchManager.suggest/submit), на Day 9 не требуется |
| BottomNavigation (4 таба: Лента/Карта/Уведомл./Профиль из mockup) | Day 10 | Появится с `FeedScreen`. На Day 9 logout сидит во временной TopAppBar→DropdownMenu, на Day 10 уйдёт в Profile-таб |
| Подгрузка деталей жалобы в превью-sheet | Day 10 | Зависит от `ComplaintDetailScreen` и `ComplaintApi.getById` |
| Pull-to-refresh | Не делаем | Bbox-запрос триггерится движением камеры, отдельный жест не нужен |
| iOS-таргет | Никогда | Релиз только Android |
| Локализация category captions | После защиты | Все строки hardcoded русские (как в Day 8 — i18n debt) |

## 10. Зависимости от других дней

**Этот spec требует:**
- Backend Day 7 (endpoint `/complaints/map`) ✅ закрыт
- Mobile Day 8 (auth, Koin DI, Voyager Navigator) ✅ закрыт

**Этот spec предоставляет хуки для:**
- Day 10 — `MarkerPreviewSheet` получит кнопку «Открыть» enabled с переходом на `ComplaintDetailScreen`; вокруг `MapScreen` появится `BottomNavigationScaffold`.
- Day 11 — `CreateComplaintPlaceholderScreen` будет заменён на реальный `CreateComplaintScreen`; FAB «➕» начнёт принимать стартовые координаты из текущей camera position.

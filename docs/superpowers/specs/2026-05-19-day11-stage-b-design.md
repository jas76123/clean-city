# Day 11 Stage B — Address Picker + Inline Suggest

**Status:** approved 2026-05-19 (brainstorm-сессия)
**Owner:** Жасмин
**Plan code-ref:** `docs/PLAN.md` §«День 11 (18.05) — CreateComplaintScreen» (Stage B — добавление к закрытому Stage A)
**Branch:** `day11-create-complaint`
**Base commits:** `b44a2b8` (Day 11 core) + `0746268` (Stage A fixes)

## 1. Цель

Дать пользователю CreateComplaintScreen два дополнительных способа указать адрес жалобы помимо GPS:
- **inline address suggest** в поле адреса (autocomplete Яндекс.Поиска, ограниченный bbox Сочи);
- **fullscreen MapPicker** с пином в центре и автоматическим reverse-geocoding при остановке камеры.

Оба пути приводят к одному и тому же месту в state (`latitude/longitude/address/district`). `LocationStatus` (источник GPS) дополняется отдельным полем `AddressSource` для пользовательского hint'а «откуда взят адрес».

## 2. Контекст и текущее состояние

**Закрыто (Stage A, коммит `0746268`):**
- `CreateComplaintScreen.AddressSection` — `OutlinedTextField` + статус-строка под ним.
- `CreateComplaintScreenModel` уже хранит `latitude/longitude/address/district/locationStatus` и реализует GPS-путь: permission → `LocationProvider.getLastKnownLocation()` → `searchProvider.reverseGeocode(lat, lon)` подгружает `address` (если поле пустое) + `district`.
- `MapSearchProvider` интерфейс с `suggest(query, region): List<MapSuggestion>` и `reverseGeocode(lat, lon): Result<ReverseGeocodeResult>`; Android-impl на Yandex `SearchManager`.
- `YandexMapHost` (expect/actual) — Compose-обёртка над Yandex MapView с параметрами `cameraPosition`, `markers: List<MapMarker>`, `onCameraMoved(BoundingBox)`, `onMarkerClick`, `onClusterTap`.
- `MapSearchBar.kt` — кастомный suggest-UI для главного MapScreen: `OutlinedTextField` + Surface c LazyColumn ниже. Внутри есть `SuggestionRow`.
- 82 unit-теста зелёные перед Stage B.

**Не делалось / неудобства Stage A:**
- В поле адреса нет автоподсказок — пользователь должен либо доверять GPS, либо вручную писать адрес. Координаты при ручном вводе не обновляются → `canSubmit=false` если GPS не сработал.
- Нет способа указать место на карте, если оно не имеет читаемого адреса (пустырь, точка между домами).

## 3. Scope Stage B

**Входит:**
- Inline address suggest в `AddressSection` — debounced (300 мс) вызов `MapSearchProvider.suggest`, список до 6 элементов Surface ниже поля.
- Fullscreen `MapPickerScreen` — карта на весь экран, неподвижный Compose-пин по центру, debounced reverseGeocode (300 мс) при остановке камеры, address pill над пином с loader/текстом, кнопка «Подтвердить адрес».
- Возврат результата из MapPicker → `CreateComplaintScreen` через Koin-singleton `AddressPickerBus` (SharedFlow).
- Новое поле `addressSource: AddressSource` в state — определяет hint в status row («Адрес выбран на карте» / «из подсказок» / «Геопозиция определена автоматически»).
- Расширенный duplicate-check trigger: после tap по suggest и после bus emit повторно вызывается `scheduleDuplicatesCheck()` (так как координаты сменились).
- Тесты для всех трёх путей (GPS / Suggest / Picker) — координаты, address, district попадают в state; ручное редактирование не сбрасывает координаты; clear-поля сбрасывает.

**Не входит (deferred / out of scope):**
- Поиск адреса **внутри** MapPickerScreen — только пин по центру (решение по brainstorm-сессии).
- Анимации пина / bounce-эффект при подтверждении.
- Кэширование reverseGeocode-результатов между сессиями.
- История suggest («ранее выбранные адреса»).
- Read-only поле после выбора адреса — поле остаётся редактируемым (правки текста не сбрасывают координаты).
- iOS impl — Android-only релиз, `YandexMapHost` уже expect/actual без iosMain.

## 4. Архитектура

### 4.1 Файловая структура

```
composeApp/src/commonMain/kotlin/com/example/cleancity/
├── ui/feature/map/
│   ├── MapConstants.kt                              CREATE  SOCHI_CENTER_LAT/LON, SOCHI_BBOX (BoundingBox 43..44 / 39..41)
│   ├── components/
│   │   └── AddressSuggestionList.kt                 CREATE  вынесенный из MapSearchBar Surface+LazyColumn+SuggestionRow, переиспользуется и в AddressSection
│   ├── components/MapSearchBar.kt                   EDIT    использует AddressSuggestionList вместо локального SuggestionRow (visual identical)
│   └── picker/
│       ├── AddressPickerBus.kt                      CREATE  class AddressPickerBus + data class PickedAddress(lat, lon, address, district?)
│       ├── MapPickerScreen.kt                       CREATE  Voyager Screen с initialLat/initialLon: Double?
│       └── MapPickerScreenModel.kt                  CREATE  state(currentLat, currentLon, address?, district?, isResolving) + onCameraMoved + confirm
├── ui/feature/create/
│   ├── CreateComplaintScreenModel.kt                EDIT    state: +addressSource, +suggestions, +isSuggesting; methods: onAddressChanged расширен (debounced suggest, downgrade source), onSuggestionTapped, init bus collect, scheduleDuplicatesCheck вызывается из обоих новых путей
│   └── CreateComplaintScreen.kt                     EDIT    AddressSection переписана: suggest inline, кнопка «Выбрать на карте», hint учитывает addressSource
└── di/AppModule.kt                                  EDIT    single { AddressPickerBus() }; factory { (lat: Double?, lon: Double?) -> MapPickerScreenModel(lat, lon, get(), get()) }

composeApp/src/commonTest/kotlin/com/example/cleancity/
├── ui/feature/create/CreateComplaintScreenModelTest.kt   EDIT  +11 тестов
└── ui/feature/map/picker/MapPickerScreenModelTest.kt     CREATE +9 тестов
```

### 4.2 Поток данных

```
                 ┌── CreateComplaintScreen ──┐
                 │  AddressSection           │
                 │   • TextField (suggest)   │
                 │   • Кнопка "На карте"     │
                 └──┬───────────────────────▲┘
collect bus.results │                       │ navigator.push(MapPickerScreen(lat?, lon?))
                    ▼                       │
              CreateComplaintScreenModel    │
                    ▲                       │
           bus.emit │                  MapPickerScreen
                    │                  + MapPickerScreenModel
                    │                       │
                    └─ AddressPickerBus ◄───┘
                       (Koin single,
                        SharedFlow<PickedAddress>)
```

Три пути ввода адреса, одно и то же место state:

| Путь | Триггер | Что попадает в state |
|---|---|---|
| GPS | permission granted, `LocationProvider.getLastKnownLocation()` | `lat`, `lon`, `address` (если поле было пустое), `district`, `addressSource=Gps`, `locationStatus=Ready` |
| Suggest | пользователь печатает → `searchProvider.suggest(query, SOCHI_BBOX)` → tap | `lat`, `lon`, `address` = suggestion.title, `district` через дополнительный `reverseGeocode`, `addressSource=Suggest` |
| Picker | bus.emit из MapPickerScreen | `lat`, `lon`, `address`, `district` — всё из `PickedAddress` (district уже определён внутри picker'а) |

### 4.3 Решения по архитектуре (зафиксированы в brainstorm)

| Решение | Выбор | Альтернатива (отклонена) |
|---|---|---|
| Хост карты в picker | переиспользуем `YandexMapHost`, передаём `markers=emptyList()` | новый `MapPickerHost` (expect/actual) — лишние ~100 строк Android-кода |
| UI suggest | паттерн `MapSearchBar` (Column + Surface + LazyColumn) | `ExposedDropdownMenuBox` (Material3) — менее предсказуем в `verticalScroll` |
| Возврат результата | Koin-singleton `AddressPickerBus` с `SharedFlow<PickedAddress>` | callback в `data class MapPickerScreen(val onResult: ...)` — не сериализуется, проблемы с process death |
| Состояние «откуда адрес» | отдельное поле `AddressSource: enum` рядом с `LocationStatus` | расширение `LocationStatus` новым case — размывает смысл (status описывает GPS, не источник адреса) |
| Поведение при ручном редактировании после suggest/picker | координаты остаются, source остаётся; clear-поля → координаты null, source=None | read-only поле после выбора — мешает дописывать «кв. 5» |

## 5. State model

`CreateComplaintScreenModel.kt`:

```kotlin
data class CreateComplaintUiState(
    // existing fields:
    val photos: List<PhotoBytes> = emptyList(),
    val category: ProblemCategory? = null,
    val categoryQuery: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val district: String? = null,
    val description: String = "",
    val locationStatus: LocationStatus = LocationStatus.Idle,
    val duplicates: List<DuplicateCandidateResponse> = emptyList(),
    val isCheckingDuplicates: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    // NEW:
    val addressSource: AddressSource = AddressSource.None,
    val suggestions: List<MapSuggestion> = emptyList(),
    val isSuggesting: Boolean = false,
)

enum class AddressSource { None, Gps, Suggest, Picker }
```

`LocationStatus` (`Idle/Requesting/Ready/PermissionDenied/Failed`) **не изменяется**.

`canSubmit` остаётся прежним (`lat!=null && lon!=null && address.isNotBlank() && ...`) — `addressSource` на canSubmit не влияет.

### 5.1 Правила перехода `addressSource`

| Событие | Переход |
|---|---|
| GPS отработал, ничего вручную ещё не выбрано | `None → Gps` |
| tap по `MapSuggestion` | `* → Suggest` |
| bus emit `PickedAddress` | `* → Picker` |
| `onAddressChanged(text)`, `text.isNotEmpty()`, текущий source `Gps` | `Gps → None` (GPS-адрес «уточняется руками») |
| `onAddressChanged(text)`, `text.isNotEmpty()`, текущий source `Suggest`/`Picker` | без изменений |
| `onAddressChanged("")` | `* → None`, lat=lon=district=null, suggestions=[] |

### 5.2 Hint в status row (внутри Composable, не в state)

```kotlin
private fun addressHint(status: LocationStatus, source: AddressSource): String = when (source) {
    AddressSource.Picker -> "Адрес выбран на карте"
    AddressSource.Suggest -> "Адрес выбран из подсказок"
    AddressSource.Gps -> "Геопозиция определена автоматически"
    AddressSource.None -> when (status) {
        LocationStatus.Idle -> "Определяем геопозицию…"
        LocationStatus.Requesting -> "Получаем GPS…"
        LocationStatus.Ready -> "Геопозиция определена автоматически"
        LocationStatus.PermissionDenied -> "Нет разрешения на GPS — введите адрес вручную"
        is LocationStatus.Failed -> "GPS недоступен — введите адрес вручную"
    }
}
```

## 6. MapPickerScreen

### 6.1 Voyager Screen

```kotlin
data class MapPickerScreen(
    val initialLat: Double? = null,
    val initialLon: Double? = null,
) : Screen {
    @Composable override fun Content() { ... }
}
```

CreateComplaintScreen вызывает `navigator.push(MapPickerScreen(state.latitude, state.longitude))`. Если оба null → камера на `SOCHI_CENTER_LAT/LON`, zoom 12. Иначе zoom 16 на переданных координатах.

### 6.2 ScreenModel

```kotlin
class MapPickerScreenModel(
    private val initialLat: Double?,
    private val initialLon: Double?,
    private val searchProvider: MapSearchProvider,
    private val bus: AddressPickerBus,
) : ScreenModel {

    data class UiState(
        val currentLat: Double,
        val currentLon: Double,
        val address: String? = null,
        val district: String? = null,
        val isResolving: Boolean = false,
    )

    private val _state = MutableStateFlow(
        UiState(
            currentLat = initialLat ?: SOCHI_CENTER_LAT,
            currentLon = initialLon ?: SOCHI_CENTER_LON,
        )
    )
    val state = _state.asStateFlow()

    private var geocodeJob: Job? = null

    fun onCameraMoved(bbox: BoundingBox) {
        val lat = (bbox.minLatitude + bbox.maxLatitude) / 2
        val lon = (bbox.minLongitude + bbox.maxLongitude) / 2
        _state.update { it.copy(currentLat = lat, currentLon = lon, isResolving = true) }
        geocodeJob?.cancel()
        geocodeJob = screenModelScope.launch {
            delay(GEOCODE_DEBOUNCE_MS)
            searchProvider.reverseGeocode(lat, lon)
                .onSuccess { r ->
                    _state.update { it.copy(address = r.address, district = r.district, isResolving = false) }
                }
                .onFailure {
                    _state.update { it.copy(address = null, isResolving = false) }
                }
        }
    }

    fun confirm() {
        val s = _state.value
        val addr = s.address ?: return
        screenModelScope.launch {
            bus.publish(PickedAddress(s.currentLat, s.currentLon, addr, s.district))
        }
    }

    private companion object { const val GEOCODE_DEBOUNCE_MS = 300L }
}
```

### 6.3 UI layout

```
┌──────────────────────────────────────────┐
│ ← Выбрать адрес                          │ ← TopAppBar
├──────────────────────────────────────────┤
│                                          │
│     [Yandex map fills entire body]       │
│                                          │
│                ┌──────────────────┐      │
│                │ ул. Транспорт... │      │ ← address pill (Compose overlay)
│                └──────────────────┘      │   isResolving → "Определяем адрес…" + CPI
│                       📍                 │ ← pin (Icons.Default.LocationOn, primary, 48dp)
│                                          │   offsetY = -24dp (кончик пина в центре)
│                                          │
├──────────────────────────────────────────┤
│  ┌────────────────────────────────────┐  │
│  │       Подтвердить адрес            │  │ ← bottomBar, disabled пока address==null
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

- `Box(fillMaxSize)` →
  - `YandexMapHost(cameraPosition, markers=emptyList(), onCameraMoved = model::onCameraMoved, onMarkerClick = {}, onClusterTap = {}, modifier = Modifier.fillMaxSize())`
  - Compose overlay `Box(fillMaxSize, contentAlignment=Center)`:
    - `Surface` (pill) offsetY `(-56).dp` — содержимое зависит от `isResolving`/`address`
    - `Icon(LocationOn, tint=primary, size=48.dp)` offsetY `(-24).dp`
- Если `isResolving` — pill: `Row { CircularProgressIndicator(16.dp); Text("Определяем адрес…") }`
- Если `!isResolving && address != null` — pill: `Text(address, maxLines=2)`
- Если `!isResolving && address == null` (геокод упал) — pill: `Text("Адрес не определён, подвиньте карту")`, кнопка disabled

### 6.4 Koin

```kotlin
single { AddressPickerBus() }
factory { (lat: Double?, lon: Double?) ->
    MapPickerScreenModel(lat, lon, get(), get())
}
```

Получение в Composable:
```kotlin
val model = koinScreenModel<MapPickerScreenModel> { parametersOf(initialLat, initialLon) }
```

## 7. AddressSection (CreateComplaintScreen)

### 7.1 Сигнатура и layout

```kotlin
@Composable
private fun AddressSection(
    address: String,
    suggestions: List<MapSuggestion>,
    isSuggesting: Boolean,
    locationStatus: LocationStatus,
    addressSource: AddressSource,
    onAddressChange: (String) -> Unit,
    onSuggestionTap: (MapSuggestion) -> Unit,
    onMapPickerClick: () -> Unit,
)
```

```
┌────────────────────────────────────────────┐
│ АДРЕС                                      │
│ ┌────────────────────────────────────┐     │
│ │ ул. Транспортная, 14         [⊗]   │     │ ← OutlinedTextField, trailing clear icon
│ └────────────────────────────────────┘     │
│ (LinearProgressIndicator 1dp если isSuggesting && suggestions.isEmpty()) │
│ ┌────────────────────────────────────┐     │
│ │ ул. Транспортная, 14, Сочи        │     │ ← AddressSuggestionList (Surface + LazyColumn)
│ │ ул. Транспортная, 16, Сочи        │     │   только если suggestions.isNotEmpty()
│ │ ... (до 6 элементов)              │     │
│ └────────────────────────────────────┘     │
│ ┌────────────────────────────────────┐     │
│ │ 🗺  Выбрать на карте                │    │ ← OutlinedButton fullWidth
│ └────────────────────────────────────┘     │
│ 📍 Адрес выбран из подсказок                │ ← status row, hint() от source+status
└────────────────────────────────────────────┘
```

- TextField — `singleLine=true`, placeholder «ул. Транспортная, 14», trailing `Icons.Default.Close` если `address.isNotEmpty()` (тап → `onAddressChange("")`).
- LinearProgressIndicator 1dp под полем — пока запрос suggest идёт и список ещё пустой.
- `AddressSuggestionList` — общий компонент (вынесен из `MapSearchBar`), height `max 240.dp`, прокручивается. Тап → `onSuggestionTap(suggestion)`.
- OutlinedButton «Выбрать на карте» — leading icon `Icons.Default.Map`, fullWidth, под suggest-списком (или сразу под TextField, если список пуст).
- Status row остаётся в конце.

### 7.2 Изменения в ScreenModel

```kotlin
class CreateComplaintScreenModel(
    private val complaintsApi: ComplaintsApiContract,
    private val locationProvider: LocationProvider,
    private val searchProvider: MapSearchProvider,
    private val addressPickerBus: AddressPickerBus,   // NEW
) : ScreenModel {

    private var suggestJob: Job? = null

    init {
        screenModelScope.launch {
            addressPickerBus.results.collect { onPickedFromMap(it) }
        }
    }

    fun onAddressChanged(text: String) {
        _state.update { s ->
            val downgradeGps = s.addressSource == AddressSource.Gps && text.isNotEmpty()
            val cleared = text.isEmpty()
            s.copy(
                address = text,
                latitude = if (cleared) null else s.latitude,
                longitude = if (cleared) null else s.longitude,
                district = if (cleared) null else s.district,
                addressSource = when {
                    cleared -> AddressSource.None
                    downgradeGps -> AddressSource.None
                    else -> s.addressSource
                },
                suggestions = if (cleared) emptyList() else s.suggestions,
            )
        }
        scheduleSuggest(text)
    }

    private fun scheduleSuggest(query: String) {
        suggestJob?.cancel()
        if (query.length < SUGGEST_MIN_QUERY) {
            _state.update { it.copy(suggestions = emptyList(), isSuggesting = false) }
            return
        }
        suggestJob = screenModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            _state.update { it.copy(isSuggesting = true) }
            val result = runCatching { searchProvider.suggest(query, SOCHI_BBOX) }
            _state.update {
                it.copy(
                    suggestions = result.getOrNull() ?: emptyList(),
                    isSuggesting = false,
                )
            }
        }
    }

    fun onSuggestionTapped(s: MapSuggestion) {
        suggestJob?.cancel()
        _state.update {
            it.copy(
                address = s.title,
                latitude = s.latitude,
                longitude = s.longitude,
                district = null,
                addressSource = AddressSource.Suggest,
                suggestions = emptyList(),
                isSuggesting = false,
            )
        }
        screenModelScope.launch { reverseGeocode(s.latitude, s.longitude) }
        scheduleDuplicatesCheck()
    }

    private fun onPickedFromMap(p: PickedAddress) {
        _state.update {
            it.copy(
                address = p.address,
                latitude = p.latitude,
                longitude = p.longitude,
                district = p.district,
                addressSource = AddressSource.Picker,
                suggestions = emptyList(),
                isSuggesting = false,
            )
        }
        scheduleDuplicatesCheck()
    }

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 300L
        const val SUGGEST_MIN_QUERY = 2
    }
}
```

`onLocationPermissionGranted` дополняется: при успехе ставит `addressSource = AddressSource.Gps` (только если до этого был `None`).

`reverseGeocode(lat, lon)` (private existing) — без изменений: подгружает `address` если пустое и `district` всегда.

### 7.3 Композиция в Content

```kotlin
AddressSection(
    address = state.address,
    suggestions = state.suggestions,
    isSuggesting = state.isSuggesting,
    locationStatus = state.locationStatus,
    addressSource = state.addressSource,
    onAddressChange = model::onAddressChanged,
    onSuggestionTap = model::onSuggestionTapped,
    onMapPickerClick = {
        navigator.push(MapPickerScreen(state.latitude, state.longitude))
    },
)
```

## 8. Тесты

### 8.1 `CreateComplaintScreenModelTest` — новые тесты

| # | Тест | Что проверяет |
|---|---|---|
| 1 | `suggest fires after debounce when query length >= 2` | `onAddressChanged("ул")` → advance 300 ms → `fakeSearchProvider.suggest` вызван 1 раз, `state.suggestions` заполнен |
| 2 | `suggest skipped if query shorter than 2` | `onAddressChanged("у")` → suggest НЕ вызван, suggestions пуст |
| 3 | `tapping suggestion sets coords/address/source` | `onSuggestionTapped(s)` → `lat/lon/address` = `s.*`, `addressSource=Suggest` |
| 4 | `tapping suggestion loads district via reverseGeocode` | tap → `fakeSearchProvider.reverseGeocode` вызван, district в state |
| 5 | `bus emit applies PickedAddress to state` | `bus.publish(PickedAddress(...))` → 4 поля в state, `source=Picker`, `suggestions=[]` |
| 6 | `editing address after suggest keeps coords` | tap suggest → `onAddressChanged("foo")` → lat/lon остаются, address="foo", source=Suggest |
| 7 | `editing address after picker keeps coords` | bus.publish → `onAddressChanged("foo")` → lat/lon остаются, source=Picker |
| 8 | `clearing address resets coords and source` | tap suggest → `onAddressChanged("")` → lat=lon=null, source=None |
| 9 | `editing GPS address downgrades source to None` | GPS path → `onAddressChanged("xxx")` → source=None, lat/lon остаются (не очистка) |
| 10 | `duplicate check fires after suggestion tap when category set` | category выбран → tap suggest → advance debounce → `fakeApi.findDuplicates` вызван |
| 11 | `duplicate check fires after picker emit when category set` | category выбран → bus.publish → `fakeApi.findDuplicates` вызван |
| 12 | `district loaded in all three paths` | GPS / Suggest / Picker — три отдельных сценария, в каждом `state.district != null` |

### 8.2 `MapPickerScreenModelTest` — новый файл

| # | Тест | Что проверяет |
|---|---|---|
| 1 | `initial state uses provided lat/lon` | ctor с `(44.0, 40.0)` → `currentLat=44, currentLon=40` |
| 2 | `initial state defaults to Sochi center if null` | ctor с `(null, null)` → `currentLat/Lon` = `SOCHI_CENTER_*` |
| 3 | `onCameraMoved updates current coords and sets isResolving` | `onCameraMoved(bbox)` → центр bbox в state, `isResolving=true` |
| 4 | `reverseGeocode fires after debounce` | `onCameraMoved` → advance 300 ms → `fakeProvider.reverseGeocode` вызван |
| 5 | `multiple rapid onCameraMoved cancel previous geocode` | `onCameraMoved` × 3 быстро → advance → `reverseGeocode` вызван 1 раз с последними координатами |
| 6 | `successful geocode populates address+district, clears isResolving` | reverseGeocode success → address/district заполнены, isResolving=false |
| 7 | `failed geocode keeps address null, clears isResolving` | reverseGeocode failure → address=null, isResolving=false |
| 8 | `confirm publishes PickedAddress when address present` | address!=null → `confirm()` → `bus.publish` вызван 1 раз с правильными значениями |
| 9 | `confirm does nothing when address null` | address=null → `confirm()` → `bus.publish` НЕ вызван |

### 8.3 Test harness

- `runTest` + `TestDispatcher` (`StandardTestDispatcher`/`UnconfinedTestDispatcher` — выбор по существующему стилю Stage A тестов).
- Fakes:
  - `FakeMapSearchProvider` — конфигурируемый ответ для `suggest()` и `reverseGeocode()`, счётчики вызовов, возможность задержки.
  - `FakeAddressPickerBus` (или реальный `AddressPickerBus` — он не имеет внешних зависимостей, можно использовать прямо).
  - `FakeComplaintsApi` уже существует в тестах Stage A.
- Sochi BBox в тестах — реальная константа `SOCHI_BBOX`, проверяется что `suggest` вызван именно с ней.

Существующие 82 теста Stage A не модифицируются (только добавляется конструкторный параметр `addressPickerBus` — обновить вызовы конструктора в test-setup).

## 9. Риски и нерешённые точки

1. **`YandexMapHost.onCameraMoved` контракт на cameraIdle.** В Stage A коде на главном MapScreen `onCameraMoved` дёргается при перемещении камеры (передаёт новый BBox). Нужно убедиться, что событие приходит и после остановки камеры (debounce 300 ms внутри ScreenModel в любом случае поглотит частые вызовы во время drag). Проверять при реализации в `YandexMapHost.android.kt` — при необходимости добавить `CameraListener` с `CameraUpdateReason.GESTURES + finished=true`.

2. **Compose-overlay поверх Android MapView.** Yandex `MapView` — это `AndroidView`, поверх него Compose рисуется штатно через `Box`. Известно работающее: главный MapScreen уже накладывает FAB-кнопки и `MapSearchBar`. Дополнительной работы не требует.

3. **Voyager Screen с `Double?` параметрами и process death.** `data class MapPickerScreen(val initialLat: Double? = null, val initialLon: Double? = null)` — для in-memory navigation работает без `Parcelable`. На случай process death — потеря lat/lon приведёт к открытию picker'а с центром Сочи, что приемлемо для редкого сценария. Если оказывается критично — добавим `@Parcelize` (требует `kotlinx.parcelize` плагина).

4. **Точность центра BBox vs точный центр камеры.** Для квадратной области экрана в проекции Mercator в пределах Сочи `(min+max)/2` совпадает с геометрическим центром карты с погрешностью <1 пикселя. Не критично.

5. **`AndroidMapSearchProvider.suggest` с кириллицей/спецсимволами.** Yandex `SearchManager` принимает любую строку. Тестируется на устройстве вручную в день реализации.

6. **`AddressSuggestionList` выделение из `MapSearchBar`.** Текущий `SuggestionRow` использует `Gray500/Gray700/Green700` из `theme`. После выноса в общий компонент — `MapSearchBar` должен выглядеть идентично (визуальный регресс не допускается). Проверяется ручным запуском главного MapScreen после Stage B.

## 10. Локальный dev-loop (для имплементации)

Backend локально:
```bash
cd ~/Desktop/Myapp/cleancity-kmp
docker compose up -d db
set -a && source .env && set +a
export DB_URL="jdbc:postgresql://localhost:5433/cleancity"
export STORAGE_PATH="$PWD/uploads"
mkdir -p uploads
nohup ./gradlew :backend:run --no-daemon > /tmp/cleancity-backend.log 2>&1 &
```

Tests:
```bash
./gradlew :composeApp:testDebugUnitTest
```

APK на Samsung A33 (USB):
```bash
~/Library/Android/sdk/platform-tools/adb -s RZCW111EQWH reverse tcp:8081 tcp:8081
./gradlew :composeApp:assembleDebug
~/Library/Android/sdk/platform-tools/adb -s RZCW111EQWH install -r \
  composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

ADB reverse слетает после reboot устройства — проверять `adb -s RZCW111EQWH reverse --list`.

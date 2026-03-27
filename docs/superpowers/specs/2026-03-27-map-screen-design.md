# Map Screen — Design Specification

## Overview

Full implementation of the Map screen for CleanCity KMP app. The map displays urban problems (dumps, potholes, broken lights, greenery issues) and cleanup events (subbotniks) on a Yandex MapKit map centered on Sochi. Users can search addresses, filter markers, view details via bottom sheets, create new markers, and vote on problem resolution.

## Architecture

### Platform Strategy

- **expect/actual pattern** for the native map widget
- **Android:** `actual` implementation via `AndroidView` wrapping Yandex MapKit (`com.yandex.android:maps.mobile:4.25.0-full`)
- **iOS:** `actual` stub (placeholder text "Карта будет доступна позже") — real implementation deferred
- **All UI logic** (bottom sheets, filters, search bar, create marker panel, voting) lives in **commonMain** as shared Compose code

### Yandex MapKit Configuration

- **API Key:** `__YANDEX_MAPS_KEY_REDACTED__`
- **Initial camera:** Sochi — latitude 43.585, longitude 39.723, zoom 14
- **Permissions (Android):** `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`

## Components

### 1. Native Map Widget (expect/actual)

**Common interface** (`commonMain`):
```
expect/actual composable: YandexMapView(
    state: MapState,
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    onMapTap: (lat: Double, lon: Double) -> Unit,
    onCameraMove: () -> Unit
)
```

**MapState** holds: camera position, zoom, user location (nullable).

**MapMarker** holds: id, lat, lon, type (PROBLEM/EVENT/RESOLVED), title, problemType (nullable).

**Android actual:** `AndroidView` with `MapView`, adds placemarks via `mapObjects.addPlacemark()`. Colored pins:
- Red — active problems (DUMP, HOLES, LIGHTING, GREENERY)
- Green — resolved problems
- Amber — problems in work
- Purple (rounded square) — cleanup events (subbotniks)

Blue pulsing dot for user location via `UserLocationLayer`.

**iOS actual:** `Box` with centered `Text("Карта будет доступна позже")`.

### 2. Search Bar

- Positioned at top of screen, overlaying the map
- Text input with search icon (left)
- On input (3+ characters): call Yandex `SuggestSession` for address suggestions
- Show dropdown list of up to 5 suggestions
- On suggestion tap: move camera to selected coordinates, dismiss dropdown

**Search is platform-specific** (Yandex SearchFactory on Android, stub on iOS). Use an expect/actual `MapSearchProvider` interface:
- `expect fun createMapSearchProvider(): MapSearchProvider`
- Interface: `suspend fun suggest(query: String, region: BoundingBox): List<SearchSuggestion>`
- `SearchSuggestion`: common data class with `title: String`, `subtitle: String?`, `lat: Double`, `lon: Double`
- Android actual: wraps `SuggestSession` from Yandex SearchFactory
- iOS actual: returns empty list (stub)

**Common state:** `searchQuery: String`, `suggestions: List<SearchSuggestion>`, `isSearchActive: Boolean`

### 3. Filter Chips

- Horizontal scrollable row below search bar
- Chips: "Все" (default active), "Проблемы", "Субботники", "Решённые"
- Single-selection — filters visible markers on map
- Styled with backdrop blur effect, pill-shaped

**State:** `activeFilter: MapFilter` enum (ALL, PROBLEMS, EVENTS, RESOLVED)

### 4. FAB Group

- Vertical stack, positioned bottom-right above bottom nav
- **Primary FAB (green "+" icon):** opens Create Marker panel
- **Secondary FAB (white crosshair icon):** moves camera to user's current location

### 5. Legend Panel

- Small floating panel, right side below search area
- Shows color coding: red = problem, green = resolved, purple = subbotnik
- Semi-transparent dark background with blur

### 6. Bottom Sheet — Problem Details

Appears when tapping a problem marker. Slides up from bottom.

**Content:**
- Drag handle
- Title (problem name)
- Status badge (Новая / В работе / Решено)
- Address + author info row
- Description text
- Verification row: "N подтверждений" + "Подтвердить" button. Shows "Официальная" when verifications >= 3.
- **Voting section "Проблема решена?":**
  - Two buttons: "Да · N%" and "Нет · N%"
  - Vote count display
  - Threshold info: "70%+ Да → статус Решена"
- "Подробнее" button (navigates to complaint detail screen — future)

**State:** `selectedMarker: MapMarker?`, `isBottomSheetVisible: Boolean`

### 7. Bottom Sheet — Subbotnik Details

Appears when tapping a subbotnik (event) marker.

**Content:**
- Event name, emoji
- Date and time
- Participant count
- "Присоединиться" button

### 8. Create Marker Panel

Bottom sheet that appears when tapping the "+" FAB.

**Content:**
- Header: "Новая метка" + close button
- **Type selector:** 2x2 grid of options:
  - Свалка (dump icon)
  - Яма (pothole icon)
  - Освещение (light icon)
  - Озеленение (greenery icon)
- **Description:** multiline text field
- **Photo upload zone:** dashed border area, tap to select photo. Shows preview with metadata after selection.
- **Privacy consent:** checkbox + ФЗ-152 text
- **Submit button:** "Отправить" — creates marker at current map center or tapped position

**State:** `isCreateMode: Boolean`, `selectedType: ProblemType?`, `description: String`, `hasPhoto: Boolean`, `privacyConsent: Boolean`

### 9. Bottom Navigation

5 tabs: Лента, **Карта** (active), Уведомления (badge count), Чаты, Профиль.

Uses Voyager tab navigation. Badge on notifications tab shows unread count from repository.

## Data Flow

```
InMemoryRepository (existing)
    │
    ├── problems: StateFlow<List<Problem>>  ──→  mapped to MapMarker list
    ├── events: StateFlow<List<CleanupEvent>> ──→  mapped to MapMarker list
    └── currentUser: StateFlow<User?>
         │
    MapScreenViewModel / MapScreenModel (Voyager ScreenModel)
         │
         ├── markers (filtered by activeFilter)
         ├── selectedMarker
         ├── searchQuery / suggestions
         ├── createMode state
         └── voting state
         │
    MapScreen composable
         ├── YandexMapView (expect/actual)
         ├── SearchBar
         ├── FilterChips
         ├── FABGroup
         ├── Legend
         ├── BottomSheet (problem / event)
         └── CreateMarkerPanel
```

## File Structure

```
composeApp/src/
├── commonMain/kotlin/com/example/cleancity/
│   ├── ui/
│   │   ├── map/
│   │   │   ├── MapScreen.kt              (main screen composable)
│   │   │   ├── MapScreenModel.kt         (Voyager ScreenModel, state management)
│   │   │   ├── MapState.kt               (data classes: MapState, MapMarker, MapFilter)
│   │   │   ├── components/
│   │   │   │   ├── MapSearchBar.kt
│   │   │   │   ├── MapFilterChips.kt
│   │   │   │   ├── MapFabGroup.kt
│   │   │   │   ├── MapLegend.kt
│   │   │   │   ├── ProblemBottomSheet.kt
│   │   │   │   ├── EventBottomSheet.kt
│   │   │   │   ├── CreateMarkerPanel.kt
│   │   │   │   └── VotingSection.kt
│   │   │   └── YandexMapView.kt          (expect declaration)
│   │   ├── navigation/
│   │   │   └── MainTabScreen.kt          (bottom nav with 5 tabs)
│   │   └── components/
│   │       └── BottomNavBar.kt           (shared bottom nav component)
│   └── platform/
│       └── Platform.kt                   (existing, add map-related expects if needed)
├── androidMain/kotlin/com/example/cleancity/
│   ├── ui/map/
│   │   └── YandexMapView.android.kt      (actual: AndroidView + MapKit)
│   ├── CleanCityApplication.kt           (MapKitFactory.setApiKey + initialize)
│   └── platform/Platform.android.kt      (existing)
└── iosMain/kotlin/com/example/cleancity/
    └── ui/map/
        └── YandexMapView.ios.kt          (actual: stub placeholder)
```

## Dependencies to Add

**gradle/libs.versions.toml:**
- Already present: `mapkit-android = "4.25.0-full"` (verify version)

**AndroidManifest.xml:**
- Add `<meta-data android:name="com.yandex.mapkit.ApiKey" ...>`
- Add location permissions (if not present)

**Android Application class:**
- Create `CleanCityApplication` extending `Application`
- Call `MapKitFactory.setApiKey(...)` and `MapKitFactory.initialize(this)` in `onCreate()`
- Register in AndroidManifest.xml

## UI Styling

All styling follows the existing design system in `ui/theme/`:
- Primary colors: green palette (green-50 through green-900)
- Accent: #5DDE8A
- Status colors: red (problems), amber (in work), green (resolved), purple (events)
- Border radius: 10px (sm), 16px (md), 24px (lg)
- Shadows: sm/md/lg as defined in Theme.kt
- Fonts: Unbounded (display), Golos Text (body) — as configured in Type.kt
- Bottom sheet: white background, 24px top border radius, drag handle

## Out of Scope

- iOS MapKit actual implementation (deferred)
- Network API calls (using InMemoryRepository)
- Real photo upload (mock only)
- Navigation to complaint detail screen (future screen)
- Dark mode

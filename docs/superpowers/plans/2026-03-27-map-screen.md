# Map Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the full Map screen with Yandex MapKit (Android), search, filters, bottom sheets, marker creation, and voting — all styled per the CleanCity design system.

**Architecture:** expect/actual for the native map widget and search provider. Android gets real Yandex MapKit; iOS gets stubs. All UI (bottom sheets, filters, FABs, create panel) is shared Compose in commonMain. State managed via Voyager ScreenModel collecting from the existing InMemoryRepository.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform 1.7.3, Yandex MapKit 4.25.0-full, Voyager 1.1.0-beta03, kotlinx-coroutines 1.8.1

**Spec:** `docs/superpowers/specs/2026-03-27-map-screen-design.md`

**Reference implementation:** `/Users/jasminagababyan/Desktop/Myapp/cleancity 2/app/src/main/java/com/example/clean__city/ui/main/MapScreen.kt`

---

## File Structure

```
composeApp/src/
├── commonMain/kotlin/com/example/cleancity/
│   ├── App.kt                                    (MODIFY — replace placeholder with navigation)
│   ├── ui/
│   │   ├── map/
│   │   │   ├── MapState.kt                       (CREATE — data classes, enums for map state)
│   │   │   ├── MapScreenModel.kt                 (CREATE — Voyager ScreenModel)
│   │   │   ├── MapScreen.kt                      (CREATE — main screen composable)
│   │   │   ├── YandexMapView.kt                  (CREATE — expect composable)
│   │   │   ├── MapSearchProvider.kt               (CREATE — expect search interface)
│   │   │   └── components/
│   │   │       ├── MapSearchBar.kt                (CREATE)
│   │   │       ├── MapFilterChips.kt              (CREATE)
│   │   │       ├── MapFabGroup.kt                 (CREATE)
│   │   │       ├── MapLegend.kt                   (CREATE)
│   │   │       ├── ProblemBottomSheet.kt          (CREATE)
│   │   │       ├── EventBottomSheet.kt            (CREATE)
│   │   │       ├── CreateMarkerPanel.kt           (CREATE)
│   │   │       └── VotingSection.kt               (CREATE)
│   │   └── navigation/
│   │       └── MainTabScreen.kt                   (CREATE — bottom nav with 5 tabs)
├── androidMain/kotlin/com/example/cleancity/
│   ├── CleanCityApplication.kt                    (CREATE — Application subclass)
│   ├── MainActivity.kt                            (MODIFY — add MapKit lifecycle)
│   └── ui/map/
│       ├── YandexMapView.android.kt               (CREATE — actual MapView)
│       └── MapSearchProvider.android.kt           (CREATE — actual search)
├── androidMain/AndroidManifest.xml                (MODIFY — add Application + MapKit meta-data)
└── iosMain/kotlin/com/example/cleancity/
    └── ui/map/
        ├── YandexMapView.ios.kt                   (CREATE — stub)
        └── MapSearchProvider.ios.kt               (CREATE — stub)
```

---

### Task 1: Map State Data Classes

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapState.kt`

- [ ] **Step 1: Create MapState.kt with all map data types**

```kotlin
package com.example.cleancity.ui.map

import com.example.cleancity.model.ProblemType
import com.example.cleancity.model.ProblemStatus

enum class MapMarkerType { PROBLEM, EVENT, RESOLVED }

enum class MapFilter(val displayName: String) {
    ALL("Все"),
    PROBLEMS("Проблемы"),
    EVENTS("Субботники"),
    RESOLVED("Решённые")
}

data class MapMarker(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: MapMarkerType,
    val title: String,
    val problemType: ProblemType? = null,
    val status: ProblemStatus? = null,
)

data class CameraPosition(
    val latitude: Double = 43.585,
    val longitude: Double = 39.723,
    val zoom: Float = 14f,
)

data class SearchSuggestion(
    val title: String,
    val subtitle: String? = null,
    val latitude: Double,
    val longitude: Double,
)
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapState.kt
git commit -m "feat(map): add map state data classes and enums"
```

---

### Task 2: Expect Declarations (Map View + Search Provider)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/YandexMapView.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapSearchProvider.kt`

- [ ] **Step 1: Create YandexMapView.kt expect declaration**

```kotlin
package com.example.cleancity.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun YandexMapView(
    modifier: Modifier = Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
)
```

- [ ] **Step 2: Create MapSearchProvider.kt expect declaration**

```kotlin
package com.example.cleancity.ui.map

interface MapSearchProvider {
    fun suggest(
        query: String,
        centerLat: Double,
        centerLon: Double,
        onResult: (List<SearchSuggestion>) -> Unit,
    )

    fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit,
    )
}

expect fun createMapSearchProvider(): MapSearchProvider
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/YandexMapView.kt \
       composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapSearchProvider.kt
git commit -m "feat(map): add expect declarations for map view and search provider"
```

---

### Task 3: iOS Stubs

**Files:**
- Create: `composeApp/src/iosMain/kotlin/com/example/cleancity/ui/map/YandexMapView.ios.kt`
- Create: `composeApp/src/iosMain/kotlin/com/example/cleancity/ui/map/MapSearchProvider.ios.kt`

- [ ] **Step 1: Create YandexMapView.ios.kt stub**

```kotlin
package com.example.cleancity.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun YandexMapView(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Карта будет доступна позже",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 2: Create MapSearchProvider.ios.kt stub**

```kotlin
package com.example.cleancity.ui.map

actual fun createMapSearchProvider(): MapSearchProvider = object : MapSearchProvider {
    override fun suggest(
        query: String,
        centerLat: Double,
        centerLon: Double,
        onResult: (List<SearchSuggestion>) -> Unit,
    ) {
        onResult(emptyList())
    }

    override fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit,
    ) {
        onResult(null)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/example/cleancity/ui/map/
git commit -m "feat(map): add iOS stub implementations for map view and search"
```

---

### Task 4: Android MapKit Setup (Application + Manifest)

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt`
- Modify: `composeApp/src/androidMain/AndroidManifest.xml`
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt`

- [ ] **Step 1: Create CleanCityApplication.kt**

```kotlin
package com.example.cleancity

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class CleanCityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapKitFactory.setApiKey("__YANDEX_MAPS_KEY_REDACTED__")
        MapKitFactory.initialize(this)
    }
}
```

- [ ] **Step 2: Update AndroidManifest.xml**

Add `android:name=".CleanCityApplication"` to the `<application>` tag and add MapKit API key meta-data:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:name=".CleanCityApplication"
        android:allowBackup="true"
        android:label="CleanCity"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">

        <meta-data
            android:name="com.yandex.mapkit.ApiKey"
            android:value="__YANDEX_MAPS_KEY_REDACTED__" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 3: Update MainActivity.kt — add MapKit lifecycle**

```kotlin
package com.example.cleancity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.cleancity.platform.initPlatform
import com.yandex.mapkit.MapKitFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPlatform(this)
        setContent {
            App()
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/CleanCityApplication.kt \
       composeApp/src/androidMain/AndroidManifest.xml \
       composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt
git commit -m "feat(map): add Android MapKit initialization and lifecycle"
```

---

### Task 5: Android Actual — YandexMapView

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/YandexMapView.android.kt`

- [ ] **Step 1: Create YandexMapView.android.kt**

Reference: `/Users/jasminagababyan/Desktop/Myapp/cleancity 2/app/src/main/java/com/example/clean__city/ui/main/MapScreen.kt` (lines 68-101 for AndroidView, 214-234 for lifecycle, 240-274 for reverse geocode).

```kotlin
package com.example.cleancity.ui.map

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.cleancity.ui.theme.*
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition as YandexCameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.mapview.MapView
import com.yandex.runtime.image.ImageProvider
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

@SuppressLint("MissingPermission")
@Composable
actual fun YandexMapView(
    modifier: Modifier,
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onMarkerClick: (MapMarker) -> Unit,
    onMapTap: (latitude: Double, longitude: Double) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // Keep a map of tap listeners so they are not garbage collected
    val tapListeners = remember { mutableMapOf<String, MapObjectTapListener>() }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                mapWindow.map.move(
                    YandexCameraPosition(
                        Point(cameraPosition.latitude, cameraPosition.longitude),
                        cameraPosition.zoom,
                        0.0f,
                        0.0f
                    )
                )
                mapWindow.map.addInputListener(object : InputListener {
                    override fun onMapTap(map: Map, point: Point) {
                        onMapTap(point.latitude, point.longitude)
                    }
                    override fun onMapLongTap(map: Map, point: Point) {}
                })
                mapView = this
            }
        },
        update = { view ->
            val map = view.mapWindow.map
            map.mapObjects.clear()
            tapListeners.clear()

            markers.forEach { marker ->
                val point = Point(marker.latitude, marker.longitude)
                val color = when (marker.type) {
                    MapMarkerType.RESOLVED -> 0xFF4DAB6E.toInt() // Green400
                    MapMarkerType.EVENT -> 0xFF8B5CF6.toInt()    // Purple
                    MapMarkerType.PROBLEM -> when (marker.status) {
                        com.example.cleancity.model.ProblemStatus.IN_WORK -> 0xFFF59E0B.toInt() // Amber
                        else -> 0xFFE8453C.toInt() // Red
                    }
                }
                val bitmap = createPinBitmap(color)
                val imageProvider = ImageProvider.fromBitmap(bitmap)
                val placemark = map.mapObjects.addPlacemark().apply {
                    geometry = point
                    setIcon(imageProvider)
                }
                val listener = MapObjectTapListener { _, _ ->
                    onMarkerClick(marker)
                    true
                }
                tapListeners[marker.id] = listener
                placemark.addTapListener(listener)
            }
        }
    )

    // MapView lifecycle
    DisposableEffect(lifecycleOwner, mapView) {
        val view = mapView ?: return@DisposableEffect onDispose {}
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}

private fun createPinBitmap(color: Int): Bitmap {
    val size = 48
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)
    // White border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, borderPaint)
    return bitmap
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/YandexMapView.android.kt
git commit -m "feat(map): add Android actual YandexMapView with placemarks"
```

---

### Task 6: Android Actual — MapSearchProvider

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/MapSearchProvider.android.kt`

- [ ] **Step 1: Create MapSearchProvider.android.kt**

Reference: `/Users/jasminagababyan/Desktop/Myapp/cleancity 2/app/src/main/java/com/example/clean__city/ui/main/MapScreen.kt` (lines 57-63 for SearchManager/SuggestSession setup, 240-306 for geocode + suggest functions).

```kotlin
package com.example.cleancity.ui.map

import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.*
import com.yandex.runtime.Error

actual fun createMapSearchProvider(): MapSearchProvider = AndroidMapSearchProvider()

private class AndroidMapSearchProvider : MapSearchProvider {
    private val searchManager: SearchManager =
        SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
    private val suggestSession: SuggestSession =
        searchManager.createSuggestSession()

    override fun suggest(
        query: String,
        centerLat: Double,
        centerLon: Double,
        onResult: (List<SearchSuggestion>) -> Unit,
    ) {
        // Search within ~5km box around center
        val delta = 0.05
        val window = BoundingBox(
            Point(centerLat - delta, centerLon - delta),
            Point(centerLat + delta, centerLon + delta),
        )
        val options = SuggestOptions().apply {
            suggestTypes = SuggestType.GEO.value or SuggestType.BIZ.value
        }
        suggestSession.suggest(
            query,
            window,
            options,
            object : SuggestSession.SuggestListener {
                override fun onResponse(response: SuggestResponse) {
                    val items = response.items.take(5).mapNotNull { item ->
                        val center = item.center ?: return@mapNotNull null
                        SearchSuggestion(
                            title = item.title?.text?.toString().orEmpty(),
                            subtitle = item.subtitle?.text?.toString(),
                            latitude = center.latitude,
                            longitude = center.longitude,
                        )
                    }
                    onResult(items)
                }
                override fun onError(error: Error) {
                    onResult(emptyList())
                }
            }
        )
    }

    override fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        onResult: (String?) -> Unit,
    ) {
        searchManager.submit(
            Point(latitude, longitude),
            16,
            SearchOptions(),
            object : Session.SearchListener {
                override fun onSearchResponse(response: Response) {
                    val meta = response.collection.children.firstOrNull()?.obj
                        ?.metadataContainer
                        ?.getItem(ToponymObjectMetadata::class.java)
                    onResult(meta?.address?.formattedAddress?.toString())
                }
                override fun onSearchError(error: Error) {
                    onResult(null)
                }
            }
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/map/MapSearchProvider.android.kt
git commit -m "feat(map): add Android actual MapSearchProvider with Yandex search"
```

---

### Task 7: MapScreenModel (State Management)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapScreenModel.kt`

- [ ] **Step 1: Create MapScreenModel.kt**

```kotlin
package com.example.cleancity.ui.map

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.model.ProblemStatus
import com.example.cleancity.model.ProblemType
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MapScreenModel : ScreenModel {

    private val searchProvider = createMapSearchProvider()

    private val _activeFilter = MutableStateFlow(MapFilter.ALL)
    val activeFilter: StateFlow<MapFilter> = _activeFilter

    private val _selectedMarker = MutableStateFlow<MapMarker?>(null)
    val selectedMarker: StateFlow<MapMarker?> = _selectedMarker

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _suggestions = MutableStateFlow<List<SearchSuggestion>>(emptyList())
    val suggestions: StateFlow<List<SearchSuggestion>> = _suggestions

    private val _isCreateMode = MutableStateFlow(false)
    val isCreateMode: StateFlow<Boolean> = _isCreateMode

    private val _createType = MutableStateFlow<ProblemType?>(null)
    val createType: StateFlow<ProblemType?> = _createType

    private val _createDescription = MutableStateFlow("")
    val createDescription: StateFlow<String> = _createDescription

    private val _createAddress = MutableStateFlow("")
    val createAddress: StateFlow<String> = _createAddress

    private val _createLat = MutableStateFlow(0.0)
    private val _createLon = MutableStateFlow(0.0)

    private val _privacyConsent = MutableStateFlow(false)
    val privacyConsent: StateFlow<Boolean> = _privacyConsent

    private val _cameraPosition = MutableStateFlow(CameraPosition())
    val cameraPosition: StateFlow<CameraPosition> = _cameraPosition

    val markers: StateFlow<List<MapMarker>> = combine(
        InMemoryRepository.problems,
        InMemoryRepository.events,
        _activeFilter,
    ) { problems, events, filter ->
        val problemMarkers = problems.map { p ->
            MapMarker(
                id = p.id,
                latitude = p.latitude,
                longitude = p.longitude,
                type = if (p.status == ProblemStatus.SOLVED) MapMarkerType.RESOLVED else MapMarkerType.PROBLEM,
                title = p.title,
                problemType = p.type,
                status = p.status,
            )
        }
        val eventMarkers = events.map { e ->
            MapMarker(
                id = e.id,
                latitude = e.latitude,
                longitude = e.longitude,
                type = MapMarkerType.EVENT,
                title = e.name,
            )
        }
        val all = problemMarkers + eventMarkers
        when (filter) {
            MapFilter.ALL -> all
            MapFilter.PROBLEMS -> all.filter { it.type == MapMarkerType.PROBLEM }
            MapFilter.EVENTS -> all.filter { it.type == MapMarkerType.EVENT }
            MapFilter.RESOLVED -> all.filter { it.type == MapMarkerType.RESOLVED }
        }
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: MapFilter) {
        _activeFilter.value = filter
    }

    fun selectMarker(marker: MapMarker) {
        _selectedMarker.value = marker
    }

    fun clearSelection() {
        _selectedMarker.value = null
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            val cam = _cameraPosition.value
            searchProvider.suggest(query, cam.latitude, cam.longitude) { results ->
                _suggestions.value = results
            }
        } else {
            _suggestions.value = emptyList()
        }
    }

    fun selectSuggestion(suggestion: SearchSuggestion) {
        _searchQuery.value = suggestion.title
        _suggestions.value = emptyList()
        _cameraPosition.value = CameraPosition(
            latitude = suggestion.latitude,
            longitude = suggestion.longitude,
            zoom = 16f,
        )
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _suggestions.value = emptyList()
    }

    fun openCreateMode() {
        _isCreateMode.value = true
        _createType.value = null
        _createDescription.value = ""
        _createAddress.value = ""
        _privacyConsent.value = false
    }

    fun closeCreateMode() {
        _isCreateMode.value = false
    }

    fun onMapTap(lat: Double, lon: Double) {
        if (_isCreateMode.value) {
            _createLat.value = lat
            _createLon.value = lon
            searchProvider.reverseGeocode(lat, lon) { address ->
                _createAddress.value = address ?: "%.5f, %.5f".format(lat, lon)
            }
        }
    }

    fun setCreateType(type: ProblemType) { _createType.value = type }
    fun setCreateDescription(desc: String) { _createDescription.value = desc }
    fun setCreateAddress(addr: String) { _createAddress.value = addr }
    fun setPrivacyConsent(consent: Boolean) { _privacyConsent.value = consent }

    fun submitProblem() {
        val type = _createType.value ?: return
        val desc = _createDescription.value.ifBlank { "Без описания" }
        val address = _createAddress.value.ifBlank { return }

        InMemoryRepository.addProblem(
            title = "${type.displayName}: $address",
            description = desc,
            type = type,
            latitude = _createLat.value,
            longitude = _createLon.value,
            address = address,
        )
        closeCreateMode()
    }

    fun voteProblem(problemId: String, voteYes: Boolean) {
        InMemoryRepository.voteProblem(problemId, voteYes)
    }

    fun verifyProblem(problemId: String) {
        InMemoryRepository.verifyProblem(problemId)
    }

    fun moveCameraTo(lat: Double, lon: Double, zoom: Float = 16f) {
        _cameraPosition.value = CameraPosition(lat, lon, zoom)
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapScreenModel.kt
git commit -m "feat(map): add MapScreenModel with state management"
```

---

### Task 8: UI Components — Search Bar, Filter Chips, FABs, Legend

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapSearchBar.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapFilterChips.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapFabGroup.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapLegend.kt`

- [ ] **Step 1: Create MapSearchBar.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cleancity.ui.map.SearchSuggestion
import com.example.cleancity.ui.theme.*

@Composable
fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    suggestions: List<SearchSuggestion>,
    onSuggestionClick: (SearchSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Поиск адреса...", color = Gray400) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp)),
        )

        if (suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp,
                color = Color.White,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    suggestions.forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSuggestionClick(item) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                        ) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Gray800)
                            if (!item.subtitle.isNullOrBlank()) {
                                Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = Gray400)
                            }
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: Create MapFilterChips.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.map.MapFilter
import com.example.cleancity.ui.theme.*

@Composable
fun MapFilterChips(
    activeFilter: MapFilter,
    onFilterClick: (MapFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val emojis = mapOf(
        MapFilter.ALL to "🗺",
        MapFilter.PROBLEMS to "🗑️",
        MapFilter.EVENTS to "🤝",
        MapFilter.RESOLVED to "✅",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MapFilter.entries.forEach { filter ->
            val isActive = filter == activeFilter
            Surface(
                onClick = { onFilterClick(filter) },
                shape = CircleShape,
                color = if (isActive) Accent else Color.White.copy(alpha = 0.15f),
                contentColor = if (isActive) Green900 else Color.White.copy(alpha = 0.8f),
                border = if (isActive) null else ButtonDefaults.outlinedButtonBorder,
            ) {
                Text(
                    text = "${emojis[filter] ?: ""} ${filter.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    fontSize = 11.sp,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Create MapFabGroup.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.theme.*

@Composable
fun MapFabGroup(
    onCreateClick: () -> Unit,
    onLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Create marker FAB
        FloatingActionButton(
            onClick = onCreateClick,
            containerColor = Accent,
            contentColor = Green900,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(48.dp),
        ) {
            Text("+", fontSize = 22.sp)
        }
        // My location FAB
        FloatingActionButton(
            onClick = onLocationClick,
            containerColor = Color.White,
            contentColor = Gray700,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.size(48.dp),
        ) {
            Text("◎", fontSize = 18.sp)
        }
    }
}
```

- [ ] **Step 4: Create MapLegend.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.theme.*

@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "МЕТКИ",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        LegendItem(color = Red, label = "Проблема")
        LegendItem(color = Accent, label = "Решена")
        LegendItem(color = Purple, label = "Субботник")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
        )
    }
}
```

- [ ] **Step 5: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapSearchBar.kt \
       composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapFilterChips.kt \
       composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapFabGroup.kt \
       composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/MapLegend.kt
git commit -m "feat(map): add search bar, filter chips, FAB group, and legend components"
```

---

### Task 9: UI Components — VotingSection

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/VotingSection.kt`

- [ ] **Step 1: Create VotingSection.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.theme.*

@Composable
fun VotingSection(
    votesYes: Int,
    votesNo: Int,
    onVoteYes: () -> Unit,
    onVoteNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = votesYes + votesNo
    val yesPercent = if (total > 0) (votesYes * 100 / total) else 0
    val noPercent = if (total > 0) (votesNo * 100 / total) else 0

    Column(
        modifier = modifier
            .background(Green50, RoundedCornerShape(12.dp))
            .border(1.dp, Green100, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            "ПРОБЛЕМА РЕШЕНА?",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Green800,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onVoteYes,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (yesPercent >= 50) Green600 else Color.White,
                    contentColor = if (yesPercent >= 50) Color.White else Gray600,
                ),
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    "✓ Да · ${if (total > 0) "$yesPercent%" else "—"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(
                onClick = onVoteNo,
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    "Нет · ${if (total > 0) "$noPercent%" else "—"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gray600,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${if (total > 0) "$total голосов · " else "Нет голосов · "}70%+ «Да» → статус Решена",
            style = MaterialTheme.typography.labelSmall,
            color = Gray500,
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/VotingSection.kt
git commit -m "feat(map): add VotingSection component"
```

---

### Task 10: UI Components — ProblemBottomSheet + EventBottomSheet

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/ProblemBottomSheet.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/EventBottomSheet.kt`

- [ ] **Step 1: Create ProblemBottomSheet.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.model.Problem
import com.example.cleancity.model.ProblemStatus
import com.example.cleancity.ui.theme.*

@Composable
fun ProblemBottomSheet(
    problem: Problem,
    onVerify: () -> Unit,
    onVoteYes: () -> Unit,
    onVoteNo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // Drag handle
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 20.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gray200)
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                // Title + status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        problem.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Gray900,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(problem.status)
                }

                Spacer(Modifier.height(6.dp))

                // Address + author
                Text(
                    "📍 ${problem.address} · Автор: ${problem.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                )

                Spacer(Modifier.height(10.dp))

                // Description
                Text(
                    problem.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray600,
                )

                Spacer(Modifier.height(14.dp))

                // Verification row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Green50, RoundedCornerShape(12.dp))
                        .border(1.dp, Green100, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val verCount = problem.verifications.size
                    val isOfficial = verCount >= 3
                    Text(
                        "✅ $verCount подтверждений${if (isOfficial) " · Официальная" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Green700,
                    )
                    Button(
                        onClick = onVerify,
                        colors = ButtonDefaults.buttonColors(containerColor = Green600),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("+ Подтвердить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Voting
                VotingSection(
                    votesYes = problem.votesYes,
                    votesNo = problem.votesNo,
                    onVoteYes = onVoteYes,
                    onVoteNo = onVoteNo,
                )

                Spacer(Modifier.height(14.dp))

                // Details button
                Button(
                    onClick = { /* navigate to complaint detail — future */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Green900),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text("Подробнее", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ProblemStatus) {
    val (bg, text) = when (status) {
        ProblemStatus.NEW -> Blue.copy(alpha = 0.9f) to Color.White
        ProblemStatus.VERIFIED -> Accent.copy(alpha = 0.9f) to Green900
        ProblemStatus.SENT -> Amber.copy(alpha = 0.9f) to Color.White
        ProblemStatus.IN_WORK -> Amber.copy(alpha = 0.9f) to Color.White
        ProblemStatus.SOLVED -> Green600.copy(alpha = 0.9f) to Color.White
    }
    Surface(
        color = bg,
        shape = CircleShape,
    ) {
        Text(
            status.displayName,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
```

- [ ] **Step 2: Create EventBottomSheet.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.model.CleanupEvent
import com.example.cleancity.ui.theme.*

@Composable
fun EventBottomSheet(
    event: CleanupEvent,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            // Drag handle
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 20.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gray200)
            )

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "🤝 ${event.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "📍 ${event.location}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "👥 ${event.participants.size} участников",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    event.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray600,
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text("Присоединиться", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/ProblemBottomSheet.kt \
       composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/EventBottomSheet.kt
git commit -m "feat(map): add ProblemBottomSheet and EventBottomSheet components"
```

---

### Task 11: UI Component — CreateMarkerPanel

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/CreateMarkerPanel.kt`

- [ ] **Step 1: Create CreateMarkerPanel.kt**

```kotlin
package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.model.ProblemType
import com.example.cleancity.ui.theme.*

@Composable
fun CreateMarkerPanel(
    selectedType: ProblemType?,
    onTypeSelect: (ProblemType) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    address: String,
    privacyConsent: Boolean,
    onPrivacyConsentChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        color = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Новая метка",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Gray900,
                )
                Surface(
                    onClick = onClose,
                    shape = CircleShape,
                    color = Gray100,
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text("✕", fontSize = 14.sp, color = Gray600)
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Type selector 2x2
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TypeOption(ProblemType.DUMP, selectedType == ProblemType.DUMP, onTypeSelect, Modifier.weight(1f))
                        TypeOption(ProblemType.HOLES, selectedType == ProblemType.HOLES, onTypeSelect, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TypeOption(ProblemType.LIGHTING, selectedType == ProblemType.LIGHTING, onTypeSelect, Modifier.weight(1f))
                        TypeOption(ProblemType.GREENERY, selectedType == ProblemType.GREENERY, onTypeSelect, Modifier.weight(1f))
                    }
                }

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    placeholder = { Text("Опишите проблему...", color = Gray300) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green400,
                        unfocusedBorderColor = Gray200,
                    ),
                )

                // Address display
                if (address.isNotBlank()) {
                    Text(
                        "📍 $address",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray500,
                    )
                } else {
                    Text(
                        "Тапните по карте для выбора места",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray400,
                    )
                }

                // Photo upload placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .border(2.dp, Green300, RoundedCornerShape(16.dp))
                        .background(Green50, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 24.sp)
                        Text("Добавить фото", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Green700)
                    }
                }

                // Privacy consent
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = privacyConsent,
                        onCheckedChange = onPrivacyConsentChange,
                        colors = CheckboxDefaults.colors(checkedColor = Green600),
                    )
                    Text(
                        "Я соглашаюсь с Политикой конфиденциальности и даю согласие на обработку персональных данных по ФЗ-152",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray500,
                        lineHeight = 16.sp,
                    )
                }

                // Submit button
                Button(
                    onClick = onSubmit,
                    enabled = selectedType != null && privacyConsent && address.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Green900),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text("Отправить", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun TypeOption(
    type: ProblemType,
    selected: Boolean,
    onSelect: (ProblemType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) Green500 else Gray200
    val bgColor = if (selected) Green50 else Color.White

    Surface(
        onClick = { onSelect(type) },
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(borderColor),
        ),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(type.emoji, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                type.displayName,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Green700 else Gray700,
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/components/CreateMarkerPanel.kt
git commit -m "feat(map): add CreateMarkerPanel component with type selector and privacy consent"
```

---

### Task 12: MapScreen Composable (Main Screen)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapScreen.kt`

- [ ] **Step 1: Create MapScreen.kt**

```kotlin
package com.example.cleancity.ui.map

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.ui.map.components.*

class MapScreen : Screen {

    @Composable
    override fun Content() {
        val model = rememberScreenModel { MapScreenModel() }

        val markers by model.markers.collectAsState()
        val selectedMarker by model.selectedMarker.collectAsState()
        val activeFilter by model.activeFilter.collectAsState()
        val searchQuery by model.searchQuery.collectAsState()
        val suggestions by model.suggestions.collectAsState()
        val isCreateMode by model.isCreateMode.collectAsState()
        val createType by model.createType.collectAsState()
        val createDescription by model.createDescription.collectAsState()
        val createAddress by model.createAddress.collectAsState()
        val privacyConsent by model.privacyConsent.collectAsState()
        val cameraPosition by model.cameraPosition.collectAsState()

        // Find full Problem/Event objects for bottom sheets
        val problems by InMemoryRepository.problems.collectAsState()
        val events by InMemoryRepository.events.collectAsState()

        val selectedProblem = selectedMarker?.let { marker ->
            if (marker.type != MapMarkerType.EVENT) problems.find { it.id == marker.id } else null
        }
        val selectedEvent = selectedMarker?.let { marker ->
            if (marker.type == MapMarkerType.EVENT) events.find { it.id == marker.id } else null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Map
            YandexMapView(
                modifier = Modifier.fillMaxSize(),
                cameraPosition = cameraPosition,
                markers = markers,
                onMarkerClick = { model.selectMarker(it) },
                onMapTap = { lat, lon ->
                    model.clearSelection()
                    model.onMapTap(lat, lon)
                },
            )

            // Search bar
            MapSearchBar(
                query = searchQuery,
                onQueryChange = { model.updateSearchQuery(it) },
                suggestions = suggestions,
                onSuggestionClick = { model.selectSuggestion(it) },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Filter chips
            MapFilterChips(
                activeFilter = activeFilter,
                onFilterClick = { model.setFilter(it) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp),
            )

            // Legend
            MapLegend(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 120.dp, end = 16.dp),
            )

            // FABs
            MapFabGroup(
                onCreateClick = { model.openCreateMode() },
                onLocationClick = {
                    // Move to Sochi center (placeholder for real location)
                    model.moveCameraTo(43.585, 39.723, 14f)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
            )

            // Problem bottom sheet
            AnimatedVisibility(
                visible = selectedProblem != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                selectedProblem?.let { problem ->
                    ProblemBottomSheet(
                        problem = problem,
                        onVerify = { model.verifyProblem(problem.id) },
                        onVoteYes = { model.voteProblem(problem.id, true) },
                        onVoteNo = { model.voteProblem(problem.id, false) },
                        onDismiss = { model.clearSelection() },
                    )
                }
            }

            // Event bottom sheet
            AnimatedVisibility(
                visible = selectedEvent != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                selectedEvent?.let { event ->
                    EventBottomSheet(
                        event = event,
                        onJoin = { InMemoryRepository.joinEvent(event.id) },
                        onDismiss = { model.clearSelection() },
                    )
                }
            }

            // Create marker panel
            AnimatedVisibility(
                visible = isCreateMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                CreateMarkerPanel(
                    selectedType = createType,
                    onTypeSelect = { model.setCreateType(it) },
                    description = createDescription,
                    onDescriptionChange = { model.setCreateDescription(it) },
                    address = createAddress,
                    privacyConsent = privacyConsent,
                    onPrivacyConsentChange = { model.setPrivacyConsent(it) },
                    onSubmit = { model.submitProblem() },
                    onClose = { model.closeCreateMode() },
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileKotlinMetadata`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/map/MapScreen.kt
git commit -m "feat(map): add MapScreen composable assembling all components"
```

---

### Task 13: Bottom Navigation + App Entry Point

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/navigation/MainTabScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt`

- [ ] **Step 1: Create MainTabScreen.kt**

```kotlin
package com.example.cleancity.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.ui.map.MapScreen
import com.example.cleancity.ui.theme.*

class MainTabScreen : Screen {

    @Composable
    override fun Content() {
        var selectedTab by remember { mutableStateOf(1) } // Map tab default
        val unreadCount by InMemoryRepository.notifications.collectAsState()

        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.White,
                    tonalElevation = 0.dp,
                ) {
                    val tabs = listOf(
                        TabItem("Лента", "🏠", 0),
                        TabItem("Карта", "🗺", 1),
                        TabItem("Уведомл.", "🔔", 2),
                        TabItem("Чаты", "💬", 3),
                        TabItem("Профиль", "👤", 4),
                    )
                    val unread = unreadCount.count { !it.isRead }

                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab.index,
                            onClick = { selectedTab = tab.index },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (tab.index == 2 && unread > 0) {
                                            Badge(containerColor = Red) {
                                                Text("$unread", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Text(tab.icon, fontSize = 20.sp)
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedTab == tab.index) Green600 else Gray400,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Green600,
                                indicatorColor = Green100,
                            ),
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (selectedTab) {
                    1 -> MapScreen().Content()
                    else -> {
                        // Placeholder for other tabs
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("Скоро будет доступно", color = Gray400)
                        }
                    }
                }
            }
        }
    }
}

private data class TabItem(val label: String, val icon: String, val index: Int)
```

- [ ] **Step 2: Update App.kt**

Replace the placeholder with navigation to MainTabScreen:

```kotlin
package com.example.cleancity

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.navigator.Navigator
import com.example.cleancity.data.InMemoryRepository
import com.example.cleancity.ui.navigation.MainTabScreen
import com.example.cleancity.ui.theme.CleanCityTheme

@Composable
fun App() {
    // Load sample data on first composition
    InMemoryRepository.loadSampleData()

    CleanCityTheme {
        Navigator(MainTabScreen())
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/navigation/MainTabScreen.kt \
       composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt
git commit -m "feat(map): add bottom navigation and wire up App entry point with MapScreen"
```

---

### Task 14: Build & Smoke Test

- [ ] **Step 1: Full Android build**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL, APK generated

- [ ] **Step 2: Fix any compilation errors**

If the build fails, read the error output carefully and fix. Common issues:
- Import mismatches (check package names match `com.example.cleancity`)
- Missing `BorderStroke` import for `CreateMarkerPanel` — replace with simpler border approach if needed
- MapKit API surface differences — compare with reference at `/Users/jasminagababyan/Desktop/Myapp/cleancity 2/app/src/main/java/com/example/clean__city/ui/main/MapScreen.kt`

- [ ] **Step 3: iOS metadata check**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL (stubs compile fine)

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat(map): complete map screen implementation with all components"
```

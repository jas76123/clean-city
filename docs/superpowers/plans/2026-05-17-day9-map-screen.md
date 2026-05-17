# Day 9 — MapScreen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реализовать первый продуктовый экран мобильного приложения — карту Сочи с маркерами реальных жалоб из backend, фильтром по 18 категориям, кластеризацией и FAB'ами геолокации и создания жалобы.

**Architecture:** UI и стейт — в `commonMain` (Compose Multiplatform + Voyager). Нативная обёртка Yandex MapKit — единственная `expect/actual` точка (`androidMain`). Bbox-запросы к `/complaints/map` через `Flow.debounce(500ms) + mapLatest` (auto-cancel inflight). Permission flow и FusedLocationProvider — через `expect/actual` обёртки. Тесты ScreenModel — `kotlin.test` + `kotlinx-coroutines-test` + `ktor-client-mock`.

**Tech Stack:** Kotlin 2.0.21, Compose Multiplatform 1.7.3, Voyager 1.1.0-beta03, ktor 3.0.3, Koin 3.5.6, kotlinx-coroutines 1.8.1, Yandex MapKit 4.25.0-full. Модели из модуля `:shared` (`MapMarker`, `MapMarkersResponse`, `ProblemCategory`, `ComplaintStatus`) — переиспользуем, не дублируем.

**Spec:** `docs/superpowers/specs/2026-05-17-day9-map-design.md`

---

## File Structure

**Create (commonMain):**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ComplaintsApi.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/CameraPosition.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/BoundingBox.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/SochiDefaults.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/Location.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/LocationProvider.kt` (expect)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/LocationPermission.kt` (expect)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapUiState.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreen.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.kt` (expect)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategoryFilterChips.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategorySheet.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerPreviewSheet.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MapFabGroup.kt`
- `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/create/CreateComplaintPlaceholderScreen.kt`

**Create (androidMain):**
- `composeApp/src/androidMain/kotlin/com/example/cleancity/domain/location/LocationProvider.android.kt`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/domain/location/LocationPermission.android.kt`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt`

**Create (commonTest):**
- `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/ComplaintsApiTest.kt`
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/FakeComplaintsApi.kt`
- `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/FakeLocationProvider.kt`

**Modify:**
- `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt` (+ ComplaintsApi, MapScreenModel)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt` (MainPlaceholderScreen → MapScreen)
- `composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt` (+ MapKit onStart/onStop)
- `composeApp/build.gradle.kts` (+ play-services-location в androidMain)
- `gradle/libs.versions.toml` (+ play-services-location version)
- `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AndroidModule.kt` (если существует — добавить LocationProvider/Permission actual factory)

**Delete:**
- `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/YandexMapView.android.kt`
- `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/MapSearchProvider.android.kt`

**No-op (already done):**
- AndroidManifest.xml — `ACCESS_FINE_LOCATION`/`ACCESS_COARSE_LOCATION` уже добавлены.
- `MapKitFactory.setApiKey` + `initialize` — уже в `CleanCityApplication.onCreate`.

---

## Phase 1 — Data layer (ComplaintsApi)

### Task 1: ComplaintsApi — interface + ktor implementation

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ComplaintsApi.kt`

- [ ] **Step 1: Создать interface + class по паттерну `AuthApi`**

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface ComplaintsApiContract {
    suspend fun getMapMarkers(
        swLat: Double,
        swLon: Double,
        neLat: Double,
        neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse
}

class ComplaintsApi(private val client: HttpClient) : ComplaintsApiContract {

    override suspend fun getMapMarkers(
        swLat: Double,
        swLon: Double,
        neLat: Double,
        neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse = client.get("/complaints/map") {
        parameter("swLat", swLat)
        parameter("swLon", swLon)
        parameter("neLat", neLat)
        parameter("neLon", neLon)
        category?.let { parameter("category", it.name) }
    }.body()
}
```

- [ ] **Step 2: Создать unit-тест с ktor-mock**

Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/ComplaintsApiTest.kt`

```kotlin
package com.example.cleancity.data.network

import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplaintsApiTest {

    @Test
    fun `getMapMarkers passes bbox and category to query string`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"markers":[]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ComplaintsApi(httpClient(engine))

        api.getMapMarkers(43.40, 39.55, 43.75, 40.05, ProblemCategory.GARBAGE)

        assertTrue(capturedUrl!!.contains("swLat=43.4"))
        assertTrue(capturedUrl!!.contains("swLon=39.55"))
        assertTrue(capturedUrl!!.contains("neLat=43.75"))
        assertTrue(capturedUrl!!.contains("neLon=40.05"))
        assertTrue(capturedUrl!!.contains("category=GARBAGE"))
    }

    @Test
    fun `getMapMarkers omits category param when null`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond("""{"markers":[]}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val api = ComplaintsApi(httpClient(engine))

        api.getMapMarkers(43.40, 39.55, 43.75, 40.05, category = null)

        assertTrue(!capturedUrl!!.contains("category="))
    }

    @Test
    fun `getMapMarkers parses response`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"markers":[
                    {"id":1,"category":"GARBAGE","status":"NEW","latitude":43.5,"longitude":39.7},
                    {"id":2,"category":"ROADS","status":"IN_PROGRESS","latitude":43.6,"longitude":39.8}
                ]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = ComplaintsApi(httpClient(engine))

        val response: MapMarkersResponse =
            api.getMapMarkers(43.40, 39.55, 43.75, 40.05, null)

        assertEquals(2, response.markers.size)
        assertEquals(1L, response.markers[0].id)
        assertEquals(ProblemCategory.GARBAGE, response.markers[0].category)
        assertEquals(ComplaintStatus.NEW, response.markers[0].status)
    }

    private fun httpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
        }
        defaultRequest { url("http://localhost/") }
    }
}
```

- [ ] **Step 3: Запустить тесты**

Run: `cd ~/Desktop/Myapp/cleancity-kmp && ./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.data.network.ComplaintsApiTest"`
Expected: 3/3 PASS

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/data/network/ComplaintsApi.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/data/network/ComplaintsApiTest.kt
git commit -m "feat(data): ComplaintsApi.getMapMarkers + ktor-mock tests"
```

---

### Task 2: Register ComplaintsApi in DI

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt` (after `single<UserApiContract> { UserApi(...) }` line)

- [ ] **Step 1: Добавить import и binding**

В блок imports добавить:
```kotlin
import com.example.cleancity.data.network.ComplaintsApi
import com.example.cleancity.data.network.ComplaintsApiContract
```

После строки `single<UserApiContract> { UserApi(get<HttpClient>()) }` добавить:
```kotlin
single<ComplaintsApiContract> { ComplaintsApi(get<HttpClient>()) }
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt
git commit -m "feat(di): register ComplaintsApi singleton"
```

---

## Phase 2 — Domain primitives (camera, bbox, defaults)

### Task 3: CameraPosition + BoundingBox + SochiDefaults

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/CameraPosition.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/BoundingBox.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/SochiDefaults.kt`

- [ ] **Step 1: CameraPosition**

```kotlin
package com.example.cleancity.domain.map

data class CameraPosition(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
)
```

- [ ] **Step 2: BoundingBox**

```kotlin
package com.example.cleancity.domain.map

data class BoundingBox(
    val swLat: Double,
    val swLon: Double,
    val neLat: Double,
    val neLon: Double,
) {
    fun expandedBy(deltaZoom: Float = 1.5f): BoundingBox {
        val factor = 1.0 / (1 shl deltaZoom.toInt().coerceAtLeast(1))
        val midLat = (swLat + neLat) / 2.0
        val midLon = (swLon + neLon) / 2.0
        val halfLat = (neLat - swLat) / 2.0 * factor
        val halfLon = (neLon - swLon) / 2.0 * factor
        return BoundingBox(
            swLat = midLat - halfLat,
            swLon = midLon - halfLon,
            neLat = midLat + halfLat,
            neLon = midLon + halfLon,
        )
    }
}
```

- [ ] **Step 3: SochiDefaults**

```kotlin
package com.example.cleancity.domain.map

object SochiDefaults {
    val CENTER = CameraPosition(latitude = 43.5855, longitude = 39.7231, zoom = 12f)
    val BBOX = BoundingBox(swLat = 43.40, swLon = 39.55, neLat = 43.75, neLon = 40.05)
}
```

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/map/
git commit -m "feat(domain): CameraPosition, BoundingBox, SochiDefaults"
```

---

## Phase 3 — Platform abstractions (location)

### Task 4: Location + LocationProvider expect/actual

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/Location.kt`
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/LocationProvider.kt`
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/domain/location/LocationProvider.android.kt`
- Modify: `gradle/libs.versions.toml` (+ play-services-location)
- Modify: `composeApp/build.gradle.kts` (+ play-services-location в androidMain deps)

- [ ] **Step 1: Добавить play-services-location в libs.versions.toml**

В блок `[versions]` добавить:
```toml
play-services-location = "21.3.0"
```

В блок `[libraries]` добавить:
```toml
play-services-location = { module = "com.google.android.gms:play-services-location", version.ref = "play-services-location" }
```

- [ ] **Step 2: Добавить зависимость в androidMain.dependencies**

`composeApp/build.gradle.kts`, в блоке `androidMain.dependencies`:
```kotlin
implementation(libs.play.services.location)
```

- [ ] **Step 3: Sync gradle**

Run: `./gradlew :composeApp:dependencies --configuration androidDebugRuntimeClasspath | grep play-services-location`
Expected: вывод содержит `com.google.android.gms:play-services-location:21.3.0`

- [ ] **Step 4: Location data class**

```kotlin
package com.example.cleancity.domain.location

data class Location(val latitude: Double, val longitude: Double)
```

- [ ] **Step 5: LocationProvider expect**

```kotlin
package com.example.cleancity.domain.location

interface LocationProvider {
    /**
     * Возвращает последнее известное местоположение. Гарантия наличия permission — на стороне caller.
     * Если location недоступен (никогда не запрашивался / GPS off) — Result.failure.
     */
    suspend fun getLastKnownLocation(): Result<Location>
}
```

- [ ] **Step 6: LocationProvider Android actual**

`composeApp/src/androidMain/kotlin/com/example/cleancity/domain/location/LocationProvider.android.kt`:
```kotlin
package com.example.cleancity.domain.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): Result<Location> =
        suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(Result.success(Location(loc.latitude, loc.longitude)))
                    } else {
                        cont.resume(Result.failure(IllegalStateException("Location null")))
                    }
                }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
}
```

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts \
        composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/ \
        composeApp/src/androidMain/kotlin/com/example/cleancity/domain/location/
git commit -m "feat(location): LocationProvider expect + Android FusedLocationProvider actual"
```

---

### Task 5: LocationPermission expect/actual (Composable controller)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/LocationPermission.kt`
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/domain/location/LocationPermission.android.kt`

- [ ] **Step 1: LocationPermission expect**

```kotlin
package com.example.cleancity.domain.location

import androidx.compose.runtime.Composable

enum class PermissionStatus { Granted, Denied, NotRequested }

class LocationPermissionController(
    val status: PermissionStatus,
    val launchRequest: () -> Unit,
)

@Composable
expect fun rememberLocationPermission(): LocationPermissionController
```

- [ ] **Step 2: LocationPermission Android actual**

```kotlin
package com.example.cleancity.domain.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberLocationPermission(): LocationPermissionController {
    val context = LocalContext.current
    var status by remember {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        mutableStateOf(if (granted) PermissionStatus.Granted else PermissionStatus.NotRequested)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        status = if (isGranted) PermissionStatus.Granted else PermissionStatus.Denied
    }
    return LocationPermissionController(
        status = status,
        launchRequest = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )
}
```

- [ ] **Step 3: Проверить компиляцию**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/domain/location/LocationPermission.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/domain/location/LocationPermission.android.kt
git commit -m "feat(location): rememberLocationPermission expect/actual"
```

---

## Phase 4 — MapUiState + MapScreenModel (TDD)

### Task 6: MapUiState

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapUiState.kt`

- [ ] **Step 1: MapUiState**

```kotlin
package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.domain.map.SochiDefaults
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.ProblemCategory

data class MapUiState(
    val cameraPosition: CameraPosition = SochiDefaults.CENTER,
    val markers: List<MapMarker> = emptyList(),
    val selectedCategory: ProblemCategory? = null,
    val selectedMarkerId: Long? = null,
    val isCategorySheetOpen: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastKnownLocation: Location? = null,
)
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapUiState.kt
git commit -m "feat(map): MapUiState data class"
```

---

### Task 7: FakeComplaintsApi + FakeLocationProvider (test doubles)

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/FakeComplaintsApi.kt`
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/FakeLocationProvider.kt`

- [ ] **Step 1: FakeComplaintsApi**

```kotlin
package com.example.cleancity.ui.feature.map

import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class FakeComplaintsApi : ComplaintsApiContract {
    data class Call(
        val swLat: Double, val swLon: Double,
        val neLat: Double, val neLon: Double,
        val category: ProblemCategory?,
    )

    val calls = mutableListOf<Call>()

    var nextResponse: List<MapMarker> = emptyList()
    var nextError: Throwable? = null
    var nextDelayMs: Long = 0

    /** Если задан — функция возвращает Deferred, который тест может resolve в нужный момент. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun getMapMarkers(
        swLat: Double, swLon: Double, neLat: Double, neLon: Double,
        category: ProblemCategory?,
    ): MapMarkersResponse {
        calls += Call(swLat, swLon, neLat, neLon, category)
        gate?.await()
        if (nextDelayMs > 0) delay(nextDelayMs)
        nextError?.let { throw it }
        return MapMarkersResponse(markers = nextResponse)
    }
}
```

- [ ] **Step 2: FakeLocationProvider**

```kotlin
package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.location.LocationProvider

class FakeLocationProvider(
    private val result: Result<Location> = Result.success(Location(43.5855, 39.7231)),
) : LocationProvider {
    var callCount = 0
    override suspend fun getLastKnownLocation(): Result<Location> {
        callCount++
        return result
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/Fake*.kt
git commit -m "test(map): FakeComplaintsApi + FakeLocationProvider"
```

---

### Task 8: MapScreenModel scaffold — initial load test (RED)

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`

- [ ] **Step 1: Написать failing-тест: init вызывает getMapMarkers с дефолтным SOCHI_BBOX**

```kotlin
package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.map.SochiDefaults
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MapScreenModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FakeComplaintsApi
    private lateinit var location: FakeLocationProvider

    @BeforeTest
    fun setup() {
        api = FakeComplaintsApi()
        location = FakeLocationProvider()
    }

    @Test
    fun `init triggers map load with default Sochi bbox`() = runTest(dispatcher) {
        val model = MapScreenModel(api, location, dispatcher)

        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        val call = api.calls.first()
        assertEquals(SochiDefaults.BBOX.swLat, call.swLat)
        assertEquals(SochiDefaults.BBOX.swLon, call.swLon)
        assertEquals(SochiDefaults.BBOX.neLat, call.neLat)
        assertEquals(SochiDefaults.BBOX.neLon, call.neLon)
        assertEquals(null, call.category)
        model.close()
    }
}
```

- [ ] **Step 2: Запустить — должно упасть на отсутствии `MapScreenModel`**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.init triggers map load with default Sochi bbox"`
Expected: FAIL — `MapScreenModel` не найден

---

### Task 9: MapScreenModel — minimal implementation (GREEN)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt`

- [ ] **Step 1: Минимальная реализация — стартовый запрос**

```kotlin
package com.example.cleancity.ui.feature.map

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.domain.location.LocationProvider
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.SochiDefaults
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapScreenModel(
    private val api: ComplaintsApiContract,
    private val locationProvider: LocationProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScreenModel {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private val bboxRequests = MutableSharedFlow<BoundingBox>(extraBufferCapacity = 64)

    init {
        screenModelScope.launch(dispatcher) {
            loadMarkers(SochiDefaults.BBOX, _state.value.selectedCategory)
        }
    }

    private suspend fun loadMarkers(bbox: BoundingBox, category: com.example.cleancity.shared.models.ProblemCategory?) {
        _state.update { it.copy(isLoading = true) }
        runCatching {
            api.getMapMarkers(bbox.swLat, bbox.swLon, bbox.neLat, bbox.neLon, category)
        }.onSuccess { resp ->
            _state.update { it.copy(markers = resp.markers, isLoading = false, error = null) }
        }.onFailure { e ->
            _state.update { it.copy(isLoading = false, error = e.message ?: "Network error") }
        }
    }

    /** Закрытие модели — для тестов. В Voyager обычно вызывается автоматически. */
    fun close() {
        // no-op; screenModelScope отменяется при dispose Screen
    }
}
```

- [ ] **Step 2: Запустить — тест зелёный**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.init triggers map load with default Sochi bbox"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "feat(map): MapScreenModel init triggers Sochi bbox load (TDD)"
```

---

### Task 10: onCameraMoved дебаунсит 500мс (RED → GREEN)

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt`

- [ ] **Step 1: Добавить failing-тест**

В `MapScreenModelTest.kt` добавить:
```kotlin
import com.example.cleancity.domain.map.BoundingBox
import kotlinx.coroutines.test.advanceTimeBy

@Test
fun `onCameraMoved debounces 500ms`() = runTest(dispatcher) {
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()
    api.calls.clear()

    repeat(5) { i ->
        model.onCameraMoved(BoundingBox(43.5 + i * 0.001, 39.5, 43.6, 39.6))
        advanceTimeBy(100)
    }
    advanceTimeBy(500)
    advanceUntilIdle()

    assertEquals(1, api.calls.size)
    assertEquals(43.504, api.calls.first().swLat, absoluteTolerance = 0.0001)
    model.close()
}
```

Добавить import `kotlin.test.assertEquals` уже есть; добавить:
```kotlin
import kotlin.math.abs
```
И если `assertEquals` для Double с tolerance не доступен — заменить на:
```kotlin
assertTrue(abs(api.calls.first().swLat - 43.504) < 0.0001)
```
(import `assertTrue` уже подразумевается).

- [ ] **Step 2: Запустить — должно упасть (`onCameraMoved` нет)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.onCameraMoved debounces 500ms"`
Expected: FAIL — `onCameraMoved` не определён

- [ ] **Step 3: Реализовать `onCameraMoved` с debounce + collectLatest**

В `MapScreenModel.kt` **полностью заменить** существующий `init` блок и `loadMarkers` на флоу-логику. Эта структура останется до конца Phase 4 — на Task 12 добавится `categoryFlow` в `combine`.

Добавить imports:
```kotlin
import com.example.cleancity.shared.models.ProblemCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration.Companion.milliseconds
```

Удалить старый `init { ... loadMarkers ... }` и `private suspend fun loadMarkers(...)`. Заменить на:

```kotlin
private val cameraBbox = MutableSharedFlow<BoundingBox>(extraBufferCapacity = 64)
private val categoryFlow = MutableStateFlow<ProblemCategory?>(null)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
private val pipelineJob = screenModelScope.launch(dispatcher) {
    cameraBbox
        .debounce(500.milliseconds)
        .onStart { emit(SochiDefaults.BBOX) }
        .combine(categoryFlow) { bbox, cat -> bbox to cat }
        .mapLatest { (bbox, cat) -> doRequest(bbox, cat) }
        .collect { /* state уже обновлён в doRequest */ }
}

fun onCameraMoved(bbox: BoundingBox) {
    cameraBbox.tryEmit(bbox)
}

private suspend fun doRequest(bbox: BoundingBox, cat: ProblemCategory?) {
    _state.update { it.copy(isLoading = true) }
    runCatching { api.getMapMarkers(bbox.swLat, bbox.swLon, bbox.neLat, bbox.neLon, cat) }
        .onSuccess { resp -> _state.update { it.copy(markers = resp.markers, isLoading = false, error = null) } }
        .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message ?: "Network error") } }
}
```

Старое поле `private val bboxRequests = MutableSharedFlow<BoundingBox>(...)` (из Task 9 scaffold) либо удалить, либо переименовать в `cameraBbox` — финальное имя `cameraBbox`. Любые `bboxRequests.tryEmit` тоже заменить.

**Почему такая структура:** `cameraBbox` дебаунсится, потом `combine` с `categoryFlow` склеивает в пару. Изменение `categoryFlow.value` (Task 12) тригерит новый emit пары `(последний-debounced-bbox, newCat)` мгновенно — debounce уже отработал ранее на bbox-источнике, повторно ждать 500мс не будет. `mapLatest` отменяет inflight-запрос при новой паре.

- [ ] **Step 4: Запустить оба теста**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.*"`
Expected: 2/2 PASS (стартовый запрос приходит через `onStart`, debounce-тест — 1 запрос за 5 эмитов)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "feat(map): onCameraMoved with 500ms debounce + collectLatest"
```

---

### Task 11: collectLatest отменяет inflight

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`

- [ ] **Step 1: Failing-тест**

```kotlin
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.ProblemCategory

@Test
fun `mapLatest cancels inflight when new bbox arrives`() = runTest(dispatcher) {
    api.nextDelayMs = 1000
    api.nextResponse = listOf(MapMarker(99, ProblemCategory.GARBAGE, ComplaintStatus.NEW, 43.0, 39.0))
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()
    api.calls.clear()

    // bbox#1: задержка 1000мс — стартует
    model.onCameraMoved(BoundingBox(43.5, 39.5, 43.6, 39.6))
    advanceTimeBy(500)   // дебаунс прошёл, запрос пошёл, ждёт 1000мс
    advanceTimeBy(400)   // прошло 400мс из задержки

    // bbox#2: отменяет inflight
    api.nextDelayMs = 0
    api.nextResponse = listOf(MapMarker(42, ProblemCategory.ROADS, ComplaintStatus.IN_PROGRESS, 43.7, 39.7))
    model.onCameraMoved(BoundingBox(44.0, 40.0, 44.1, 40.1))
    advanceTimeBy(500)
    advanceUntilIdle()

    // В state должен быть результат bbox#2 (id=42), не bbox#1 (id=99)
    assertEquals(listOf(42L), model.state.value.markers.map { it.id })
    model.close()
}
```

- [ ] **Step 2: Запустить — должен пройти (`mapLatest` уже в реализации)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.mapLatest cancels inflight when new bbox arrives"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "test(map): verify mapLatest cancels inflight bbox request"
```

---

### Task 12: selectCategory — немедленный запрос без debounce

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt`

- [ ] **Step 1: Failing-тест**

```kotlin
@Test
fun `selectCategory triggers immediate request without debounce`() = runTest(dispatcher) {
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()
    api.calls.clear()

    model.selectCategory(ProblemCategory.GARBAGE)
    advanceTimeBy(50)
    advanceUntilIdle()

    assertEquals(1, api.calls.size)
    assertEquals(ProblemCategory.GARBAGE, api.calls.first().category)
    assertEquals(ProblemCategory.GARBAGE, model.state.value.selectedCategory)
    model.close()
}
```

- [ ] **Step 2: Запустить — FAIL (`selectCategory` нет)**

- [ ] **Step 3: Реализовать `selectCategory`**

Вся флоу-логика (debounce, combine, mapLatest) уже на месте с Task 10 — `categoryFlow` — `MutableStateFlow<ProblemCategory?>` подписан в `combine`. Достаточно добавить публичный метод, который обновляет `state.selectedCategory` и пишет в `categoryFlow`:

```kotlin
fun selectCategory(category: ProblemCategory?) {
    _state.update { it.copy(selectedCategory = category, isCategorySheetOpen = false) }
    categoryFlow.value = category
}
```

`combine(debouncedBbox, categoryFlow)` пересылает пару при изменении любого источника — изменение `categoryFlow.value` сразу выпустит новую пару `(последний-debounced-bbox, newCat)`. Debounce у bbox-источника уже отработал и держит последнее значение, повторно ждать 500мс не будет. Результат — мгновенный запрос с новой категорией.

- [ ] **Step 4: Запустить — все 4 теста должны быть зелёные**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.*"`
Expected: 4/4 PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "feat(map): selectCategory triggers immediate refetch"
```

---

### Task 13: onMarkerClick + closeMarkerSheet + toggleCategory reset

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt`

- [ ] **Step 1: Failing-тесты**

```kotlin
@Test
fun `onMarkerClick sets selectedMarkerId`() = runTest(dispatcher) {
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()

    model.onMarkerClick(42L)
    assertEquals(42L, model.state.value.selectedMarkerId)
    model.close()
}

@Test
fun `closeMarkerSheet clears selectedMarkerId`() = runTest(dispatcher) {
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()
    model.onMarkerClick(42L)

    model.closeMarkerSheet()
    assertEquals(null, model.state.value.selectedMarkerId)
    model.close()
}

@Test
fun `selectCategory of currently selected resets to null`() = runTest(dispatcher) {
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()
    model.selectCategory(ProblemCategory.GARBAGE)
    advanceUntilIdle()
    api.calls.clear()

    model.toggleCategory(ProblemCategory.GARBAGE)
    advanceUntilIdle()

    assertEquals(null, model.state.value.selectedCategory)
    assertEquals(1, api.calls.size)
    assertEquals(null, api.calls.first().category)
    model.close()
}
```

- [ ] **Step 2: Запустить — должны упасть**

- [ ] **Step 3: Реализовать**

В `MapScreenModel.kt`:
```kotlin
fun onMarkerClick(id: Long) {
    _state.update { it.copy(selectedMarkerId = id) }
}

fun closeMarkerSheet() {
    _state.update { it.copy(selectedMarkerId = null) }
}

fun toggleCategory(category: ProblemCategory) {
    val newCategory = if (_state.value.selectedCategory == category) null else category
    selectCategory(newCategory)
}

fun openCategorySheet() { _state.update { it.copy(isCategorySheetOpen = true) } }
fun closeCategorySheet() { _state.update { it.copy(isCategorySheetOpen = false) } }
```

- [ ] **Step 4: Запустить — 7/7 PASS**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.*"`
Expected: 7/7 PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "feat(map): marker selection + category toggle reset"
```

---

### Task 14: Error preserves markers

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`

- [ ] **Step 1: Тест (поведение уже реализовано в `doRequest`)**

```kotlin
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.ComplaintStatus
import io.ktor.utils.io.errors.IOException

@Test
fun `error preserves previously loaded markers`() = runTest(dispatcher) {
    api.nextResponse = listOf(
        MapMarker(1, ProblemCategory.GARBAGE, ComplaintStatus.NEW, 43.5, 39.5),
        MapMarker(2, ProblemCategory.ROADS, ComplaintStatus.IN_PROGRESS, 43.6, 39.6),
    )
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()
    assertEquals(2, model.state.value.markers.size)

    api.nextError = IOException("offline")
    model.onCameraMoved(BoundingBox(43.5, 39.5, 43.6, 39.6))
    advanceTimeBy(600)
    advanceUntilIdle()

    assertEquals(2, model.state.value.markers.size, "markers should NOT be cleared on error")
    assertEquals("offline", model.state.value.error)
    model.close()
}
```

Если import `io.ktor.utils.io.errors.IOException` недоступен в commonTest — заменить на `RuntimeException("offline")`.

- [ ] **Step 2: Запустить — PASS (поведение уже есть)**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.error preserves previously loaded markers"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "test(map): verify markers preserved on network error"
```

---

### Task 15: onLocationFabClicked — permission gating + camera move

**Files:**
- Modify: `composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt`

- [ ] **Step 1: Failing-тесты**

```kotlin
import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.location.PermissionStatus

@Test
fun `onLocationFabClicked when granted fetches location and moves camera`() = runTest(dispatcher) {
    val provider = FakeLocationProvider(Result.success(Location(43.6, 39.8)))
    val model = MapScreenModel(api, provider, dispatcher)
    advanceUntilIdle()
    var launchedRequest = false

    model.onLocationFabClicked(PermissionStatus.Granted) { launchedRequest = true }
    advanceUntilIdle()

    assertEquals(1, provider.callCount)
    assertEquals(false, launchedRequest)
    assertEquals(43.6, model.state.value.cameraPosition.latitude)
    assertEquals(39.8, model.state.value.cameraPosition.longitude)
    assertEquals(15f, model.state.value.cameraPosition.zoom)
    model.close()
}

@Test
fun `onLocationFabClicked when NotRequested calls launchRequest`() = runTest(dispatcher) {
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()
    var launched = false

    model.onLocationFabClicked(PermissionStatus.NotRequested) { launched = true }

    assertEquals(true, launched)
    assertEquals(0, location.callCount)
    model.close()
}

@Test
fun `onLocationFabClicked when Denied sets error snackbar`() = runTest(dispatcher) {
    val model = MapScreenModel(api, location, dispatcher)
    advanceUntilIdle()

    model.onLocationFabClicked(PermissionStatus.Denied) {}

    assertEquals("Разрешите геолокацию в настройках", model.state.value.error)
    model.close()
}
```

- [ ] **Step 2: Запустить — FAIL**

- [ ] **Step 3: Реализовать `onLocationFabClicked`**

В `MapScreenModel.kt`:
```kotlin
import com.example.cleancity.domain.location.PermissionStatus
import com.example.cleancity.domain.map.CameraPosition

fun onLocationFabClicked(status: PermissionStatus, launchRequest: () -> Unit) {
    when (status) {
        PermissionStatus.NotRequested -> launchRequest()
        PermissionStatus.Denied -> _state.update {
            it.copy(error = "Разрешите геолокацию в настройках")
        }
        PermissionStatus.Granted -> screenModelScope.launch(dispatcher) {
            locationProvider.getLastKnownLocation()
                .onSuccess { loc ->
                    _state.update {
                        it.copy(
                            lastKnownLocation = loc,
                            cameraPosition = CameraPosition(loc.latitude, loc.longitude, zoom = 15f),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = "Не удалось получить местоположение") }
                }
        }
    }
}

fun clearError() { _state.update { it.copy(error = null) } }
```

- [ ] **Step 4: Запустить все тесты**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "com.example.cleancity.ui.feature.map.MapScreenModelTest.*"`
Expected: 10/10 PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt \
        composeApp/src/commonTest/kotlin/com/example/cleancity/ui/feature/map/MapScreenModelTest.kt
git commit -m "feat(map): onLocationFabClicked — gating by PermissionStatus + camera move"
```

---

## Phase 5 — UI components (stateless)

### Task 16: CategoryFilterChips

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategoryFilterChips.kt`

- [ ] **Step 1: Implementation**

```kotlin
package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.ProblemCategory

private val TOP_6 = listOf(
    ProblemCategory.GARBAGE,
    ProblemCategory.ROADS,
    ProblemCategory.LIGHTING,
    ProblemCategory.GREENERY,
    ProblemCategory.SIDEWALKS,
    ProblemCategory.LANDSCAPING,
)

@Composable
fun CategoryFilterChips(
    selectedCategory: ProblemCategory?,
    onCategorySelected: (ProblemCategory?) -> Unit,
    onMoreClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items: List<Any> = buildList {
        add(AllChip)
        addAll(TOP_6)
        add(MoreChip)
    }
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        items(items) { item ->
            when (item) {
                AllChip -> FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text("Все") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                MoreChip -> {
                    val moreLabel = selectedCategory
                        ?.takeIf { it !in TOP_6 }
                        ?.localizedLabel
                        ?: "Ещё"
                    FilterChip(
                        selected = selectedCategory != null && selectedCategory !in TOP_6,
                        onClick = onMoreClicked,
                        label = { Text(moreLabel) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                is ProblemCategory -> FilterChip(
                    selected = selectedCategory == item,
                    onClick = {
                        onCategorySelected(if (selectedCategory == item) null else item)
                    },
                    label = { Text(item.localizedLabel) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

private object AllChip
private object MoreChip
```

- [ ] **Step 2: Проверить компиляцию**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategoryFilterChips.kt
git commit -m "feat(map): CategoryFilterChips component"
```

---

### Task 17: CategorySheet (modal bottom sheet, 18 категорий)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategorySheet.kt`

- [ ] **Step 1: Implementation**

```kotlin
package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.ProblemCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySheet(
    initialSelection: ProblemCategory?,
    onApply: (ProblemCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pending by remember { mutableStateOf(initialSelection) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Выберите категорию", modifier = Modifier.padding(vertical = 12.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(ProblemCategory.entries) { category ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = pending == category,
                                onClick = { pending = category },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = pending == category, onClick = { pending = category })
                        Text(category.localizedLabel, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            Button(
                onClick = { onApply(pending) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            ) { Text("Применить") }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/CategorySheet.kt
git commit -m "feat(map): CategorySheet — modal with 18 categories"
```

---

### Task 18: MarkerPreviewSheet

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerPreviewSheet.kt`

- [ ] **Step 1: Implementation**

```kotlin
package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkerPreviewSheet(
    marker: MapMarker,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = "${marker.category.localizedLabel} · ${marker.status.localizedLabel()}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Координаты: %.4f, %.4f".format(marker.latitude, marker.longitude),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = { /* enabled = false */ },
                enabled = false,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) { Text("Открыть детально (Day 10)") }
        }
    }
}

private fun ComplaintStatus.localizedLabel(): String = when (this) {
    ComplaintStatus.NEW -> "Новая"
    ComplaintStatus.IN_PROGRESS -> "В работе"
    ComplaintStatus.RESOLVED -> "Решено"
    ComplaintStatus.REJECTED -> "Отклонено"
    ComplaintStatus.DUPLICATE -> "Дубликат"
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MarkerPreviewSheet.kt
git commit -m "feat(map): MarkerPreviewSheet with disabled detail button"
```

---

### Task 19: MapFabGroup (2 FAB: location + create)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MapFabGroup.kt`

- [ ] **Step 1: Implementation**

```kotlin
package com.example.cleancity.ui.feature.map.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MapFabGroup(
    onLocationClick: () -> Unit,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FloatingActionButton(onClick = onLocationClick) {
            Icon(Icons.Default.MyLocation, contentDescription = "Моё местоположение")
        }
        ExtendedFloatingActionButton(
            onClick = onCreateClick,
            text = { Text("Сообщить") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/components/MapFabGroup.kt
git commit -m "feat(map): MapFabGroup — location + create FABs"
```

---

### Task 20: CreateComplaintPlaceholderScreen

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/create/CreateComplaintPlaceholderScreen.kt`

- [ ] **Step 1: Implementation**

```kotlin
package com.example.cleancity.ui.feature.create

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

class CreateComplaintPlaceholderScreen : Screen {
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Новая жалоба") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("В разработке", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Создание жалобы появится в Day 11",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/create/CreateComplaintPlaceholderScreen.kt
git commit -m "feat(create): placeholder screen for FAB target until Day 11"
```

---

## Phase 6 — Native YandexMapHost (expect/actual)

### Task 21: YandexMapHost expect signature

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.kt`

- [ ] **Step 1: expect**

```kotlin
package com.example.cleancity.ui.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.shared.models.MapMarker

@Composable
expect fun YandexMapHost(
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onCameraMoved: (BoundingBox) -> Unit,
    onMarkerClick: (markerId: Long) -> Unit,
    onClusterTap: (BoundingBox) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 2: Compile (упадёт пока нет actual)**

Это ожидаемо — следующий шаг создаст actual.

---

### Task 22: YandexMapHost Android actual — base map + lifecycle

**Files:**
- Create: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt`

- [ ] **Step 1: Базовая обёртка MapView + lifecycle**

```kotlin
package com.example.cleancity.ui.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cleancity.domain.map.BoundingBox
import com.example.cleancity.domain.map.CameraPosition
import com.example.cleancity.shared.models.MapMarker
import com.yandex.mapkit.Animation
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition as YCameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.Map as YMap
import com.yandex.mapkit.mapview.MapView

@Composable
actual fun YandexMapHost(
    cameraPosition: CameraPosition,
    markers: List<MapMarker>,
    onCameraMoved: (BoundingBox) -> Unit,
    onMarkerClick: (markerId: Long) -> Unit,
    onClusterTap: (BoundingBox) -> Unit,
    modifier: Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewState = remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { view ->
                view.mapWindow.map.move(
                    YCameraPosition(
                        Point(cameraPosition.latitude, cameraPosition.longitude),
                        cameraPosition.zoom, 0f, 0f,
                    )
                )
                mapViewState.value = view
            }
        },
    )

    DisposableEffect(lifecycleOwner, mapViewState.value) {
        val view = mapViewState.value ?: return@DisposableEffect onDispose { }
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
            view.onStop()
        }
    }

    // Camera move from state (e.g. location FAB)
    LaunchedEffect(cameraPosition) {
        val view = mapViewState.value ?: return@LaunchedEffect
        view.mapWindow.map.move(
            YCameraPosition(
                Point(cameraPosition.latitude, cameraPosition.longitude),
                cameraPosition.zoom, 0f, 0f,
            ),
            Animation(Animation.Type.SMOOTH, 0.4f),
            null,
        )
    }

    // Camera idle → bbox callback
    DisposableEffect(mapViewState.value, onCameraMoved) {
        val view = mapViewState.value ?: return@DisposableEffect onDispose { }
        val listener = CameraListener { map: YMap, _, _, finished ->
            if (finished) {
                val region = map.visibleRegion
                val bbox = BoundingBox(
                    swLat = minOf(region.bottomLeft.latitude, region.topRight.latitude),
                    swLon = minOf(region.bottomLeft.longitude, region.topRight.longitude),
                    neLat = maxOf(region.bottomLeft.latitude, region.topRight.latitude),
                    neLon = maxOf(region.bottomLeft.longitude, region.topRight.longitude),
                )
                onCameraMoved(bbox)
            }
        }
        view.mapWindow.map.addCameraListener(listener)
        onDispose { view.mapWindow.map.removeCameraListener(listener) }
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt
git commit -m "feat(map): YandexMapHost — base AndroidView with camera + bbox callback"
```

---

### Task 23: YandexMapHost — pin bitmap + ClusterizedPlacemarkCollection

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt`

- [ ] **Step 1: Добавить pin-bitmap helper и кластеризацию**

В конец файла добавить:

```kotlin
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.SideEffect
import com.example.cleancity.shared.models.ComplaintStatus
import com.yandex.mapkit.map.ClusterListener
import com.yandex.mapkit.map.ClusterTapListener
import com.yandex.mapkit.map.ClusterizedPlacemarkCollection
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.runtime.image.ImageProvider

private fun statusColor(status: ComplaintStatus): Int = when (status) {
    ComplaintStatus.NEW -> 0xFFF59E0B.toInt()
    ComplaintStatus.IN_PROGRESS -> 0xFF3B82F6.toInt()
    ComplaintStatus.RESOLVED -> 0xFF10B981.toInt()
    ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> 0xFF9CA3AF.toInt()
}

private fun createPinBitmap(color: Int, sizePx: Int = 48): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val r = sizePx / 2f - 2f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, fill)
    canvas.drawCircle(sizePx / 2f, sizePx / 2f, r, stroke)
    return bitmap
}

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

И в основном composable, после блока `DisposableEffect(...CameraListener...)`, добавить:

```kotlin
// Markers + clustering
DisposableEffect(mapViewState.value, markers, onMarkerClick, onClusterTap) {
    val view = mapViewState.value ?: return@DisposableEffect onDispose { }
    val tapListeners = mutableListOf<Pair<PlacemarkMapObject, MapObjectTapListener>>()

    val clusterListener = ClusterListener { cluster ->
        cluster.appearance.setIcon(ImageProvider.fromBitmap(createClusterBitmap(cluster.size)))
        cluster.addClusterTapListener(ClusterTapListener { c ->
            val placemarks = c.placemarks
            if (placemarks.isEmpty()) return@ClusterTapListener true
            var minLat = Double.MAX_VALUE; var maxLat = -Double.MAX_VALUE
            var minLon = Double.MAX_VALUE; var maxLon = -Double.MAX_VALUE
            placemarks.forEach { p ->
                val pt = p.geometry
                if (pt.latitude < minLat) minLat = pt.latitude
                if (pt.latitude > maxLat) maxLat = pt.latitude
                if (pt.longitude < minLon) minLon = pt.longitude
                if (pt.longitude > maxLon) maxLon = pt.longitude
            }
            onClusterTap(BoundingBox(minLat, minLon, maxLat, maxLon))
            true
        })
    }

    val collection: ClusterizedPlacemarkCollection =
        view.mapWindow.map.mapObjects.addClusterizedPlacemarkCollection(clusterListener)

    markers.forEach { marker ->
        val placemark = collection.addPlacemark().apply {
            geometry = Point(marker.latitude, marker.longitude)
            setIcon(ImageProvider.fromBitmap(createPinBitmap(statusColor(marker.status))))
        }
        val listener = MapObjectTapListener { _, _ ->
            onMarkerClick(marker.id)
            true
        }
        placemark.addTapListener(listener)
        tapListeners.add(placemark to listener)
    }
    collection.clusterPlacemarks(60.0, 15)

    onDispose {
        tapListeners.forEach { (p, l) -> p.removeTapListener(l) }
        view.mapWindow.map.mapObjects.remove(collection)
    }
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL (если ImportError на ClusterTapListener — поменять путь на `com.yandex.mapkit.map.ClusterTapListener`; на 4.x SDK путь именно такой)

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/ui/feature/map/YandexMapHost.android.kt
git commit -m "feat(map): pin bitmaps + ClusterizedPlacemarkCollection with cluster tap"
```

---

## Phase 7 — Integration (MapScreen + DI + App.kt)

### Task 24: MapScreen — склейка всего

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreen.kt`

- [ ] **Step 1: MapScreen implementation**

```kotlin
package com.example.cleancity.ui.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.domain.location.rememberLocationPermission
import com.example.cleancity.ui.feature.create.CreateComplaintPlaceholderScreen
import com.example.cleancity.ui.feature.map.components.CategoryFilterChips
import com.example.cleancity.ui.feature.map.components.CategorySheet
import com.example.cleancity.ui.feature.map.components.MapFabGroup
import com.example.cleancity.ui.feature.map.components.MarkerPreviewSheet

class MapScreen(private val onLogout: () -> Unit) : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val model: MapScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val permission = rememberLocationPermission()
        val snackbarHost = remember { SnackbarHostState() }
        var menuOpen by remember { mutableStateOf(false) }

        LaunchedEffect(state.error) {
            state.error?.let {
                snackbarHost.showSnackbar(it)
                model.clearError()
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Чистый Город") },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Выйти") },
                                onClick = { menuOpen = false; onLogout() },
                                leadingIcon = { Icon(Icons.Default.Logout, null) },
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHost) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Column {
                    CategoryFilterChips(
                        selectedCategory = state.selectedCategory,
                        onCategorySelected = { model.selectCategory(it) },
                        onMoreClicked = { model.openCategorySheet() },
                    )
                    Box(Modifier.fillMaxSize()) {
                        YandexMapHost(
                            cameraPosition = state.cameraPosition,
                            markers = state.markers,
                            onCameraMoved = model::onCameraMoved,
                            onMarkerClick = model::onMarkerClick,
                            onClusterTap = { bbox ->
                                // Простой zoom: пересчёт центра + +1.5 zoom — делает YandexMapHost LaunchedEffect
                                val midLat = (bbox.swLat + bbox.neLat) / 2.0
                                val midLon = (bbox.swLon + bbox.neLon) / 2.0
                                val newZoom = (state.cameraPosition.zoom + 1.5f).coerceAtMost(20f)
                                model.onCameraMoved(bbox)
                                // обновить cameraPosition напрямую через ScreenModel
                                model.zoomTo(midLat, midLon, newZoom)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (state.isLoading && state.markers.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                        if (state.isLoading && state.markers.isNotEmpty()) {
                            LinearProgressIndicator(Modifier.fillMaxSize().padding(top = 0.dp))
                        }
                        MapFabGroup(
                            onLocationClick = {
                                model.onLocationFabClicked(permission.status, permission.launchRequest)
                            },
                            onCreateClick = { navigator.push(CreateComplaintPlaceholderScreen()) },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }

                state.selectedMarkerId?.let { id ->
                    val marker = state.markers.firstOrNull { it.id == id }
                    if (marker != null) {
                        MarkerPreviewSheet(marker = marker, onDismiss = { model.closeMarkerSheet() })
                    }
                }

                if (state.isCategorySheetOpen) {
                    CategorySheet(
                        initialSelection = state.selectedCategory,
                        onApply = { model.selectCategory(it) },
                        onDismiss = { model.closeCategorySheet() },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Добавить недостающий `zoomTo` в MapScreenModel**

```kotlin
import com.example.cleancity.domain.map.CameraPosition

fun zoomTo(lat: Double, lon: Double, zoom: Float) {
    _state.update { it.copy(cameraPosition = CameraPosition(lat, lon, zoom)) }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreen.kt \
        composeApp/src/commonMain/kotlin/com/example/cleancity/ui/feature/map/MapScreenModel.kt
git commit -m "feat(map): MapScreen — компоновка top-bar, chips, host, FABs, sheets"
```

---

### Task 25: Register MapScreenModel в DI

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt`

- [ ] **Step 1: Добавить imports**

```kotlin
import com.example.cleancity.domain.location.LocationProvider
import com.example.cleancity.ui.feature.map.MapScreenModel
```

- [ ] **Step 2: Добавить factory**

После строки `factory { (token: String) -> ResetPasswordScreenModel(token, get()) }`:
```kotlin
factory { MapScreenModel(get<ComplaintsApiContract>(), get<LocationProvider>()) }
```

- [ ] **Step 3: Зарегистрировать LocationProvider в Android-специфичном модуле**

Проверить, существует ли `composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt`. Если да — добавить в его `module { ... }`:

```kotlin
import com.example.cleancity.domain.location.AndroidLocationProvider
import com.example.cleancity.domain.location.LocationProvider
import org.koin.android.ext.koin.androidContext

single<LocationProvider> { AndroidLocationProvider(androidContext()) }
```

Если файл не существует — создать `composeApp/src/androidMain/kotlin/com/example/cleancity/di/AndroidModule.kt`:
```kotlin
package com.example.cleancity.di

import com.example.cleancity.domain.location.AndroidLocationProvider
import com.example.cleancity.domain.location.LocationProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidModule(): Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
}
```

И убедиться, что `androidModule()` подключен в Koin-старте (в `CleanCityApplication.onCreate` или где init Koin). Если HttpClientEngine уже регистрируется в другом месте — оставить там, не дублировать.

- [ ] **Step 4: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/di/AppModule.kt \
        composeApp/src/androidMain/kotlin/com/example/cleancity/di/
git commit -m "feat(di): register MapScreenModel + AndroidLocationProvider"
```

---

### Task 26: MainActivity — MapKit lifecycle

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt`

- [ ] **Step 1: Добавить onStart / onStop**

```kotlin
import com.yandex.mapkit.MapKitFactory

// в classе MainActivity, после onCreate:

override fun onStart() {
    super.onStart()
    MapKitFactory.getInstance().onStart()
}

override fun onStop() {
    MapKitFactory.getInstance().onStop()
    super.onStop()
}
```

- [ ] **Step 2: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/com/example/cleancity/MainActivity.kt
git commit -m "feat(android): MapKitFactory.onStart/onStop in MainActivity"
```

---

### Task 27: Replace MainPlaceholderScreen → MapScreen в App.kt

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt`

- [ ] **Step 1: Заменить import и обе ветки навигации**

Удалить:
```kotlin
import com.example.cleancity.ui.feature.main.MainPlaceholderScreen
```

Добавить:
```kotlin
import com.example.cleancity.ui.feature.map.MapScreen
```

Заменить обе ветки `MainPlaceholderScreen(...)` (в `initial` и в `LaunchedEffect(authState)`):

`AuthState.Guest`:
```kotlin
AuthState.Guest -> MapScreen(onLogout = { authRepo.toAnonymous() })
```

`is AuthState.Authenticated`:
```kotlin
is AuthState.Authenticated -> MapScreen(
    onLogout = { coroutineScope.launch { authRepo.logout() } },
)
```

- [ ] **Step 2: Build + установить**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/example/cleancity/App.kt
git commit -m "feat(app): wire MapScreen as Guest/Authenticated root"
```

---

### Task 28: Cleanup legacy/

**Files:**
- Delete: `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/YandexMapView.android.kt`
- Delete: `composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/MapSearchProvider.android.kt`

- [ ] **Step 1: Удалить файлы**

```bash
rm composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/YandexMapView.android.kt
rm composeApp/src/androidMain/kotlin/com/example/cleancity/legacy/MapSearchProvider.android.kt
```

Если папка `legacy/` пустая после этого — удалить и её:
```bash
rmdir composeApp/src/androidMain/kotlin/com/example/cleancity/legacy 2>/dev/null || true
```

- [ ] **Step 2: Build (убедиться, что никто не импортил legacy)**

Run: `./gradlew :composeApp:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add -A composeApp/src/androidMain/kotlin/com/example/cleancity/
git commit -m "chore: remove parked legacy/ map files — replaced by YandexMapHost"
```

---

## Phase 8 — Manual smoke test (Checkpoint)

### Task 29: Smoke на Medium_Phone AVD против docker-compose backend

**Pre-requisites:**
- Docker Desktop запущен; в репо `docker compose up -d` (Postgres :5433, backend :8081)
- Medium_Phone AVD запущен (`emulator -avd Medium_Phone &`)
- `local.properties` содержит `API_BASE_URL=http://10.0.2.2:8081` и `YANDEX_MAPS_API_KEY=...`

- [ ] **Step 1: Создать 3 тестовых жалобы через backend (Postman / curl)**

```bash
# Логин админом для получения токена (предполагается, что админ-аккаунт уже есть)
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@cleancity.local","password":"<admin-password>"}' \
  | jq -r '.auth.accessToken')

# Зарегистрировать резидента (нужно для votes/complaints, но для smoke прокатит и без него)
# Создать 3 жалобы напрямую через DB или через POST /complaints от резидента —
# для smoke достаточно вставить руками SQL'ем:
docker compose exec postgres psql -U cleancity -c "
INSERT INTO complaints(category, status, latitude, longitude, title, district, author_id, created_at)
VALUES
  ('GARBAGE','NEW',43.585,39.720,'Мусор · ул. Курортная', 'Центральный', 1, NOW()),
  ('ROADS','IN_PROGRESS',43.600,39.730,'Дороги · ул. Морская', 'Центральный', 1, NOW()),
  ('LIGHTING','RESOLVED',43.595,39.740,'Освещение · пр. Победы', 'Центральный', 1, NOW());
"
```

(Если SQL-схема отличается — подстроить под текущие миграции; ключевое — координаты в Сочи.)

- [ ] **Step 2: Установить APK на эмулятор**

```bash
./gradlew :composeApp:installDebug
adb shell am start -n com.example.cleancity/.MainActivity
```

- [ ] **Step 3: Прогнать 12 сценариев из spec §8 (вручную)**

Использовать таблицу из `docs/superpowers/specs/2026-05-17-day9-map-design.md` §8 как чек-лист. Каждый сценарий — observe + ✅/❌.

- [ ] **Step 4: Зафиксировать результаты в PLAN.md**

Открыть `docs/PLAN.md`, найти секцию «День 9», поставить галочки `[x]` напротив выполненных пунктов. Добавить ниже строку:
```markdown
**День 9 закрыт YYYY-MM-DD** — N/12 smoke-сценариев зелёные. (Перечислить failed сценарии и план фикса, если есть.)
```

- [ ] **Step 5: Финальный commit**

```bash
git add docs/PLAN.md
git commit -m "docs(plan): Day 9 closed — N/12 smoke-сценариев прошли"
```

- [ ] **Step 6: Merge в main (если работали на ветке)**

Если делалось на `day9-mobile-map`:
```bash
git checkout main
git merge --no-ff day9-mobile-map -m "Merge branch 'day9-mobile-map' — Day 9 закрыт"
```

---

## Self-Review

Прошёл по spec sections:

| Spec section | Tasks |
|---|---|
| §3 Scope входит | T1–T28 |
| §4.1 файловая структура | T1–T28 (отражено в File Structure блоке вверху, с поправкой на существующие модели из `:shared`) |
| §4.2 expect/actual YandexMapHost | T21, T22, T23 |
| §4.3 DI | T2, T25 |
| §5.1 MapUiState | T6 |
| §5.2 поток bbox-запросов | T9, T10, T11 |
| §5.3 tap-сценарии (marker / cluster / empty) | T13, T23, T24 |
| §5.4 геолокация | T4, T5, T15, T24 |
| §5.5 error handling | T14 |
| §6.1 layout | T24 |
| §6.2 chips | T16, T17 |
| §6.3 маркеры | T23 |
| §6.4 кластеры | T23 |
| §6.5 loading states | T24 |
| §6.6 MapKit lifecycle | T22, T26 |
| §6.7 manifest | no-op (уже в файле) |
| §7.1 unit-тесты ScreenModel | T8–T15 |
| §7.2 не тестируем | (явно не покрыто — это валидно, smoke в §8) |
| §7.3 backend integration (optional) | T1 (ktor-mock-test покрывает контракт) |
| §8 checkpoint | T29 |

Все требования spec покрыты задачами.

**Placeholder scan:** в плане нет TBD/TODO/«implement later»/«similar to Task N» — всё с реальным кодом.

**Type consistency:**
- `MapMarker.id: Long` — используется как `Long?` в `MapUiState.selectedMarkerId`, `onMarkerClick(id: Long)`, `marker.id` в `MarkerPreviewSheet` — ✅
- `ProblemCategory` (а не `ComplaintCategory`) во всех файлах — ✅
- `ComplaintStatus` (а не выдуманный enum) — ✅
- `BoundingBox` имеет `swLat/swLon/neLat/neLon` — везде одинаково — ✅
- `selectCategory` принимает `ProblemCategory?` — везде ✅
- `onCameraMoved`, `onMarkerClick`, `onClusterTap` — сигнатуры одинаковы между expect и actual ✅
- `LocationPermissionController.status: PermissionStatus`, `launchRequest: () -> Unit` — ✅
- `MapScreenModel.onLocationFabClicked(status, launchRequest)` принимает то же — ✅

Готов к выполнению.

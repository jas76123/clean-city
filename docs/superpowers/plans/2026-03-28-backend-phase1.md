# Backend Phase 1: Markers API — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a Ktor backend with PostgreSQL/PostGIS that serves a REST API for creating and retrieving map markers (complaints and subbotniks), plus a shared KMP module for type-safe API models.

**Architecture:** Three Gradle modules — `:shared` (API models, KMP), `:backend` (Ktor server, JVM-only), `:composeApp` (existing client). Both client and server depend on `:shared`. Backend uses Exposed ORM with Flyway migrations and local filesystem photo storage behind an abstraction.

**Tech Stack:** Kotlin 2.0.21, Ktor 3.0.3, Exposed 0.57.0, PostgreSQL 16 + PostGIS 3.4, Flyway 10.22.0, kotlinx.serialization, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-03-28-backend-phase1-design.md`

---

### Task 1: Gradle — Add `:shared` module

**Files:**
- Create: `shared/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml` (add kotlinx-serialization)

- [ ] **Step 1: Add serialization plugin and library to version catalog**

In `gradle/libs.versions.toml`, add the serialization version and plugin:

```toml
# In [versions] section, add:
kotlinx-serialization = "1.7.3"

# In [libraries] section, add:
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }

# In [plugins] section, add:
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

- [ ] **Step 2: Create `shared/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm()
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
```

- [ ] **Step 3: Register `:shared` in `settings.gradle.kts`**

Add `include(":shared")` after the existing `include(":composeApp")` line.

- [ ] **Step 4: Add `:shared` dependency to `composeApp/build.gradle.kts`**

In the `commonMain.dependencies` block, add:

```kotlin
implementation(project(":shared"))
```

- [ ] **Step 5: Verify project syncs**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :shared:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml shared/ composeApp/build.gradle.kts
git commit -m "feat: add :shared KMP module with serialization"
```

---

### Task 2: Shared module — API models

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/ProblemType.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/MarkerStatus.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/ComplaintResponse.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/SubbotnikResponse.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/models/MapMarkersResponse.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/CreateComplaintRequest.kt`
- Create: `shared/src/commonMain/kotlin/com/example/cleancity/shared/requests/CreateSubbotnikRequest.kt`

- [ ] **Step 1: Create `ProblemType.kt`**

```kotlin
package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class ProblemType {
    DUMP, ROAD, LIGHTING, GREENERY
}
```

- [ ] **Step 2: Create `MarkerStatus.kt`**

```kotlin
package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
enum class MarkerStatus {
    NEW, RESOLVED
}
```

- [ ] **Step 3: Create `ComplaintResponse.kt`**

```kotlin
package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class ComplaintResponse(
    val id: Long,
    val type: ProblemType,
    val description: String,
    val photoUrl: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val status: MarkerStatus,
    val createdAt: String
)
```

- [ ] **Step 4: Create `SubbotnikResponse.kt`**

```kotlin
package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class SubbotnikResponse(
    val id: Long,
    val title: String,
    val description: String,
    val photoUrl: String?,
    val date: String,
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val createdAt: String
)
```

- [ ] **Step 5: Create `MapMarkersResponse.kt`**

```kotlin
package com.example.cleancity.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class MapMarkersResponse(
    val complaints: List<ComplaintResponse>,
    val subbotniks: List<SubbotnikResponse>
)
```

- [ ] **Step 6: Create `CreateComplaintRequest.kt`**

```kotlin
package com.example.cleancity.shared.requests

import com.example.cleancity.shared.models.ProblemType
import kotlinx.serialization.Serializable

@Serializable
data class CreateComplaintRequest(
    val type: ProblemType,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String
)
```

- [ ] **Step 7: Create `CreateSubbotnikRequest.kt`**

```kotlin
package com.example.cleancity.shared.requests

import kotlinx.serialization.Serializable

@Serializable
data class CreateSubbotnikRequest(
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String
)
```

- [ ] **Step 8: Verify compilation**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :shared:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add shared/
git commit -m "feat(shared): add API models and request DTOs"
```

---

### Task 3: Gradle — Add `:backend` module

**Files:**
- Create: `backend/build.gradle.kts`
- Create: `backend/src/main/resources/logback.xml`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`

- [ ] **Step 1: Add backend dependencies to version catalog**

In `gradle/libs.versions.toml`:

```toml
# In [versions] section, add:
ktor = "3.0.3"
exposed = "0.57.0"
postgresql = "42.7.4"
flyway = "10.22.0"
logback = "1.5.12"
h2 = "2.3.232"

# In [libraries] section, add:
ktor-server-core = { module = "io.ktor:ktor-server-core", version.ref = "ktor" }
ktor-server-netty = { module = "io.ktor:ktor-server-netty", version.ref = "ktor" }
ktor-server-content-negotiation = { module = "io.ktor:ktor-server-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-server-cors = { module = "io.ktor:ktor-server-cors", version.ref = "ktor" }
ktor-server-status-pages = { module = "io.ktor:ktor-server-status-pages", version.ref = "ktor" }
ktor-server-test-host = { module = "io.ktor:ktor-server-test-host", version.ref = "ktor" }
exposed-core = { module = "org.jetbrains.exposed:exposed-core", version.ref = "exposed" }
exposed-jdbc = { module = "org.jetbrains.exposed:exposed-jdbc", version.ref = "exposed" }
exposed-java-time = { module = "org.jetbrains.exposed:exposed-java-time", version.ref = "exposed" }
postgresql-jdbc = { module = "org.postgresql:postgresql", version.ref = "postgresql" }
flyway-core = { module = "org.flywaydb:flyway-core", version.ref = "flyway" }
flyway-postgresql = { module = "org.flywaydb:flyway-database-postgresql", version.ref = "flyway" }
logback-classic = { module = "ch.qos.logback:logback-classic", version.ref = "logback" }
h2-database = { module = "com.h2database:h2", version.ref = "h2" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }

# In [plugins] section, add:
ktor = { id = "io.ktor.plugin", version.ref = "ktor" }
```

- [ ] **Step 2: Create `backend/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.kotlinJvm) // need to add to catalog if missing; see step 2a
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "com.example.cleancity"
version = "0.0.1"

application {
    mainClass.set("com.example.cleancity.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    implementation(libs.postgresql.jdbc)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.h2.database)
}
```

- [ ] **Step 2a: Add `kotlinJvm` plugin to version catalog if missing**

Check if `kotlinJvm` plugin alias exists in `gradle/libs.versions.toml` `[plugins]` section. If not, add:

```toml
kotlinJvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

- [ ] **Step 3: Register `:backend` in `settings.gradle.kts`**

Add `include(":backend")` after `include(":shared")`.

- [ ] **Step 4: Create `backend/src/main/resources/logback.xml`**

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [ ] **Step 5: Verify project syncs**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:dependencies`
Expected: BUILD SUCCESSFUL, lists ktor/exposed/flyway dependencies

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml backend/
git commit -m "feat: add :backend Ktor module with dependencies"
```

---

### Task 4: Database — Exposed tables and Flyway migration

**Files:**
- Create: `backend/src/main/resources/db/migration/V1__create_markers.sql`
- Create: `backend/src/main/kotlin/com/example/cleancity/database/tables/Complaints.kt`
- Create: `backend/src/main/kotlin/com/example/cleancity/database/tables/Subbotniks.kt`
- Create: `backend/src/main/kotlin/com/example/cleancity/config/DatabaseConfig.kt`

- [ ] **Step 1: Create Flyway migration `V1__create_markers.sql`**

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE complaints (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    photo_path VARCHAR(500) NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE subbotniks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    photo_path VARCHAR(500),
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    event_date DATE NOT NULL,
    event_time TIME NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_complaints_location ON complaints USING GIST(location);
CREATE INDEX idx_subbotniks_location ON subbotniks USING GIST(location);
```

- [ ] **Step 2: Create `Complaints.kt` Exposed table**

```kotlin
package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Complaints : Table("complaints") {
    val id = long("id").autoIncrement()
    val type = varchar("type", 20)
    val description = text("description")
    val photoPath = varchar("photo_path", 500)
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = varchar("address", 500)
    val deviceId = varchar("device_id", 100)
    val status = varchar("status", 20).default("NEW")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

Note: We store lat/lon as separate double columns in Exposed (the PostGIS `location` column is managed by raw SQL in the migration and queries). Exposed doesn't have native PostGIS support, so geo queries use raw SQL.

- [ ] **Step 3: Create `Subbotniks.kt` Exposed table**

```kotlin
package com.example.cleancity.database.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Subbotniks : Table("subbotniks") {
    val id = long("id").autoIncrement()
    val title = varchar("title", 300)
    val description = text("description")
    val photoPath = varchar("photo_path", 500).nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val address = varchar("address", 500)
    val eventDate = date("event_date")
    val eventTime = time("event_time")
    val deviceId = varchar("device_id", 100)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
```

- [ ] **Step 4: Update migration to include lat/lon columns alongside PostGIS**

Update `V1__create_markers.sql` to add separate lat/lon columns that Exposed can read, while keeping the PostGIS column for geo queries:

```sql
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE complaints (
    id BIGSERIAL PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    photo_path VARCHAR(500) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE subbotniks (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    description TEXT NOT NULL,
    photo_path VARCHAR(500),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    location GEOGRAPHY(POINT, 4326) NOT NULL,
    address VARCHAR(500) NOT NULL,
    event_date DATE NOT NULL,
    event_time TIME NOT NULL,
    device_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_complaints_location ON complaints USING GIST(location);
CREATE INDEX idx_subbotniks_location ON subbotniks USING GIST(location);

-- Auto-populate location from lat/lon on insert
CREATE OR REPLACE FUNCTION set_location() RETURNS TRIGGER AS $$
BEGIN
    NEW.location := ST_SetSRID(ST_MakePoint(NEW.longitude, NEW.latitude), 4326)::geography;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER complaints_set_location BEFORE INSERT OR UPDATE ON complaints
    FOR EACH ROW EXECUTE FUNCTION set_location();

CREATE TRIGGER subbotniks_set_location BEFORE INSERT OR UPDATE ON subbotniks
    FOR EACH ROW EXECUTE FUNCTION set_location();
```

- [ ] **Step 5: Create `DatabaseConfig.kt`**

```kotlin
package com.example.cleancity.config

import io.ktor.server.application.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

fun Application.configureDatabase() {
    val dbUrl = environment.config.propertyOrNull("database.url")?.getString()
        ?: System.getenv("DB_URL")
        ?: "jdbc:postgresql://localhost:5432/cleancity"
    val dbUser = environment.config.propertyOrNull("database.user")?.getString()
        ?: System.getenv("DB_USER")
        ?: "cleancity"
    val dbPassword = environment.config.propertyOrNull("database.password")?.getString()
        ?: System.getenv("DB_PASSWORD")
        ?: "cleancity"

    Flyway.configure()
        .dataSource(dbUrl, dbUser, dbPassword)
        .load()
        .migrate()

    Database.connect(
        url = dbUrl,
        driver = "org.postgresql.Driver",
        user = dbUser,
        password = dbPassword
    )
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add backend/src/
git commit -m "feat(backend): add database tables, migration, and config"
```

---

### Task 5: Photo storage service

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/storage/StorageService.kt`
- Create: `backend/src/main/kotlin/com/example/cleancity/storage/LocalStorageService.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/storage/LocalStorageServiceTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.cleancity.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

class LocalStorageServiceTest {

    private fun createTempStorage(): LocalStorageService {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return LocalStorageService(tempDir.absolutePath, "http://localhost:8080")
    }

    @Test
    fun `save stores file and returns path`() {
        val storage = createTempStorage()
        val bytes = "fake-image-data".toByteArray()

        val path = storage.save("test.jpg", bytes)

        assertTrue(path.endsWith(".jpg"))
        val stored = storage.get(path)
        assertNotNull(stored)
        assertEquals("fake-image-data", String(stored))
    }

    @Test
    fun `get returns null for missing file`() {
        val storage = createTempStorage()

        val result = storage.get("nonexistent.jpg")

        assertNull(result)
    }

    @Test
    fun `getUrl returns full URL`() {
        val storage = createTempStorage()

        val url = storage.getUrl("abc123.jpg")

        assertEquals("http://localhost:8080/api/photos/abc123.jpg", url)
    }

    @Test
    fun `save generates unique filenames`() {
        val storage = createTempStorage()
        val bytes = "data".toByteArray()

        val path1 = storage.save("photo.jpg", bytes)
        val path2 = storage.save("photo.jpg", bytes)

        assertTrue(path1 != path2)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.storage.LocalStorageServiceTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Create `StorageService.kt`**

```kotlin
package com.example.cleancity.storage

interface StorageService {
    fun save(fileName: String, bytes: ByteArray): String
    fun get(fileName: String): ByteArray?
    fun getUrl(fileName: String): String
}
```

- [ ] **Step 4: Create `LocalStorageService.kt`**

```kotlin
package com.example.cleancity.storage

import java.io.File
import java.util.UUID

class LocalStorageService(
    private val storagePath: String,
    private val baseUrl: String
) : StorageService {

    init {
        File(storagePath).mkdirs()
    }

    override fun save(fileName: String, bytes: ByteArray): String {
        val extension = fileName.substringAfterLast('.', "jpg")
        val uniqueName = "${UUID.randomUUID()}.$extension"
        File(storagePath, uniqueName).writeBytes(bytes)
        return uniqueName
    }

    override fun get(fileName: String): ByteArray? {
        val file = File(storagePath, fileName)
        return if (file.exists()) file.readBytes() else null
    }

    override fun getUrl(fileName: String): String {
        return "$baseUrl/api/photos/$fileName"
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.storage.LocalStorageServiceTest"`
Expected: 4 tests PASSED

- [ ] **Step 6: Commit**

```bash
git add backend/src/
git commit -m "feat(backend): add StorageService with local filesystem impl"
```

---

### Task 6: Marker repository

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/markers/MarkerRepository.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/markers/MarkerRepositoryTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Subbotniks
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MarkerRepositoryTest {

    private lateinit var repo: MarkerRepository

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.drop(Complaints, Subbotniks)
            SchemaUtils.create(Complaints, Subbotniks)
        }
        repo = MarkerRepository()
    }

    @Test
    fun `createComplaint inserts and returns complaint`() {
        val result = repo.createComplaint(
            type = "DUMP",
            description = "Illegal dump near park",
            photoPath = "abc123.jpg",
            latitude = 43.585,
            longitude = 39.723,
            address = "ул. Ленина, 42",
            deviceId = "device-1"
        )

        assertNotNull(result)
        assertEquals("DUMP", result.type)
        assertEquals("Illegal dump near park", result.description)
        assertEquals("abc123.jpg", result.photoPath)
        assertEquals(43.585, result.latitude)
        assertEquals(39.723, result.longitude)
        assertEquals("NEW", result.status)
    }

    @Test
    fun `createSubbotnik inserts and returns subbotnik`() {
        val result = repo.createSubbotnik(
            title = "Park cleanup",
            description = "Bring gloves",
            photoPath = null,
            latitude = 43.585,
            longitude = 39.723,
            address = "Сквер Победы",
            eventDate = "2026-04-01",
            eventTime = "10:00",
            deviceId = "device-1"
        )

        assertNotNull(result)
        assertEquals("Park cleanup", result.title)
        assertEquals("2026-04-01", result.eventDate)
        assertEquals("10:00", result.eventTime)
    }

    @Test
    fun `getAllComplaints returns inserted complaints`() {
        repo.createComplaint("ROAD", "Pothole", "p.jpg", 43.0, 39.0, "addr", "d1")
        repo.createComplaint("LIGHTING", "Dark street", "d.jpg", 43.1, 39.1, "addr2", "d2")

        val all = repo.getAllComplaints()

        assertEquals(2, all.size)
    }

    @Test
    fun `getAllSubbotniks returns inserted subbotniks`() {
        repo.createSubbotnik("Cleanup", "desc", null, 43.0, 39.0, "addr", "2026-04-01", "10:00", "d1")

        val all = repo.getAllSubbotniks()

        assertEquals(1, all.size)
    }

    @Test
    fun `getComplaintById returns correct complaint`() {
        val created = repo.createComplaint("DUMP", "desc", "p.jpg", 43.0, 39.0, "addr", "d1")

        val found = repo.getComplaintById(created.id)

        assertNotNull(found)
        assertEquals(created.id, found.id)
    }

    @Test
    fun `getSubbotnikById returns correct subbotnik`() {
        val created = repo.createSubbotnik("Cleanup", "desc", null, 43.0, 39.0, "addr", "2026-04-01", "10:00", "d1")

        val found = repo.getSubbotnikById(created.id)

        assertNotNull(found)
        assertEquals(created.id, found.id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.markers.MarkerRepositoryTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Create `MarkerRepository.kt`**

```kotlin
package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Subbotniks
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

data class ComplaintRow(
    val id: Long,
    val type: String,
    val description: String,
    val photoPath: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String,
    val status: String,
    val createdAt: OffsetDateTime
)

data class SubbotnikRow(
    val id: Long,
    val title: String,
    val description: String,
    val photoPath: String?,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val eventDate: String,
    val eventTime: String,
    val deviceId: String,
    val createdAt: OffsetDateTime
)

class MarkerRepository {

    fun createComplaint(
        type: String,
        description: String,
        photoPath: String,
        latitude: Double,
        longitude: Double,
        address: String,
        deviceId: String
    ): ComplaintRow = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Complaints.insert {
            it[Complaints.type] = type
            it[Complaints.description] = description
            it[Complaints.photoPath] = photoPath
            it[Complaints.latitude] = latitude
            it[Complaints.longitude] = longitude
            it[Complaints.address] = address
            it[Complaints.deviceId] = deviceId
            it[Complaints.status] = "NEW"
            it[Complaints.createdAt] = now
        }[Complaints.id]

        ComplaintRow(id, type, description, photoPath, latitude, longitude, address, deviceId, "NEW", now)
    }

    fun createSubbotnik(
        title: String,
        description: String,
        photoPath: String?,
        latitude: Double,
        longitude: Double,
        address: String,
        eventDate: String,
        eventTime: String,
        deviceId: String
    ): SubbotnikRow = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val id = Subbotniks.insert {
            it[Subbotniks.title] = title
            it[Subbotniks.description] = description
            it[Subbotniks.photoPath] = photoPath
            it[Subbotniks.latitude] = latitude
            it[Subbotniks.longitude] = longitude
            it[Subbotniks.address] = address
            it[Subbotniks.eventDate] = LocalDate.parse(eventDate)
            it[Subbotniks.eventTime] = LocalTime.parse(eventTime)
            it[Subbotniks.deviceId] = deviceId
            it[Subbotniks.createdAt] = now
        }[Subbotniks.id]

        SubbotnikRow(id, title, description, photoPath, latitude, longitude, address, eventDate, eventTime, deviceId, now)
    }

    fun getAllComplaints(): List<ComplaintRow> = transaction {
        Complaints.selectAll().map { it.toComplaintRow() }
    }

    fun getAllSubbotniks(): List<SubbotnikRow> = transaction {
        Subbotniks.selectAll().map { it.toSubbotnikRow() }
    }

    fun getComplaintById(id: Long): ComplaintRow? = transaction {
        Complaints.selectAll().where { Complaints.id eq id }.firstOrNull()?.toComplaintRow()
    }

    fun getSubbotnikById(id: Long): SubbotnikRow? = transaction {
        Subbotniks.selectAll().where { Subbotniks.id eq id }.firstOrNull()?.toSubbotnikRow()
    }

    private fun ResultRow.toComplaintRow() = ComplaintRow(
        id = this[Complaints.id],
        type = this[Complaints.type],
        description = this[Complaints.description],
        photoPath = this[Complaints.photoPath],
        latitude = this[Complaints.latitude],
        longitude = this[Complaints.longitude],
        address = this[Complaints.address],
        deviceId = this[Complaints.deviceId],
        status = this[Complaints.status],
        createdAt = this[Complaints.createdAt]
    )

    private fun ResultRow.toSubbotnikRow() = SubbotnikRow(
        id = this[Subbotniks.id],
        title = this[Subbotniks.title],
        description = this[Subbotniks.description],
        photoPath = this[Subbotniks.photoPath],
        latitude = this[Subbotniks.latitude],
        longitude = this[Subbotniks.longitude],
        address = this[Subbotniks.address],
        eventDate = this[Subbotniks.eventDate].toString(),
        eventTime = this[Subbotniks.eventTime].toString(),
        deviceId = this[Subbotniks.deviceId],
        createdAt = this[Subbotniks.createdAt]
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.markers.MarkerRepositoryTest"`
Expected: 6 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add backend/src/
git commit -m "feat(backend): add MarkerRepository with Exposed queries"
```

---

### Task 7: Marker service (business logic)

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/markers/MarkerService.kt`
- Create: `backend/src/test/kotlin/com/example/cleancity/markers/MarkerServiceTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Subbotniks
import com.example.cleancity.shared.models.MarkerStatus
import com.example.cleancity.shared.models.ProblemType
import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.shared.requests.CreateSubbotnikRequest
import com.example.cleancity.storage.LocalStorageService
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class MarkerServiceTest {

    private lateinit var service: MarkerService
    private lateinit var storagePath: String

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.drop(Complaints, Subbotniks)
            SchemaUtils.create(Complaints, Subbotniks)
        }
        storagePath = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.currentTimeMillis()}").absolutePath
        val storage = LocalStorageService(storagePath, "http://localhost:8080")
        service = MarkerService(MarkerRepository(), storage)
    }

    @Test
    fun `createComplaint returns response with photo URL`() {
        val request = CreateComplaintRequest(
            type = ProblemType.DUMP,
            description = "Illegal dump",
            latitude = 43.585,
            longitude = 39.723,
            address = "ул. Ленина, 42",
            deviceId = "device-1"
        )
        val photo = "fake-jpeg-data".toByteArray()

        val response = service.createComplaint(request, photo, "photo.jpg")

        assertNotNull(response)
        assertEquals(ProblemType.DUMP, response.type)
        assertEquals("Illegal dump", response.description)
        assertTrue(response.photoUrl.startsWith("http://localhost:8080/api/photos/"))
        assertEquals(MarkerStatus.NEW, response.status)
    }

    @Test
    fun `createSubbotnik works without photo`() {
        val request = CreateSubbotnikRequest(
            title = "Park cleanup",
            description = "Bring gloves",
            date = "2026-04-01",
            time = "10:00",
            latitude = 43.585,
            longitude = 39.723,
            address = "Сквер Победы",
            deviceId = "device-1"
        )

        val response = service.createSubbotnik(request, null, null)

        assertNotNull(response)
        assertEquals("Park cleanup", response.title)
        assertEquals(null, response.photoUrl)
    }

    @Test
    fun `createComplaint rejects oversized photo`() {
        val request = CreateComplaintRequest(
            type = ProblemType.ROAD,
            description = "Pothole",
            latitude = 43.0,
            longitude = 39.0,
            address = "addr",
            deviceId = "d1"
        )
        val bigPhoto = ByteArray(11 * 1024 * 1024) // 11 MB

        assertFailsWith<IllegalArgumentException> {
            service.createComplaint(request, bigPhoto, "big.jpg")
        }
    }

    @Test
    fun `createComplaint rejects invalid file extension`() {
        val request = CreateComplaintRequest(
            type = ProblemType.ROAD,
            description = "Pothole",
            latitude = 43.0,
            longitude = 39.0,
            address = "addr",
            deviceId = "d1"
        )

        assertFailsWith<IllegalArgumentException> {
            service.createComplaint(request, "data".toByteArray(), "file.gif")
        }
    }

    @Test
    fun `getMarkers returns both types`() {
        val complaint = CreateComplaintRequest(ProblemType.DUMP, "desc", 43.0, 39.0, "addr", "d1")
        service.createComplaint(complaint, "data".toByteArray(), "p.jpg")

        val subbotnik = CreateSubbotnikRequest("Title", "desc", "2026-04-01", "10:00", 43.0, 39.0, "addr", "d1")
        service.createSubbotnik(subbotnik, null, null)

        val markers = service.getAllMarkers()

        assertEquals(1, markers.complaints.size)
        assertEquals(1, markers.subbotniks.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.markers.MarkerServiceTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Create `MarkerService.kt`**

```kotlin
package com.example.cleancity.markers

import com.example.cleancity.shared.models.*
import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.shared.requests.CreateSubbotnikRequest
import com.example.cleancity.storage.StorageService

class MarkerService(
    private val repository: MarkerRepository,
    private val storage: StorageService
) {
    companion object {
        private const val MAX_PHOTO_SIZE = 10 * 1024 * 1024 // 10 MB
        private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png")
    }

    fun createComplaint(
        request: CreateComplaintRequest,
        photoBytes: ByteArray,
        photoFileName: String
    ): ComplaintResponse {
        validatePhoto(photoBytes, photoFileName)

        val savedPath = storage.save(photoFileName, photoBytes)
        val row = repository.createComplaint(
            type = request.type.name,
            description = request.description,
            photoPath = savedPath,
            latitude = request.latitude,
            longitude = request.longitude,
            address = request.address,
            deviceId = request.deviceId
        )

        return row.toComplaintResponse()
    }

    fun createSubbotnik(
        request: CreateSubbotnikRequest,
        photoBytes: ByteArray?,
        photoFileName: String?
    ): SubbotnikResponse {
        if (photoBytes != null && photoFileName != null) {
            validatePhoto(photoBytes, photoFileName)
        }

        val savedPath = if (photoBytes != null && photoFileName != null) {
            storage.save(photoFileName, photoBytes)
        } else null

        val row = repository.createSubbotnik(
            title = request.title,
            description = request.description,
            photoPath = savedPath,
            latitude = request.latitude,
            longitude = request.longitude,
            address = request.address,
            eventDate = request.date,
            eventTime = request.time,
            deviceId = request.deviceId
        )

        return row.toSubbotnikResponse()
    }

    fun getAllMarkers(): MapMarkersResponse {
        val complaints = repository.getAllComplaints().map { it.toComplaintResponse() }
        val subbotniks = repository.getAllSubbotniks().map { it.toSubbotnikResponse() }
        return MapMarkersResponse(complaints, subbotniks)
    }

    fun getComplaintById(id: Long): ComplaintResponse? {
        return repository.getComplaintById(id)?.toComplaintResponse()
    }

    fun getSubbotnikById(id: Long): SubbotnikResponse? {
        return repository.getSubbotnikById(id)?.toSubbotnikResponse()
    }

    private fun validatePhoto(bytes: ByteArray, fileName: String) {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        require(extension in ALLOWED_EXTENSIONS) {
            "Invalid file format: $extension. Allowed: ${ALLOWED_EXTENSIONS.joinToString()}"
        }
        require(bytes.size <= MAX_PHOTO_SIZE) {
            "File too large: ${bytes.size} bytes. Max: $MAX_PHOTO_SIZE bytes"
        }
    }

    private fun ComplaintRow.toComplaintResponse() = ComplaintResponse(
        id = id,
        type = ProblemType.valueOf(type),
        description = description,
        photoUrl = storage.getUrl(photoPath),
        latitude = latitude,
        longitude = longitude,
        address = address,
        status = MarkerStatus.valueOf(status),
        createdAt = createdAt.toString()
    )

    private fun SubbotnikRow.toSubbotnikResponse() = SubbotnikResponse(
        id = id,
        title = title,
        description = description,
        photoUrl = photoPath?.let { storage.getUrl(it) },
        date = eventDate,
        time = eventTime,
        latitude = latitude,
        longitude = longitude,
        address = address,
        createdAt = createdAt.toString()
    )
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.markers.MarkerServiceTest"`
Expected: 5 tests PASSED

- [ ] **Step 5: Commit**

```bash
git add backend/src/
git commit -m "feat(backend): add MarkerService with photo validation"
```

---

### Task 8: REST routes and Application entry point

**Files:**
- Create: `backend/src/main/kotlin/com/example/cleancity/markers/MarkerRoutes.kt`
- Create: `backend/src/main/kotlin/com/example/cleancity/Application.kt`
- Create: `backend/src/main/resources/application.conf`
- Create: `backend/src/test/kotlin/com/example/cleancity/markers/MarkerRoutesTest.kt`

- [ ] **Step 1: Write the route integration test**

```kotlin
package com.example.cleancity.markers

import com.example.cleancity.database.tables.Complaints
import com.example.cleancity.database.tables.Subbotniks
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.SubbotnikResponse
import com.example.cleancity.storage.LocalStorageService
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkerRoutesTest {

    private lateinit var storage: LocalStorageService
    private lateinit var service: MarkerService
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setup() {
        Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.drop(Complaints, Subbotniks)
            SchemaUtils.create(Complaints, Subbotniks)
        }
        val storagePath = File(System.getProperty("java.io.tmpdir"), "cleancity-test-${System.currentTimeMillis()}").absolutePath
        storage = LocalStorageService(storagePath, "http://localhost:8080")
        service = MarkerService(MarkerRepository(), storage)
    }

    private fun ApplicationTestBuilder.configureTestApp() {
        install(ContentNegotiation) { json() }
        routing { markerRoutes(service, storage) }
    }

    @Test
    fun `POST complaint returns 201 with response`() = testApplication {
        configureTestApp()

        val response = client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"type":"DUMP","description":"Illegal dump","latitude":43.585,"longitude":39.723,"address":"ул. Ленина, 42","deviceId":"device-1"}""")
                append("photo", "fake-jpeg".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }
        )

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<ComplaintResponse>(response.bodyAsText())
        assertEquals("Illegal dump", body.description)
    }

    @Test
    fun `POST complaint without photo returns 400`() = testApplication {
        configureTestApp()

        val response = client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"type":"DUMP","description":"desc","latitude":43.0,"longitude":39.0,"address":"addr","deviceId":"d1"}""")
            }
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST subbotnik without photo returns 201`() = testApplication {
        configureTestApp()

        val response = client.submitFormWithBinaryData(
            url = "/api/subbotniks",
            formData = formData {
                append("data", """{"title":"Cleanup","description":"Bring gloves","date":"2026-04-01","time":"10:00","latitude":43.585,"longitude":39.723,"address":"Сквер Победы","deviceId":"device-1"}""")
            }
        )

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<SubbotnikResponse>(response.bodyAsText())
        assertEquals("Cleanup", body.title)
    }

    @Test
    fun `GET markers returns all markers`() = testApplication {
        configureTestApp()

        // Create one complaint
        client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"type":"ROAD","description":"Pothole","latitude":43.0,"longitude":39.0,"address":"addr","deviceId":"d1"}""")
                append("photo", "jpeg".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"p.jpg\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }
        )

        val response = client.get("/api/markers")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<MapMarkersResponse>(response.bodyAsText())
        assertEquals(1, body.complaints.size)
        assertEquals(0, body.subbotniks.size)
    }

    @Test
    fun `GET complaint by id returns 200`() = testApplication {
        configureTestApp()

        val createResponse = client.submitFormWithBinaryData(
            url = "/api/complaints",
            formData = formData {
                append("data", """{"type":"DUMP","description":"desc","latitude":43.0,"longitude":39.0,"address":"addr","deviceId":"d1"}""")
                append("photo", "jpeg".toByteArray(), Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"p.jpg\"")
                    append(HttpHeaders.ContentType, "image/jpeg")
                })
            }
        )
        val created = json.decodeFromString<ComplaintResponse>(createResponse.bodyAsText())

        val response = client.get("/api/complaints/${created.id}")

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET nonexistent complaint returns 404`() = testApplication {
        configureTestApp()

        val response = client.get("/api/complaints/999")

        assertEquals(HttpStatusCode.NotFound, response.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.markers.MarkerRoutesTest"`
Expected: FAIL — function not found

- [ ] **Step 3: Create `MarkerRoutes.kt`**

```kotlin
package com.example.cleancity.markers

import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.shared.requests.CreateSubbotnikRequest
import com.example.cleancity.storage.StorageService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Routing.markerRoutes(service: MarkerService, storage: StorageService) {
    route("/api") {
        post("/complaints") {
            val multipart = call.receiveMultipart()
            var requestJson: String? = null
            var photoBytes: ByteArray? = null
            var photoFileName: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "data") requestJson = part.value
                    }
                    is PartData.FileItem -> {
                        if (part.name == "photo") {
                            photoBytes = part.provider().readBytes()
                            photoFileName = part.originalFileName ?: "upload.jpg"
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (requestJson == null || photoBytes == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing required fields: data and photo")
                return@post
            }

            try {
                val request = Json.decodeFromString<CreateComplaintRequest>(requestJson!!)
                val response = service.createComplaint(request, photoBytes!!, photoFileName!!)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
            }
        }

        post("/subbotniks") {
            val multipart = call.receiveMultipart()
            var requestJson: String? = null
            var photoBytes: ByteArray? = null
            var photoFileName: String? = null

            multipart.forEachPart { part ->
                when (part) {
                    is PartData.FormItem -> {
                        if (part.name == "data") requestJson = part.value
                    }
                    is PartData.FileItem -> {
                        if (part.name == "photo") {
                            photoBytes = part.provider().readBytes()
                            photoFileName = part.originalFileName ?: "upload.jpg"
                        }
                    }
                    else -> {}
                }
                part.dispose()
            }

            if (requestJson == null) {
                call.respond(HttpStatusCode.BadRequest, "Missing required field: data")
                return@post
            }

            try {
                val request = Json.decodeFromString<CreateSubbotnikRequest>(requestJson!!)
                val response = service.createSubbotnik(request, photoBytes, photoFileName)
                call.respond(HttpStatusCode.Created, response)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
            }
        }

        get("/markers") {
            val markers = service.getAllMarkers()
            call.respond(markers)
        }

        get("/complaints/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
            val complaint = service.getComplaintById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Complaint not found")
            call.respond(complaint)
        }

        get("/subbotniks/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
            val subbotnik = service.getSubbotnikById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Subbotnik not found")
            call.respond(subbotnik)
        }

        get("/photos/{filename}") {
            val filename = call.parameters["filename"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing filename")
            val bytes = storage.get(filename)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Photo not found")
            val contentType = when {
                filename.endsWith(".png") -> ContentType.Image.PNG
                else -> ContentType.Image.JPEG
            }
            call.respondBytes(bytes, contentType)
        }
    }
}
```

- [ ] **Step 4: Create `application.conf`**

```hocon
ktor {
    deployment {
        port = 8080
    }
    application {
        modules = [ com.example.cleancity.ApplicationKt.module ]
    }
}

database {
    url = "jdbc:postgresql://localhost:5432/cleancity"
    user = "cleancity"
    password = "cleancity"
}

storage {
    path = "./uploads"
}
```

- [ ] **Step 5: Create `Application.kt`**

```kotlin
package com.example.cleancity

import com.example.cleancity.config.configureDatabase
import com.example.cleancity.markers.MarkerRepository
import com.example.cleancity.markers.MarkerService
import com.example.cleancity.markers.markerRoutes
import com.example.cleancity.storage.LocalStorageService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

fun Application.module() {
    configureDatabase()

    install(ContentNegotiation) { json() }

    install(CORS) {
        anyHost()
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, cause.message ?: "Bad request")
        }
        exception<Exception> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, "Internal server error")
        }
    }

    val storagePath = environment.config.propertyOrNull("storage.path")?.getString()
        ?: System.getenv("STORAGE_PATH")
        ?: "./uploads"
    val baseUrl = "http://localhost:${environment.config.port}"

    val storage = LocalStorageService(storagePath, baseUrl)
    val repository = MarkerRepository()
    val service = MarkerService(repository, storage)

    routing {
        markerRoutes(service, storage)
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test --tests "com.example.cleancity.markers.MarkerRoutesTest"`
Expected: 6 tests PASSED

- [ ] **Step 7: Commit**

```bash
git add backend/src/
git commit -m "feat(backend): add REST routes and Application entry point"
```

---

### Task 9: Docker Compose and Dockerfile

**Files:**
- Create: `docker-compose.yml`
- Create: `backend/Dockerfile`

- [ ] **Step 1: Create `backend/Dockerfile`**

```dockerfile
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
COPY .. .
RUN gradle :backend:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/backend/build/install/backend/ ./
RUN mkdir -p /app/uploads
EXPOSE 8080
ENTRYPOINT ["./bin/backend"]
```

- [ ] **Step 2: Create `docker-compose.yml`**

```yaml
services:
  db:
    image: postgis/postgis:16-3.4
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: cleancity
      POSTGRES_USER: cleancity
      POSTGRES_PASSWORD: cleancity
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cleancity"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: .
      dockerfile: backend/Dockerfile
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy
    environment:
      DB_URL: jdbc:postgresql://db:5432/cleancity
      DB_USER: cleancity
      DB_PASSWORD: cleancity
      STORAGE_PATH: /app/uploads
    volumes:
      - uploads:/app/uploads

volumes:
  pgdata:
  uploads:
```

- [ ] **Step 3: Create `.dockerignore`**

```
.gradle/
build/
*/build/
.idea/
*.iml
.kotlin/
local.properties
```

- [ ] **Step 4: Verify Docker Compose config is valid**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && docker compose config`
Expected: Prints resolved YAML without errors

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml backend/Dockerfile .dockerignore
git commit -m "feat: add Docker Compose with PostGIS and backend service"
```

---

### Task 10: Smoke test — start everything and verify

- [ ] **Step 1: Start PostgreSQL with Docker Compose**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && docker compose up db -d`
Expected: PostGIS container starts and becomes healthy

- [ ] **Step 2: Run backend locally against Docker PostgreSQL**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:run`
Expected: Server starts on port 8080, Flyway runs migration, logs show "Application started"

- [ ] **Step 3: Create a test complaint via curl**

Run:
```bash
curl -X POST http://localhost:8080/api/complaints \
  -F 'data={"type":"DUMP","description":"Test illegal dump","latitude":43.585,"longitude":39.723,"address":"ул. Ленина, 42","deviceId":"test-device"}' \
  -F 'photo=@/dev/urandom;filename=test.jpg;type=image/jpeg' \
  | python3 -m json.tool
```

Expected: 201 response with `ComplaintResponse` JSON

- [ ] **Step 4: Create a test subbotnik via curl**

Run:
```bash
curl -X POST http://localhost:8080/api/subbotniks \
  -F 'data={"title":"Test cleanup","description":"Bring gloves","date":"2026-04-01","time":"10:00","latitude":43.585,"longitude":39.723,"address":"Сквер Победы","deviceId":"test-device"}' \
  | python3 -m json.tool
```

Expected: 201 response with `SubbotnikResponse` JSON

- [ ] **Step 5: Fetch all markers**

Run: `curl http://localhost:8080/api/markers | python3 -m json.tool`
Expected: JSON with `complaints` (1 item) and `subbotniks` (1 item)

- [ ] **Step 6: Stop services**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && docker compose down`

- [ ] **Step 7: Run all backend tests**

Run: `cd /Users/jasminagababyan/Desktop/Myapp/cleancity-kmp && ./gradlew :backend:test`
Expected: All tests PASSED

- [ ] **Step 8: Commit any fixes if needed, then final commit**

```bash
git add -A
git commit -m "chore: smoke test verified, backend phase 1 complete"
```

# Backend Phase 1: Markers API — Design Spec

## Overview

Ktor backend for CleanCity KMP app. Phase 1 delivers the ability to create and display two types of markers on the map: complaints (жалобы) and subbotniks (субботники).

## Tech Stack

- **Server:** Ktor (Kotlin) with Netty engine
- **Database:** PostgreSQL 16 + PostGIS 3.4
- **ORM:** Exposed (JetBrains)
- **Migrations:** Flyway
- **Serialization:** kotlinx.serialization
- **Photo storage:** Local filesystem (behind `StorageService` abstraction)
- **Infrastructure:** Docker Compose (PostgreSQL + app)
- **Auth:** Anonymous device_id (no login/registration)

## Project Structure

Three Gradle modules:

```
cleancity-kmp/
├── composeApp/          # Existing KMP client (depends on :shared)
├── shared/              # NEW — API models shared between client and server
│   └── src/commonMain/kotlin/com/example/cleancity/shared/
│       ├── models/
│       │   ├── ProblemType.kt
│       │   ├── MarkerStatus.kt
│       │   ├── ComplaintResponse.kt
│       │   ├── SubbotnikResponse.kt
│       │   └── MapMarkersResponse.kt
│       └── requests/
│           ├── CreateComplaintRequest.kt
│           └── CreateSubbotnikRequest.kt
├── backend/             # NEW — Ktor server (depends on :shared)
│   └── src/main/kotlin/com/example/cleancity/
│       ├── Application.kt
│       ├── config/
│       │   └── Database.kt
│       ├── markers/
│       │   ├── MarkerRoutes.kt
│       │   ├── MarkerService.kt
│       │   └── MarkerRepository.kt
│       ├── storage/
│       │   ├── StorageService.kt
│       │   └── LocalStorageService.kt
│       └── database/
│           └── tables/
│               ├── Complaints.kt
│               └── Subbotniks.kt
├── iosApp/
├── docker-compose.yml
└── settings.gradle.kts  # Updated: includes :shared and :backend
```

## Shared Module — API Models

### Enums

```kotlin
@Serializable
enum class ProblemType { DUMP, ROAD, LIGHTING, GREENERY }

@Serializable
enum class MarkerStatus { NEW, RESOLVED }
```

### Requests

```kotlin
@Serializable
data class CreateComplaintRequest(
    val type: ProblemType,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String
)

@Serializable
data class CreateSubbotnikRequest(
    val title: String,
    val description: String,
    val date: String,       // ISO 8601 date
    val time: String,       // HH:mm
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val deviceId: String
)
```

### Responses

```kotlin
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

@Serializable
data class MapMarkersResponse(
    val complaints: List<ComplaintResponse>,
    val subbotniks: List<SubbotnikResponse>
)
```

## REST API

| Method | Path | Description | Body |
|--------|------|-------------|------|
| POST | `/api/complaints` | Create complaint | Multipart: `data` (JSON) + `photo` (file) |
| POST | `/api/subbotniks` | Create subbotnik | Multipart: `data` (JSON) + `photo` (file, optional) |
| GET | `/api/markers?swLat=&swLon=&neLat=&neLon=` | Get markers in map bounds | — |
| GET | `/api/complaints/{id}` | Get complaint details | — |
| GET | `/api/subbotniks/{id}` | Get subbotnik details | — |
| GET | `/api/photos/{filename}` | Serve photo file | — |

### Multipart Upload Format

For `POST /api/complaints`:
- Part `data`: JSON string of `CreateComplaintRequest`
- Part `photo`: Image file (JPEG/PNG), mandatory

For `POST /api/subbotniks`:
- Part `data`: JSON string of `CreateSubbotnikRequest`
- Part `photo`: Image file (JPEG/PNG), optional

### Geo Query

`GET /api/markers` returns all complaints and subbotniks within the specified bounding box. The `bounds` parameters (`swLat`, `swLon`, `neLat`, `neLon`) define the visible map area.

## Database Schema

PostgreSQL 16 with PostGIS extension.

### Migration V1__create_markers.sql

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

### Geo Query Pattern

```sql
SELECT * FROM complaints
WHERE ST_Within(location, ST_MakeEnvelope(swLon, swLat, neLon, neLat, 4326));
```

## Photo Storage

Interface-based abstraction for future migration to S3/MinIO:

```kotlin
interface StorageService {
    suspend fun save(fileName: String, bytes: ByteArray): String  // returns relative path
    suspend fun get(fileName: String): ByteArray?
    fun getUrl(fileName: String): String
}
```

`LocalStorageService` implements this using a configurable directory (env var `STORAGE_PATH`).

File naming: `{uuid}.{extension}` to avoid collisions.

Constraints:
- Accepted formats: JPEG, PNG
- Max file size: 10 MB
- Server validates format and size, returns 400 on violation

## Infrastructure — Docker Compose

```yaml
services:
  db:
    image: postgis/postgis:16-3.4
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: cleancity
      POSTGRES_USER: cleancity
      POSTGRES_PASSWORD: cleancity
    volumes: [pgdata:/var/lib/postgresql/data]

  backend:
    build: ./backend
    ports: ["8080:8080"]
    depends_on: [db]
    environment:
      DB_URL: jdbc:postgresql://db:5432/cleancity
      DB_USER: cleancity
      DB_PASSWORD: cleancity
      STORAGE_PATH: /app/uploads
    volumes: [uploads:/app/uploads]

volumes:
  pgdata:
  uploads:
```

## Client Integration

`composeApp` replaces `InMemoryRepository` with an HTTP client using Ktor Client:

```kotlin
class ApiClient(private val baseUrl: String) {
    private val client = HttpClient {
        install(ContentNegotiation) { json() }
    }

    suspend fun getMarkers(bounds: MapBounds): MapMarkersResponse
    suspend fun createComplaint(request: CreateComplaintRequest, photo: ByteArray): ComplaintResponse
    suspend fun createSubbotnik(request: CreateSubbotnikRequest, photo: ByteArray?): SubbotnikResponse
}
```

Both `composeApp` and `backend` depend on `:shared` for type-safe request/response models.

## Backend Dependencies

| Library | Purpose |
|---------|---------|
| ktor-server-netty | HTTP server engine |
| ktor-server-content-negotiation | Content negotiation plugin |
| ktor-serialization-kotlinx-json | JSON serialization |
| exposed-core, exposed-jdbc | ORM |
| postgresql (JDBC driver) | Database driver |
| flyway-core | Schema migrations |
| logback-classic | Logging |

## Out of Scope (Phase 1)

- User authentication (registration/login) — Phase 2
- Voting on complaints — Phase 3
- Subbotnik chat — Phase 4
- Content moderation/reporting — Phase 5
- Notifications — Deferred
- iOS native map implementation
- Dark mode
- Rate limiting
- Gamification/XP

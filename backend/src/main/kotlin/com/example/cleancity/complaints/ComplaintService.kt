package com.example.cleancity.complaints

import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintPhotoResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.storage.ImageProcessor
import com.example.cleancity.storage.StorageService
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Просмотрщик жалоб — гость, резидент или админ. Используется для
 * фильтра видимости (резиденты не видят REJECTED/DUPLICATE на публике;
 * автор видит свои всегда — это покрывается отдельным эндпоинтом /mine).
 */
sealed class Viewer {
    object Guest : Viewer()
    data class Authenticated(val userId: Long, val role: UserRole) : Viewer()
}

class PhotoUpload(
    val bytes: ByteArray,
    val originalName: String?
)

class ComplaintService(
    private val repo: ComplaintRepository,
    private val storage: StorageService
) {
    companion object {
        // Сочи и окрестности (с запасом) — координаты вне этой области отклоняются.
        // SPEC §5.1: «Бэкенд верифицирует, что координаты внутри Сочи (BBox-checks)».
        private const val SOCHI_MIN_LAT = 43.0
        private const val SOCHI_MAX_LAT = 44.0
        private const val SOCHI_MIN_LON = 39.0
        private const val SOCHI_MAX_LON = 41.0

        private const val MAX_PHOTOS = 5
        private const val MIN_PHOTOS = 1

        private val PUBLIC_VISIBLE: Set<ComplaintStatus> =
            setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED)
        private val ALL_STATUSES: Set<ComplaintStatus> = ComplaintStatus.entries.toSet()
    }

    fun create(authorId: Long, req: CreateComplaintRequest, photos: List<PhotoUpload>): ComplaintResponse {
        validate(req, photos)

        val processed = photos.mapIndexed { i, upload ->
            ImageProcessor.validate(upload.bytes)
            processAndStore(upload, sortOrder = i)
        }

        val address = req.address.trim()
        val id = repo.create(
            authorId = authorId,
            category = req.category,
            title = generateTitle(req.category, address),
            description = req.description.trim(),
            latitude = req.latitude,
            longitude = req.longitude,
            address = address,
            district = req.district?.trim()?.takeIf { it.isNotBlank() }
        )

        val photoRows = repo.savePhotos(id, processed)

        return repo.findById(id)?.toResponse(photoRows.toResponses())
            ?: error("Complaint just created but not found, id=$id")
    }

    fun getById(id: Long, viewer: Viewer): ComplaintResponse? {
        val row = repo.findById(id) ?: return null
        if (!isVisible(row.status, viewer, row.authorId)) return null
        val photos = repo.listPhotos(id).toResponses()
        return row.toResponse(photos)
    }

    fun list(viewer: Viewer, filter: PublicListFilter): ComplaintListResponse {
        val (rows, total) = repo.list(
            ComplaintFilter(
                visibleStatuses = visibleStatusesFor(viewer),
                category = filter.category,
                district = filter.district,
                sort = filter.sort,
                page = filter.page,
                size = filter.size
            )
        )
        val photos = repo.listPhotosForMany(rows.map { it.id })
        val items = rows.map { it.toResponse((photos[it.id] ?: emptyList()).toResponses()) }
        return ComplaintListResponse(items, filter.page, filter.size, total)
    }

    fun listMine(authorId: Long, page: Int, size: Int): ComplaintListResponse {
        val (rows, total) = repo.list(
            ComplaintFilter(
                visibleStatuses = ALL_STATUSES,
                authorId = authorId,
                page = page,
                size = size
            )
        )
        val photos = repo.listPhotosForMany(rows.map { it.id })
        val items = rows.map { it.toResponse((photos[it.id] ?: emptyList()).toResponses()) }
        return ComplaintListResponse(items, page, size, total)
    }

    fun listMarkers(
        viewer: Viewer,
        swLat: Double, swLon: Double,
        neLat: Double, neLon: Double,
        category: ProblemCategory?
    ): MapMarkersResponse {
        val rows = repo.listMarkers(swLat, swLon, neLat, neLon, category, visibleStatusesFor(viewer))
        return MapMarkersResponse(
            markers = rows.map { MapMarker(it.id, it.category, it.status, it.latitude, it.longitude) }
        )
    }

    // --- private helpers ---

    private fun validate(req: CreateComplaintRequest, photos: List<PhotoUpload>) {
        require(photos.size in MIN_PHOTOS..MAX_PHOTOS) { "Need $MIN_PHOTOS to $MAX_PHOTOS photos" }
        require(req.description.isNotBlank()) { "Description is required" }
        require(req.description.length <= 5000) { "Description is too long" }
        require(req.address.isNotBlank()) { "Address is required" }
        require(req.latitude in SOCHI_MIN_LAT..SOCHI_MAX_LAT) { "Latitude outside Sochi" }
        require(req.longitude in SOCHI_MIN_LON..SOCHI_MAX_LON) { "Longitude outside Sochi" }
    }

    private fun generateTitle(category: ProblemCategory, address: String): String {
        val street = address.split(',').firstOrNull()?.trim()?.ifBlank { null } ?: address
        return "${category.localizedLabel} · $street".take(300)
    }

    private fun processAndStore(upload: PhotoUpload, sortOrder: Int): NewPhoto {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val year = now.year.toString()
        val month = now.monthValue.toString().padStart(2, '0')
        val uuid = UUID.randomUUID().toString()
        val originalKey = "photos/$year/$month/$uuid.jpg"
        val thumbKey = "photos/$year/$month/${uuid}_thumb.jpg"

        val originalBytes = ImageProcessor.normalizeOriginal(upload.bytes)
        val thumbBytes = ImageProcessor.thumbnail(upload.bytes)

        val originalUrl = storage.save(originalKey, originalBytes, "image/jpeg")
        val thumbUrl = storage.save(thumbKey, thumbBytes, "image/jpeg")

        return NewPhoto(
            storageKey = originalKey,
            photoUrl = originalUrl,
            thumbUrl = thumbUrl,
            sortOrder = sortOrder
        )
    }

    private fun visibleStatusesFor(viewer: Viewer): Set<ComplaintStatus> = when (viewer) {
        is Viewer.Authenticated -> if (viewer.role == UserRole.ADMIN) ALL_STATUSES else PUBLIC_VISIBLE
        Viewer.Guest -> PUBLIC_VISIBLE
    }

    private fun isVisible(status: ComplaintStatus, viewer: Viewer, authorId: Long): Boolean {
        if (status in PUBLIC_VISIBLE) return true
        if (viewer is Viewer.Authenticated) {
            if (viewer.role == UserRole.ADMIN) return true
            if (viewer.userId == authorId) return true
            // TODO Day 5: разрешить также тем, кто проголосовал «+1» — для пуш-контекста.
        }
        return false
    }

    private fun ComplaintRow.toResponse(photos: List<ComplaintPhotoResponse>): ComplaintResponse =
        ComplaintResponse(
            id = id,
            authorId = authorId,
            authorName = authorName,
            category = category,
            title = title,
            description = description,
            latitude = latitude,
            longitude = longitude,
            address = address,
            district = district,
            status = status,
            photos = photos,
            duplicateOfId = duplicateOfId,
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString(),
            resolvedAt = resolvedAt?.toString()
        )

    private fun List<PhotoRow>.toResponses(): List<ComplaintPhotoResponse> = map {
        ComplaintPhotoResponse(it.id, it.photoUrl, it.thumbUrl, it.sortOrder)
    }
}

data class PublicListFilter(
    val category: ProblemCategory? = null,
    val district: String? = null,
    val sort: ComplaintSort = ComplaintSort.DATE,
    val page: Int = 0,
    val size: Int = 20
)

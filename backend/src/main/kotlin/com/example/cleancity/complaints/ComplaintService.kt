package com.example.cleancity.complaints

import com.example.cleancity.ConflictException
import com.example.cleancity.ForbiddenException
import com.example.cleancity.NotFoundException
import com.example.cleancity.auth.AuditLogger
import com.example.cleancity.database.tables.AuditAction
import com.example.cleancity.notifications.NotificationService
import com.example.cleancity.notifications.NotificationTexts
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.CategorySla
import com.example.cleancity.shared.models.ComplaintListResponse
import com.example.cleancity.shared.models.ComplaintPhotoResponse
import com.example.cleancity.shared.models.ComplaintResponse
import com.example.cleancity.shared.models.ComplaintStatus
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.DuplicateCandidateResponse
import com.example.cleancity.shared.models.DuplicateCandidatesResponse
import com.example.cleancity.shared.models.MapMarker
import com.example.cleancity.shared.models.MapMarkersResponse
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.StatusChangeResponse
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.ChangeStatusRequest
import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.storage.ImageProcessor
import com.example.cleancity.storage.StorageService
import com.example.cleancity.votes.VoteRepository
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Просмотрщик жалоб — гость, резидент или админ. Используется для фильтра видимости
 * (резиденты не видят REJECTED/DUPLICATE на публике; автор и проголосовавший — видят).
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
    private val storage: StorageService,
    private val voteRepo: VoteRepository,
    private val notifications: NotificationService,
    private val audit: AuditLogger
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

        // 500 м ≈ покрывает несколько кварталов в Сочи и ловит дубликаты, которые
        // 100 м пропускали (двор vs. соседняя улица). 1000 м — технический потолок,
        // выше уже больше шума, чем полезных совпадений.
        private const val DEFAULT_DUPLICATE_RADIUS_M = 500
        private const val MAX_DUPLICATE_RADIUS_M = 1000

        private val PUBLIC_VISIBLE: Set<ComplaintStatus> =
            setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED)
        private val ALL_STATUSES: Set<ComplaintStatus> = ComplaintStatus.entries.toSet()
        private val ACTIVE_AS_DUPLICATE_TARGET: Set<ComplaintStatus> =
            setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED)

        // SPEC §3.2: какие переходы статуса допустимы.
        private val ALLOWED_TRANSITIONS: Map<ComplaintStatus, Set<ComplaintStatus>> = mapOf(
            ComplaintStatus.NEW to setOf(
                ComplaintStatus.IN_PROGRESS,
                ComplaintStatus.REJECTED,
                ComplaintStatus.DUPLICATE
            ),
            ComplaintStatus.IN_PROGRESS to setOf(
                ComplaintStatus.RESOLVED,
                ComplaintStatus.REJECTED,
                ComplaintStatus.DUPLICATE
            )
        )

        private val ADMIN_ROLES = setOf(UserRole.ADMIN, UserRole.OPERATOR, UserRole.INSPECTOR)

        // Активные статусы, для которых SLA может «гореть» (terminal — никогда).
        private val SLA_ACTIVE = setOf(ComplaintStatus.NEW, ComplaintStatus.IN_PROGRESS)
    }

    fun create(authorId: Long, req: CreateComplaintRequest, photos: List<PhotoUpload>): ComplaintResponse {
        validate(req, photos)

        val processed = photos.mapIndexed { i, upload ->
            ImageProcessor.validate(upload.bytes)
            processAndStore(upload, sortOrder = i)
        }

        val address = req.address.trim()

        val (id, photoRows) = transaction {
            val newId = repo.create(
                authorId = authorId,
                category = req.category,
                title = generateTitle(req.category, address),
                description = req.description.trim(),
                latitude = req.latitude,
                longitude = req.longitude,
                address = address,
                // Нормализуем район геокодера к одному из 4 District (храним каноничный
                // label) — иначе фильтр по району в веб-админке не находит совпадений.
                district = District.fromGeocoderText(req.district)?.localizedLabel
            )
            val rows = repo.savePhotos(newId, processed)
            voteRepo.insertAuthorVote(newId, authorId)
            newId to rows
        }

        val row = repo.findById(id) ?: error("Complaint just created but not found, id=$id")
        return row.toResponse(
            photos = photoRows.toResponses(),
            votesCount = voteRepo.countYes(id),
            userVoted = true,
            statusHistory = emptyList()
        )
    }

    fun getById(id: Long, viewer: Viewer): ComplaintResponse? {
        val row = repo.findById(id) ?: return null
        if (!isVisible(row.id, row.status, viewer, row.authorId)) return null

        val photos = repo.listPhotos(id).toResponses()
        val votesCount = voteRepo.countYes(id)
        val userVoted = (viewer as? Viewer.Authenticated)?.let { voteRepo.userVoted(id, it.userId) } ?: false
        val history = repo.listStatusHistory(id).map { it.toResponse() }

        val (slaDeadline, slaBreached) = slaFor(row, viewer)
        return row.toResponse(photos, votesCount, userVoted, history, slaDeadline, slaBreached)
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
        return enrichList(rows, viewer, filter.page, filter.size, total)
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
        return enrichList(rows, Viewer.Authenticated(authorId, UserRole.RESIDENT), page, size, total)
    }

    fun listVoted(userId: Long, page: Int, size: Int): ComplaintListResponse {
        val (rows, total) = repo.listVoted(userId, page, size)
        return enrichList(rows, Viewer.Authenticated(userId, UserRole.RESIDENT), page, size, total)
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

    fun findDuplicates(
        latitude: Double,
        longitude: Double,
        category: ProblemCategory,
        radiusMeters: Int?
    ): DuplicateCandidatesResponse {
        require(latitude in SOCHI_MIN_LAT..SOCHI_MAX_LAT) { "Latitude outside Sochi" }
        require(longitude in SOCHI_MIN_LON..SOCHI_MAX_LON) { "Longitude outside Sochi" }
        val radius = (radiusMeters ?: DEFAULT_DUPLICATE_RADIUS_M)
            .coerceIn(1, MAX_DUPLICATE_RADIUS_M)

        val rows = repo.findDuplicates(latitude, longitude, category, radius)
        return DuplicateCandidatesResponse(
            items = rows.map {
                DuplicateCandidateResponse(
                    id = it.id,
                    title = it.title,
                    category = it.category,
                    status = it.status,
                    address = it.address,
                    votesCount = it.votesCount,
                    distanceMeters = it.distanceMeters,
                    createdAt = it.createdAt.toString()
                )
            }
        )
    }

    /**
     * Сменить статус. SPEC §5.2: обязательный комментарий, валидация перехода,
     * для DUPLICATE — проверка существования и активности оригинала, слияние голосов.
     * Push уведомления — после успешного коммита транзакции (на Day 5 — заглушка).
     */
    fun changeStatus(
        complaintId: Long,
        actor: Viewer.Authenticated,
        req: ChangeStatusRequest,
        ip: String?,
        userAgent: String?
    ): ComplaintResponse {
        if (actor.role !in ADMIN_ROLES) {
            throw ForbiddenException("Только админ может менять статус")
        }
        val comment = req.comment.trim()
        require(comment.isNotBlank()) { "Комментарий обязателен" }
        require(comment.length <= 2000) { "Комментарий слишком длинный (макс. 2000)" }

        val current = repo.findById(complaintId)
            ?: throw NotFoundException("Жалоба не найдена")

        val allowed = ALLOWED_TRANSITIONS[current.status]
            ?: throw ConflictException("Жалоба уже в терминальном статусе ${current.status}")
        if (req.toStatus !in allowed) {
            throw ConflictException("Недопустимый переход: ${current.status} → ${req.toStatus}")
        }

        val duplicateOfId: Long? = if (req.toStatus == ComplaintStatus.DUPLICATE) {
            val original = req.duplicateOfId
                ?: throw IllegalArgumentException("Для статуса DUPLICATE укажите duplicateOfId")
            if (original == complaintId) {
                throw IllegalArgumentException("Жалоба не может быть дубликатом самой себя")
            }
            val originalRow = repo.findById(original)
                ?: throw IllegalArgumentException("Оригинал не найден")
            if (originalRow.status !in ACTIVE_AS_DUPLICATE_TARGET) {
                throw IllegalArgumentException("Оригинал должен быть активной жалобой")
            }
            original
        } else {
            null
        }

        val setResolvedNow = req.toStatus == ComplaintStatus.RESOLVED

        // Транзакция: UPDATE complaints + INSERT status_changes + (опц.) merge голосов
        // + INSERT в notifications. Если что-то падает — всё откатывается атомарно
        // (SPEC §5.2): статус не должен меняться без уведомления.
        transaction {
            repo.updateStatus(
                complaintId = complaintId,
                newStatus = req.toStatus,
                duplicateOfId = duplicateOfId,
                setResolvedNow = setResolvedNow
            )
            repo.insertStatusChange(
                complaintId = complaintId,
                fromStatus = current.status,
                toStatus = req.toStatus,
                comment = comment,
                changedById = actor.userId
            )
            if (req.toStatus == ComplaintStatus.DUPLICATE && duplicateOfId != null) {
                voteRepo.mergeVotesInto(originalId = duplicateOfId, duplicateId = complaintId)
            }

            val recipients = when (req.toStatus) {
                ComplaintStatus.IN_PROGRESS, ComplaintStatus.RESOLVED -> listOf(current.authorId)
                ComplaintStatus.REJECTED, ComplaintStatus.DUPLICATE -> {
                    val supporters = voteRepo.listSupporterIds(complaintId)
                    (supporters + current.authorId).distinct()
                }
                ComplaintStatus.NEW -> emptyList()
            }
            if (recipients.isNotEmpty()) {
                val text = NotificationTexts.statusChange(
                    complaintTitle = current.title,
                    toStatus = req.toStatus,
                    adminComment = comment
                )
                notifications.notify(
                    recipientUserIds = recipients,
                    kind = NotificationKind.COMPLAINT_STATUS,
                    title = text.title,
                    body = text.body,
                    iconStyle = text.iconStyle,
                    complaintId = complaintId
                )
            }
        }

        audit.log(
            action = AuditAction.COMPLAINT_STATUS_CHANGE,
            actorUserId = actor.userId,
            targetType = "complaint",
            targetId = complaintId.toString(),
            ip = ip,
            userAgent = userAgent,
            metadata = """{"from":"${current.status}","to":"${req.toStatus}"""" +
                (duplicateOfId?.let { ""","duplicate_of_id":$it""" } ?: "") + "}"
        )

        return getById(complaintId, actor)
            ?: error("Complaint just updated but not visible, id=$complaintId")
    }

    // --- private helpers ---

    private fun enrichList(
        rows: List<ComplaintRow>,
        viewer: Viewer,
        page: Int,
        size: Int,
        total: Long
    ): ComplaintListResponse {
        val ids = rows.map { it.id }
        val photos = repo.listPhotosForMany(ids)
        val countsByComplaint = voteRepo.countYesForMany(ids)
        val votedSet = (viewer as? Viewer.Authenticated)
            ?.let { voteRepo.votedComplaintIds(ids, it.userId) }
            ?: emptySet()

        val items = rows.map {
            val (deadline, breached) = slaFor(it, viewer)
            it.toResponse(
                photos = (photos[it.id] ?: emptyList()).toResponses(),
                votesCount = countsByComplaint[it.id] ?: 0,
                userVoted = it.id in votedSet,
                statusHistory = emptyList(),
                slaDeadline = deadline,
                slaBreached = breached
            )
        }
        return ComplaintListResponse(items, page, size, total)
    }

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
        is Viewer.Authenticated -> if (viewer.role in ADMIN_ROLES) ALL_STATUSES else PUBLIC_VISIBLE
        Viewer.Guest -> PUBLIC_VISIBLE
    }

    /**
     * SLA-метка для строки. SPEC §146: жителям SLA не видна нигде — для не-админа
     * возвращаем (null, false), поле не утекает в JSON.
     */
    private fun slaFor(row: ComplaintRow, viewer: Viewer): Pair<String?, Boolean> {
        val isAdmin = (viewer as? Viewer.Authenticated)?.role in ADMIN_ROLES
        if (!isAdmin) return null to false
        val deadline = row.createdAt.plusHours(CategorySla.hoursFor(row.category).toLong())
        val breached = row.status in SLA_ACTIVE &&
            OffsetDateTime.now(ZoneOffset.UTC).isAfter(deadline)
        return deadline.toString() to breached
    }

    private fun isVisible(
        complaintId: Long,
        status: ComplaintStatus,
        viewer: Viewer,
        authorId: Long
    ): Boolean {
        if (status in PUBLIC_VISIBLE) return true
        if (viewer is Viewer.Authenticated) {
            if (viewer.role in ADMIN_ROLES) return true
            if (viewer.userId == authorId) return true
            // SPEC §4.3: REJECTED/DUPLICATE видны проголосовавшему «+1» —
            // чтобы он увидел пояснение админа (см. экран «Поддержанные»).
            return voteRepo.userVoted(complaintId, viewer.userId)
        }
        return false
    }

    private fun ComplaintRow.toResponse(
        photos: List<ComplaintPhotoResponse>,
        votesCount: Int,
        userVoted: Boolean,
        statusHistory: List<StatusChangeResponse>,
        slaDeadline: String? = null,
        slaBreached: Boolean = false
    ): ComplaintResponse =
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
            votesCount = votesCount,
            userVoted = userVoted,
            duplicateOfId = duplicateOfId,
            createdAt = createdAt.toString(),
            updatedAt = updatedAt.toString(),
            resolvedAt = resolvedAt?.toString(),
            statusHistory = statusHistory,
            slaDeadline = slaDeadline,
            slaBreached = slaBreached
        )

    private fun StatusChangeRow.toResponse(): StatusChangeResponse = StatusChangeResponse(
        fromStatus = fromStatus,
        toStatus = toStatus,
        comment = comment,
        changedByName = changedByName,
        createdAt = createdAt.toString()
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

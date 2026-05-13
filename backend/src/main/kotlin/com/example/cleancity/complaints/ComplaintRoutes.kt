package com.example.cleancity.complaints

import com.example.cleancity.BadRequestException
import com.example.cleancity.ErrorCodes
import com.example.cleancity.NotFoundException
import com.example.cleancity.UnauthorizedException
import com.example.cleancity.auth.clientIp
import com.example.cleancity.auth.userAgentSafe
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.UserRole
import com.example.cleancity.shared.requests.ChangeStatusRequest
import com.example.cleancity.shared.requests.CreateComplaintRequest
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.utils.io.toByteArray
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.Json

private const val MAX_PAGE_SIZE = 100

fun Route.complaintRoutes(service: ComplaintService) {
    val json = Json { ignoreUnknownKeys = true }

    route("/complaints") {

        // Карта, список и /duplicates доступны и гостям, и резидентам, и админам —
        // видимость регулируется внутри service по принципалу JWT (для гостей principal == null).
        authenticate("auth-jwt", optional = true) {

            get {
                val viewer = call.viewer()
                val filter = call.parsePublicFilter()
                call.respond(service.list(viewer, filter))
            }

            get("/map") {
                val viewer = call.viewer()
                val swLat = call.queryDouble("swLat") ?: throw badField("swLat is required")
                val swLon = call.queryDouble("swLon") ?: throw badField("swLon is required")
                val neLat = call.queryDouble("neLat") ?: throw badField("neLat is required")
                val neLon = call.queryDouble("neLon") ?: throw badField("neLon is required")
                if (swLat >= neLat || swLon >= neLon) throw badField("Invalid bbox")
                val category = call.queryEnum<ProblemCategory>("category")
                call.respond(service.listMarkers(viewer, swLat, swLon, neLat, neLon, category))
            }

            get("/duplicates") {
                val lat = call.queryDouble("lat") ?: throw badField("lat is required")
                val lon = call.queryDouble("lon") ?: throw badField("lon is required")
                val rawCategory = call.request.queryParameters["category"]
                    ?: throw badField("category is required")
                val category = runCatching { ProblemCategory.valueOf(rawCategory.uppercase()) }.getOrNull()
                    ?: throw badField("Invalid 'category' value: $rawCategory")
                val radius = call.queryInt("radius")
                call.respond(service.findDuplicates(lat, lon, category, radius))
            }

            get("/{id}") {
                val viewer = call.viewer()
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: throw badField("Invalid id")
                val complaint = service.getById(id, viewer)
                    ?: throw NotFoundException("Complaint not found")
                call.respond(complaint)
            }
        }

        // Только для аутентифицированных резидентов/админов.
        authenticate("auth-jwt") {

            post {
                val userId = call.requireUserId()

                var requestJson: String? = null
                val photos = mutableListOf<PhotoUpload>()

                call.receiveMultipart().forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> if (part.name == "data") requestJson = part.value
                        is PartData.FileItem -> if (part.name == "photo" || part.name == "photo[]" || part.name == "photos") {
                            val bytes = part.provider().toByteArray()
                            photos += PhotoUpload(bytes, part.originalFileName)
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (requestJson == null) throw badField("Missing 'data' part with JSON request")
                if (photos.isEmpty()) throw badField("At least one 'photo' file is required")

                val req = try {
                    json.decodeFromString<CreateComplaintRequest>(requestJson!!)
                } catch (e: Exception) {
                    throw badField("Invalid 'data' JSON: ${e.message}")
                }

                val response = service.create(userId, req, photos)
                call.respond(HttpStatusCode.Created, response)
            }

            get("/mine") {
                val userId = call.requireUserId()
                val page = call.queryInt("page") ?: 0
                val size = (call.queryInt("size") ?: 20).coerceAtMost(MAX_PAGE_SIZE)
                call.respond(service.listMine(userId, page, size))
            }

            get("/voted") {
                val userId = call.requireUserId()
                val page = (call.queryInt("page") ?: 0).coerceAtLeast(0)
                val size = (call.queryInt("size") ?: 20).coerceIn(1, MAX_PAGE_SIZE)
                call.respond(service.listVoted(userId, page, size))
            }

            patch("/{id}/status") {
                val actor = call.authenticated()
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: throw badField("Invalid id")
                val req = try {
                    call.receive<ChangeStatusRequest>()
                } catch (e: Exception) {
                    throw badField("Invalid JSON: ${e.message}")
                }
                val updated = service.changeStatus(
                    complaintId = id,
                    actor = actor,
                    req = req,
                    ip = call.clientIp(),
                    userAgent = call.userAgentSafe()
                )
                call.respond(updated)
            }
        }
    }
}

// --- helpers ---

private fun badField(message: String): BadRequestException =
    BadRequestException(message, ErrorCodes.VALIDATION_BAD_FIELD)

private fun ApplicationCall.viewer(): Viewer {
    val principal = principal<JWTPrincipal>() ?: return Viewer.Guest
    val userId = principal.payload.subject?.toLongOrNull() ?: return Viewer.Guest
    val role = runCatching { UserRole.valueOf(principal.payload.getClaim("role").asString()) }
        .getOrDefault(UserRole.RESIDENT)
    return Viewer.Authenticated(userId, role)
}

private fun ApplicationCall.authenticated(): Viewer.Authenticated {
    val v = viewer()
    if (v !is Viewer.Authenticated) throw UnauthorizedException("Not authenticated")
    return v
}

private fun ApplicationCall.requireUserId(): Long {
    return principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
        ?: throw UnauthorizedException("Not authenticated")
}

private fun ApplicationCall.queryDouble(name: String): Double? =
    request.queryParameters[name]?.toDoubleOrNull()

private fun ApplicationCall.queryInt(name: String): Int? =
    request.queryParameters[name]?.toIntOrNull()

/**
 * Возвращает enum из query или null, если параметр не передан.
 * Если параметр передан, но недопустим — бросает BadRequestException.
 */
private inline fun <reified T : Enum<T>> ApplicationCall.queryEnum(name: String): T? {
    val raw = request.queryParameters[name] ?: return null
    return runCatching { enumValueOf<T>(raw.uppercase()) }.getOrNull()
        ?: throw badField("Invalid '$name' value: $raw")
}

private fun ApplicationCall.parsePublicFilter(): PublicListFilter {
    val category = queryEnum<ProblemCategory>("category")
    val sort = (request.queryParameters["sort"]?.uppercase()?.let {
        runCatching { ComplaintSort.valueOf(it) }.getOrNull()
    }) ?: ComplaintSort.DATE
    val district = request.queryParameters["district"]?.takeIf { it.isNotBlank() }
    val page = (queryInt("page") ?: 0).coerceAtLeast(0)
    val size = (queryInt("size") ?: 20).coerceIn(1, MAX_PAGE_SIZE)
    return PublicListFilter(category, district, sort, page, size)
}

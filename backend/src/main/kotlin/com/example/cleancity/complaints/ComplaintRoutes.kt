package com.example.cleancity.complaints

import com.example.cleancity.auth.clientIp
import com.example.cleancity.auth.userAgentSafe
import com.example.cleancity.shared.models.MessageResponse
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
                val filter = call.parsePublicFilter() ?: return@get
                call.respond(service.list(viewer, filter))
            }

            get("/map") {
                val viewer = call.viewer()
                val swLat = call.queryDouble("swLat") ?: return@get call.badRequest("swLat is required")
                val swLon = call.queryDouble("swLon") ?: return@get call.badRequest("swLon is required")
                val neLat = call.queryDouble("neLat") ?: return@get call.badRequest("neLat is required")
                val neLon = call.queryDouble("neLon") ?: return@get call.badRequest("neLon is required")
                if (swLat >= neLat || swLon >= neLon) {
                    return@get call.badRequest("Invalid bbox")
                }
                val category = call.queryEnum<ProblemCategory>("category") ?: return@get
                call.respond(service.listMarkers(viewer, swLat, swLon, neLat, neLon, category.value))
            }

            get("/duplicates") {
                val lat = call.queryDouble("lat") ?: return@get call.badRequest("lat is required")
                val lon = call.queryDouble("lon") ?: return@get call.badRequest("lon is required")
                val rawCategory = call.request.queryParameters["category"]
                    ?: return@get call.badRequest("category is required")
                val category = runCatching { ProblemCategory.valueOf(rawCategory.uppercase()) }.getOrNull()
                    ?: return@get call.badRequest("Invalid 'category' value: $rawCategory")
                val radius = call.queryInt("radius")
                try {
                    call.respond(service.findDuplicates(lat, lon, category, radius))
                } catch (e: IllegalArgumentException) {
                    call.badRequest(e.message ?: "Invalid request")
                }
            }

            get("/{id}") {
                val viewer = call.viewer()
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@get call.badRequest("Invalid id")
                val complaint = service.getById(id, viewer)
                if (complaint == null) {
                    call.respond(HttpStatusCode.NotFound, MessageResponse("Complaint not found"))
                } else {
                    call.respond(complaint)
                }
            }
        }

        // Только для аутентифицированных резидентов/админов.
        authenticate("auth-jwt") {

            post {
                val userId = call.requireUserId() ?: return@post

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

                if (requestJson == null) {
                    return@post call.badRequest("Missing 'data' part with JSON request")
                }
                if (photos.isEmpty()) {
                    return@post call.badRequest("At least one 'photo' file is required")
                }

                val req = try {
                    json.decodeFromString<CreateComplaintRequest>(requestJson!!)
                } catch (e: Exception) {
                    return@post call.badRequest("Invalid 'data' JSON: ${e.message}")
                }

                try {
                    val response = service.create(userId, req, photos)
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: IllegalArgumentException) {
                    call.badRequest(e.message ?: "Invalid request")
                }
            }

            get("/mine") {
                val userId = call.requireUserId() ?: return@get
                val page = call.queryInt("page") ?: 0
                val size = (call.queryInt("size") ?: 20).coerceAtMost(MAX_PAGE_SIZE)
                call.respond(service.listMine(userId, page, size))
            }

            get("/voted") {
                val userId = call.requireUserId() ?: return@get
                val page = (call.queryInt("page") ?: 0).coerceAtLeast(0)
                val size = (call.queryInt("size") ?: 20).coerceIn(1, MAX_PAGE_SIZE)
                call.respond(service.listVoted(userId, page, size))
            }

            patch("/{id}/status") {
                val actor = call.authenticated() ?: return@patch
                val id = call.parameters["id"]?.toLongOrNull()
                    ?: return@patch call.badRequest("Invalid id")
                val req = try {
                    call.receive<ChangeStatusRequest>()
                } catch (e: Exception) {
                    return@patch call.badRequest("Invalid JSON: ${e.message}")
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

private fun ApplicationCall.viewer(): Viewer {
    val principal = principal<JWTPrincipal>() ?: return Viewer.Guest
    val userId = principal.payload.subject?.toLongOrNull() ?: return Viewer.Guest
    val role = runCatching { UserRole.valueOf(principal.payload.getClaim("role").asString()) }
        .getOrDefault(UserRole.RESIDENT)
    return Viewer.Authenticated(userId, role)
}

private suspend fun ApplicationCall.authenticated(): Viewer.Authenticated? {
    val v = viewer()
    if (v !is Viewer.Authenticated) {
        respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
        return null
    }
    return v
}

private suspend fun ApplicationCall.requireUserId(): Long? {
    val id = principal<JWTPrincipal>()?.payload?.subject?.toLongOrNull()
    if (id == null) respond(HttpStatusCode.Unauthorized, MessageResponse("Not authenticated"))
    return id
}

private fun ApplicationCall.queryDouble(name: String): Double? =
    request.queryParameters[name]?.toDoubleOrNull()

private fun ApplicationCall.queryInt(name: String): Int? =
    request.queryParameters[name]?.toIntOrNull()

/**
 * Возвращает обёртку с распарсенным enum (или дефолт null, если параметр не передан).
 * Если параметр передан, но недопустим — отвечает 400 и возвращает null,
 * чтобы caller сделал `?: return@get`.
 */
private suspend inline fun <reified T : Enum<T>> ApplicationCall.queryEnum(name: String): EnumResult<T>? {
    val raw = request.queryParameters[name] ?: return EnumResult(null)
    val value = runCatching { enumValueOf<T>(raw.uppercase()) }.getOrNull()
    if (value == null) {
        respond(HttpStatusCode.BadRequest, MessageResponse("Invalid '$name' value: $raw"))
        return null
    }
    return EnumResult(value)
}

@JvmInline
value class EnumResult<T>(val value: T?)

private suspend fun ApplicationCall.parsePublicFilter(): PublicListFilter? {
    val category = queryEnum<ProblemCategory>("category") ?: return null
    val sort = (request.queryParameters["sort"]?.uppercase()?.let {
        runCatching { ComplaintSort.valueOf(it) }.getOrNull()
    }) ?: ComplaintSort.DATE
    val district = request.queryParameters["district"]?.takeIf { it.isNotBlank() }
    val page = (queryInt("page") ?: 0).coerceAtLeast(0)
    val size = (queryInt("size") ?: 20).coerceIn(1, MAX_PAGE_SIZE)
    return PublicListFilter(category.value, district, sort, page, size)
}

private suspend fun ApplicationCall.badRequest(message: String) {
    respond(HttpStatusCode.BadRequest, MessageResponse(message))
}

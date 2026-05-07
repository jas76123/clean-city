package com.example.cleancity.markers

import com.example.cleancity.shared.requests.CreateComplaintRequest
import com.example.cleancity.storage.StorageService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Route.markerRoutes(service: MarkerService, storage: StorageService) {
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
                            photoBytes = part.streamProvider().readBytes()
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

        get("/markers") {
            val swLat = call.request.queryParameters["swLat"]?.toDoubleOrNull()
            val swLon = call.request.queryParameters["swLon"]?.toDoubleOrNull()
            val neLat = call.request.queryParameters["neLat"]?.toDoubleOrNull()
            val neLon = call.request.queryParameters["neLon"]?.toDoubleOrNull()

            val markers = if (swLat != null && swLon != null && neLat != null && neLon != null) {
                service.getMarkersInBounds(swLat, swLon, neLat, neLon)
            } else {
                service.getAllMarkers()
            }
            call.respond(markers)
        }

        get("/complaints/{id}") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid id")
            val complaint = service.getComplaintById(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Complaint not found")
            call.respond(complaint)
        }

        get("/photos/{filename}") {
            val filename = call.parameters["filename"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing filename")
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return@get call.respond(HttpStatusCode.BadRequest, "Invalid filename")
            }
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

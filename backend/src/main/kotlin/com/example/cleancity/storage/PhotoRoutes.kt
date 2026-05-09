package com.example.cleancity.storage

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Раздаёт файлы из [LocalStorageService] на dev-сервере. В prod не
 * подключается — всё отдаёт Yandex Object Storage напрямую.
 *
 * Формат ключа: `photos/<year>/<month>/<uuid>.jpg` (см. SPEC §6).
 * Принимаем многоуровневый путь через `{path...}`.
 */
fun Route.photoRoutes(storage: LocalStorageService) {
    get("/photos/{path...}") {
        val parts = call.parameters.getAll("path")
        if (parts.isNullOrEmpty()) {
            call.respond(HttpStatusCode.BadRequest, mapOf("message" to "Missing path"))
            return@get
        }
        // Роут уже сматчился на `/photos/...`, поэтому первая часть пути — `photos`.
        // Storage-ключ имеет формат `photos/<year>/<month>/<uuid>.jpg` (см. SPEC §6),
        // поэтому склеиваем все части как есть.
        val key = parts.joinToString("/")
        val bytes = storage.read(key)
        if (bytes == null) {
            call.respond(HttpStatusCode.NotFound, mapOf("message" to "Photo not found"))
            return@get
        }
        val contentType = if (key.endsWith(".png")) ContentType.Image.PNG else ContentType.Image.JPEG
        call.respondBytes(bytes, contentType)
    }
}

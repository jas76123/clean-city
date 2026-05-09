package com.example.cleancity.storage

import java.io.File

/**
 * Файловое хранилище для dev. Запись/чтение в `storagePath/<key>`, публикация
 * через `GET /photos/<key>` (см. PhotoRoutes). Защита от path-traversal —
 * по canonical-path, ключи могут содержать `/` (формат `photos/YYYY/MM/uuid.jpg`).
 */
class LocalStorageService(
    private val storagePath: String,
    private val baseUrl: String
) : StorageService {

    private val rootCanonical: String = File(storagePath).also { it.mkdirs() }.canonicalPath

    override fun save(key: String, bytes: ByteArray, contentType: String): String {
        val target = resolveSafe(key) ?: error("Invalid storage key: $key")
        target.parentFile?.mkdirs()
        target.writeBytes(bytes)
        return urlFor(key)
    }

    override fun delete(key: String) {
        resolveSafe(key)?.delete()
    }

    override fun urlFor(key: String): String = "$baseUrl/photos/$key"

    /** Чтение для эндпоинта `GET /photos/{key}`. Возвращает null при traversal/отсутствии. */
    fun read(key: String): ByteArray? {
        val file = resolveSafe(key) ?: return null
        return if (file.exists() && file.isFile) file.readBytes() else null
    }

    private fun resolveSafe(key: String): File? {
        if (key.isBlank() || key.contains("..") || key.startsWith("/")) return null
        val file = File(storagePath, key)
        return if (file.canonicalPath.startsWith(rootCanonical)) file else null
    }
}

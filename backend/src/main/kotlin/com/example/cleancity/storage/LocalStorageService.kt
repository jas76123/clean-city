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

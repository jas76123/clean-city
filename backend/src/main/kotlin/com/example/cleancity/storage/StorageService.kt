package com.example.cleancity.storage

interface StorageService {
    fun save(fileName: String, bytes: ByteArray): String
    fun get(fileName: String): ByteArray?
    fun getUrl(fileName: String): String
}

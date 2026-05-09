package com.example.cleancity.storage

interface StorageService {
    /** Сохраняет байты под `key` и возвращает публичный URL. */
    fun save(key: String, bytes: ByteArray, contentType: String): String

    /** Удаляет объект по ключу (тихо игнорирует отсутствие). */
    fun delete(key: String)

    /** Публичный URL для уже сохранённого объекта. */
    fun urlFor(key: String): String
}

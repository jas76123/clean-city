package com.example.cleancity.data.storage

data class Tokens(val access: String, val refresh: String)

interface TokenStorage {
    suspend fun read(): Tokens?
    suspend fun write(access: String, refresh: String)
    suspend fun clear()
}

expect class TokenStorageFactory {
    fun create(): TokenStorage
}

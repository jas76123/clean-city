package com.example.cleancity.data.storage

class FakeTokenStorage : TokenStorage {
    private var tokens: Tokens? = null
    val writes = mutableListOf<Tokens>()
    var clearCount = 0
        private set

    fun preset(tokens: Tokens?) { this.tokens = tokens }

    override suspend fun read(): Tokens? = tokens
    override suspend fun write(access: String, refresh: String) {
        tokens = Tokens(access, refresh)
        writes += tokens!!
    }
    override suspend fun clear() {
        tokens = null
        clearCount += 1
    }
}

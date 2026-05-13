package com.example.cleancity.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidTokenStorage(private val context: Context) : TokenStorage {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "cleancity_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override suspend fun read(): Tokens? = withContext(Dispatchers.IO) {
        val a = prefs.getString(KEY_ACCESS, null) ?: return@withContext null
        val r = prefs.getString(KEY_REFRESH, null) ?: return@withContext null
        Tokens(a, r)
    }

    override suspend fun write(access: String, refresh: String) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(KEY_ACCESS, access)
                .putString(KEY_REFRESH, refresh)
                .apply()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit().clear().apply()
        }
    }

    companion object {
        private const val KEY_ACCESS = "access"
        private const val KEY_REFRESH = "refresh"
    }
}

actual class TokenStorageFactory(private val context: Context) {
    actual fun create(): TokenStorage = AndroidTokenStorage(context)
}

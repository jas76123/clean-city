package com.example.cleancity.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.seenNotificationsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "seen_notifications")

class AndroidSeenNotificationStore(private val context: Context) : SeenNotificationStore {
    override suspend fun get(userId: Long): Long {
        val key = longPreferencesKey("lastSeen:$userId")
        return context.seenNotificationsDataStore.data.first()[key] ?: 0L
    }

    override suspend fun set(userId: Long, id: Long) {
        val key = longPreferencesKey("lastSeen:$userId")
        context.seenNotificationsDataStore.edit { it[key] = id }
    }

    override suspend fun clear(userId: Long) {
        val key = longPreferencesKey("lastSeen:$userId")
        context.seenNotificationsDataStore.edit { it.remove(key) }
    }
}

actual class SeenNotificationStoreFactory(private val context: Context) {
    actual fun create(): SeenNotificationStore = AndroidSeenNotificationStore(context)
}

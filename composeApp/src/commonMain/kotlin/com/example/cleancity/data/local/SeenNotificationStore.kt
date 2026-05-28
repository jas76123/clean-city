package com.example.cleancity.data.local

/**
 * Per-user хранилище последнего известного id уведомления, чтобы
 * не пушить повторно одну и ту же запись из разных каналов (polling + worker).
 * 0 — стартовое значение «никаких записей ещё не видели».
 */
interface SeenNotificationStore {
    suspend fun get(userId: Long): Long
    suspend fun set(userId: Long, id: Long)
    suspend fun clear(userId: Long)
}

expect class SeenNotificationStoreFactory {
    fun create(): SeenNotificationStore
}

package com.example.cleancity.data.local

class InMemorySeenNotificationStore : SeenNotificationStore {
    private val map = mutableMapOf<Long, Long>()
    override suspend fun get(userId: Long): Long = map[userId] ?: 0L
    override suspend fun set(userId: Long, id: Long) { map[userId] = id }
    override suspend fun clear(userId: Long) { map.remove(userId) }
}

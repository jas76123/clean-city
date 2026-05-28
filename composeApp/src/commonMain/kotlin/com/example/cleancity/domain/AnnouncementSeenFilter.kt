package com.example.cleancity.domain

import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.shared.models.NotificationKind
import com.example.cleancity.shared.models.NotificationResponse

/**
 * Возвращает новые непрочитанные ANNOUNCEMENT, которые ещё не показывались
 * через push/баннер для этого юзера. Обновляет lastSeen до max(items.id).
 *
 * Контракт «первого вызова»: если lastSeen == 0 (юзер только что залогинился) —
 * возвращает пустой список, но запоминает max. Иначе будут сыпать N баннеров
 * сразу при первом входе.
 */
class AnnouncementSeenFilter(private val store: SeenNotificationStore) {

    suspend fun newAnnouncements(
        userId: Long,
        items: List<NotificationResponse>,
    ): List<NotificationResponse> {
        val lastSeen = store.get(userId)
        val maxId = items.maxOfOrNull { it.id } ?: 0L

        if (lastSeen == 0L) {
            if (maxId > 0) store.set(userId, maxId)
            return emptyList()
        }

        val newOnes = items.filter {
            it.kind == NotificationKind.ANNOUNCEMENT &&
                it.readAt == null &&
                it.id > lastSeen
        }
        if (maxId > lastSeen) store.set(userId, maxId)
        return newOnes
    }
}

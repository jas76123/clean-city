package com.example.cleancity.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.data.network.NotificationsApiContract
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AnnouncementSeenFilter
import com.example.cleancity.domain.AuthState
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Периодически (раз в 15 минут — минимум для PeriodicWorkRequest) проверяет
 * /notifications и кидает системные уведомления для новых ANNOUNCEMENT,
 * которые ещё не показывались (через [AnnouncementSeenFilter]).
 *
 * Покрывает сценарий «приложение убито из памяти». Foreground polling
 * (30 сек, см. UnreadCountStore) покрывает остальные сценарии.
 */
class AnnouncementCheckWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params), KoinComponent {

    private val api: NotificationsApiContract by inject()
    private val seenStore: SeenNotificationStore by inject()
    private val filter: AnnouncementSeenFilter by inject()
    private val authRepo: AuthRepository by inject()
    private val dispatcher: SystemNotificationDispatcher by inject()

    override suspend fun doWork(): Result {
        val auth = authRepo.state.value as? AuthState.Authenticated
            ?: return Result.success()
        val userId = auth.user.id
        val resp = runCatching { api.list(limit = 50) }.getOrNull()
            ?: return Result.retry()
        val newOnes = filter.newAnnouncements(userId, resp.items)
        newOnes.forEach { dispatcher.notify(it) }
        return Result.success()
    }
}

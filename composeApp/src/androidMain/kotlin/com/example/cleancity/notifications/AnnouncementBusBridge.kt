package com.example.cleancity.notifications

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.cleancity.domain.NotificationEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Когда приложение не foreground (свёрнуто), in-app Snackbar
 * не показывается. Мост получает событие из NotificationEventBus
 * и диспатчит в системную шторку через [SystemNotificationDispatcher].
 *
 * Дедуп с WorkManager-worker'ом — через общий SeenNotificationStore
 * на уровне AnnouncementSeenFilter (мост получает уже только новые).
 */
class AnnouncementBusBridge(
    private val bus: NotificationEventBus,
    private val dispatcher: SystemNotificationDispatcher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            bus.newAnnouncements.collect { n ->
                val isForeground = ProcessLifecycleOwner.get()
                    .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                if (!isForeground) {
                    dispatcher.notify(n)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

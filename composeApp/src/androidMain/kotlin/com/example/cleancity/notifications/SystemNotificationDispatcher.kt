package com.example.cleancity.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.cleancity.MainActivity
import com.example.cleancity.R
import com.example.cleancity.shared.models.NotificationResponse

/**
 * Android-only обёртка над NotificationManagerCompat. Не используется
 * на iOS — там бин не регистрируется. Создаёт notification channel
 * при первом обращении.
 */
class SystemNotificationDispatcher(private val ctx: Context) {

    companion object {
        const val CHANNEL_ID = "cleancity_announcements"
        const val CHANNEL_NAME = "Объявления"
        const val EXTRA_NOTIFICATION_ID = "cleancity.notification_id"
        const val EXTRA_OPEN_TAB = "cleancity.open_tab"
    }

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Объявления от муниципальных служб"
            enableVibration(true)
        }
        NotificationManagerCompat.from(ctx).createNotificationChannel(channel)
    }

    fun notify(n: NotificationResponse) {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_ID, n.id)
            putExtra(EXTRA_OPEN_TAB, "notifications")
        }
        val pending = PendingIntent.getActivity(
            ctx,
            n.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.body.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(n.id.toInt(), notif)
        } catch (e: SecurityException) {
            // Permission POST_NOTIFICATIONS не дан — ничего не делаем,
            // юзер увидит запись в списке через polling. Не падаем.
        }
    }
}

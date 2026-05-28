package com.example.cleancity

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.cleancity.di.androidModule
import com.example.cleancity.di.appModule
import com.example.cleancity.notifications.AnnouncementBusBridge
import com.example.cleancity.notifications.AnnouncementCheckWorker
import com.yandex.mapkit.MapKitFactory
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class CleanCityApplication : Application() {

    private val bridge: AnnouncementBusBridge by inject()

    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.YANDEX_MAPS_API_KEY.isNotBlank()) {
            "YANDEX_MAPS_API_KEY is not configured. Add it to local.properties or set as env var."
        }
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPS_API_KEY)
        MapKitFactory.initialize(this)
        startKoin {
            androidLogger(if (BuildConfig.IS_DEBUG) Level.ERROR else Level.NONE)
            androidContext(this@CleanCityApplication)
            modules(androidModule(), appModule())
        }
        scheduleAnnouncementWorker()
        bridge.start()
    }

    private fun scheduleAnnouncementWorker() {
        val req = PeriodicWorkRequestBuilder<AnnouncementCheckWorker>(
            15, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "announcement-check",
            ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }
}

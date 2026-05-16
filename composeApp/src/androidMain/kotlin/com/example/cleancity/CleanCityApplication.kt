package com.example.cleancity

import android.app.Application
import com.example.cleancity.di.androidModule
import com.example.cleancity.di.appModule
import com.yandex.mapkit.MapKitFactory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class CleanCityApplication : Application() {
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
    }
}

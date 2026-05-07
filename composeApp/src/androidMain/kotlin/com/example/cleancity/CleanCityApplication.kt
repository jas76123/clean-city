package com.example.cleancity

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class CleanCityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.YANDEX_MAPS_API_KEY.isNotBlank()) {
            "YANDEX_MAPS_API_KEY is not configured. Add it to local.properties or set as env var."
        }
        MapKitFactory.setApiKey(BuildConfig.YANDEX_MAPS_API_KEY)
        MapKitFactory.initialize(this)
    }
}

package com.example.cleancity

import android.app.Application
import com.yandex.mapkit.MapKitFactory

class CleanCityApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapKitFactory.setApiKey("__YANDEX_MAPS_KEY_REDACTED__")
        MapKitFactory.initialize(this)
    }
}

package com.example.cleancity.di

import com.example.cleancity.BuildConfig
import com.example.cleancity.data.local.SeenNotificationStoreFactory
import com.example.cleancity.data.storage.TokenStorageFactory
import com.example.cleancity.domain.location.AndroidLocationProvider
import com.example.cleancity.domain.location.LocationProvider
import com.example.cleancity.notifications.AnnouncementBusBridge
import com.example.cleancity.notifications.SystemNotificationDispatcher
import com.example.cleancity.ui.feature.map.AndroidMapSearchProvider
import com.example.cleancity.ui.feature.map.MapSearchProvider
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidModule(): Module = module {
    single { NetworkConfig(baseUrl = BuildConfig.API_BASE_URL, isDebug = BuildConfig.IS_DEBUG) }
    single { TokenStorageFactory(androidContext()) }
    single { SeenNotificationStoreFactory(androidContext()) }
    single { SystemNotificationDispatcher(androidContext()) }
    single { AnnouncementBusBridge(bus = get(), dispatcher = get()) }
    single<HttpClientEngine> { OkHttp.create() }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single<MapSearchProvider> { AndroidMapSearchProvider() }
}

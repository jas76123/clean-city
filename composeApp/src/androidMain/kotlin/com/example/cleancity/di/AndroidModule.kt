package com.example.cleancity.di

import com.example.cleancity.BuildConfig
import com.example.cleancity.data.storage.TokenStorageFactory
import com.example.cleancity.domain.location.AndroidLocationProvider
import com.example.cleancity.domain.location.LocationProvider
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
    single<HttpClientEngine> { OkHttp.create() }
    single<LocationProvider> { AndroidLocationProvider(androidContext()) }
    single<MapSearchProvider> { AndroidMapSearchProvider() }
}

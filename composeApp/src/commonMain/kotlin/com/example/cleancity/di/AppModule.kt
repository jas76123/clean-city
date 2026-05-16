package com.example.cleancity.di

import com.example.cleancity.data.network.AuthApi
import com.example.cleancity.data.network.AuthApiContract
import com.example.cleancity.data.network.UserApi
import com.example.cleancity.data.network.UserApiContract
import com.example.cleancity.data.network.AuthFailureHandler
import com.example.cleancity.data.network.createHttpClient
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.TokenStorage
import com.example.cleancity.data.storage.TokenStorageFactory
import com.example.cleancity.ui.feature.auth.ForgotPasswordScreenModel
import com.example.cleancity.ui.feature.auth.LoginScreenModel
import com.example.cleancity.ui.feature.auth.RegisterScreenModel
import com.example.cleancity.ui.feature.auth.ResetPasswordScreenModel
import com.example.cleancity.ui.feature.auth.VerifyEmailScreenModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

data class NetworkConfig(val baseUrl: String, val isDebug: Boolean)

fun appModule(): Module = module {
    single { get<TokenStorageFactory>().create() } bind TokenStorage::class

    single {
        val cfg = get<NetworkConfig>()
        val storage: TokenStorage = get()
        // AuthFailureHandler — late-bound через AuthRepository
        val handler = object : AuthFailureHandler {
            override fun onAuthFailure() {
                get<AuthRepository>().forceAnonymous()
            }
        }
        createHttpClient(
            engine = get<HttpClientEngine>(),
            baseUrl = cfg.baseUrl,
            isDebug = cfg.isDebug,
            tokenStorage = storage,
            onAuthFailure = handler,
        )
    }

    single<AuthApiContract> { AuthApi(get<HttpClient>()) }
    single<UserApiContract> { UserApi(get<HttpClient>()) }

    single { AuthRepository(get(), get(), get()) }

    factory { LoginScreenModel(get()) }
    factory { RegisterScreenModel(get()) }
    factory { (email: String) -> VerifyEmailScreenModel(email, get()) }
    factory { ForgotPasswordScreenModel(get()) }
    factory { (token: String) -> ResetPasswordScreenModel(token, get()) }
}

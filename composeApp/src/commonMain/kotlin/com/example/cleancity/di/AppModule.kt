package com.example.cleancity.di

import com.example.cleancity.data.local.SeenNotificationStore
import com.example.cleancity.data.local.SeenNotificationStoreFactory
import com.example.cleancity.data.network.AnnouncementsApi
import com.example.cleancity.data.network.AnnouncementsApiContract
import com.example.cleancity.data.network.AuthApi
import com.example.cleancity.data.network.AuthApiContract
import com.example.cleancity.data.network.ComplaintsApi
import com.example.cleancity.data.network.ComplaintsApiContract
import com.example.cleancity.data.network.NotificationsApi
import com.example.cleancity.data.network.NotificationsApiContract
import com.example.cleancity.data.network.UserApi
import com.example.cleancity.data.network.UserApiContract
import com.example.cleancity.data.network.AuthFailureHandler
import com.example.cleancity.data.network.createHttpClient
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.data.storage.TokenStorage
import com.example.cleancity.data.storage.TokenStorageFactory
import com.example.cleancity.domain.AnnouncementSeenFilter
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.NotificationEventBus
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.domain.location.LocationProvider
import com.example.cleancity.ui.feature.auth.ForgotPasswordScreenModel
import com.example.cleancity.ui.feature.auth.LoginScreenModel
import com.example.cleancity.ui.feature.auth.RegisterScreenModel
import com.example.cleancity.ui.feature.auth.ResetPasswordScreenModel
import com.example.cleancity.ui.feature.auth.VerifyEmailScreenModel
import com.example.cleancity.ui.feature.create.CreateComplaintScreenModel
import com.example.cleancity.ui.feature.detail.ComplaintDetailScreenModel
import com.example.cleancity.ui.feature.feed.AnnouncementDetailScreenModel
import com.example.cleancity.ui.feature.feed.FeedScreenModel
import com.example.cleancity.ui.feature.map.MapScreenModel
import com.example.cleancity.ui.feature.map.MapSearchProvider
import com.example.cleancity.ui.feature.notifications.NotificationsScreenModel
import com.example.cleancity.ui.feature.profile.ProfileScreenModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

data class NetworkConfig(val baseUrl: String, val isDebug: Boolean)

fun appModule(): Module = module {
    single { get<TokenStorageFactory>().create() } bind TokenStorage::class
    single { get<SeenNotificationStoreFactory>().create() } bind SeenNotificationStore::class

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
    single<ComplaintsApiContract> { ComplaintsApi(get<HttpClient>()) }
    single<AnnouncementsApiContract> { AnnouncementsApi(get<HttpClient>()) }
    single<NotificationsApiContract> { NotificationsApi(get<HttpClient>()) }

    single { com.example.cleancity.data.network.httpClientTokenInvalidator(get()) }

    single { AuthRepository(get(), get(), get(), get()) }

    single { NotificationEventBus() }
    single { AnnouncementSeenFilter(get<SeenNotificationStore>()) }

    single {
        val authRepo = get<AuthRepository>()
        UnreadCountStore(
            api = get<NotificationsApiContract>(),
            seenStore = get<SeenNotificationStore>(),
            filter = get<AnnouncementSeenFilter>(),
            bus = get<NotificationEventBus>(),
            authProvider = { (authRepo.state.value as? AuthState.Authenticated)?.user?.id },
        )
    }

    single { com.example.cleancity.ui.feature.map.picker.AddressPickerBus() }

    factory { LoginScreenModel(get()) }
    factory { RegisterScreenModel(get()) }
    factory { (email: String) -> VerifyEmailScreenModel(email, get()) }
    factory { ForgotPasswordScreenModel(get()) }
    factory { (token: String) -> ResetPasswordScreenModel(token, get()) }
    factory {
        MapScreenModel(
            api = get<ComplaintsApiContract>(),
            locationProvider = get<LocationProvider>(),
            searchProvider = get<MapSearchProvider>(),
        )
    }
    factory { (lat: Double?, lon: Double?) ->
        com.example.cleancity.ui.feature.map.picker.MapPickerScreenModel(
            initialLat = lat,
            initialLon = lon,
            searchProvider = get<MapSearchProvider>(),
            bus = get<com.example.cleancity.ui.feature.map.picker.AddressPickerBus>(),
        )
    }
    factory {
        FeedScreenModel(
            complaintsApi = get<ComplaintsApiContract>(),
            announcementsApi = get<AnnouncementsApiContract>(),
            authRepo = get(),
        )
    }
    factory { (complaintId: Long) ->
        ComplaintDetailScreenModel(
            complaintId = complaintId,
            complaintsApi = get<ComplaintsApiContract>(),
            authRepo = get(),
        )
    }
    factory { AnnouncementDetailScreenModel(api = get<AnnouncementsApiContract>()) }
    factory {
        ProfileScreenModel(
            complaintsApi = get<ComplaintsApiContract>(),
            authRepo = get(),
        )
    }
    factory {
        NotificationsScreenModel(
            api = get<NotificationsApiContract>(),
            unreadCountStore = get<UnreadCountStore>(),
            authRepo = get(),
        )
    }
    factory {
        CreateComplaintScreenModel(
            complaintsApi = get<ComplaintsApiContract>(),
            locationProvider = get<LocationProvider>(),
            searchProvider = get<MapSearchProvider>(),
            addressPickerBus = get<com.example.cleancity.ui.feature.map.picker.AddressPickerBus>(),
        )
    }
}

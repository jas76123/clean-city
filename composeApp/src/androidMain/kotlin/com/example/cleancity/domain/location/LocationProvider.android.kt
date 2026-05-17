package com.example.cleancity.domain.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidLocationProvider(context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): Result<Location> =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(Result.success(Location(loc.latitude, loc.longitude)))
                    } else {
                        // getCurrentLocation вернул null — fallback на последнее известное,
                        // его FusedProvider кэширует с предыдущих сессий
                        client.lastLocation
                            .addOnSuccessListener { last ->
                                if (last != null) {
                                    cont.resume(
                                        Result.success(Location(last.latitude, last.longitude)),
                                    )
                                } else {
                                    cont.resume(
                                        Result.failure(
                                            IllegalStateException("Location unavailable"),
                                        ),
                                    )
                                }
                            }
                            .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
                    }
                }
                .addOnFailureListener { e -> cont.resume(Result.failure(e)) }
        }
}

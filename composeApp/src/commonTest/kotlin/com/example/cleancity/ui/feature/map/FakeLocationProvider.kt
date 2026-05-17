package com.example.cleancity.ui.feature.map

import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.location.LocationProvider

class FakeLocationProvider(
    private val result: Result<Location> = Result.success(Location(43.5855, 39.7231)),
) : LocationProvider {
    var callCount = 0
    override suspend fun getLastKnownLocation(): Result<Location> {
        callCount++
        return result
    }
}

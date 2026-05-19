package com.example.cleancity.ui.feature.create

import com.example.cleancity.domain.location.Location
import com.example.cleancity.domain.location.LocationProvider

class FakeLocationProvider : LocationProvider {
    var nextResult: Result<Location> = Result.failure(IllegalStateException("not configured"))
    var callCount: Int = 0

    override suspend fun getLastKnownLocation(): Result<Location> {
        callCount++
        return nextResult
    }
}

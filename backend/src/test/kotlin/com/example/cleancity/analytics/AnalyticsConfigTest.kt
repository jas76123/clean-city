package com.example.cleancity.analytics

import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsConfigTest {

    @Test
    fun `targets match spec`() {
        assertEquals(80.0, AnalyticsConfig.SLA_TARGET_PCT)
        assertEquals(10.0, AnalyticsConfig.REOPEN_TARGET_PCT)
        assertEquals(24.0, AnalyticsConfig.DTA_TARGET_HOURS)
    }

    @Test
    fun `reopen window matches spec`() {
        assertEquals(50.0, AnalyticsConfig.REOPEN_RADIUS_METERS)
        assertEquals(30, AnalyticsConfig.REOPEN_WINDOW_DAYS)
    }
}

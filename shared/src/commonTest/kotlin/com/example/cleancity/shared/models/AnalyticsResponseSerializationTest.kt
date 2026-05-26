package com.example.cleancity.shared.models

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class AnalyticsResponseSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `OperationalSnapshot round-trips`() {
        val snapshot = OperationalSnapshot(
            backlog = 12,
            overdueNow = 3,
            avgDtaHours24h = 4.5,
            dtaTargetHours = 24.0,
            createdToday = 7,
            createdYesterday = 9,
            statusBreakdown = mapOf("NEW" to 5, "IN_PROGRESS" to 7),
        )
        val str = json.encodeToString(snapshot)
        val parsed = json.decodeFromString<OperationalSnapshot>(str)
        assertTrue(parsed == snapshot)
    }

    @Test
    fun `BurningComplaintItem round-trips`() {
        val item = BurningComplaintItem(
            id = 42L,
            title = "Сломанная урна",
            districtCode = "ADL",
            category = "GARBAGE",
            createdAt = Instant.parse("2026-05-26T08:00:00Z"),
            slaDueAt = Instant.parse("2026-05-27T08:00:00Z"),
            secondsToDeadline = -3600L,
        )
        val str = json.encodeToString(item)
        val parsed = json.decodeFromString<BurningComplaintItem>(str)
        assertTrue(parsed == item)
    }

    @Test
    fun `StrategicKpis and ReopenStat round-trip`() {
        val kpis = StrategicKpis(
            slaCompliancePct = 78.4,
            slaTargetPct = 80.0,
            medianResolutionHours = 36.0,
            p90ResolutionHours = 92.0,
            reopenRate = 0.08,
            reopenTargetPct = 10.0,
            throughput = 145,
        )
        val reopen = ReopenStat(reopenRate = 0.08, reopenCount = 12, resolvedCount = 150)
        val kpisStr = json.encodeToString(kpis)
        val reopenStr = json.encodeToString(reopen)
        assertTrue(json.decodeFromString<StrategicKpis>(kpisStr) == kpis)
        assertTrue(json.decodeFromString<ReopenStat>(reopenStr) == reopen)
    }
}

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

    @Test
    fun `CategoryStat extended fields round-trip`() {
        val stat = CategoryStat(
            category = ProblemCategory.GARBAGE,
            label = "Мусор",
            count = 50,
            sharePct = 12.5,
            avgResolutionHours = 24.0,
            medianResolutionHours = 18.0,
            p90ResolutionHours = 60.0,
            slaCompliancePct = 72.0,
        )
        val str = json.encodeToString(stat)
        val parsed = json.decodeFromString<CategoryStat>(str)
        assertTrue(parsed == stat)
    }

    @Test
    fun `DistrictStat extended fields round-trip`() {
        val stat = DistrictStat(
            district = District.ADLER,
            label = "Адлер",
            count = 30,
            newCount = 10,
            resolvedCount = 20,
            medianResolutionHours = 28.0,
            slaCompliancePct = 81.0,
        )
        val str = json.encodeToString(stat)
        val parsed = json.decodeFromString<DistrictStat>(str)
        assertTrue(parsed == stat)
    }

    @Test
    fun `TrendsResponse with createdSeries and resolvedSeries`() {
        val trends = TrendsResponse(
            days = emptyList(),
            createdSeries = listOf(
                TrendPoint(Instant.parse("2026-05-20T00:00:00Z"), 5),
                TrendPoint(Instant.parse("2026-05-21T00:00:00Z"), 8),
            ),
            resolvedSeries = listOf(
                TrendPoint(Instant.parse("2026-05-20T00:00:00Z"), 3),
                TrendPoint(Instant.parse("2026-05-21T00:00:00Z"), 7),
            ),
            groupBy = "day",
        )
        val str = json.encodeToString(trends)
        val parsed = json.decodeFromString<TrendsResponse>(str)
        assertTrue(parsed == trends)
    }
}

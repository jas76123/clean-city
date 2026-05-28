package com.example.cleancity.analytics.pdf

import com.example.cleancity.analytics.AnalyticsService
import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.MonthlyKpis
import com.example.cleancity.shared.models.SlaStat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthlyReportPdfServiceTest {

    // Clock зафиксирован на 28 мая 2026, 11:32 UTC = 14:32 MSK
    private val fixedClock = Clock.fixed(Instant.parse("2026-05-28T11:32:00Z"), ZoneId.of("Europe/Moscow"))

    private fun newService(svc: AnalyticsService): MonthlyReportPdfService =
        MonthlyReportPdfService(analyticsService = svc, clock = fixedClock)

    private fun mockAnalytics(
        overview: AnalyticsOverview = sampleOverview(),
        districts: List<DistrictStat> = District.entries.map { DistrictStat(it, it.localizedLabel, 0, 0, 0, null, null) },
        sla: List<SlaStat> = emptyList(),
    ): AnalyticsService {
        val svc: AnalyticsService = mockk()
        every { svc.overviewRange(any(), any()) } returns overview
        every { svc.byDistrictRange(any(), any()) } returns districts
        every { svc.slaRange(any(), any()) } returns sla
        return svc
    }

    @Test
    fun `generates non-empty PDF starting with magic bytes`() {
        val bytes = newService(mockAnalytics()).generate()
        assertTrue(bytes.size > 1000, "size = ${bytes.size}")
        assertEquals("%PDF-".toByteArray().toList(), bytes.take(5))
    }

    @Test
    fun `filename includes previous month in YYYY-MM`() {
        val name = newService(mockAnalytics()).filename()
        assertEquals("cleancity-monthly-report-2026-04.pdf", name)
    }

    @Test
    fun `range computed for previous full month in MSK`() {
        val svc = mockk<AnalyticsService>(relaxed = true)
        every { svc.overviewRange(any(), any()) } returns sampleOverview(0, 0, 0, 0, 0, 0)
        every { svc.byDistrictRange(any(), any()) } returns emptyList()
        every { svc.slaRange(any(), any()) } returns emptyList()

        newService(svc).generate()

        verify {
            svc.overviewRange(
                match { it.year == 2026 && it.monthValue == 4 && it.dayOfMonth == 1 },
                match { it.year == 2026 && it.monthValue == 5 && it.dayOfMonth == 1 },
            )
        }
    }

    private fun sampleOverview(
        total: Int = 142, new: Int = 18, inProgress: Int = 31, resolved: Int = 87,
        rejected: Int = 4, duplicate: Int = 2,
    ) = AnalyticsOverview(
        total = total, new = new, inProgress = inProgress, resolved = resolved,
        rejected = rejected, duplicate = duplicate,
        today = 0, week = 0, slaBreachCount = 0,
        monthlyKpis = EMPTY_KPIS,
    )

    companion object {
        private val EMPTY_KPIS = MonthlyKpis(
            total = 0, prevTotal = 0,
            avgResolutionHours = null, prevAvgResolutionHours = null,
            resolvedWithin7dPct = null, prevResolvedWithin7dPct = null,
            newCount = 0, inProgressCount = 0, resolvedCount = 0,
            rejectedCount = 0, duplicateCount = 0,
        )
    }
}

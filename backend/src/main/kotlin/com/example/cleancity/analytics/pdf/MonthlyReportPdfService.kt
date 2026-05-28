package com.example.cleancity.analytics.pdf

import com.example.cleancity.analytics.AnalyticsService
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class MonthlyReportPdfService(
    private val analyticsService: AnalyticsService,
    private val clock: Clock = Clock.system(ZoneId.of("Europe/Moscow")),
) {
    private val msk: ZoneId = ZoneId.of("Europe/Moscow")
    private val layout = MonthlyReportLayout()

    fun generate(): ByteArray {
        val (monthStart, monthEnd) = computeRange()
        val overview = analyticsService.overviewRange(monthStart, monthEnd)
        val districts = analyticsService.byDistrictRange(monthStart, monthEnd)
        val sla = analyticsService.slaRange(monthStart, monthEnd)
        val out = ByteArrayOutputStream()
        layout.render(
            output = out,
            monthStart = monthStart,
            generatedAt = OffsetDateTime.now(clock),
            overview = overview,
            districts = districts,
            sla = sla,
        )
        return out.toByteArray()
    }

    fun filename(): String {
        val (start, _) = computeRange()
        return "cleancity-monthly-report-%04d-%02d.pdf".format(start.year, start.monthValue)
    }

    private fun computeRange(): Pair<OffsetDateTime, OffsetDateTime> {
        val nowMsk = OffsetDateTime.now(clock).atZoneSameInstant(msk)
        val firstOfCurrent = LocalDate.of(nowMsk.year, nowMsk.month, 1).atStartOfDay(msk)
        val firstOfPrev = firstOfCurrent.minusMonths(1)
        return firstOfPrev.toOffsetDateTime() to firstOfCurrent.toOffsetDateTime()
    }
}

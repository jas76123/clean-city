package com.example.cleancity.analytics.pdf

import com.lowagie.text.pdf.PdfReader
import com.lowagie.text.pdf.parser.PdfTextExtractor
import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.District
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.MonthlyKpis
import com.example.cleancity.shared.models.ProblemCategory
import com.example.cleancity.shared.models.SlaStat
import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MonthlyReportLayoutTest {

    private fun render(
        overview: AnalyticsOverview = sampleOverview(slaBreach = 0),
        districts: List<DistrictStat> = sampleDistricts(),
        sla: List<SlaStat> = sampleSla(),
        generatedAt: OffsetDateTime = OffsetDateTime.of(2026, 5, 28, 14, 32, 0, 0, ZoneOffset.ofHours(3)),
        monthStart: OffsetDateTime = OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.ofHours(3)),
    ): String {
        val out = ByteArrayOutputStream()
        MonthlyReportLayout().render(
            output = out,
            monthStart = monthStart,
            generatedAt = generatedAt,
            overview = overview,
            districts = districts,
            sla = sla,
        )
        val bytes = out.toByteArray()
        assertTrue(bytes.size > 1000, "PDF должен быть не пустой, размер: ${bytes.size}")
        assertEquals("%PDF-".toByteArray().toList(), bytes.take(5), "PDF magic bytes")
        val reader = PdfReader(bytes)
        val extractor = PdfTextExtractor(reader)
        return (1..reader.numberOfPages).joinToString("\n") { extractor.getTextFromPage(it) }
    }

    @Test
    fun `header contains brand and russian month title`() {
        val text = render()
        assertTrue(text.contains("Чистый Город"), "Бренд: $text")
        assertTrue(text.contains("Сводный отчёт за апрель 2026"), "Заголовок: $text")
        assertTrue(text.contains("г. Сочи"), "Город: $text")
    }

    @Test
    fun `kpi block shows all 6 rows even when zero`() {
        val text = render(overview = sampleOverview(total = 0, new = 0, inProgress = 0, resolved = 0, rejected = 0, duplicate = 0))
        listOf("Всего жалоб", "Новых", "В работе", "Решено", "Отклонено", "Дубликаты").forEach {
            assertTrue(text.contains(it), "Должна быть строка '$it'")
        }
    }

    @Test
    fun `sla banner appears only when breach count is positive`() {
        val zero = render(overview = sampleOverview(slaBreach = 0))
        assertTrue(!zero.contains("Нарушено SLA"), "При 0 нарушений баннера быть не должно")

        val twelve = render(overview = sampleOverview(slaBreach = 12))
        assertTrue(twelve.contains("Нарушено SLA: 12"), "При 12 нарушений показываем счётчик")
    }

    @Test
    fun `district table always shows 4 rows in fixed order`() {
        val text = render(
            districts = listOf(
                DistrictStat(District.CENTRAL, "Центральный", 0, 0, 0, null, null),
                DistrictStat(District.ADLER, "Адлерский", 0, 0, 0, null, null),
                DistrictStat(District.KHOSTA, "Хостинский", 0, 0, 0, null, null),
                DistrictStat(District.LAZAREVSKOE, "Лазаревский", 0, 0, 0, null, null),
            )
        )
        assertTrue(text.contains("Центральный"))
        assertTrue(text.contains("Адлерский"))
        assertTrue(text.contains("Хостинский"))
        assertTrue(text.contains("Лазаревский"))
    }

    @Test
    fun `sla table shows fallback when empty`() {
        val text = render(sla = emptyList())
        assertTrue(text.contains("За отчётный период данных нет"), "Должен быть fallback: $text")
    }

    private fun sampleOverview(
        total: Int = 142, new: Int = 18, inProgress: Int = 31, resolved: Int = 87,
        rejected: Int = 4, duplicate: Int = 2, slaBreach: Int = 0,
    ) = AnalyticsOverview(
        total = total, new = new, inProgress = inProgress, resolved = resolved,
        rejected = rejected, duplicate = duplicate,
        today = 0, week = 0, slaBreachCount = slaBreach,
        monthlyKpis = EMPTY_KPIS,
    )

    private fun sampleDistricts() = listOf(
        DistrictStat(District.CENTRAL, "Центральный", 62, 8, 38, null, null),
        DistrictStat(District.ADLER, "Адлерский", 41, 5, 28, null, null),
        DistrictStat(District.KHOSTA, "Хостинский", 24, 3, 15, null, null),
        DistrictStat(District.LAZAREVSKOE, "Лазаревский", 15, 2, 6, null, null),
    )

    private fun sampleSla() = listOf(
        SlaStat(ProblemCategory.GARBAGE, "Мусор", 48, 31.0, 8.0, 10),
        SlaStat(ProblemCategory.ROADS, "Дороги", 72, 68.0, 22.0, 5),
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

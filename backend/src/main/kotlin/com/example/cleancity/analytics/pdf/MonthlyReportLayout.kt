package com.example.cleancity.analytics.pdf

import com.example.cleancity.shared.models.AnalyticsOverview
import com.example.cleancity.shared.models.DistrictStat
import com.example.cleancity.shared.models.SlaStat
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.Rectangle
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import java.awt.Color
import java.io.OutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class MonthlyReportLayout {

    private companion object {
        const val FONT_REGULAR = "DejaVuSans"
        const val FONT_BOLD = "DejaVuSans-Bold"

        val MONTHS_NOMINATIVE = listOf(
            "январь", "февраль", "март", "апрель", "май", "июнь",
            "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
        )

        val SLA_RED = Color(0xE8, 0x44, 0x3C)
        val GREY = Color(0x6B, 0x70, 0x80)

        val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")
    }

    init {
        if (!FontFactory.isRegistered(FONT_REGULAR)) {
            val regular = javaClass.classLoader.getResource("fonts/DejaVuSans.ttf")
                ?: error("DejaVuSans.ttf not found in resources/fonts/")
            val bold = javaClass.classLoader.getResource("fonts/DejaVuSans-Bold.ttf")
                ?: error("DejaVuSans-Bold.ttf not found in resources/fonts/")
            FontFactory.register(regular.toString(), FONT_REGULAR)
            FontFactory.register(bold.toString(), FONT_BOLD)
        }
    }

    private fun font(bold: Boolean, size: Float, color: Color = Color.BLACK): Font {
        val name = if (bold) FONT_BOLD else FONT_REGULAR
        return FontFactory.getFont(name, "Identity-H", true, size, Font.NORMAL, color)
    }

    fun render(
        output: OutputStream,
        monthStart: OffsetDateTime,
        generatedAt: OffsetDateTime,
        overview: AnalyticsOverview,
        districts: List<DistrictStat>,
        sla: List<SlaStat>,
    ) {
        val doc = Document(PageSize.A4, 36f, 36f, 36f, 36f)
        PdfWriter.getInstance(doc, output)
        doc.open()
        writeHeader(doc, monthStart, generatedAt)
        writeKpiBlock(doc, overview)
        if (overview.slaBreachCount > 0) writeSlaBanner(doc, overview.slaBreachCount)
        writeDistrictTable(doc, districts)
        writeSlaTable(doc, sla)
        doc.close()
    }

    private fun writeHeader(doc: Document, monthStart: OffsetDateTime, generatedAt: OffsetDateTime) {
        doc.add(Paragraph("Чистый Город", font(bold = true, size = 18f)))
        val month = MONTHS_NOMINATIVE[monthStart.monthValue - 1]
        doc.add(Paragraph("Сводный отчёт за $month ${monthStart.year} г.", font(bold = true, size = 13f)))
        doc.add(
            Paragraph(
                "г. Сочи · сформирован ${generatedAt.format(DATE_FMT)} MSK",
                font(bold = false, size = 9f, color = GREY),
            )
        )
        doc.add(Paragraph(" ", font(bold = false, size = 6f)))
    }

    private fun writeKpiBlock(doc: Document, o: AnalyticsOverview) {
        doc.add(Paragraph("Сводка за месяц", font(bold = true, size = 13f)))
        val table = PdfPTable(floatArrayOf(3f, 1f)).apply {
            widthPercentage = 60f
            horizontalAlignment = Element.ALIGN_LEFT
            setSpacingBefore(4f)
            setSpacingAfter(12f)
        }
        listOf(
            "Всего жалоб" to o.total,
            "Новых" to o.new,
            "В работе" to o.inProgress,
            "Решено" to o.resolved,
            "Отклонено" to o.rejected,
            "Дубликаты" to o.duplicate,
        ).forEach { (label, value) ->
            table.addCell(textCell(label, font(bold = false, size = 10f), align = Element.ALIGN_LEFT))
            table.addCell(textCell(value.toString(), font(bold = true, size = 10f), align = Element.ALIGN_RIGHT))
        }
        doc.add(table)
    }

    private fun writeSlaBanner(doc: Document, count: Int) {
        val p = Paragraph(
            Phrase("⚠ Нарушено SLA: $count жалоб", font(bold = true, size = 11f, color = SLA_RED))
        )
        p.spacingAfter = 12f
        doc.add(p)
    }

    private fun writeDistrictTable(doc: Document, districts: List<DistrictStat>) {
        doc.add(Paragraph("По районам Сочи", font(bold = true, size = 13f)))
        val table = PdfPTable(floatArrayOf(3f, 1f, 1f, 1f)).apply {
            widthPercentage = 80f
            horizontalAlignment = Element.ALIGN_LEFT
            setSpacingBefore(4f)
            setSpacingAfter(12f)
        }
        listOf("Район", "Всего", "Новых", "Решено").forEachIndexed { idx, h ->
            table.addCell(headerCell(h, if (idx == 0) Element.ALIGN_LEFT else Element.ALIGN_RIGHT))
        }
        districts.forEach { d ->
            table.addCell(textCell(d.label, font(bold = false, size = 10f), align = Element.ALIGN_LEFT))
            table.addCell(textCell(d.count.toString(), font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            table.addCell(textCell(d.newCount.toString(), font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            table.addCell(textCell(d.resolvedCount.toString(), font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
        }
        doc.add(table)
    }

    private fun writeSlaTable(doc: Document, sla: List<SlaStat>) {
        doc.add(Paragraph("SLA по категориям", font(bold = true, size = 13f)))
        if (sla.isEmpty()) {
            doc.add(Paragraph("За отчётный период данных нет.", font(bold = false, size = 10f, color = GREY)))
            return
        }
        val table = PdfPTable(floatArrayOf(3f, 1f, 1f, 1f)).apply {
            widthPercentage = 90f
            horizontalAlignment = Element.ALIGN_LEFT
            setSpacingBefore(4f)
        }
        listOf("Категория", "Норматив", "Среднее", "Нарушено").forEachIndexed { idx, h ->
            table.addCell(headerCell(h, if (idx == 0) Element.ALIGN_LEFT else Element.ALIGN_RIGHT))
        }
        sla.forEach { s ->
            table.addCell(textCell(s.label, font(bold = false, size = 10f), align = Element.ALIGN_LEFT))
            table.addCell(textCell("${s.slaHours} ч", font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            val avg = s.avgResolutionHours
            table.addCell(textCell(if (avg == null) "—" else "${avg.toInt()} ч", font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
            table.addCell(textCell("${s.breachPct.toInt()}%", font(bold = false, size = 10f), align = Element.ALIGN_RIGHT))
        }
        doc.add(table)
    }

    private fun textCell(text: String, font: Font, align: Int): PdfPCell =
        PdfPCell(Phrase(text, font)).apply {
            horizontalAlignment = align
            border = Rectangle.BOTTOM
            borderColorBottom = Color(0xE5, 0xE7, 0xEB)
            paddingTop = 4f
            paddingBottom = 4f
        }

    private fun headerCell(text: String, align: Int): PdfPCell =
        PdfPCell(Phrase(text, font(bold = true, size = 10f))).apply {
            horizontalAlignment = align
            border = Rectangle.BOTTOM
            borderColorBottom = Color.BLACK
            paddingTop = 4f
            paddingBottom = 4f
            backgroundColor = Color(0xF3, 0xF4, 0xF6)
        }
}

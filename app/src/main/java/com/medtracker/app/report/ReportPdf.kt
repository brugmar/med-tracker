package com.medtracker.app.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.toTimeString
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Renders a [MedicineReport] as an A4, black-and-white PDF meant for printing:
 * grayscale only, hairlines and light fills instead of solid areas, one section
 * per calendar week with its chart, sum/average and a day-by-day dose table.
 */
fun renderMedicineReportPdf(report: MedicineReport, out: OutputStream) {
    ReportPdfRenderer(report).writeTo(out)
}

// A4 in PostScript points.
private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 46f
private const val FOOTER_ZONE = 56f
private const val CONTENT_RIGHT = PAGE_WIDTH - MARGIN
private const val CONTENT_WIDTH = CONTENT_RIGHT - MARGIN
private const val CONTENT_BOTTOM = PAGE_HEIGHT - FOOTER_ZONE

// Grayscale ink. Light fills rasterize to sparse dithering, so they stay cheap to print.
private val INK = Color.rgb(20, 20, 20)
private val INK_SOFT = Color.rgb(75, 75, 75)
private val INK_FAINT = Color.rgb(130, 130, 130)
private val RULE_LIGHT = Color.rgb(190, 190, 190)
private val BAR_FILL = Color.rgb(226, 226, 226)
private val WEEKEND_FILL = Color.rgb(243, 243, 243)

private val SANS: Typeface = Typeface.SANS_SERIF
private val SANS_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
private val SANS_BOLD: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
private val SANS_ITALIC: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)

private val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val WEEKDAY_DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val DAY_FULL_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val GENERATED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm")

private fun textPaint(
    size: Float,
    color: Int,
    typeface: Typeface = SANS,
    letterSpacing: Float = 0f
): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.textSize = size
    this.color = color
    this.typeface = typeface
    this.letterSpacing = letterSpacing
}

private fun strokePaint(color: Int, width: Float): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
    }

private fun fillPaint(color: Int): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color
    }

private fun Canvas.textRight(text: String, x: Float, y: Float, paint: Paint) {
    drawText(text, x - paint.measureText(text), y, paint)
}

private fun Canvas.textCenter(text: String, x: Float, y: Float, paint: Paint) {
    drawText(text, x - paint.measureText(text) / 2f, y, paint)
}

/** Shrinks a copy of [base] until [text] fits into [maxWidth]. */
private fun fitted(base: Paint, text: String, maxWidth: Float, minSize: Float): Paint {
    val paint = Paint(base)
    while (paint.measureText(text) > maxWidth && paint.textSize > minSize) {
        paint.textSize -= 0.5f
    }
    return paint
}

/** Averages get 3 significant figures: 386.666 → "387", 0.4286 → "0.429". */
private fun formatAverage(value: Double): String {
    if (value <= 0) return formatAmount(0.0)
    return java.math.BigDecimal(value)
        .round(java.math.MathContext(3))
        .stripTrailingZeros()
        .toPlainString()
}

/** Smallest "nice" number (1/2/2.5/5 × 10^k) that is >= [value]; chart y-scale. */
private fun niceCeil(value: Double): Double {
    if (value <= 0) return 1.0
    val base = 10.0.pow(floor(log10(value)))
    val fraction = value / base
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return nice * base
}

/**
 * Collects positioned draw commands page by page; rendering happens afterwards so
 * every footer can carry the final page count.
 */
private class PageComposer {
    val pages = mutableListOf<MutableList<(Canvas) -> Unit>>(mutableListOf())
    var y = MARGIN
        private set

    fun fits(height: Float): Boolean = y + height <= CONTENT_BOTTOM

    fun newPage() {
        pages += mutableListOf<(Canvas) -> Unit>()
        y = MARGIN
    }

    fun ensure(height: Float) {
        if (!fits(height)) newPage()
    }

    fun spacer(height: Float) {
        y = (y + height).coerceAtMost(CONTENT_BOTTOM)
    }

    /** Places a block at the current cursor, breaking to a new page when needed. */
    fun add(height: Float, draw: (Canvas, Float) -> Unit) {
        if (!fits(height) && y > MARGIN) newPage()
        val top = y
        pages.last() += { canvas -> draw(canvas, top) }
        y += height
    }
}

private class ReportPdfRenderer(private val report: MedicineReport) {

    private val composer = PageComposer()
    private val scaleMax = niceCeil(report.maxDailyTotal)

    // Shared paints.
    private val caption = textPaint(7.6f, INK_FAINT, SANS_MEDIUM, letterSpacing = 0.16f)
    private val body = textPaint(9f, INK)
    private val bodySoft = textPaint(8.4f, INK_SOFT)
    private val bodyFaint = textPaint(8.4f, INK_FAINT)
    private val noDoses = textPaint(8.4f, INK_FAINT, SANS_ITALIC)
    private val chartLabel = textPaint(7f, INK_SOFT)
    private val chartLabelFaint = textPaint(7f, RULE_LIGHT)
    private val chartValue = textPaint(7.2f, INK_SOFT, SANS_MEDIUM)
    private val axisLabel = textPaint(6.6f, INK_FAINT)
    private val dayTotal = textPaint(9f, INK, SANS_MEDIUM)
    private val footerPaint = textPaint(7f, INK_FAINT)

    private val rule = strokePaint(INK, 1.3f)
    private val hairline = strokePaint(RULE_LIGHT, 0.6f)
    private val rowLine = strokePaint(Color.rgb(210, 210, 210), 0.5f)
    private val barStroke = strokePaint(INK, 0.8f)
    private val barFill = fillPaint(BAR_FILL)
    private val weekendFill = fillPaint(WEEKEND_FILL)
    private val baseline = strokePaint(INK, 1f)
    private val avgLine = strokePaint(INK_SOFT, 0.9f).apply {
        pathEffect = DashPathEffect(floatArrayOf(3f, 3.5f), 0f)
    }

    fun writeTo(out: OutputStream) {
        compose()
        val document = PdfDocument()
        try {
            val total = composer.pages.size
            composer.pages.forEachIndexed { index, ops ->
                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val page = document.startPage(info)
                ops.forEach { it(page.canvas) }
                drawFooter(page.canvas, index + 1, total)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    // ---- composition ----

    private fun compose() {
        addHeader()
        composer.spacer(16f)
        addSectionCaption("SUMMARY")
        addSummaryTiles()
        composer.spacer(14f)
        addSectionCaption("DAILY TOTALS — FULL PERIOD (${report.unit})")
        addOverviewChart()
        composer.spacer(14f)
        addSectionCaption("TIME OF DAY")
        addTimeOfDay()
        composer.spacer(20f)
        report.weeks.forEach { addWeekSection(it) }
        addFootnotes()
    }

    private fun addHeader() {
        val medicineTitle = fitted(
            textPaint(25f, INK, SANS_BOLD), report.medicineName, CONTENT_WIDTH, 13f
        )
        composer.add(84f) { canvas, top ->
            canvas.drawText(
                "MEDICINE REPORT · LAST ${report.dayCount} DAYS",
                MARGIN, top + 9f,
                textPaint(8f, INK_SOFT, SANS_MEDIUM, letterSpacing = 0.18f)
            )
            canvas.drawText(report.medicineName, MARGIN, top + 38f, medicineTitle)
            canvas.drawText(periodLine(), MARGIN, top + 55f, textPaint(10.5f, INK_SOFT))
            canvas.drawText(
                "Generated on ${report.generatedAt.format(GENERATED_AT)} · MedTracker",
                MARGIN, top + 69f, textPaint(8f, INK_FAINT)
            )
            canvas.drawLine(MARGIN, top + 80f, CONTENT_RIGHT, top + 80f, rule)
        }
    }

    private fun periodLine(): String {
        val start = report.startDate
        val end = report.endDate
        val range = if (start.year == end.year) {
            "${start.format(DAY_FULL_MONTH)} – ${end.format(FULL_DATE)}"
        } else {
            "${start.format(FULL_DATE)} – ${end.format(FULL_DATE)}"
        }
        return "$range · ${report.dayCount} days, report day not included"
    }

    private fun addSectionCaption(text: String) {
        composer.ensure(40f) // never strand a caption at the bottom of a page
        composer.add(16f) { canvas, top ->
            canvas.drawText(text, MARGIN, top + 8f, caption)
            val textEnd = MARGIN + caption.measureText(text) + 8f
            if (textEnd < CONTENT_RIGHT) {
                canvas.drawLine(textEnd, top + 5.5f, CONTENT_RIGHT, top + 5.5f, hairline)
            }
        }
    }

    private fun addSummaryTiles() {
        data class Tile(val value: String, val label: String)

        val peak = report.peakDay
        val tiles = listOf(
            Tile(formatUnitTotals(report.totals, report.unit), "TOTAL TAKEN"),
            Tile(report.doseCount.toString(), if (report.doseCount == 1) "DOSE" else "DOSES"),
            Tile("${formatAverage(report.averagePerDay)} ${report.unit}", "AVG PER DAY"),
            Tile("${report.daysWithDoses} of ${report.dayCount}", "DAYS WITH INTAKE"),
            if (peak != null) {
                Tile(
                    "${formatAmount(peak.primaryTotal)} ${report.unit}",
                    "PEAK · ${peak.date.format(DAY_MONTH).uppercase()}"
                )
            } else {
                Tile("—", "PEAK DAY")
            }
        )

        val columnWidth = CONTENT_WIDTH / tiles.size
        composer.add(36f) { canvas, top ->
            tiles.forEachIndexed { index, tile ->
                val left = MARGIN + index * columnWidth
                val valuePaint = fitted(
                    textPaint(13.5f, INK, SANS_BOLD), tile.value, columnWidth - 12f, 8f
                )
                canvas.drawText(tile.value, left, top + 16f, valuePaint)
                canvas.drawText(
                    tile.label, left, top + 28f,
                    textPaint(6.6f, INK_FAINT, SANS_MEDIUM, letterSpacing = 0.08f)
                )
                if (index > 0) {
                    canvas.drawLine(left - 9f, top + 4f, left - 9f, top + 29f, hairline)
                }
            }
        }
    }

    private fun addOverviewChart() {
        val values = report.days.map { it.primaryTotal }
        // Label every 5th day plus the last one; repeat the month name when it changes.
        var labelledMonth = 0
        val labels = report.days.mapIndexed { index, day ->
            val last = index == report.days.lastIndex
            if (index % 5 != 0 && !(last && index % 5 >= 2)) return@mapIndexed ""
            val withMonth = day.date.monthValue != labelledMonth
            labelledMonth = day.date.monthValue
            if (withMonth) day.date.format(DAY_MONTH) else day.date.dayOfMonth.toString()
        }
        addChart(
            height = 118f,
            values = values,
            labels = labels,
            faintLabels = List(values.size) { false },
            average = report.averagePerDay.takeIf { it > 0 },
            showBarValues = false,
            emptyText = "No doses recorded in this period"
        )
    }

    private fun addTimeOfDay() {
        val buckets = listOf("Night 00–06", "Morning 06–12", "Afternoon 12–18", "Evening 18–24")
        val counts = report.timeOfDayCounts
        val maxCount = max(1, counts.max())
        val barLeft = MARGIN + 96f
        val barMaxWidth = CONTENT_WIDTH - 96f - 34f
        val rowHeight = 13.5f

        composer.add(buckets.size * rowHeight + 2f) { canvas, top ->
            buckets.forEachIndexed { index, label ->
                val rowTop = top + index * rowHeight
                canvas.drawText(label, MARGIN, rowTop + 9f, bodySoft)
                val count = counts[index]
                if (count > 0) {
                    val width = max(2.5f, barMaxWidth * count / maxCount)
                    canvas.drawRect(barLeft, rowTop + 2.5f, barLeft + width, rowTop + 10f, barFill)
                    canvas.drawRect(barLeft, rowTop + 2.5f, barLeft + width, rowTop + 10f, barStroke)
                }
                canvas.textRight(count.toString(), CONTENT_RIGHT, rowTop + 9f, dayTotal)
            }
        }
    }

    private fun addWeekSection(week: ReportWeek) {
        // Keep the header, the chart and at least one table row on the same page.
        composer.ensure(200f)
        addWeekHeader(week, continued = false)
        addWeekChart(week)
        composer.spacer(6f)
        addTableHeader()
        week.days.forEach { day ->
            val rowHeight = dayRowHeight(day)
            if (!composer.fits(rowHeight + 2f)) {
                composer.newPage()
                addWeekHeader(week, continued = true)
                addTableHeader()
            }
            addDayRow(day, rowHeight)
        }
        composer.spacer(20f)
    }

    private fun weekTitle(week: ReportWeek): String {
        val first = week.days.first().date
        val last = week.days.last().date
        val range = "${first.format(WEEKDAY_DAY_MONTH)} – ${last.format(WEEKDAY_DAY_MONTH)}"
        val partial = if (week.isPartial) " · ${week.days.size} DAYS" else ""
        return "WEEK ${week.index} · ${range.uppercase()}$partial"
    }

    private fun addWeekHeader(week: ReportWeek, continued: Boolean) {
        val title = weekTitle(week) + if (continued) " — CONTINUED" else ""
        val stats = "Sum ${formatUnitTotals(week.totals, report.unit)}   ·   " +
            "${week.doseCount} ${if (week.doseCount == 1) "dose" else "doses"}   ·   " +
            "Avg ${formatAverage(week.averagePerDay)} ${report.unit}/day"
        val titlePaint = textPaint(10f, INK, SANS_BOLD, letterSpacing = 0.04f)
        val titleWidth = titlePaint.measureText(title)
        val statsPaint = fitted(
            textPaint(8.4f, INK_SOFT), stats, CONTENT_WIDTH - titleWidth - 16f, 6.4f
        )

        composer.add(26f) { canvas, top ->
            canvas.drawLine(MARGIN, top + 2f, CONTENT_RIGHT, top + 2f, rule)
            canvas.drawText(title, MARGIN, top + 17f, titlePaint)
            canvas.textRight(stats, CONTENT_RIGHT, top + 17f, statsPaint)
        }
    }

    private fun addWeekChart(week: ReportWeek) {
        val byDate = week.days.associateBy { it.date }
        val dates = (0..6).map { week.weekStart.plusDays(it.toLong()) }
        val values = dates.map { date -> byDate[date]?.primaryTotal }
        val labels = dates.map { it.format(WEEKDAY_DAY_MONTH) }
        addChart(
            height = 104f,
            values = values,
            labels = labels,
            faintLabels = dates.map { it !in byDate },
            average = week.averagePerDay.takeIf { it > 0 },
            showBarValues = true,
            emptyText = "No doses this week"
        )
    }

    /**
     * Bar chart block. A null value marks a day outside the report window: its label
     * is faded and no bar is drawn. All charts share the same y-scale ([scaleMax]).
     */
    private fun addChart(
        height: Float,
        values: List<Double?>,
        labels: List<String>,
        faintLabels: List<Boolean>,
        average: Double?,
        showBarValues: Boolean,
        emptyText: String
    ) {
        composer.add(height) { canvas, top ->
            val plotLeft = MARGIN + 30f
            // Right gutter holds the average label so it can never collide with bars.
            val plotRight = CONTENT_RIGHT - 46f
            val plotTop = top + if (showBarValues) 12f else 7f
            val plotBottom = top + height - 14f
            val plotHeight = plotBottom - plotTop
            val hasData = values.any { (it ?: 0.0) > 0.0 }

            // Gridlines with y-labels at 1/2 and max; solid baseline at zero.
            listOf(0.5f, 1f).forEach { fraction ->
                val y = plotBottom - plotHeight * fraction
                canvas.drawLine(plotLeft, y, plotRight, y, hairline)
                if (hasData) {
                    canvas.textRight(
                        formatAmount(scaleMax * fraction), plotLeft - 5f, y + 2.2f, axisLabel
                    )
                }
            }
            canvas.drawLine(plotLeft, plotBottom, plotRight, plotBottom, baseline)
            if (hasData) {
                canvas.textRight("0", plotLeft - 5f, plotBottom + 2.2f, axisLabel)
            }

            val slot = (plotRight - plotLeft) / values.size
            val barWidth = slot * 0.6f

            values.forEachIndexed { index, value ->
                val centerX = plotLeft + index * slot + slot / 2f
                if (value != null && value > 0) {
                    val barHeight = max(2.5f, (value / scaleMax * plotHeight).toFloat())
                    val barTop = plotBottom - barHeight
                    val left = centerX - barWidth / 2f
                    canvas.drawRect(left, barTop, left + barWidth, plotBottom, barFill)
                    canvas.drawRect(left, barTop, left + barWidth, plotBottom, barStroke)
                    if (showBarValues) {
                        canvas.textCenter(formatAmount(value), centerX, barTop - 3.5f, chartValue)
                    }
                }
                val label = labels[index]
                if (label.isNotEmpty()) {
                    canvas.textCenter(
                        label, centerX, plotBottom + 10f,
                        if (faintLabels[index]) chartLabelFaint else chartLabel
                    )
                }
            }

            if (!hasData) {
                canvas.textCenter(
                    emptyText, (plotLeft + plotRight) / 2f,
                    plotTop + plotHeight / 2f, noDoses
                )
            } else if (average != null) {
                val y = plotBottom - (average / scaleMax * plotHeight).toFloat()
                canvas.drawLine(plotLeft, y, plotRight, y, avgLine)
                canvas.drawText("avg ${formatAverage(average)}", plotRight + 5f, y + 2.4f, axisLabel)
            }
        }
    }

    // ---- day table ----

    private val dateColumnWidth = 88f
    private val dosesLeft = MARGIN + dateColumnWidth + 10f
    private val dosesRight = CONTENT_RIGHT - 80f
    private val doseLineHeight = 13f

    private fun addTableHeader() {
        composer.add(15f) { canvas, top ->
            val labels = textPaint(6.4f, INK_FAINT, SANS_MEDIUM, letterSpacing = 0.1f)
            canvas.drawText("DAY", MARGIN, top + 7f, labels)
            canvas.drawText("DOSES — TIME AND AMOUNT", dosesLeft, top + 7f, labels)
            canvas.textRight("DAY TOTAL", CONTENT_RIGHT, top + 7f, labels)
            canvas.drawLine(MARGIN, top + 11f, CONTENT_RIGHT, top + 11f, rowLine)
        }
    }

    /** A dose as the two text parts of one chip: faint time, solid amount. */
    private fun dayChips(day: ReportDay): List<Pair<String, String>> =
        day.doses.map { dose ->
            dose.timestamp.toTimeString() to "${formatAmount(dose.amount)} ${dose.unit}"
        }

    private fun chipWidth(chip: Pair<String, String>): Float =
        bodyFaint.measureText(chip.first) + 4f + body.measureText(chip.second)

    /** Lays chips into lines with greedy wrapping inside the doses column. */
    private fun chipLines(day: ReportDay): List<List<Pair<String, String>>> {
        val chips = dayChips(day)
        if (chips.isEmpty()) return emptyList()
        val maxWidth = dosesRight - dosesLeft
        val gap = 16f
        val lines = mutableListOf(mutableListOf<Pair<String, String>>())
        var x = 0f
        chips.forEach { chip ->
            val width = chipWidth(chip)
            if (lines.last().isNotEmpty() && x + gap + width > maxWidth) {
                lines += mutableListOf<Pair<String, String>>()
                x = 0f
            }
            x += (if (lines.last().isEmpty()) 0f else gap) + width
            lines.last() += chip
        }
        return lines
    }

    private fun dayRowHeight(day: ReportDay): Float =
        max(1, chipLines(day).size) * doseLineHeight + 8f

    private fun addDayRow(day: ReportDay, rowHeight: Float) {
        val lines = chipLines(day)
        composer.add(rowHeight) { canvas, top ->
            val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY ||
                day.date.dayOfWeek == DayOfWeek.SUNDAY
            if (weekend) {
                canvas.drawRect(MARGIN - 4f, top, CONTENT_RIGHT + 4f, top + rowHeight, weekendFill)
            }
            val baselineY = top + 13f

            val weekday = day.date.format(DateTimeFormatter.ofPattern("EEE"))
            val weekdayPaint = textPaint(9f, INK, SANS_MEDIUM)
            canvas.drawText(weekday, MARGIN, baselineY, weekdayPaint)
            canvas.drawText(
                day.date.format(DAY_MONTH),
                MARGIN + weekdayPaint.measureText(weekday) + 5f, baselineY, bodySoft
            )

            if (lines.isEmpty()) {
                canvas.drawText("no doses", dosesLeft, baselineY, noDoses)
                canvas.textRight("—", CONTENT_RIGHT, baselineY, bodyFaint)
            } else {
                lines.forEachIndexed { lineIndex, line ->
                    var x = dosesLeft
                    val lineY = baselineY + lineIndex * doseLineHeight
                    line.forEach { (time, amount) ->
                        canvas.drawText(time, x, lineY, bodyFaint)
                        x += bodyFaint.measureText(time) + 4f
                        canvas.drawText(amount, x, lineY, body)
                        x += body.measureText(amount) + 16f
                    }
                }
                val total = if (day.primaryTotal > 0) {
                    "${formatAmount(day.primaryTotal)} ${report.unit}"
                } else {
                    "—"
                }
                canvas.textRight(total, CONTENT_RIGHT, baselineY, dayTotal)
            }
            canvas.drawLine(MARGIN, top + rowHeight, CONTENT_RIGHT, top + rowHeight, rowLine)
        }
    }

    private fun addFootnotes() {
        val notes = buildList {
            add(
                "Week averages are per day, over the days each week contributes to the period" +
                    " — the first and last week may cover fewer than 7 days."
            )
            if (report.hasForeignUnits) {
                add(
                    "Doses recorded in a different unit are listed in the day rows but are" +
                        " not included in totals, averages or charts (${report.unit} only)."
                )
            }
            add("Shaded rows are weekends. Times are local to the device at logging time.")
            add("Generated by MedTracker — a personal intake log, not medical advice.")
        }
        val lineHeight = 9.5f
        val height = notes.size * lineHeight + 10f
        composer.ensure(height)
        composer.add(height) { canvas, top ->
            canvas.drawLine(MARGIN, top + 2f, CONTENT_RIGHT, top + 2f, hairline)
            val notePaint = textPaint(7f, INK_FAINT)
            notes.forEachIndexed { index, note ->
                canvas.drawText(note, MARGIN, top + 12f + index * lineHeight, notePaint)
            }
        }
    }

    private fun drawFooter(canvas: Canvas, page: Int, total: Int) {
        val y = PAGE_HEIGHT - 42f
        canvas.drawLine(MARGIN, y, CONTENT_RIGHT, y, hairline)
        val textY = y + 11f
        canvas.drawText(
            "${report.medicineName} — ${report.startDate.format(DAY_MONTH)} to " +
                "${report.endDate.format(DAY_MONTH)} ${report.endDate.year}",
            MARGIN, textY, footerPaint
        )
        canvas.textCenter("MedTracker", (MARGIN + CONTENT_RIGHT) / 2f, textY, footerPaint)
        canvas.textRight("Page $page of $total", CONTENT_RIGHT, textY, footerPaint)
    }
}

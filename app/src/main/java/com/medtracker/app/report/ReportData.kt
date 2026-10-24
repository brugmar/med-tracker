package com.medtracker.app.report

import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.NightCycle
import com.medtracker.app.data.formatAmount
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Default report span: the 30 completed nights before the current night. */
const val DEFAULT_REPORT_DAYS = 30

/** Bounds on a user-chosen report span, to keep the PDF a sane size. */
const val MIN_REPORT_DAYS = 1
const val MAX_REPORT_DAYS = 366

/** One night, keyed by its start date; [doses] are chronological and may be empty. */
class ReportDay(
    val date: LocalDate,
    val doses: List<DoseLog>,
    primaryUnit: String
) {
    /** Amount taken this night in the report's primary unit. */
    val primaryTotal: Double = doses.filter { it.unit == primaryUnit }.sumOf { it.amount }
    val hasForeignUnits: Boolean = doses.any { it.unit != primaryUnit }
}

/** A week of night start dates (Monday–Sunday), clipped to the report window. */
class ReportWeek(
    val index: Int,
    /** The week's Monday; can precede the window start in a partial first week. */
    val weekStart: LocalDate,
    val days: List<ReportDay>,
    primaryUnit: String
) {
    val doseCount: Int = days.sumOf { it.doses.size }
    val totals: Map<String, Double> = sumByUnit(days.flatMap { it.doses })
    val primaryTotal: Double = totals[primaryUnit] ?: 0.0

    /** Average per night over the nights of this week inside the window. */
    val averagePerDay: Double = if (days.isEmpty()) 0.0 else primaryTotal / days.size
    val isPartial: Boolean = days.size < 7
}

class MedicineReport(
    val medicineName: String,
    /**
     * Unit used for charts, averages and the peak night. Doses logged in a different
     * (historical) unit still appear in the night rows but are kept out of those numbers.
     */
    val unit: String,
    /** Start date of the first included night. */
    val startDate: LocalDate,
    /** Start date of the last completed night; its window ends on the next date. */
    val endDate: LocalDate,
    val generatedAt: LocalDateTime,
    val weeks: List<ReportWeek>,
    val nightCycle: NightCycle = NightCycle()
) {
    val days: List<ReportDay> = weeks.flatMap { it.days }
    val dayCount: Int = days.size
    val doseCount: Int = days.sumOf { it.doses.size }
    val totals: Map<String, Double> = sumByUnit(days.flatMap { it.doses })
    val primaryTotal: Double = totals[unit] ?: 0.0
    val averagePerDay: Double = if (days.isEmpty()) 0.0 else primaryTotal / dayCount
    val daysWithDoses: Int = days.count { it.doses.isNotEmpty() }
    val peakDay: ReportDay? = days.filter { it.primaryTotal > 0 }.maxByOrNull { it.primaryTotal }
    /** Largest nightly total; shared y-scale of every chart so weeks stay comparable. */
    val maxDailyTotal: Double = days.maxOfOrNull { it.primaryTotal } ?: 0.0
    val hasForeignUnits: Boolean = days.any { it.hasForeignUnits }

    /** Dose counts in four six-hour blocks relative to the configured night start. */
    val timeOfDayCounts: List<Int> = run {
        val counts = IntArray(4)
        days.flatMap { it.doses }.forEach { dose ->
            counts[nightCycle.timeBlockAt(dose.timestamp)]++
        }
        counts.toList()
    }
}

/**
 * Builds the report for [days] completed nights before [reportDate] (a night start date).
 * [days] is clamped to [[MIN_REPORT_DAYS], [MAX_REPORT_DAYS]]. Logs outside the window are
 * dropped, so callers may pass a loosely filtered list.
 */
fun buildMedicineReport(
    medicine: Medicine,
    logs: List<DoseLog>,
    reportDate: LocalDate? = null,
    generatedAt: LocalDateTime = LocalDateTime.now(),
    days: Int = DEFAULT_REPORT_DAYS,
    nightCycle: NightCycle = NightCycle()
): MedicineReport {
    val window = days.coerceIn(MIN_REPORT_DAYS, MAX_REPORT_DAYS)
    val anchor = reportDate ?: nightCycle.dateAt(System.currentTimeMillis())
    val start = anchor.minusDays(window.toLong())
    val end = anchor.minusDays(1)

    val byDate = logs
        .sortedWith(compareBy({ it.timestamp }, { it.id }))
        .groupBy { nightCycle.dateAt(it.timestamp) }
        .filterKeys { !it.isBefore(start) && !it.isAfter(end) }

    val days = generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .map { date -> ReportDay(date, byDate[date].orEmpty(), medicine.unit) }
        .toList()

    val weeks = days
        .groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        .toSortedMap()
        .entries
        .mapIndexed { index, (monday, weekDays) ->
            ReportWeek(index + 1, monday, weekDays, medicine.unit)
        }

    return MedicineReport(
        medicineName = medicine.name,
        unit = medicine.unit,
        startDate = start,
        endDate = end,
        generatedAt = generatedAt,
        weeks = weeks,
        nightCycle = nightCycle
    )
}

private fun sumByUnit(doses: List<DoseLog>): Map<String, Double> =
    doses.groupBy { it.unit }.mapValues { (_, unitDoses) -> unitDoses.sumOf { it.amount } }

/** Renders unit totals with the primary unit first, e.g. "900 mg" or "900 mg + 2 tablet". */
fun formatUnitTotals(totals: Map<String, Double>, primaryUnit: String): String {
    val parts = mutableListOf("${formatAmount(totals[primaryUnit] ?: 0.0)} $primaryUnit")
    totals.keys.filter { it != primaryUnit }.sorted().forEach { unit ->
        parts += "${formatAmount(totals.getValue(unit))} $unit"
    }
    return parts.joinToString(" + ")
}

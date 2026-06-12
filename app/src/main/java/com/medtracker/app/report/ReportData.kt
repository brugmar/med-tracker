package com.medtracker.app.report

import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.toLocalDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Days covered by a report: the 30 days before — and not including — the report day. */
const val REPORT_DAYS = 30L

/** One calendar day of the report; [doses] are chronological and may be empty. */
class ReportDay(
    val date: LocalDate,
    val doses: List<DoseLog>,
    primaryUnit: String
) {
    /** Amount taken this day in the report's primary unit. */
    val primaryTotal: Double = doses.filter { it.unit == primaryUnit }.sumOf { it.amount }
    val hasForeignUnits: Boolean = doses.any { it.unit != primaryUnit }
}

/** A calendar week (Monday–Sunday) clipped to the report window. */
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

    /** Average per day over the days of this week that fall inside the window. */
    val averagePerDay: Double = if (days.isEmpty()) 0.0 else primaryTotal / days.size
    val isPartial: Boolean = days.size < 7
}

class MedicineReport(
    val medicineName: String,
    /**
     * Unit used for charts, averages and the peak day. Doses logged in a different
     * (historical) unit still appear in the day rows but are kept out of those numbers.
     */
    val unit: String,
    /** First day of the window, inclusive. */
    val startDate: LocalDate,
    /** Last day of the window, inclusive — the day before the report was requested. */
    val endDate: LocalDate,
    val generatedAt: LocalDateTime,
    val weeks: List<ReportWeek>
) {
    val days: List<ReportDay> = weeks.flatMap { it.days }
    val dayCount: Int = days.size
    val doseCount: Int = days.sumOf { it.doses.size }
    val totals: Map<String, Double> = sumByUnit(days.flatMap { it.doses })
    val primaryTotal: Double = totals[unit] ?: 0.0
    val averagePerDay: Double = if (days.isEmpty()) 0.0 else primaryTotal / dayCount
    val daysWithDoses: Int = days.count { it.doses.isNotEmpty() }
    val peakDay: ReportDay? = days.filter { it.primaryTotal > 0 }.maxByOrNull { it.primaryTotal }
    /** Largest daily total; shared y-scale of every chart so weeks stay comparable. */
    val maxDailyTotal: Double = days.maxOfOrNull { it.primaryTotal } ?: 0.0
    val hasForeignUnits: Boolean = days.any { it.hasForeignUnits }

    /** Dose counts in the four six-hour blocks of the day: 0–6, 6–12, 12–18, 18–24. */
    val timeOfDayCounts: List<Int> = run {
        val zone = ZoneId.systemDefault()
        val counts = IntArray(4)
        days.flatMap { it.doses }.forEach { dose ->
            val hour = Instant.ofEpochMilli(dose.timestamp).atZone(zone).hour
            counts[hour / 6]++
        }
        counts.toList()
    }
}

/**
 * Builds the report for the 30 days before [reportDate] ([reportDate] itself excluded).
 * Logs outside the window are dropped, so callers may pass a loosely filtered list.
 */
fun buildMedicineReport(
    medicine: Medicine,
    logs: List<DoseLog>,
    reportDate: LocalDate = LocalDate.now(),
    generatedAt: LocalDateTime = LocalDateTime.now()
): MedicineReport {
    val start = reportDate.minusDays(REPORT_DAYS)
    val end = reportDate.minusDays(1)

    val byDate = logs
        .sortedWith(compareBy({ it.timestamp }, { it.id }))
        .groupBy { it.timestamp.toLocalDate() }
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
        weeks = weeks
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

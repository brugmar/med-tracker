package com.medtracker.app

import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.NightCycle
import com.medtracker.app.data.atTimeMillis
import com.medtracker.app.report.MAX_REPORT_DAYS
import com.medtracker.app.report.MIN_REPORT_DAYS
import com.medtracker.app.report.buildMedicineReport
import com.medtracker.app.report.formatUnitTotals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class ReportDataTest {

    // A Friday; the window is Wed 2026-05-13 … Thu 2026-06-11.
    private val reportDate: LocalDate = LocalDate.of(2026, 6, 12)
    private val medicine = Medicine(id = 1, name = "Ibuprofen", defaultAmount = 200.0, unit = "mg")

    private val midnight = NightCycle(LocalTime.MIDNIGHT)
    private var nextId = 1L

    private fun log(
        date: LocalDate,
        time: String = "08:00",
        amount: Double = 200.0,
        unit: String = "mg"
    ): DoseLog {
        return DoseLog(
            id = nextId++,
            medicineId = medicine.id,
            medicineName = medicine.name,
            amount = amount,
            unit = unit,
            timestamp = date.atTimeMillis(LocalTime.parse(time))
        )
    }

    @Test
    fun windowCoversThirtyDaysAndExcludesToday() {
        val report = buildMedicineReport(
            medicine,
            listOf(
                log(reportDate),                 // today — excluded
                log(reportDate.minusDays(1)),    // last day of window
                log(reportDate.minusDays(30)),   // first day of window
                log(reportDate.minusDays(31))    // before the window — excluded
            ),
            reportDate,
            nightCycle = midnight
        )

        assertEquals(LocalDate.of(2026, 5, 13), report.startDate)
        assertEquals(LocalDate.of(2026, 6, 11), report.endDate)
        assertEquals(30, report.dayCount)
        assertEquals(2, report.doseCount)
        assertEquals(reportDate.minusDays(30), report.days.first().date)
        assertEquals(reportDate.minusDays(1), report.days.last().date)
    }

    @Test
    fun weeksSplitOnMondaysWithPartialEdges() {
        val report = buildMedicineReport(medicine, emptyList(), reportDate, nightCycle = midnight)

        assertEquals(listOf(5, 7, 7, 7, 4), report.weeks.map { it.days.size })
        assertEquals(listOf(1, 2, 3, 4, 5), report.weeks.map { it.index })
        assertTrue(report.weeks.all { it.weekStart.dayOfWeek == DayOfWeek.MONDAY })
        assertEquals(listOf(true, false, false, false, true), report.weeks.map { it.isPartial })
        // Days flow without gaps in week order.
        assertEquals(report.days.map { it.date }, report.days.map { it.date }.sorted())
    }

    @Test
    fun weekSumAverageAndDoseCount() {
        val monday = LocalDate.of(2026, 5, 18)
        val report = buildMedicineReport(
            medicine,
            listOf(
                log(monday, "08:00", 200.0),
                log(monday, "20:00", 400.0),
                log(monday.plusDays(2), "12:00", 100.0)
            ),
            reportDate,
            nightCycle = midnight
        )

        val week = report.weeks[1]
        assertEquals(monday, week.weekStart)
        assertEquals(700.0, week.primaryTotal, 1e-9)
        assertEquals(3, week.doseCount)
        assertEquals(100.0, week.averagePerDay, 1e-9)
    }

    @Test
    fun partialWeekAverageUsesOnlyDaysInsideWindow() {
        // Last week has only 4 in-window days (Jun 8–11).
        val report = buildMedicineReport(
            medicine,
            listOf(log(LocalDate.of(2026, 6, 9), "09:00", 400.0)),
            reportDate,
            nightCycle = midnight
        )

        val lastWeek = report.weeks.last()
        assertEquals(4, lastWeek.days.size)
        assertEquals(100.0, lastWeek.averagePerDay, 1e-9)
    }

    @Test
    fun overallStats() {
        val report = buildMedicineReport(
            medicine,
            listOf(
                log(LocalDate.of(2026, 5, 20), "08:00", 200.0),
                log(LocalDate.of(2026, 5, 20), "21:00", 400.0),
                log(LocalDate.of(2026, 6, 1), "10:00", 300.0)
            ),
            reportDate,
            nightCycle = midnight
        )

        assertEquals(900.0, report.primaryTotal, 1e-9)
        assertEquals(3, report.doseCount)
        assertEquals(2, report.daysWithDoses)
        assertEquals(30.0, report.averagePerDay, 1e-9)
        assertEquals(LocalDate.of(2026, 5, 20), report.peakDay?.date)
        assertEquals(600.0, report.maxDailyTotal, 1e-9)
    }

    @Test
    fun foreignUnitsAreListedButKeptOutOfPrimaryNumbers() {
        val day = LocalDate.of(2026, 5, 20)
        val report = buildMedicineReport(
            medicine,
            listOf(log(day, "08:00", 200.0), log(day, "12:00", 2.0, unit = "tablet")),
            reportDate,
            nightCycle = midnight
        )

        assertTrue(report.hasForeignUnits)
        assertEquals(200.0, report.primaryTotal, 1e-9)
        assertEquals(2.0, report.totals["tablet"]!!, 1e-9)
        assertEquals(2, report.doseCount)
        val reportDay = report.days.first { it.date == day }
        assertEquals(2, reportDay.doses.size)
        assertEquals(200.0, reportDay.primaryTotal, 1e-9)
        assertEquals("200 mg + 2 tablet", formatUnitTotals(report.totals, "mg"))
    }

    @Test
    fun timeOfDayBuckets() {
        val day = LocalDate.of(2026, 5, 20)
        val report = buildMedicineReport(
            medicine,
            listOf(
                log(day, "03:15"),
                log(day, "06:00"),
                log(day, "11:59"),
                log(day, "17:30"),
                log(day.plusDays(1), "23:45")
            ),
            reportDate,
            nightCycle = midnight
        )

        assertEquals(listOf(1, 2, 1, 1), report.timeOfDayCounts)
    }

    @Test
    fun customDayCountSetsWindowAndCount() {
        val report = buildMedicineReport(medicine, emptyList(), reportDate, days = 7, nightCycle = midnight)

        assertEquals(reportDate.minusDays(7), report.startDate)
        assertEquals(reportDate.minusDays(1), report.endDate)
        assertEquals(7, report.dayCount)
        assertEquals(7, report.weeks.sumOf { it.days.size })
        assertTrue(report.weeks.all { it.weekStart.dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun customDayCountStillRespectsWindow() {
        // 14-day report: window is [reportDate-14 .. reportDate-1], inclusive.
        val report = buildMedicineReport(
            medicine,
            listOf(
                log(reportDate.minusDays(15)),  // before the window -> excluded
                log(reportDate.minusDays(14)),  // first day of the window
                log(reportDate.minusDays(1))    // last day of the window
            ),
            reportDate,
            days = 14,
            nightCycle = midnight
        )

        assertEquals(14, report.dayCount)
        assertEquals(2, report.doseCount)
        assertEquals(reportDate.minusDays(14), report.days.first().date)
    }

    @Test
    fun dayCountIsClampedToBounds() {
        assertEquals(
            MIN_REPORT_DAYS,
            buildMedicineReport(medicine, emptyList(), reportDate, days = 0, nightCycle = midnight).dayCount
        )
        assertEquals(
            MAX_REPORT_DAYS,
            buildMedicineReport(medicine, emptyList(), reportDate, days = 10_000, nightCycle = midnight).dayCount
        )
    }

    @Test
    fun emptyReportIsSafe() {
        val report = buildMedicineReport(medicine, emptyList(), reportDate, nightCycle = midnight)

        assertEquals(30, report.dayCount)
        assertEquals(0, report.doseCount)
        assertEquals(0, report.daysWithDoses)
        assertEquals(0.0, report.averagePerDay, 1e-9)
        assertNull(report.peakDay)
        assertEquals(0.0, report.maxDailyTotal, 1e-9)
        assertFalse(report.hasForeignUnits)
        assertTrue(report.days.all { it.doses.isEmpty() })
        assertEquals("0 mg", formatUnitTotals(report.totals, "mg"))
    }

    @Test
    fun dosesWithinADayAreChronological() {
        val day = LocalDate.of(2026, 5, 21)
        val report = buildMedicineReport(
            medicine,
            listOf(log(day, "21:00", 300.0), log(day, "07:30", 100.0), log(day, "13:00", 200.0)),
            reportDate,
            nightCycle = midnight
        )

        val doses = report.days.first { it.date == day }.doses
        assertEquals(listOf(100.0, 200.0, 300.0), doses.map { it.amount })
    }

    @Test
    fun eveningAndNextMorningAreOneReportedNight() {
        val monday = LocalDate.of(2026, 6, 8)
        val logs = listOf(log(monday, "18:00", 1.0), log(monday.plusDays(1), "01:00", 1.0))
        val report = buildMedicineReport(medicine, logs, reportDate)
        val night = report.days.single { it.date == monday }

        assertEquals(2, night.doses.size)
        assertEquals(2.0, night.primaryTotal, 1e-9)
        assertEquals(1, report.daysWithDoses)
        assertEquals(monday, report.peakDay?.date)
        assertEquals(logs, night.doses)
    }

    @Test
    fun changingStartRecalculatesHistoricalNightTotalsAndPeaks() {
        val monday = LocalDate.of(2026, 6, 8)
        val logs = listOf(log(monday, "18:00", 1.0), log(monday.plusDays(1), "01:00", 1.0))
        val before = buildMedicineReport(medicine, logs, reportDate)
        val after = buildMedicineReport(medicine, logs, reportDate, nightCycle = NightCycle(LocalTime.of(19, 0)))

        assertEquals(1, before.daysWithDoses)
        assertEquals(2, after.daysWithDoses)
        assertEquals(2.0, before.maxDailyTotal, 1e-9)
        assertEquals(1.0, after.maxDailyTotal, 1e-9)
        assertEquals(before.primaryTotal, after.primaryTotal, 1e-9)
        assertEquals(logs, after.days.flatMap { it.doses })
    }

    @Test
    fun reportUsesExactNightBoundariesAndIncludesMorningOfReportDate() {
        val cycle = NightCycle(LocalTime.of(17, 30))
        val first = reportDate.minusDays(7)
        val report = buildMedicineReport(
            medicine,
            listOf(
                log(first, "17:29"), // before the first night
                log(first, "17:30"), // first inclusive boundary
                log(reportDate, "01:00"), // last completed night
                log(reportDate, "17:29:59"), // last second of that night
                log(reportDate, "17:30") // current night, excluded
            ),
            reportDate,
            days = 7,
            nightCycle = cycle
        )
        assertEquals(7, report.dayCount)
        assertEquals(3, report.doseCount)
        assertEquals(1, report.days.first().doses.size)
        assertEquals(2, report.days.last().doses.size)
        assertEquals(cycle, report.nightCycle)
    }

    @Test
    fun timeBlocksAreRelativeToNightStartAndRecomputedWhenItChanges() {
        val date = reportDate.minusDays(2)
        val logs = listOf(log(date, "18:00"), log(date.plusDays(1), "01:00"))
        val before = buildMedicineReport(medicine, logs, reportDate)
        val after = buildMedicineReport(medicine, logs, reportDate, nightCycle = NightCycle(LocalTime.of(19, 0)))
        assertEquals(listOf(1, 1, 0, 0), before.timeOfDayCounts)
        assertEquals(listOf(0, 1, 0, 1), after.timeOfDayCounts)
    }
}

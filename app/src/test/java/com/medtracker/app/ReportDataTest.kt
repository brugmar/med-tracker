package com.medtracker.app

import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.atTimeMillis
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

    private var nextId = 1L

    private fun log(
        date: LocalDate,
        time: String = "08:00",
        amount: Double = 200.0,
        unit: String = "mg"
    ): DoseLog {
        val (hour, minute) = time.split(":").map { it.toInt() }
        return DoseLog(
            id = nextId++,
            medicineId = medicine.id,
            medicineName = medicine.name,
            amount = amount,
            unit = unit,
            timestamp = date.atTimeMillis(LocalTime.of(hour, minute))
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
            reportDate
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
        val report = buildMedicineReport(medicine, emptyList(), reportDate)

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
            reportDate
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
            reportDate
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
            reportDate
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
            reportDate
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
            reportDate
        )

        assertEquals(listOf(1, 2, 1, 1), report.timeOfDayCounts)
    }

    @Test
    fun emptyReportIsSafe() {
        val report = buildMedicineReport(medicine, emptyList(), reportDate)

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
            reportDate
        )

        val doses = report.days.first { it.date == day }.doses
        assertEquals(listOf(100.0, 200.0, 300.0), doses.map { it.amount })
    }
}

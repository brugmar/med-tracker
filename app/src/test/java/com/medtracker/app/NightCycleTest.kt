package com.medtracker.app

import com.medtracker.app.data.NightCycle
import com.medtracker.app.data.nightDateLabel
import com.medtracker.app.data.nightWeekdayLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

class NightCycleTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val monday = LocalDate.of(2026, 10, 19)
    private val cycle = NightCycle()

    private fun timestamp(date: LocalDate, time: String): Long =
        date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun eveningAndFollowingMorningBelongToTheSameNight() {
        assertEquals(monday, cycle.dateAt(timestamp(monday, "18:00"), zone))
        assertEquals(monday, cycle.dateAt(timestamp(monday.plusDays(1), "01:00"), zone))
    }

    @Test
    fun windowsIncludeStartAndExcludeNextStartWithoutLosingMilliseconds() {
        val start = cycle.startMillis(monday, zone)
        val end = cycle.endMillis(monday, zone)
        assertEquals(monday.minusDays(1), cycle.dateAt(start - 1, zone))
        assertEquals(monday, cycle.dateAt(start, zone))
        assertEquals(monday, cycle.dateAt(end - 1, zone))
        assertEquals(monday.plusDays(1), cycle.dateAt(end, zone))
        assertEquals(end, cycle.startMillis(monday.plusDays(1), zone))
    }

    @Test
    fun boundaryCanIncludeMinutes() {
        val custom = NightCycle.fromMinutes(17 * 60 + 35)
        assertEquals("17:35", custom.startLabel)
        assertEquals("17:34", custom.endLabel)
        assertEquals(monday.minusDays(1), custom.dateAt(timestamp(monday, "17:34:59"), zone))
        assertEquals(monday, custom.dateAt(timestamp(monday, "17:35"), zone))
    }

    @Test
    fun changingBoundaryRegroupsOriginalTimestamps() {
        val timestamps = listOf(timestamp(monday, "18:00"), timestamp(monday.plusDays(1), "01:00"))
        assertEquals(mapOf(monday to 2), timestamps.groupingBy { cycle.dateAt(it, zone) }.eachCount())
        val later = NightCycle(LocalTime.of(19, 0))
        assertEquals(
            mapOf(monday.minusDays(1) to 1, monday to 1),
            timestamps.groupingBy { later.dateAt(it, zone) }.eachCount()
        )
    }

    @Test
    fun editingClockTimeKeepsDoseInSelectedNight() {
        assertEquals(timestamp(monday.plusDays(1), "01:00"), cycle.timestampAt(monday, LocalTime.of(1, 0), zone))
        assertEquals(timestamp(monday, "18:00"), cycle.timestampAt(monday, LocalTime.of(18, 0), zone))
        assertEquals(cycle.startMillis(monday, zone), cycle.timestampAt(monday, LocalTime.of(17, 0), zone))
        assertEquals(timestamp(monday.plusDays(1), "16:59"), cycle.timestampAt(monday, LocalTime.of(16, 59), zone))
    }

    @Test
    fun midnightBoundaryRetainsCalendarDayGrouping() {
        val midnight = NightCycle(LocalTime.MIDNIGHT)
        assertEquals(monday, midnight.dateAt(timestamp(monday, "00:00"), zone))
        assertEquals(monday, midnight.dateAt(timestamp(monday, "23:59"), zone))
        assertEquals(monday.plusDays(1), midnight.dateAt(timestamp(monday.plusDays(1), "00:00"), zone))
        assertEquals("00:00 - 23:59", midnight.windowLabel)
    }

    @Test
    fun lateBoundaryAndYearRollover() {
        val date = LocalDate.of(2026, 12, 31)
        val late = NightCycle.fromMinutes(1439)
        assertEquals(date.minusDays(1), late.dateAt(timestamp(date, "23:58"), zone))
        assertEquals(date, late.dateAt(timestamp(date, "23:59"), zone))
        assertEquals(date, late.dateAt(timestamp(date.plusDays(1), "23:58:59"), zone))
        assertTrue(date.nightDateLabel().contains("2026"))
        assertTrue(date.nightDateLabel().contains("2027"))
    }

    @Test
    fun springAndAutumnNightsUseLocalBoundaryInsteadOfFixed24Hours() {
        val spring = LocalDate.of(2026, 3, 28)
        val autumn = LocalDate.of(2026, 10, 24)
        assertEquals(23L * 60 * 60 * 1000, cycle.endMillis(spring, zone) - cycle.startMillis(spring, zone))
        assertEquals(25L * 60 * 60 * 1000, cycle.endMillis(autumn, zone) - cycle.startMillis(autumn, zone))
        assertEquals(spring, cycle.dateAt(timestamp(spring.plusDays(1), "01:00"), zone))
        assertEquals(autumn, cycle.dateAt(timestamp(autumn.plusDays(1), "01:00"), zone))
    }

    @Test
    fun missingBoundaryHourIsResolvedConsistentlyWithQueryWindow() {
        val custom = NightCycle(LocalTime.of(2, 30))
        val date = LocalDate.of(2026, 3, 29)
        assertEquals(timestamp(date, "03:30"), custom.startMillis(date, zone))
        assertEquals(date.minusDays(1), custom.dateAt(timestamp(date, "03:00"), zone))
        assertEquals(date, custom.dateAt(timestamp(date, "03:30"), zone))
    }

    @Test
    fun repeatedHourDoesNotMoveDosesBackToPreviousNight() {
        val custom = NightCycle(LocalTime.of(2, 30))
        val date = LocalDate.of(2026, 10, 25)
        val secondTwoFifteen = Instant.parse("2026-10-25T01:15:00Z").toEpochMilli()
        assertEquals(date, custom.dateAt(secondTwoFifteen, zone))
        assertEquals(secondTwoFifteen, custom.timestampAt(date, LocalTime.of(2, 15), zone))
    }

    @Test
    fun nonexistentClockTimeCannotMoveAnEditedDoseOutsideTheNight() {
        val custom = NightCycle(LocalTime.of(2, 30))
        val spring = LocalDate.of(2026, 3, 29)
        assertNull(custom.timestampAt(spring, LocalTime.of(3, 0), zone))
        assertNull(cycle.timestampAt(spring.minusDays(1), LocalTime.of(2, 15), zone))
    }

    @Test
    fun timeBlocksFollowConfiguredStartIncludingMinuteOffset() {
        val custom = NightCycle(LocalTime.of(17, 30))
        assertEquals(listOf("17:30-23:29", "23:30-05:29", "05:30-11:29", "11:30-17:29"), custom.timeBlockLabels)
        assertEquals(0, custom.timeBlockAt(timestamp(monday, "17:30"), zone))
        assertEquals(1, custom.timeBlockAt(timestamp(monday.plusDays(1), "01:00"), zone))
        assertEquals(2, custom.timeBlockAt(timestamp(monday.plusDays(1), "05:30"), zone))
        assertEquals(3, custom.timeBlockAt(timestamp(monday.plusDays(1), "17:29"), zone))
    }

    @Test
    fun labelsIdentifyBothWeekdays() {
        assertEquals("Mon/Tue", monday.nightWeekdayLabel(TextStyle.SHORT, Locale.ENGLISH))
        assertEquals("Sun/Mon", monday.minusDays(1).nightWeekdayLabel(TextStyle.SHORT, Locale.ENGLISH))
    }

    @Test
    fun invalidBoundariesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { NightCycle.fromMinutes(-1) }
        assertThrows(IllegalArgumentException::class.java) { NightCycle.fromMinutes(1440) }
        assertThrows(IllegalArgumentException::class.java) { NightCycle(LocalTime.of(17, 0, 1)) }
    }
}

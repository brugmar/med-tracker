package com.medtracker.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A night is identified by the date on which its window starts. */
data class NightCycle(val startTime: LocalTime = LocalTime.of(17, 0)) {
    init {
        require(startTime.second == 0 && startTime.nano == 0) { "Use minute precision." }
    }

    val startMinutes: Int get() = startTime.hour * 60 + startTime.minute
    val startLabel: String get() = startTime.format(TIME_FORMAT)
    val endLabel: String get() = startTime.minusMinutes(1).format(TIME_FORMAT)
    val windowLabel: String get() = "$startLabel - $endLabel" +
        if (startTime == LocalTime.MIDNIGHT) "" else " next day"

    fun startMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()

    fun endMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        startMillis(date.plusDays(1), zone)

    /** Half-open windows [start, end); boundaries follow local time, including DST. */
    fun dateAt(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
        return if (timestamp < startMillis(date, zone)) date.minusDays(1) else date
    }

    /** Resolves an edited clock time within the selected night, even after midnight. */
    fun timestampAt(date: LocalDate, time: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long? =
        listOf(date, date.plusDays(1)).flatMap { calendarDate ->
            val local = calendarDate.atTime(time)
            zone.rules.getValidOffsets(local).map { local.toInstant(it).toEpochMilli() }
        }.firstOrNull { dateAt(it, zone) == date }

    /** Four wall-clock blocks starting at the configured boundary. */
    fun timeBlockAt(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val time = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
        return Math.floorMod(time.hour * 60 + time.minute - startMinutes, 24 * 60) / 360
    }

    val timeBlockLabels: List<String> get() = (0..3).map { index ->
        val start = startTime.plusHours(index * 6L)
        "${start.format(TIME_FORMAT)}-${start.plusHours(6).minusMinutes(1).format(TIME_FORMAT)}"
    }

    companion object {
        fun fromMinutes(minutes: Int): NightCycle {
            require(minutes in 0 until 24 * 60) { "Invalid night start time." }
            return NightCycle(LocalTime.of(minutes / 60, minutes % 60))
        }

        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
    }
}

fun LocalDate.nightWeekdayLabel(
    style: TextStyle = TextStyle.SHORT,
    locale: Locale = Locale.getDefault()
): String = "${dayOfWeek.getDisplayName(style, locale)}/${plusDays(1).dayOfWeek.getDisplayName(style, locale)}"

/** Both calendar dates remain explicit at month and year boundaries. */
fun LocalDate.nightDateLabel(): String {
    val next = plusDays(1)
    val firstFormat = if (year == next.year) "d MMM" else "d MMM yyyy"
    return "${format(DateTimeFormatter.ofPattern(firstFormat))} / ${next.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}"
}

fun LocalDate.nightChartLabel(): String = "$dayOfMonth/${plusDays(1).dayOfMonth}"

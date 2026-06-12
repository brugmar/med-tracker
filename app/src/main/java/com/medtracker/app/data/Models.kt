package com.medtracker.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultAmount: Double,
    val unit: String,
    val presetAmount1: Double = defaultAmount * 0.5,
    val presetAmount2: Double = defaultAmount,
    val presetAmount3: Double = defaultAmount * 2
)

@Entity(tableName = "dose_logs")
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    // Name and unit are copied into the log so history stays readable
    // even if the medicine is later renamed or deleted.
    val medicineName: String,
    val amount: Double,
    val unit: String,
    val timestamp: Long
)

data class TodayStat(val total: Double, val count: Int)

fun Medicine.presetAmounts(): List<Double> =
    listOf(presetAmount1, presetAmount2, presetAmount3)

/** Renders 1.0 as "1" and 0.5 as "0.5"; at most two decimal places. */
fun formatAmount(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return BigDecimal.valueOf(value)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
}

/** Accepts both "0.5" and "0,5". Returns null when not a number. */
fun parseAmount(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()

fun LocalDate.startMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun LocalDate.atTimeMillis(time: LocalTime): Long =
    atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")

fun Long.toTimeString(): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun parseTime(text: String): LocalTime? {
    val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(text.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(hour, minute)
}

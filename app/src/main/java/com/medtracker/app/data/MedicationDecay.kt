package com.medtracker.app.data

import kotlin.math.pow

private const val MILLIS_PER_HOUR = 60.0 * 60.0 * 1_000.0

/**
 * Calculates how much of [medicineId]'s logged doses remains at [atTimestamp].
 *
 * Each dose decays independently according to exponential half-life decay, so
 * overlapping doses are additive. A dose contributes its full amount at its
 * timestamp and half its amount after every [halfLifeHours] that elapses.
 * Future doses and doses belonging to another medicine are ignored.
 *
 * @return `null` when [halfLifeHours] is absent, non-finite, or not positive;
 * otherwise the remaining amount, including `0.0` when no dose applies.
 */
fun calculateRemainingAmount(
    doseLogs: Iterable<DoseLog>,
    medicineId: Long,
    halfLifeHours: Double?,
    atTimestamp: Long
): Double? {
    if (halfLifeHours == null || !halfLifeHours.isFinite() || halfLifeHours <= 0.0) {
        return null
    }

    // A stable order makes the floating-point sum independent of the order in
    // which a database or test collection happens to provide the dose logs.
    val applicableDoses = doseLogs
        .asSequence()
        .filter { dose ->
            dose.medicineId == medicineId &&
                dose.timestamp <= atTimestamp &&
                dose.amount.isFinite()
        }
        .sortedWith(compareBy<DoseLog>({ it.timestamp }, { it.id }, { it.amount }))

    var remainingAmount = 0.0
    for (dose in applicableDoses) {
        // Subtract as doubles after the timestamp comparison to avoid signed
        // Long overflow for timestamps at opposite ends of the valid range.
        val elapsedHours =
            (atTimestamp.toDouble() - dose.timestamp.toDouble()) / MILLIS_PER_HOUR
        val elapsedHalfLives = elapsedHours / halfLifeHours
        remainingAmount += dose.amount * 0.5.pow(elapsedHalfLives)
    }

    return remainingAmount
}

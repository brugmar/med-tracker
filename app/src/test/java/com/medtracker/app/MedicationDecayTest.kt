package com.medtracker.app

import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.calculateRemainingAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class MedicationDecayTest {

    private val medicineId = 7L
    private val evaluationTime = Instant.parse("2026-08-06T11:00:00Z")

    private fun dose(
        amount: Double,
        takenAt: Instant,
        medicineId: Long = this.medicineId,
        id: Long = takenAt.toEpochMilli()
    ) = DoseLog(
        id = id,
        medicineId = medicineId,
        medicineName = "Esogno",
        amount = amount,
        unit = "mg",
        timestamp = takenAt.toEpochMilli()
    )

    @Test
    fun oneHalfLifeLeavesHalfTheDose() {
        val remaining = calculateRemainingAmount(
            doseLogs = listOf(dose(4.0, evaluationTime.minus(6, ChronoUnit.HOURS))),
            medicineId = medicineId,
            halfLifeHours = 6.0,
            atTimestamp = evaluationTime.toEpochMilli()
        )

        assertEquals(2.0, remaining!!, 1e-12)
    }

    @Test
    fun overlappingDosesDecayIndependentlyAndAddTogether() {
        val doses = listOf(
            dose(4.0, Instant.parse("2026-08-05T23:00:00Z")),
            dose(4.0, Instant.parse("2026-08-06T05:00:00Z")),
            dose(4.0, Instant.parse("2026-08-06T10:00:00Z"))
        )

        val remaining = calculateRemainingAmount(
            doseLogs = doses,
            medicineId = medicineId,
            halfLifeHours = 6.0,
            atTimestamp = evaluationTime.toEpochMilli()
        )

        // 1 mg (12 h old) + 2 mg (6 h old) + ~3.5636 mg (1 h old).
        assertEquals(6.563594872561357, remaining!!, 1e-12)
    }

    @Test
    fun aDoseAtTheEvaluationTimeContributesItsFullAmount() {
        val remaining = calculateRemainingAmount(
            doseLogs = listOf(dose(4.0, evaluationTime)),
            medicineId = medicineId,
            halfLifeHours = 6.0,
            atTimestamp = evaluationTime.toEpochMilli()
        )

        assertEquals(4.0, remaining!!, 0.0)
    }

    @Test
    fun futureDosesAndOtherMedicinesAreExcluded() {
        val remaining = calculateRemainingAmount(
            doseLogs = listOf(
                dose(4.0, evaluationTime.minus(6, ChronoUnit.HOURS)),
                dose(100.0, evaluationTime.plus(1, ChronoUnit.MILLIS)),
                dose(
                    amount = 100.0,
                    takenAt = evaluationTime.minus(1, ChronoUnit.HOURS),
                    medicineId = medicineId + 1
                )
            ),
            medicineId = medicineId,
            halfLifeHours = 6.0,
            atTimestamp = evaluationTime.toEpochMilli()
        )

        assertEquals(2.0, remaining!!, 1e-12)
    }

    @Test
    fun configuredHalfLifeWithNoApplicableDosesReturnsZero() {
        val remaining = calculateRemainingAmount(
            doseLogs = emptyList(),
            medicineId = medicineId,
            halfLifeHours = 6.0,
            atTimestamp = evaluationTime.toEpochMilli()
        )

        assertEquals(0.0, remaining!!, 0.0)
    }

    @Test
    fun missingOrInvalidHalfLifeReturnsNull() {
        val doses = listOf(dose(4.0, evaluationTime))

        listOf(null, 0.0, -6.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { halfLife ->
            assertNull(
                calculateRemainingAmount(
                    doseLogs = doses,
                    medicineId = medicineId,
                    halfLifeHours = halfLife,
                    atTimestamp = evaluationTime.toEpochMilli()
                )
            )
        }
    }

    @Test
    fun resultIsIndependentOfInputOrder() {
        val doses = listOf(
            dose(1e16, evaluationTime.minus(18, ChronoUnit.HOURS), id = 3),
            dose(1.0, evaluationTime.minus(12, ChronoUnit.HOURS), id = 2),
            dose(4.0, evaluationTime.minus(6, ChronoUnit.HOURS), id = 1)
        )

        val chronological = calculateRemainingAmount(
            doseLogs = doses,
            medicineId = medicineId,
            halfLifeHours = 6.0,
            atTimestamp = evaluationTime.toEpochMilli()
        )
        val reversed = calculateRemainingAmount(
            doseLogs = doses.reversed(),
            medicineId = medicineId,
            halfLifeHours = 6.0,
            atTimestamp = evaluationTime.toEpochMilli()
        )

        assertEquals(chronological, reversed)
    }
}

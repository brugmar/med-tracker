package com.medtracker.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.NightCycle
import com.medtracker.app.data.decodeBackup
import com.medtracker.app.data.encodeBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/**
 * Backup uses org.json, which is only real on a device, so these run as instrumented
 * tests rather than plain JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class BackupTest {

    @Test
    fun nightStartAndOriginalTimestampsSurviveRoundTrip() {
        val cycle = NightCycle(LocalTime.of(19, 35))
        val log = DoseLog(1, 1, "Medicine", 1.0, "tablet", 1_780_000_000_123)
        val decoded = decodeBackup(encodeBackup(emptyList(), listOf(log), cycle))
        assertEquals(cycle, decoded.nightCycle)
        assertEquals(listOf(log), decoded.doseLogs)
    }

    @Test
    fun backupWithoutNightStartLeavesPreferenceUnspecified() {
        val json = """{"version":1,"medicines":[],"doseLogs":[]}"""
        assertNull(decodeBackup(json).nightCycle)
    }

    @Test
    fun invalidNightStartIsRejectedBeforeImport() {
        listOf(-1, 1440).forEach { minutes ->
            val json = """{"version":1,"medicines":[],"doseLogs":[],"nightStartMinutes":$minutes}"""
            assertThrows(IllegalArgumentException::class.java) { decodeBackup(json) }
        }
    }

    private fun roundTripMedicines(medicines: List<Medicine>): Map<Long, Medicine> =
        decodeBackup(encodeBackup(medicines, emptyList())).medicines.associateBy { it.id }

    @Test
    fun dailyMaxSurvivesRoundTrip() {
        val withMax = Medicine(
            id = 1, name = "Ibuprofen", defaultAmount = 200.0, unit = "mg", dailyMaxAmount = 1200.0
        )
        val withoutMax = Medicine(id = 2, name = "Vitamin D", defaultAmount = 1.0, unit = "tablet")

        val decoded = roundTripMedicines(listOf(withMax, withoutMax))

        assertEquals(1200.0, decoded.getValue(1).dailyMaxAmount!!, 1e-9)
        assertNull(decoded.getValue(2).dailyMaxAmount)
    }

    @Test
    fun halfLifeSurvivesRoundTrip() {
        val withHalfLife = Medicine(
            id = 1, name = "Esogno", defaultAmount = 4.0, unit = "mg", halfLifeHours = 6.0
        )
        val withoutHalfLife = Medicine(id = 2, name = "Vitamin D", defaultAmount = 1.0, unit = "tablet")

        val decoded = roundTripMedicines(listOf(withHalfLife, withoutHalfLife))

        assertEquals(6.0, decoded.getValue(1).halfLifeHours!!, 1e-9)
        assertNull(decoded.getValue(2).halfLifeHours)
    }

    @Test
    fun colorSurvivesRoundTrip() {
        val withColor = Medicine(
            id = 1, name = "Ibuprofen", defaultAmount = 200.0, unit = "mg", colorKey = "#2A7FFF"
        )
        val withoutColor = Medicine(id = 2, name = "Vitamin D", defaultAmount = 1.0, unit = "tablet")

        val decoded = roundTripMedicines(listOf(withColor, withoutColor))

        assertEquals("#2A7FFF", decoded.getValue(1).colorKey)
        assertNull(decoded.getValue(2).colorKey)
    }

    @Test
    fun sortOrderSurvivesRoundTrip() {
        val second = Medicine(id = 1, name = "Ibuprofen", defaultAmount = 200.0, unit = "mg", sortOrder = 1)
        val first = Medicine(id = 2, name = "Vitamin D", defaultAmount = 1.0, unit = "tablet", sortOrder = 0)

        val decoded = roundTripMedicines(listOf(second, first))

        assertEquals(1, decoded.getValue(1).sortOrder)
        assertEquals(0, decoded.getValue(2).sortOrder)
    }

    @Test
    fun legacyBackupWithoutSortOrderFallsBackToArrayPosition() {
        val json = """
            {
              "format": "com.medtracker.app.backup",
              "version": 1,
              "medicines": [
                {"id": 7, "name": "Aspirin", "defaultAmount": 100.0, "unit": "mg"},
                {"id": 3, "name": "Vitamin D", "defaultAmount": 1.0, "unit": "tablet"}
              ],
              "doseLogs": []
            }
        """.trimIndent()

        val decoded = decodeBackup(json).medicines.associateBy { it.id }
        assertEquals(0, decoded.getValue(7).sortOrder)
        assertEquals(1, decoded.getValue(3).sortOrder)
    }

    @Test
    fun legacyBackupWithoutOptionalMedicineFieldsDecodesToNulls() {
        // A version-1 backup written before the optional medicine fields existed.
        val json = """
            {
              "format": "com.medtracker.app.backup",
              "version": 1,
              "medicines": [
                {"id": 1, "name": "Aspirin", "defaultAmount": 100.0, "unit": "mg"}
              ],
              "doseLogs": []
            }
        """.trimIndent()

        val medicine = decodeBackup(json).medicines.single()
        assertNull(medicine.dailyMaxAmount)
        assertNull(medicine.halfLifeHours)
        assertNull(medicine.colorKey)
    }

    @Test
    fun nonPositiveDailyMaxIsRejected() {
        val json = """
            {
              "format": "com.medtracker.app.backup",
              "version": 1,
              "medicines": [
                {"id": 1, "name": "Aspirin", "defaultAmount": 100.0, "unit": "mg", "dailyMaxAmount": 0.0}
              ],
              "doseLogs": []
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) { decodeBackup(json) }
    }

    @Test
    fun nonPositiveHalfLifeIsRejected() {
        val json = """
            {
              "format": "com.medtracker.app.backup",
              "version": 1,
              "medicines": [
                {"id": 1, "name": "Esogno", "defaultAmount": 4.0, "unit": "mg", "halfLifeHours": 0.0}
              ],
              "doseLogs": []
            }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) { decodeBackup(json) }
    }
}

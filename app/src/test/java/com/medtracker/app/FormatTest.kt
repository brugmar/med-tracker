package com.medtracker.app

import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.parseAmount
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.parseTime
import com.medtracker.app.data.presetAmounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class FormatTest {

    @Test
    fun wholeNumbersLoseDecimals() {
        assertEquals("1", formatAmount(1.0))
        assertEquals("400", formatAmount(400.0))
    }

    @Test
    fun fractionsKeepUpToTwoDecimals() {
        assertEquals("0.5", formatAmount(0.5))
        assertEquals("0.75", formatAmount(0.75))
        assertEquals("0.3", formatAmount(0.1 + 0.2))
    }

    @Test
    fun parsingAcceptsCommaAndDot() {
        assertEquals(0.5, parseAmount("0,5")!!, 1e-9)
        assertEquals(2.0, parseAmount(" 2 ")!!, 1e-9)
        assertNull(parseAmount("abc"))
    }

    @Test
    fun timeParsingAcceptsTwentyFourHourClock() {
        assertEquals(LocalTime.of(8, 30), parseTime("08:30"))
        assertEquals(LocalTime.of(8, 30), parseTime("8:30"))
        assertEquals(LocalTime.of(23, 59), parseTime("23:59"))
        assertNull(parseTime("24:00"))
        assertNull(parseTime("12:60"))
    }

    @Test
    fun newMedicineDefaultsQuickDosesFromDefaultAmount() {
        val medicine = Medicine(name = "Test", defaultAmount = 10.0, unit = "mg")

        assertEquals(listOf(5.0, 10.0, 20.0), medicine.presetAmounts())
    }
}

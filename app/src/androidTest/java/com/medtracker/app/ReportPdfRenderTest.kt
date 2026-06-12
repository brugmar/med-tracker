package com.medtracker.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.atTimeMillis
import com.medtracker.app.report.buildMedicineReport
import com.medtracker.app.report.renderMedicineReportPdf
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Renders sample reports into the app's external files dir so they can be pulled
 * with adb and reviewed by eye:
 * adb pull /sdcard/Android/data/com.medtracker.app/files/report-sample.pdf
 */
@RunWith(AndroidJUnit4::class)
class ReportPdfRenderTest {

    private val medicine = Medicine(id = 1, name = "Ibuprofen", defaultAmount = 200.0, unit = "mg")

    @Test
    fun renderSampleReport() {
        val today = LocalDate.now()
        val start = today.minusDays(30)
        val random = Random(42)
        var id = 1L
        val logs = mutableListOf<DoseLog>()

        fun dose(date: LocalDate, time: LocalTime, amount: Double, unit: String = "mg") {
            logs += DoseLog(
                id = id++,
                medicineId = medicine.id,
                medicineName = medicine.name,
                amount = amount,
                unit = unit,
                timestamp = date.atTimeMillis(time)
            )
        }

        (0 until 30).forEach { offset ->
            val date = start.plusDays(offset.toLong())
            // A few deliberate shapes: a pause mid-month, a heavy day, a late-night dose.
            when {
                offset in 12..14 -> return@forEach // three-day gap
                offset == 9 -> {
                    dose(date, LocalTime.of(6, 50), 400.0)
                    dose(date, LocalTime.of(11, 20), 200.0)
                    dose(date, LocalTime.of(16, 5), 200.0)
                    dose(date, LocalTime.of(22, 40), 200.0) // peak day: 1000 mg
                }
                offset == 20 -> {
                    dose(date, LocalTime.of(1, 35), 200.0) // night bucket
                    dose(date, LocalTime.of(13, 10), 200.0)
                }
                offset == 24 -> {
                    dose(date, LocalTime.of(9, 0), 200.0)
                    dose(date, LocalTime.of(19, 30), 1.0, unit = "tablet") // foreign unit
                }
                else -> {
                    val count = random.nextInt(0, 4) // 0..3 doses
                    repeat(count) { n ->
                        val hour = listOf(7, 13, 20)[n] + random.nextInt(0, 3)
                        val minute = random.nextInt(0, 60)
                        val amount = if (random.nextInt(4) == 0) 400.0 else 200.0
                        dose(date, LocalTime.of(hour, minute), amount)
                    }
                }
            }
        }

        writePdf("report-sample.pdf") { out ->
            renderMedicineReportPdf(buildMedicineReport(medicine, logs, today), out)
        }
    }

    @Test
    fun renderEmptyReport() {
        writePdf("report-empty.pdf") { out ->
            renderMedicineReportPdf(buildMedicineReport(medicine, emptyList(), LocalDate.now()), out)
        }
    }

    private fun writePdf(name: String, render: (java.io.OutputStream) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), name)
        file.outputStream().use(render)
        assertTrue("PDF should not be empty", file.length() > 1_000)
    }
}

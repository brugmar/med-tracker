package com.medtracker.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.medtracker.app.data.AppDatabase
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.NightCycle
import com.medtracker.app.data.atTimeMillis
import com.medtracker.app.data.decodeBackup
import com.medtracker.app.data.encodeBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class NightCycleIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application get() = instrumentation.targetContext.applicationContext as Application
    private lateinit var database: AppDatabase
    private lateinit var prefs: SharedPreferences
    private lateinit var model: AppViewModel
    private val stores = mutableListOf<ViewModelStore>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val medicine = Medicine(id = 1, name = "Night test", defaultAmount = 1.0, unit = "tablet")

    @Before
    fun setup(): Unit = runBlocking {
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).build()
        prefs = application.getSharedPreferences("night_cycle_integration_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        model = newModel()
        database.dao().insertMedicine(medicine)
    }

    private fun newModel(): AppViewModel {
        lateinit var result: AppViewModel
        instrumentation.runOnMainSync {
            result = AppViewModel(application, database.dao(), prefs)
            stores += ViewModelStore().apply { put("test", result) }
        }
        return result
    }

    @After
    fun cleanup() {
        scope.cancel()
        instrumentation.runOnMainSync { stores.forEach { it.clear() } }
        database.close()
        prefs.edit().clear().commit()
    }

    private fun dosesFor(date: LocalDate): List<DoseLog> = listOf(
        DoseLog(1, 1, medicine.name, 1.0, medicine.unit, date.atTimeMillis(LocalTime.of(18, 0))),
        DoseLog(2, 1, medicine.name, 1.0, medicine.unit, date.plusDays(1).atTimeMillis(LocalTime.of(1, 0)))
    )

    @Test
    fun historicalSelectionAndChartsRequeryWhenStartChanges() = runBlocking {
        val night = model.currentNight.value.minusDays(3)
        val logs = dosesFor(night)
        database.dao().insertLogs(logs)
        instrumentation.runOnMainSync {
            model.selectStatsMedicine(1)
            model.selectStatsDay(night)
        }
        model.statsDayLogs.launchIn(scope)
        model.statsRangeLogs.launchIn(scope)
        withTimeout(5_000) { model.statsDayLogs.first { it.size == 2 } }
        withTimeout(5_000) { model.statsRangeLogs.first { it.size == 2 } }

        instrumentation.runOnMainSync { model.setNightStart(LocalTime.of(19, 0)) }
        val selected = withTimeout(5_000) { model.statsDayLogs.first { it.size == 1 } }
        assertEquals(listOf(logs[1]), selected)
        assertEquals(night, model.statsDay.value)
        assertEquals(logs, database.dao().allLogsForBackup())

        val report = model.medicineReportFor(medicine, 7)
        assertEquals(2, report.daysWithDoses)
        assertEquals(1.0, report.maxDailyTotal, 1e-9)
        assertEquals(NightCycle(LocalTime.of(19, 0)), report.nightCycle)
    }

    @Test
    fun currentNightTotalsRecalculateAndPreferenceSurvivesRecreation() = runBlocking {
        database.dao().insertLogs(dosesFor(model.currentNight.value))
        model.todayStats.launchIn(scope)
        withTimeout(5_000) { model.todayStats.first { it[1]?.count == 2 } }
        instrumentation.runOnMainSync { model.setNightStart(LocalTime.of(19, 0)) }
        val totals = withTimeout(5_000) { model.todayStats.first { it[1]?.count == 1 } }
        assertEquals(1.0, totals.getValue(1).total, 1e-9)
        assertEquals(NightCycle(LocalTime.of(19, 0)), newModel().nightCycle.value)
        assertEquals(model.nightCycle.value, decodeBackup(model.exportBackupJson()).nightCycle)
    }

    @Test
    fun importingBackupRestoresBoundaryAndLegacyBackupPreservesPreference() = runBlocking {
        val cycle = NightCycle(LocalTime.of(20, 15))
        model.importBackupJson(encodeBackup(listOf(medicine), dosesFor(LocalDate.of(2026, 10, 19)), cycle))
        assertEquals(cycle, model.nightCycle.value)
        model.importBackupJson("""{"version":1,"medicines":[],"doseLogs":[]}""")
        assertEquals(cycle, model.nightCycle.value)
    }
}

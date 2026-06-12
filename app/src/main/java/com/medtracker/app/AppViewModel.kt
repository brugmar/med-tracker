package com.medtracker.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medtracker.app.data.AppDatabase
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.TodayStat
import com.medtracker.app.data.decodeBackup
import com.medtracker.app.data.encodeBackup
import com.medtracker.app.data.startMillis
import com.medtracker.app.report.MedicineReport
import com.medtracker.app.report.REPORT_DAYS
import com.medtracker.app.report.buildMedicineReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).dao()

    val medicines: StateFlow<List<Medicine>> = dao.medicines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Anchor for "today"; refreshed on resume so an app left open survives midnight. */
    private val today = MutableStateFlow(LocalDate.now())

    fun refreshToday() {
        today.value = LocalDate.now()
    }

    /** Per-medicine total amount and dose count for the current day. */
    val todayStats: StateFlow<Map<Long, TodayStat>> = today
        .flatMapLatest { day -> dao.logsBetween(day.startMillis(), day.plusDays(1).startMillis()) }
        .map { logs ->
            logs.groupBy { it.medicineId }
                .mapValues { (_, doses) -> TodayStat(doses.sumOf { it.amount }, doses.size) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---- Statistics tab ----

    val statsMedicineId = MutableStateFlow<Long?>(null)
    val statsDay = MutableStateFlow(LocalDate.now())

    fun selectStatsMedicine(id: Long) {
        statsMedicineId.value = id
    }

    fun selectStatsDay(day: LocalDate) {
        statsDay.value = day
    }

    /** Doses of the selected medicine on the selected day. */
    val statsDayLogs: StateFlow<List<DoseLog>> =
        combine(statsMedicineId, statsDay) { id, day -> id to day }
            .flatMapLatest { (id, day) ->
                if (id == null) flowOf(emptyList())
                else dao.logsForMedicineBetween(id, day.startMillis(), day.plusDays(1).startMillis())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 30 days of the selected medicine; feeds the week and month charts. */
    val statsRangeLogs: StateFlow<List<DoseLog>> =
        combine(statsMedicineId, today) { id, anchor -> id to anchor }
            .flatMapLatest { (id, anchor) ->
                if (id == null) flowOf(emptyList())
                else dao.logsForMedicineBetween(
                    id,
                    anchor.minusDays(29).startMillis(),
                    anchor.plusDays(1).startMillis()
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---- Actions ----

    fun addMedicine(
        name: String,
        defaultAmount: Double,
        unit: String,
        presetAmount1: Double,
        presetAmount2: Double,
        presetAmount3: Double
    ) {
        viewModelScope.launch {
            dao.insertMedicine(
                Medicine(
                    name = name.trim(),
                    defaultAmount = defaultAmount,
                    unit = unit.trim(),
                    presetAmount1 = presetAmount1,
                    presetAmount2 = presetAmount2,
                    presetAmount3 = presetAmount3
                )
            )
        }
    }

    fun updateMedicine(medicine: Medicine) {
        viewModelScope.launch { dao.updateMedicine(medicine) }
    }

    fun deleteMedicine(medicine: Medicine) {
        viewModelScope.launch { dao.deleteMedicine(medicine) }
    }

    fun logDose(medicine: Medicine, amount: Double) {
        refreshToday()
        viewModelScope.launch {
            dao.insertLog(
                DoseLog(
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    amount = amount,
                    unit = medicine.unit,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteLog(log: DoseLog) {
        viewModelScope.launch { dao.deleteLog(log) }
    }

    fun updateLog(log: DoseLog) {
        refreshToday()
        viewModelScope.launch { dao.updateLog(log) }
    }

    /** Report over the 30 days before today (today excluded), for the PDF export. */
    suspend fun medicineReportFor(medicine: Medicine): MedicineReport {
        val today = LocalDate.now()
        val logs = dao.logsForMedicineOnce(
            medicine.id,
            today.minusDays(REPORT_DAYS).startMillis(),
            today.startMillis()
        )
        return withContext(Dispatchers.Default) {
            buildMedicineReport(medicine, logs, today)
        }
    }

    suspend fun exportBackupJson(): String {
        val medicines = dao.allMedicinesForBackup()
        val doseLogs = dao.allLogsForBackup()
        return withContext(Dispatchers.Default) {
            encodeBackup(medicines = medicines, doseLogs = doseLogs)
        }
    }

    suspend fun importBackupJson(text: String) {
        val backup = withContext(Dispatchers.Default) {
            decodeBackup(text)
        }
        dao.replaceAllData(backup.medicines, backup.doseLogs)
        refreshToday()
    }
}

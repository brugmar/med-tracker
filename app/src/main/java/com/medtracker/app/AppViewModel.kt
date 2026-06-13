package com.medtracker.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medtracker.app.data.AppDatabase
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.TodayStat
import com.medtracker.app.data.decodeBackup
import com.medtracker.app.data.encodeBackup
import com.medtracker.app.data.startMillis
import com.medtracker.app.report.DEFAULT_REPORT_DAYS
import com.medtracker.app.report.MAX_REPORT_DAYS
import com.medtracker.app.report.MIN_REPORT_DAYS
import com.medtracker.app.report.MedicineReport
import com.medtracker.app.report.buildMedicineReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** In-app theme override; SYSTEM follows the device setting. */
enum class ThemeMode(val key: String, val label: String) {
    LIGHT("light", "Light"),
    SYSTEM("system", "System"),
    DARK("dark", "Dark");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.get(application).dao()
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null)))

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    // Mutable so drag-reorder can apply the new order synchronously; the database
    // write follows and Room's re-emission then confirms the same order.
    private val _medicines = MutableStateFlow<List<Medicine>>(emptyList())
    val medicines: StateFlow<List<Medicine>> = _medicines.asStateFlow()

    init {
        viewModelScope.launch {
            dao.medicines().collect { _medicines.value = it }
        }
    }

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
        presetAmount3: Double,
        dailyMaxAmount: Double?,
        colorKey: String?
    ) {
        viewModelScope.launch {
            dao.insertMedicineAtEnd(
                Medicine(
                    name = name.trim(),
                    defaultAmount = defaultAmount,
                    unit = unit.trim(),
                    presetAmount1 = presetAmount1,
                    presetAmount2 = presetAmount2,
                    presetAmount3 = presetAmount3,
                    dailyMaxAmount = dailyMaxAmount,
                    colorKey = colorKey
                )
            )
        }
    }

    /**
     * Moves the medicine [fromId] to the position of [toId] and persists the new
     * order. Applied to [medicines] immediately so an in-flight drag sees its own
     * reorders; identifying both ends by id keeps repeated drag events idempotent.
     */
    fun moveMedicine(fromId: Long, toId: Long) {
        val current = _medicines.value
        val fromIndex = current.indexOfFirst { it.id == fromId }
        val toIndex = current.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0 || fromIndex == toIndex) return
        val reordered = current.toMutableList()
            .apply { add(toIndex, removeAt(fromIndex)) }
            .mapIndexed { index, medicine -> medicine.copy(sortOrder = index) }
        _medicines.value = reordered
        viewModelScope.launch { dao.updateMedicines(reordered) }
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

    /** Report over the [days] days before today (today excluded), for the PDF export. */
    suspend fun medicineReportFor(
        medicine: Medicine,
        days: Int = DEFAULT_REPORT_DAYS
    ): MedicineReport {
        val window = days.coerceIn(MIN_REPORT_DAYS, MAX_REPORT_DAYS)
        val today = LocalDate.now()
        val logs = dao.logsForMedicineOnce(
            medicine.id,
            today.minusDays(window.toLong()).startMillis(),
            today.startMillis()
        )
        return withContext(Dispatchers.Default) {
            buildMedicineReport(medicine, logs, today, days = window)
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

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
    }
}

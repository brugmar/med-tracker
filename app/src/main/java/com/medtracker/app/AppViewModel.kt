package com.medtracker.app

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medtracker.app.data.AppDatabase
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.MedTrackerDao
import com.medtracker.app.data.NightCycle
import com.medtracker.app.data.TodayStat
import com.medtracker.app.data.calculateRemainingAmount
import com.medtracker.app.data.decodeBackup
import com.medtracker.app.data.encodeBackup
import com.medtracker.app.report.DEFAULT_REPORT_DAYS
import com.medtracker.app.report.MAX_REPORT_DAYS
import com.medtracker.app.report.MIN_REPORT_DAYS
import com.medtracker.app.report.MedicineReport
import com.medtracker.app.report.buildMedicineReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

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
class AppViewModel internal constructor(
    application: Application,
    private val dao: MedTrackerDao,
    private val prefs: SharedPreferences
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        AppDatabase.get(application).dao(),
        application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    )

    private val _nightCycle = MutableStateFlow(
        NightCycle.fromMinutes(prefs.getInt(KEY_NIGHT_START, 17 * 60).coerceIn(0, 1439))
    )
    val nightCycle: StateFlow<NightCycle> = _nightCycle.asStateFlow()

    val themeMode = MutableStateFlow(ThemeMode.fromKey(prefs.getString(KEY_THEME_MODE, null)))

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        prefs.edit().putString(KEY_THEME_MODE, mode.key).apply()
    }

    // Mutable so drag-reorder can apply the new order synchronously; the database
    // write follows and Room's re-emission then confirms the same order.
    private val _medicines = MutableStateFlow<List<Medicine>>(emptyList())
    val medicines: StateFlow<List<Medicine>> = _medicines.asStateFlow()

    /** Night anchor and clock for the live remaining-amount estimate. */
    private val _currentNight = MutableStateFlow(nightCycle.value.dateAt(System.currentTimeMillis()))
    val currentNight: StateFlow<LocalDate> = _currentNight.asStateFlow()
    private val currentTimeMillis = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            dao.medicines().collect { _medicines.value = it }
        }
        viewModelScope.launch {
            nightCycle.collectLatest { cycle ->
                while (true) {
                    val now = System.currentTimeMillis()
                    val night = cycle.dateAt(now)
                    val untilBoundary = cycle.endMillis(night) - now
                    delay(minOf(REMAINING_AMOUNT_REFRESH_MILLIS, untilBoundary).coerceAtLeast(1))
                    refreshToday()
                }
            }
        }
    }

    /** Refreshed on resume and every minute, including across the night boundary. */
    fun refreshToday() {
        val timestamp = System.currentTimeMillis()
        val previousNight = _currentNight.value
        val nextNight = nightCycle.value.dateAt(timestamp)
        _currentNight.value = nextNight
        if (statsDay.value == previousNight || statsDay.value.isAfter(nextNight)) {
            statsDay.value = nextNight
        }
        currentTimeMillis.value = timestamp
    }

    fun setNightStart(time: LocalTime) {
        val cycle = NightCycle(time.withSecond(0).withNano(0))
        prefs.edit().putInt(KEY_NIGHT_START, cycle.startMinutes).apply()
        _nightCycle.value = cycle
        refreshToday()
    }

    /** Re-queries raw timestamps when the boundary changes, including historical logs. */
    val todayStats: StateFlow<Map<Long, TodayStat>> = combine(currentNight, nightCycle) { day, cycle ->
        day to cycle
    }
        .flatMapLatest { (day, cycle) -> dao.logsBetween(cycle.startMillis(day), cycle.endMillis(day)) }
        .map { logs ->
            logs.groupBy { it.medicineId }
                .mapValues { (_, doses) -> TodayStat(doses.sumOf { it.amount }, doses.size) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Approximate amount currently remaining in the body for configured medicines. */
    val remainingAmounts: StateFlow<Map<Long, Double>> =
        combine(medicines, dao.doseLogs(), currentTimeMillis) { medicines, logs, atTimestamp ->
            val logsByMedicine = logs.groupBy { it.medicineId }
            medicines.mapNotNull { medicine ->
                calculateRemainingAmount(
                    doseLogs = logsByMedicine[medicine.id].orEmpty(),
                    medicineId = medicine.id,
                    halfLifeHours = medicine.halfLifeHours,
                    atTimestamp = atTimestamp
                )?.let { medicine.id to it }
            }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    // ---- Statistics tab ----

    val statsMedicineId = MutableStateFlow<Long?>(null)
    val statsDay = MutableStateFlow(currentNight.value)

    fun selectStatsMedicine(id: Long) {
        statsMedicineId.value = id
    }

    fun selectStatsDay(day: LocalDate) {
        statsDay.value = minOf(day, currentNight.value)
    }

    /** Doses of the selected medicine in the selected night. */
    val statsDayLogs: StateFlow<List<DoseLog>> =
        combine(statsMedicineId, statsDay, nightCycle) { id, day, cycle -> Triple(id, day, cycle) }
            .flatMapLatest { (id, day, cycle) ->
                if (id == null) flowOf(emptyList())
                else dao.logsForMedicineBetween(id, cycle.startMillis(day), cycle.endMillis(day))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 30 nights of the selected medicine; feeds both chart ranges. */
    val statsRangeLogs: StateFlow<List<DoseLog>> =
        combine(statsMedicineId, currentNight, nightCycle) { id, anchor, cycle -> Triple(id, anchor, cycle) }
            .flatMapLatest { (id, anchor, cycle) ->
                if (id == null) flowOf(emptyList())
                else dao.logsForMedicineBetween(
                    id,
                    cycle.startMillis(anchor.minusDays(29)),
                    cycle.endMillis(anchor)
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
        halfLifeHours: Double?,
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
                    halfLifeHours = halfLifeHours,
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
        val timestamp = System.currentTimeMillis()
        currentTimeMillis.value = timestamp
        viewModelScope.launch {
            dao.insertLog(
                DoseLog(
                    medicineId = medicine.id,
                    medicineName = medicine.name,
                    amount = amount,
                    unit = medicine.unit,
                    timestamp = timestamp
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

    /** Report over completed nights; the current, incomplete night is excluded. */
    suspend fun medicineReportFor(
        medicine: Medicine,
        days: Int = DEFAULT_REPORT_DAYS
    ): MedicineReport {
        val window = days.coerceIn(MIN_REPORT_DAYS, MAX_REPORT_DAYS)
        val cycle = nightCycle.value
        val today = cycle.dateAt(System.currentTimeMillis())
        val logs = dao.logsForMedicineOnce(
            medicine.id,
            cycle.startMillis(today.minusDays(window.toLong())),
            cycle.startMillis(today)
        )
        return withContext(Dispatchers.Default) {
            buildMedicineReport(medicine, logs, today, days = window, nightCycle = cycle)
        }
    }

    suspend fun exportBackupJson(): String {
        val medicines = dao.allMedicinesForBackup()
        val doseLogs = dao.allLogsForBackup()
        return withContext(Dispatchers.Default) {
            encodeBackup(medicines = medicines, doseLogs = doseLogs, nightCycle = nightCycle.value)
        }
    }

    suspend fun importBackupJson(text: String) {
        val backup = withContext(Dispatchers.Default) {
            decodeBackup(text)
        }
        dao.replaceAllData(backup.medicines, backup.doseLogs)
        backup.nightCycle?.let { setNightStart(it.startTime) }
        refreshToday()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_NIGHT_START = "night_start_minutes"
        const val REMAINING_AMOUNT_REFRESH_MILLIS = 60_000L
    }
}

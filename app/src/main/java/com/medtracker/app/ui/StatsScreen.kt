@file:OptIn(ExperimentalMaterial3Api::class)

package com.medtracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medtracker.app.AppViewModel
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.atTimeMillis
import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.parseTime
import com.medtracker.app.data.toLocalDate
import com.medtracker.app.data.toTimeString
import com.medtracker.app.ui.theme.MedicineAccent
import com.medtracker.app.ui.theme.medicineAccent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(viewModel: AppViewModel) {
    val medicines by viewModel.medicines.collectAsState()
    val selectedId by viewModel.statsMedicineId.collectAsState()
    val day by viewModel.statsDay.collectAsState()
    val dayLogs by viewModel.statsDayLogs.collectAsState()
    val rangeLogs by viewModel.statsRangeLogs.collectAsState()

    // Keep a valid selection: first medicine by default, or after a deletion.
    LaunchedEffect(medicines, selectedId) {
        if (medicines.none { it.id == selectedId }) {
            viewModel.statsMedicineId.value = medicines.firstOrNull()?.id
        }
    }

    if (medicines.isEmpty()) {
        EmptyState(
            icon = AppIcons.Chart,
            title = "Nothing to chart yet",
            body = "Add a medicine first — statistics will show up here."
        )
        return
    }

    val selectedMedicine = medicines.firstOrNull { it.id == selectedId } ?: return
    val accent = medicineAccent(selectedMedicine)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            medicines.forEach { medicine ->
                val chipAccent = medicineAccent(medicine)
                FilterChip(
                    selected = medicine.id == selectedId,
                    onClick = { viewModel.selectStatsMedicine(medicine.id) },
                    label = { Text(medicine.name) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(chipAccent.solid)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipAccent.container,
                        selectedLabelColor = chipAccent.onContainer
                    )
                )
            }
        }

        DayCard(
            medicine = selectedMedicine,
            accent = accent,
            day = day,
            logs = dayLogs,
            onPreviousDay = { viewModel.selectStatsDay(day.minusDays(1)) },
            onNextDay = { viewModel.selectStatsDay(day.plusDays(1)) },
            onDeleteLog = viewModel::deleteLog,
            onUpdateLog = viewModel::updateLog
        )

        ChartCard(title = "Last 7 days", medicine = selectedMedicine, accent = accent, logs = rangeLogs, days = 7)
        ChartCard(title = "Last 30 days", medicine = selectedMedicine, accent = accent, logs = rangeLogs, days = 30)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun DayCard(
    medicine: Medicine,
    accent: MedicineAccent,
    day: LocalDate,
    logs: List<DoseLog>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDeleteLog: (DoseLog) -> Unit,
    onUpdateLog: (DoseLog) -> Unit
) {
    var logToDelete by remember { mutableStateOf<DoseLog?>(null) }
    var logToEditTime by remember { mutableStateOf<DoseLog?>(null) }
    val today = LocalDate.now()
    val total = logs.sumOf { it.amount }
    val overMax = medicine.dailyMaxAmount?.let { total > it } ?: false
    val totalColor = when {
        overMax -> MaterialTheme.colorScheme.error
        total > 0 -> accent.solid
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousDay) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous day",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = when (day) {
                        today -> "Today"
                        today.minusDays(1) -> "Yesterday"
                        else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy"))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNextDay, enabled = day.isBefore(today)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next day",
                        tint = if (day.isBefore(today)) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    formatAmount(total),
                    style = MaterialTheme.typography.displaySmall,
                    color = totalColor,
                    modifier = Modifier.alignByBaseline()
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    medicine.unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = totalColor,
                    modifier = Modifier.alignByBaseline()
                )
            }
            Text(
                buildString {
                    append(if (logs.size == 1) "1 dose" else "${logs.size} doses")
                    if (overMax) append(" · over ${formatAmount(medicine.dailyMaxAmount!!)} ${medicine.unit} max")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (overMax) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            if (logs.isNotEmpty()) {
                HorizontalDivider(
                    Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                logs.forEach { log ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                log.timestamp.toTimeString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${formatAmount(log.amount)} ${log.unit}",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { logToEditTime = log }) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = "Edit time",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = { logToDelete = log }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Delete entry",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    logToDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            title = { Text("Delete this entry?") },
            text = {
                Text("${log.medicineName}, ${formatAmount(log.amount)} ${log.unit} at ${log.timestamp.toTimeString()}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLog(log)
                        logToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) { Text("Cancel") }
            }
        )
    }

    logToEditTime?.let { log ->
        EditLogTimeDialog(
            log = log,
            day = day,
            onDismiss = { logToEditTime = null },
            onSave = { updatedLog ->
                onUpdateLog(updatedLog)
                logToEditTime = null
            }
        )
    }
}

@Composable
private fun EditLogTimeDialog(
    log: DoseLog,
    day: LocalDate,
    onDismiss: () -> Unit,
    onSave: (DoseLog) -> Unit
) {
    var timeText by remember(log) { mutableStateOf(log.timestamp.toTimeString()) }
    val parsedTime = parseTime(timeText)
    val valid = parsedTime != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit time") },
        text = {
            Column {
                Text(
                    "${log.medicineName}, ${formatAmount(log.amount)} ${log.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Time") },
                    placeholder = { Text("HH:mm") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    isError = !valid
                )
                if (!valid) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Use 24-hour time, e.g. 08:30 or 21:05.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(log.copy(timestamp = day.atTimeMillis(parsedTime!!)))
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChartCard(
    title: String,
    medicine: Medicine,
    accent: MedicineAccent,
    logs: List<DoseLog>,
    days: Int
) {
    val today = LocalDate.now()
    val dailyTotals = remember(logs) {
        logs.groupBy { it.timestamp.toLocalDate() }
            .mapValues { (_, dayLogs) -> dayLogs.sumOf { it.amount } }
    }
    val dates = remember(today, days) {
        (days - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }
    val values = dates.map { dailyTotals[it] ?: 0.0 }
    val total = values.sum()
    val previousValues = values.dropLast(1)
    val averageExcludingToday =
        if (previousValues.isEmpty()) 0.0 else previousValues.sum() / previousValues.size

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append("Total ${formatAmount(total)} ${medicine.unit} · avg excl. today ")
                    append("${formatAmount(averageExcludingToday)} ${medicine.unit}/day")
                    medicine.dailyMaxAmount?.let { append(" · max ${formatAmount(it)} ${medicine.unit}/day") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            BarChart(
                values = values,
                labels = dates.mapIndexed { index, date -> chartLabel(days, index, date) },
                barColor = accent.solid,
                showValues = days <= 7,
                averageValue = averageExcludingToday,
                maxLine = medicine.dailyMaxAmount,
                modifier = Modifier.fillMaxWidth().height(168.dp)
            )
        }
    }
}

private fun chartLabel(days: Int, index: Int, date: LocalDate): String =
    if (days <= 7) {
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
    } else {
        // Label today and then every 5th day counting backwards.
        if ((days - 1 - index) % 5 == 0) date.dayOfMonth.toString() else ""
    }

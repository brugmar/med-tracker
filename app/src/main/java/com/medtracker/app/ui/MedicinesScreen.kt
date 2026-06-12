@file:OptIn(ExperimentalMaterial3Api::class)

package com.medtracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medtracker.app.AppViewModel
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.parseAmount
import com.medtracker.app.report.DEFAULT_REPORT_DAYS
import com.medtracker.app.report.MAX_REPORT_DAYS
import com.medtracker.app.report.MIN_REPORT_DAYS
import com.medtracker.app.report.renderMedicineReportPdf
import com.medtracker.app.ui.theme.MedicineRgb
import com.medtracker.app.ui.theme.defaultMedicineColorHex
import com.medtracker.app.ui.theme.medicineAccent
import com.medtracker.app.ui.theme.medicineColorHex
import com.medtracker.app.ui.theme.medicineColorValueToHex
import com.medtracker.app.ui.theme.normalizeMedicineColorHex
import com.medtracker.app.ui.theme.parseMedicineColorHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MedicinesScreen(viewModel: AppViewModel, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val medicines by viewModel.medicines.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var medicineToEdit by remember { mutableStateOf<Medicine?>(null) }
    var medicineToDelete by remember { mutableStateOf<Medicine?>(null) }
    var medicineToReport by remember { mutableStateOf<Medicine?>(null) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingReportRequest by remember { mutableStateOf<ReportRequest?>(null) }
    var reportDaysText by rememberSaveable { mutableStateOf(DEFAULT_REPORT_DAYS.toString()) }
    val reportDays = reportDaysText.toIntOrNull()
    val reportDaysValid = reportDays != null && reportDays in MIN_REPORT_DAYS..MAX_REPORT_DAYS

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val backup = viewModel.exportBackupJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(backup.toByteArray(Charsets.UTF_8))
                    } ?: error("Could not open selected file.")
                }
            }.onSuccess {
                onMessage("Data exported")
            }.onFailure { error ->
                onMessage("Export failed: ${error.readableMessage()}")
            }
        }
    }

    val reportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val request = pendingReportRequest
        if (uri == null || request == null) {
            pendingReportRequest = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            runCatching {
                val report = viewModel.medicineReportFor(request.medicine, request.days)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        renderMedicineReportPdf(report, output)
                    } ?: error("Could not open selected file.")
                }
            }.onSuccess {
                pendingReportRequest = null
                onMessage("Report saved")
            }.onFailure { error ->
                pendingReportRequest = null
                onMessage("Report failed: ${error.readableMessage()}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.reader(Charsets.UTF_8).readText()
                    } ?: error("Could not open selected file.")
                }
            }.onSuccess { backupText ->
                pendingImportText = backupText
            }.onFailure { error ->
                onMessage("Import failed: ${error.readableMessage()}")
            }
        }
    }

    val onExport = {
        exportLauncher.launch(defaultBackupFileName())
    }
    val onImport = {
        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    Scaffold(
        containerColor = Color.Transparent,
        // Outer scaffold already consumed the system bar insets.
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add medicine") }
            )
        }
    ) { padding ->
        if (medicines.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                BackupActions(
                    onExport = onExport,
                    onImport = onImport,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                EmptyState(
                    icon = AppIcons.Pill,
                    title = "No medicines yet",
                    body = "Use “Add medicine” to save a medicine with its name and default dosage.",
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "backup") {
                    BackupActions(onExport = onExport, onImport = onImport)
                }
                items(medicines, key = { it.id }) { medicine ->
                    MedicineRow(
                        medicine = medicine,
                        onReport = { medicineToReport = medicine },
                        onEdit = { medicineToEdit = medicine },
                        onDelete = { medicineToDelete = medicine }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        MedicineDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, amount, unit, preset1, preset2, preset3, dailyMax, colorKey ->
                viewModel.addMedicine(name, amount, unit, preset1, preset2, preset3, dailyMax, colorKey)
                showAddDialog = false
            }
        )
    }

    medicineToEdit?.let { medicine ->
        MedicineDialog(
            existing = medicine,
            onDismiss = { medicineToEdit = null },
            onSave = { name, amount, unit, preset1, preset2, preset3, dailyMax, colorKey ->
                viewModel.updateMedicine(
                    medicine.copy(
                        name = name,
                        defaultAmount = amount,
                        unit = unit,
                        presetAmount1 = preset1,
                        presetAmount2 = preset2,
                        presetAmount3 = preset3,
                        dailyMaxAmount = dailyMax,
                        colorKey = colorKey
                    )
                )
                medicineToEdit = null
            }
        )
    }

    medicineToReport?.let { medicine ->
        ReportDialog(
            medicine = medicine,
            daysText = reportDaysText,
            daysValid = reportDaysValid,
            onDaysChange = { input -> reportDaysText = input.filter { it.isDigit() }.take(3) },
            onDismiss = { medicineToReport = null },
            onSave = {
                val days = reportDays ?: DEFAULT_REPORT_DAYS
                pendingReportRequest = ReportRequest(medicine, days)
                medicineToReport = null
                reportLauncher.launch(reportFileName(medicine, days))
            }
        )
    }

    medicineToDelete?.let { medicine ->
        AlertDialog(
            onDismissRequest = { medicineToDelete = null },
            title = { Text("Delete ${medicine.name}?") },
            text = { Text("It disappears from the main screen, but doses already logged stay in the history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedicine(medicine)
                        medicineToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { medicineToDelete = null }) { Text("Cancel") }
            }
        )
    }

    pendingImportText?.let { backupText ->
        AlertDialog(
            onDismissRequest = { pendingImportText = null },
            title = { Text("Import backup?") },
            text = { Text("This replaces all current medicines and dose history with the selected backup.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching {
                                viewModel.importBackupJson(backupText)
                            }.onSuccess {
                                pendingImportText = null
                                onMessage("Data imported")
                            }.onFailure { error ->
                                pendingImportText = null
                                onMessage("Import failed: ${error.readableMessage()}")
                            }
                        }
                    }
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportText = null }) { Text("Cancel") }
            }
        )
    }
}

private data class ReportRequest(val medicine: Medicine, val days: Int)

private data class DefaultColor(val label: String, val hex: String)

private val DEFAULT_MEDICINE_COLORS = listOf(
    DefaultColor("Pale yellow", "#FEF3C7"),
    DefaultColor("Peach", "#FED7AA"),
    DefaultColor("Rose", "#FECDD3"),
    DefaultColor("Pink", "#FBCFE8"),
    DefaultColor("Lavender", "#DDD6FE"),
    DefaultColor("Sky", "#BAE6FD"),
    DefaultColor("Mint", "#BBF7D0"),
    DefaultColor("Seafoam", "#A7F3D0")
)

@Composable
private fun BackupActions(
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                "Backup",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export data")
                }
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import data")
                }
            }
        }
    }
}

@Composable
private fun MedicineRow(
    medicine: Medicine,
    onReport: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = medicineAccent(medicine)

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedicineAvatar(name = medicine.name, accent = accent, size = 42)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    medicine.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append("Default ${formatAmount(medicine.defaultAmount)} ${medicine.unit}")
                        medicine.dailyMaxAmount?.let {
                            append(" · max ${formatAmount(it)} ${medicine.unit}/day")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onReport) {
                Icon(
                    AppIcons.Chart,
                    contentDescription = "Save PDF report",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private val REPORT_DAY_PRESETS = listOf(7, 30, 90)

@Composable
private fun ReportDialog(
    medicine: Medicine,
    daysText: String,
    daysValid: Boolean,
    onDaysChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                AppIcons.Chart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Save report") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "PDF for ${medicine.name}, ending yesterday. Includes daily doses, weekly charts, sums and averages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = daysText,
                    onValueChange = onDaysChange,
                    label = { Text("Days") },
                    singleLine = true,
                    isError = !daysValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    REPORT_DAY_PRESETS.forEach { preset ->
                        FilterChip(
                            selected = daysText.toIntOrNull() == preset,
                            onClick = { onDaysChange(preset.toString()) },
                            label = {
                                Text(
                                    text = preset.toString(),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (!daysValid) {
                    Text(
                        "Enter a whole number of days between $MIN_REPORT_DAYS and $MAX_REPORT_DAYS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = daysValid, onClick = onSave) { Text("Save PDF") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MedicineDialog(
    existing: Medicine?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        amount: Double,
        unit: String,
        presetAmount1: Double,
        presetAmount2: Double,
        presetAmount3: Double,
        dailyMaxAmount: Double?,
        colorKey: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amountText by remember { mutableStateOf(existing?.let { formatAmount(it.defaultAmount) } ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var preset1Text by remember { mutableStateOf(existing?.let { formatAmount(it.presetAmount1) } ?: "") }
    var preset2Text by remember { mutableStateOf(existing?.let { formatAmount(it.presetAmount2) } ?: "") }
    var preset3Text by remember { mutableStateOf(existing?.let { formatAmount(it.presetAmount3) } ?: "") }
    var maxText by remember { mutableStateOf(existing?.dailyMaxAmount?.let { formatAmount(it) } ?: "") }
    var colorText by remember {
        mutableStateOf(existing?.colorKey?.let { medicineColorValueToHex(it) } ?: existing?.let {
            defaultMedicineColorHex(it.id)
        } ?: "#00696B")
    }
    var showColorChooser by remember { mutableStateOf(false) }
    var presetsEdited by remember(existing) { mutableStateOf(existing != null) }

    val amount = parseAmount(amountText)
    val preset1 = parseAmount(preset1Text)
    val preset2 = parseAmount(preset2Text)
    val preset3 = parseAmount(preset3Text)
    val maxAmount = parseAmount(maxText)
    val presetsValid = listOf(preset1, preset2, preset3).all { it != null && it > 0 }
    // The daily max is optional: blank is fine, but a typed value must be positive.
    val maxValid = maxText.isBlank() || (maxAmount != null && maxAmount > 0)
    val normalizedColor = normalizeMedicineColorHex(colorText)
    val dailyMax = maxAmount.takeIf { maxText.isNotBlank() }
    val valid = name.isNotBlank() && unit.isNotBlank() && amount != null && amount > 0 &&
        presetsValid && maxValid && normalizedColor != null

    LaunchedEffect(amountText) {
        if (!presetsEdited && amount != null && amount > 0) {
            preset1Text = formatAmount(amount * 0.5)
            preset2Text = formatAmount(amount)
            preset3Text = formatAmount(amount * 2)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                AppIcons.Pill,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(if (existing == null) "Add medicine" else "Edit medicine") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Default dose") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        placeholder = { Text("mg") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Examples: 200 mg, 0.5 tablet, 10 ml, 2 drops",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = maxText,
                    onValueChange = { maxText = it },
                    label = { Text("Daily max (optional)") },
                    singleLine = true,
                    isError = !maxValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { if (unit.isNotBlank()) Text(unit) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (!maxValid) "Enter a number above 0, or leave empty for no limit."
                    else "Over this daily total the home button and charts turn red — nothing is blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!maxValid) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Quick doses",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                QuickDoseField(
                    value = preset1Text,
                    onValueChange = {
                        presetsEdited = true
                        preset1Text = it
                    },
                    label = "Quick dose 1",
                    unit = unit
                )
                QuickDoseField(
                    value = preset2Text,
                    onValueChange = {
                        presetsEdited = true
                        preset2Text = it
                    },
                    label = "Quick dose 2",
                    unit = unit
                )
                QuickDoseField(
                    value = preset3Text,
                    onValueChange = {
                        presetsEdited = true
                        preset3Text = it
                    },
                    label = "Quick dose 3",
                    unit = unit
                )
                Text(
                    "Color",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MedicineColorSummary(
                    colorText = colorText,
                    onChoose = { showColorChooser = true }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(name.trim(), amount!!, unit.trim(), preset1!!, preset2!!, preset3!!, dailyMax, normalizedColor!!)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showColorChooser) {
        ColorChooserDialog(
            colorText = colorText,
            onColorTextChange = { colorText = it },
            onDismiss = { showColorChooser = false }
        )
    }
}

@Composable
private fun MedicineColorSummary(colorText: String, onChoose: () -> Unit) {
    val normalized = normalizeMedicineColorHex(colorText)
    val rgb = parseMedicineColorHex(normalized ?: colorText) ?: MedicineRgb(0, 105, 107)
    val previewColor = Color(rgb.red, rgb.green, rgb.blue)

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(previewColor)
            )
            Text(
                normalized ?: colorText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = onChoose) {
                Text("Choose")
            }
        }
    }
}

@Composable
private fun ColorChooserDialog(
    colorText: String,
    onColorTextChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose color") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "Default colors",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DefaultColorSwatches(
                    selectedHex = normalizeMedicineColorHex(colorText),
                    onSelected = onColorTextChange
                )
                Text(
                    "Custom color",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CustomColorControls(
                    colorText = colorText,
                    onColorTextChange = onColorTextChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun DefaultColorSwatches(selectedHex: String?, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DEFAULT_MEDICINE_COLORS.chunked(4).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                rowColors.forEach { color ->
                    val selected = selectedHex == color.hex
                    Surface(
                        onClick = { onSelected(color.hex) },
                        shape = CircleShape,
                        color = Color(
                            red = color.hex.substring(1, 3).toInt(16),
                            green = color.hex.substring(3, 5).toInt(16),
                            blue = color.hex.substring(5, 7).toInt(16)
                        ),
                        border = if (selected) {
                            BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .semantics { contentDescription = color.label }
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun CustomColorControls(colorText: String, onColorTextChange: (String) -> Unit) {
    val normalized = normalizeMedicineColorHex(colorText)
    val rgb = parseMedicineColorHex(normalized ?: colorText) ?: MedicineRgb(0, 105, 107)
    val previewColor = Color(rgb.red, rgb.green, rgb.blue)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(previewColor)
            )
            OutlinedTextField(
                value = colorText,
                onValueChange = { onColorTextChange(sanitizeHexInput(it)) },
                label = { Text("Hex color") },
                placeholder = { Text("#00696B") },
                singleLine = true,
                isError = normalized == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.weight(1f)
            )
        }
        if (normalized == null) {
            Text(
                "Use a 6-digit color, e.g. #00696B.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        ColorChannelSlider(label = "R", value = rgb.red) { red ->
            onColorTextChange(medicineColorHex(red, rgb.green, rgb.blue))
        }
        ColorChannelSlider(label = "G", value = rgb.green) { green ->
            onColorTextChange(medicineColorHex(rgb.red, green, rgb.blue))
        }
        ColorChannelSlider(label = "B", value = rgb.blue) { blue ->
            onColorTextChange(medicineColorHex(rgb.red, rgb.green, blue))
        }
    }
}

@Composable
private fun ColorChannelSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.End,
            modifier = Modifier.width(34.dp)
        )
    }
}

private fun sanitizeHexInput(input: String): String {
    val cleaned = input.uppercase(Locale.ROOT)
        .filterIndexed { index, char ->
            char in '0'..'9' || char in 'A'..'F' || (char == '#' && index == 0)
        }
    return if (cleaned.startsWith("#")) cleaned.take(7) else cleaned.take(6)
}

@Composable
private fun QuickDoseField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    unit: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = {
            if (unit.isNotBlank()) Text(unit)
        },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun defaultBackupFileName(): String {
    val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    return "medtracker-backup-$date.json"
}

private fun reportFileName(medicine: Medicine, days: Int): String {
    val slug = medicine.name.lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(40)
        .ifEmpty { "medicine" }
    val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    return "medtracker-report-$slug-${days}d-$date.pdf"
}

private fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unknown error"

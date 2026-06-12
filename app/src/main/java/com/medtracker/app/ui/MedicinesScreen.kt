@file:OptIn(ExperimentalMaterial3Api::class)

package com.medtracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medtracker.app.AppViewModel
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.parseAmount
import com.medtracker.app.ui.theme.medicineAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun MedicinesScreen(viewModel: AppViewModel, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val medicines by viewModel.medicines.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var medicineToEdit by remember { mutableStateOf<Medicine?>(null) }
    var medicineToDelete by remember { mutableStateOf<Medicine?>(null) }
    var pendingImportText by remember { mutableStateOf<String?>(null) }

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
            onSave = { name, amount, unit, preset1, preset2, preset3, dailyMax ->
                viewModel.addMedicine(name, amount, unit, preset1, preset2, preset3, dailyMax)
                showAddDialog = false
            }
        )
    }

    medicineToEdit?.let { medicine ->
        MedicineDialog(
            existing = medicine,
            onDismiss = { medicineToEdit = null },
            onSave = { name, amount, unit, preset1, preset2, preset3, dailyMax ->
                viewModel.updateMedicine(
                    medicine.copy(
                        name = name,
                        defaultAmount = amount,
                        unit = unit,
                        presetAmount1 = preset1,
                        presetAmount2 = preset2,
                        presetAmount3 = preset3,
                        dailyMaxAmount = dailyMax
                    )
                )
                medicineToEdit = null
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
private fun MedicineRow(medicine: Medicine, onEdit: () -> Unit, onDelete: () -> Unit) {
    val accent = medicineAccent(medicine.id)

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
        dailyMaxAmount: Double?
    ) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amountText by remember { mutableStateOf(existing?.let { formatAmount(it.defaultAmount) } ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var preset1Text by remember { mutableStateOf(existing?.let { formatAmount(it.presetAmount1) } ?: "") }
    var preset2Text by remember { mutableStateOf(existing?.let { formatAmount(it.presetAmount2) } ?: "") }
    var preset3Text by remember { mutableStateOf(existing?.let { formatAmount(it.presetAmount3) } ?: "") }
    var maxText by remember { mutableStateOf(existing?.dailyMaxAmount?.let { formatAmount(it) } ?: "") }
    var presetsEdited by remember(existing) { mutableStateOf(existing != null) }

    val amount = parseAmount(amountText)
    val preset1 = parseAmount(preset1Text)
    val preset2 = parseAmount(preset2Text)
    val preset3 = parseAmount(preset3Text)
    val maxAmount = parseAmount(maxText)
    val presetsValid = listOf(preset1, preset2, preset3).all { it != null && it > 0 }
    // The daily max is optional: blank is fine, but a typed value must be positive.
    val maxValid = maxText.isBlank() || (maxAmount != null && maxAmount > 0)
    val dailyMax = maxAmount.takeIf { maxText.isNotBlank() }
    val valid = name.isNotBlank() && unit.isNotBlank() && amount != null && amount > 0 &&
        presetsValid && maxValid

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
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(name.trim(), amount!!, unit.trim(), preset1!!, preset2!!, preset3!!, dailyMax) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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

private fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: "Unknown error"

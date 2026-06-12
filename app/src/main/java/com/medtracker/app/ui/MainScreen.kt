@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.medtracker.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medtracker.app.AppViewModel
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.TodayStat
import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.parseAmount
import com.medtracker.app.data.presetAmounts
import com.medtracker.app.ui.theme.MedicineAccent
import com.medtracker.app.ui.theme.medicineAccent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun MainScreen(
    viewModel: AppViewModel,
    onLogged: (String) -> Unit,
    onManageMedicines: () -> Unit
) {
    val medicines by viewModel.medicines.collectAsState()
    val todayStats by viewModel.todayStats.collectAsState()
    var medicineToLog by remember { mutableStateOf<Medicine?>(null) }

    if (medicines.isEmpty()) {
        EmptyState(
            icon = AppIcons.Pill,
            title = "No medicines yet",
            body = "Add your medicines with a default dosage, then log a dose with a single tap.",
            action = {
                Button(onClick = onManageMedicines) { Text("Add medicines") }
            }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item(key = "summary") {
                TodayProgressCard(medicines = medicines, stats = todayStats)
            }
            items(medicines, key = { it.id }) { medicine ->
                MedicineCard(
                    medicine = medicine,
                    todayStat = todayStats[medicine.id],
                    onClick = { medicineToLog = medicine }
                )
            }
            item(key = "hint") {
                Text(
                    "Tap a medicine to log a dose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }

    medicineToLog?.let { medicine ->
        TakeDoseDialog(
            medicine = medicine,
            onDismiss = { medicineToLog = null },
            onConfirm = { amount ->
                viewModel.logDose(medicine, amount)
                medicineToLog = null
                onLogged("Logged ${medicine.name} — ${formatAmount(amount)} ${medicine.unit}")
            }
        )
    }
}

private fun greeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun TodayProgressCard(medicines: List<Medicine>, stats: Map<Long, TodayStat>) {
    val logged = medicines.count { stats.containsKey(it.id) }
    val complete = logged == medicines.size
    val progress by animateFloatAsState(
        targetValue = logged / medicines.size.toFloat(),
        label = "todayProgress"
    )

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (complete) "All medicines logged today"
                    else "$logged of ${medicines.size} medicines logged today",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (complete) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f)
            )
        }
    }
}

@Composable
private fun MedicineCard(medicine: Medicine, todayStat: TodayStat?, onClick: () -> Unit) {
    val accent = medicineAccent(medicine)
    val max = medicine.dailyMaxAmount
    // Soft ceiling: flip the whole card to the error palette once the day goes over.
    val overMax = max != null && todayStat != null && todayStat.total > max

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (overMax) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (overMax) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp).animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedicineAvatar(name = medicine.name, accent = accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    medicine.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (overMax) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Default ${formatAmount(medicine.defaultAmount)} ${medicine.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overMax) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            if (todayStat != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.widthIn(max = 148.dp)
                ) {
                    Text(
                        "${formatAmount(todayStat.total)} ${medicine.unit}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (overMax) MaterialTheme.colorScheme.error else accent.solid,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (overMax) "over ${formatAmount(max!!)} ${medicine.unit} max"
                        else if (todayStat.count == 1) "1 dose today" else "${todayStat.count} doses today",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overMax) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.container),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Log dose",
                        tint = accent.onContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MedicineAvatar(name: String, accent: MedicineAccent, size: Int = 46) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(accent.container),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = accent.onContainer
        )
    }
}

@Composable
private fun TakeDoseDialog(
    medicine: Medicine,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amountText by remember(medicine) { mutableStateOf(formatAmount(medicine.defaultAmount)) }
    val amount = parseAmount(amountText)
    val valid = amount != null && amount > 0
    val accent = medicineAccent(medicine)

    // Step the amount up or down by one default dose, never below zero.
    val step = medicine.defaultAmount
    val canDecrease = amount != null && amount - step > 1e-9
    val adjust: (Double) -> Unit = { delta ->
        amountText = formatAmount(((amount ?: 0.0) + delta).coerceAtLeast(0.0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { MedicineAvatar(name = medicine.name, accent = accent, size = 48) },
        title = { Text("Log ${medicine.name}") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    medicine.presetAmounts().forEach { preset ->
                        FilterChip(
                            selected = amount == preset,
                            onClick = { amountText = formatAmount(preset) },
                            label = { Text("${formatAmount(preset)} ${medicine.unit}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.container,
                                selectedLabelColor = accent.onContainer
                            )
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { adjust(-step) },
                        enabled = canDecrease,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = accent.container,
                            contentColor = accent.onContainer
                        )
                    ) {
                        // Material core icons have no "minus"; draw a simple bar.
                        Box(
                            Modifier
                                .size(width = 16.dp, height = 2.dp)
                                .semantics {
                                    contentDescription = "Subtract ${formatAmount(step)} ${medicine.unit}"
                                }
                                .background(LocalContentColor.current, CircleShape)
                        )
                    }
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        suffix = { Text(medicine.unit) },
                        isError = !valid,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = { adjust(step) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = accent.container,
                            contentColor = accent.onContainer
                        )
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add ${formatAmount(step)} ${medicine.unit}"
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (!valid) "Enter a number, e.g. ${formatAmount(medicine.defaultAmount)}"
                    else "+ / − change the dose by ${formatAmount(step)} ${medicine.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!valid) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(amount!!) }) { Text("Log dose") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

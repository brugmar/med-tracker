@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.medtracker.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import java.util.Locale

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
                Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        LocalDate.now()
                            .format(DateTimeFormatter.ofPattern("EEEE d MMMM"))
                            .uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                }
            }
            item(key = "summary") {
                TodayHeroCard(medicines = medicines, stats = todayStats)
            }
            item(key = "sectionLabel") {
                Text(
                    "Your medicines",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 0.dp)
                )
            }
            items(medicines, key = { it.id }) { medicine ->
                MedicineCard(
                    medicine = medicine,
                    todayStat = todayStats[medicine.id],
                    onClick = { medicineToLog = medicine }
                )
            }
        }
    }

    medicineToLog?.let { medicine ->
        LogDoseSheet(
            medicine = medicine,
            todayStat = todayStats[medicine.id],
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
private fun TodayHeroCard(medicines: List<Medicine>, stats: Map<Long, TodayStat>) {
    val logged = medicines.count { stats.containsKey(it.id) }
    val complete = logged == medicines.size
    val progress by animateFloatAsState(
        targetValue = logged / medicines.size.toFloat(),
        label = "todayProgress"
    )

    Surface(shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProgressRing(
                progress = progress,
                complete = complete,
                label = "$logged/${medicines.size}"
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (complete) "All logged for today"
                    else "$logged of ${medicines.size} logged today",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (complete) "Nice — you're all caught up."
                    else "Tap a medicine below to log a dose.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

/** Circular progress with the logged count in the center, a check mark when done. */
@Composable
private fun ProgressRing(progress: Float, complete: Boolean, label: String) {
    val ringColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)

    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (progress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        if (complete) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        } else {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
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
        Column(Modifier.fillMaxWidth().padding(14.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Surface(shape = CircleShape, color = accent.container) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = accent.onContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Log",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent.onContainer
                            )
                        }
                    }
                }
            }
            // Quiet daily-max meter: only when a ceiling is set and something was logged.
            if (max != null && todayStat != null) {
                Spacer(Modifier.height(12.dp))
                val fraction by animateFloatAsState(
                    targetValue = (todayStat.total / max).toFloat().coerceIn(0f, 1f),
                    label = "dailyMaxFraction"
                )
                val barColor = if (overMax) MaterialTheme.colorScheme.error else accent.solid
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(barColor.copy(alpha = 0.15f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(barColor)
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
            .clip(RoundedCornerShape((size * 0.32f).dp))
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
private fun LogDoseSheet(
    medicine: Medicine,
    todayStat: TodayStat?,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by remember(medicine) { mutableStateOf(formatAmount(medicine.defaultAmount)) }
    val amount = parseAmount(amountText)
    val valid = amount != null && amount > 0
    val accent = medicineAccent(medicine)
    // Filled accent button needs its own contrast color, like customMedicineAccent does.
    val onAccent = if (accent.solid.luminance() > 0.5f) Color(0xFF101414) else Color.White

    // Step the amount up or down by one default dose, never below zero.
    val step = medicine.defaultAmount
    val canDecrease = amount != null && amount - step > 1e-9
    val adjust: (Double) -> Unit = { delta ->
        amountText = formatAmount(((amount ?: 0.0) + delta).coerceAtLeast(0.0))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
                .imePadding()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MedicineAvatar(name = medicine.name, accent = accent, size = 48)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Log ${medicine.name}",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (todayStat == null) "Nothing logged yet today"
                        else "Today so far: ${formatAmount(todayStat.total)} ${medicine.unit} · " +
                            (if (todayStat.count == 1) "1 dose" else "${todayStat.count} doses"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                medicine.presetAmounts().forEach { preset ->
                    PresetAmountButton(
                        label = "${formatAmount(preset)} ${medicine.unit}",
                        selected = amount == preset,
                        accent = accent,
                        onClick = { amountText = formatAmount(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
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
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onConfirm(amount!!) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.solid,
                    contentColor = onAccent
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Log dose", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PresetAmountButton(
    label: String,
    selected: Boolean,
    accent: MedicineAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) accent.container else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.5.dp, accent.solid) else null,
        modifier = modifier.height(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) accent.onContainer else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

package com.medtracker.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medtracker.app.data.NightCycle
import com.medtracker.app.data.parseTime
import java.time.LocalTime

@Composable
fun NightCycleSettings(
    cycle: NightCycle,
    onStartChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Night cycle", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { editing = true }) { Text("Change") }
            }
            Text(cycle.windowLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                "All totals, limits, charts and reports use this window. Changing the start time also recalculates the entire dose history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (editing) {
        var input by remember(cycle) { mutableStateOf(cycle.startLabel) }
        val time = parseTime(input)
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Night start time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Start time (24-hour)") },
                        placeholder = { Text("17:00") },
                        singleLine = true,
                        isError = time == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (time == null) "Enter a time from 00:00 to 23:59."
                        else "Night: ${NightCycle(time).windowLabel}. All previous totals will be recalculated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (time == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = time != null, onClick = {
                    onStartChange(time!!)
                    editing = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
}

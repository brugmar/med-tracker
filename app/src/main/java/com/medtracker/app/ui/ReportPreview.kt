@file:OptIn(ExperimentalLayoutApi::class)

package com.medtracker.app.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medtracker.app.data.DoseLog
import com.medtracker.app.data.Medicine
import com.medtracker.app.data.formatAmount
import com.medtracker.app.data.toTimeString
import com.medtracker.app.report.MedicineReport
import com.medtracker.app.report.ReportDay
import com.medtracker.app.report.ReportWeek
import com.medtracker.app.report.formatUnitTotals
import com.medtracker.app.ui.theme.MedicineAccent
import com.medtracker.app.ui.theme.medicineAccent
import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.max

data class ReportPreviewState(
    val medicine: Medicine,
    val report: MedicineReport
)

@Composable
fun MedicineReportPreviewDialog(
    preview: ReportPreviewState,
    onDismiss: () -> Unit
) {
    val accent = medicineAccent(preview.medicine)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                ReportPreviewTopBar(onDismiss = onDismiss)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "header") {
                        ReportPreviewHeader(preview.medicine, preview.report, accent)
                    }
                    item(key = "summary") {
                        ReportSummaryGrid(preview.report, accent)
                    }
                    item(key = "overview") {
                        ReportOverviewCard(preview.medicine, preview.report, accent)
                    }
                    item(key = "timeOfDay") {
                        TimeOfDayCard(preview.report, accent)
                    }
                    item(key = "weeklyTitle") {
                        SectionTitle(
                            title = "Weekly usage",
                            subtitle = "Each week shows daily totals and the exact dose times recorded."
                        )
                    }
                    items(preview.report.weeks, key = { week -> week.index }) { week ->
                        ReportWeekCard(
                            medicine = preview.medicine,
                            report = preview.report,
                            week = week,
                            accent = accent
                        )
                    }
                    if (preview.report.hasForeignUnits) {
                        item(key = "foreignUnits") {
                            ReportNote(
                                "Some older doses use a different unit. Charts, averages and peak-day values use ${preview.report.unit}; day details keep the unit originally logged."
                            )
                        }
                    }
                    item(key = "reportDayExcluded") {
                        ReportNote("The report ends yesterday, so doses taken today are not included.")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportPreviewTopBar(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            AppIcons.Chart,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "Report preview",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss) {
            Icon(
                AppIcons.Close,
                contentDescription = "Close preview",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportPreviewHeader(
    medicine: Medicine,
    report: MedicineReport,
    accent: MedicineAccent
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MedicineAvatar(name = medicine.name, accent = accent, size = 52)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    medicine.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    reportPeriodLine(report),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Generated ${report.generatedAt.format(REPORT_GENERATED_AT)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun ReportSummaryGrid(report: MedicineReport, accent: MedicineAccent) {
    SectionTitle(title = "Summary")
    Spacer(Modifier.height(8.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        SummaryTile(
            value = formatUnitTotals(report.totals, report.unit),
            label = "Total taken",
            accent = accent,
            modifier = Modifier.weight(1f)
        )
        SummaryTile(
            value = report.doseCount.toString(),
            label = if (report.doseCount == 1) "Dose logged" else "Doses logged",
            accent = accent,
            modifier = Modifier.weight(1f)
        )
        SummaryTile(
            value = "${formatReportAverage(report.averagePerDay)} ${report.unit}",
            label = "Average per day",
            accent = accent,
            modifier = Modifier.weight(1f)
        )
        SummaryTile(
            value = "${report.daysWithDoses} of ${report.dayCount}",
            label = "Days with intake",
            accent = accent,
            modifier = Modifier.weight(1f)
        )
        SummaryTile(
            value = report.peakDay?.let { "${formatAmount(it.primaryTotal)} ${report.unit}" } ?: "None",
            label = report.peakDay?.let { "Peak day · ${it.date.format(REPORT_DAY_MONTH)}" } ?: "Peak day",
            accent = accent,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryTile(
    value: String,
    label: String,
    accent: MedicineAccent,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = accent.solid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportOverviewCard(
    medicine: Medicine,
    report: MedicineReport,
    accent: MedicineAccent
) {
    val values = remember(report) { report.days.map { it.primaryTotal } }
    val labels = remember(report) { overviewLabels(report.days) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SectionTitle(
                title = "Daily totals",
                subtitle = "Total ${formatUnitTotals(report.totals, report.unit)} · avg ${formatReportAverage(report.averagePerDay)} ${report.unit}/day"
            )
            Spacer(Modifier.height(14.dp))
            BarChart(
                values = values,
                labels = labels,
                barColor = accent.solid,
                highlightIndex = -1,
                showValues = report.dayCount <= 7,
                averageValue = report.averagePerDay.takeIf { it > 0 },
                maxLine = medicine.dailyMaxAmount,
                emptyText = "No doses recorded in this period",
                modifier = Modifier.fillMaxWidth().height(174.dp)
            )
            medicine.dailyMaxAmount?.let { maxAmount ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "Daily max line: ${formatAmount(maxAmount)} ${medicine.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TimeOfDayCard(report: MedicineReport, accent: MedicineAccent) {
    val totalCount = report.timeOfDayCounts.sum()
    val maxCount = report.timeOfDayCounts.maxOrNull() ?: 0

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            SectionTitle(
                title = "Time of day",
                subtitle = if (totalCount == 0) "No dose times recorded in this period."
                else "$totalCount logged dose times grouped into six-hour blocks."
            )
            Spacer(Modifier.height(12.dp))
            TIME_BLOCKS.forEachIndexed { index, block ->
                TimeOfDayRow(
                    label = block.label,
                    range = block.range,
                    count = report.timeOfDayCounts.getOrElse(index) { 0 },
                    maxCount = maxCount,
                    accent = accent
                )
                if (index != TIME_BLOCKS.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun TimeOfDayRow(
    label: String,
    range: String,
    count: Int,
    maxCount: Int,
    accent: MedicineAccent
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(88.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                range,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val widthFraction = if (maxCount == 0) 0f else count / maxCount.toFloat()
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(accent.solid)
            )
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (count > 0) accent.solid else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun ReportWeekCard(
    medicine: Medicine,
    report: MedicineReport,
    week: ReportWeek,
    accent: MedicineAccent
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Week ${week.index}", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        buildString {
                            append(weekRangeLine(week))
                            if (week.isPartial) append(" · partial week")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    formatUnitTotals(week.totals, report.unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent.solid
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WeekMetric(
                    label = "Doses",
                    value = week.doseCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                WeekMetric(
                    label = "Avg/day",
                    value = "${formatReportAverage(week.averagePerDay)} ${report.unit}",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(14.dp))
            BarChart(
                values = week.days.map { it.primaryTotal },
                labels = week.days.map { weekdayLabel(it.date) },
                barColor = accent.solid,
                highlightIndex = -1,
                showValues = true,
                averageValue = week.averagePerDay.takeIf { it > 0 },
                maxLine = medicine.dailyMaxAmount,
                emptyText = "No doses this week",
                modifier = Modifier.fillMaxWidth().height(128.dp)
            )
            Spacer(Modifier.height(12.dp))
            week.days.forEachIndexed { index, day ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    )
                }
                ReportDayUsageRow(day = day, primaryUnit = report.unit)
            }
        }
    }
}

@Composable
private fun WeekMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReportDayUsageRow(day: ReportDay, primaryUnit: String) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                day.date.format(REPORT_WEEKDAY_DAY_MONTH),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (day.doses.isEmpty()) "No doses" else formatUnitTotals(unitTotals(day.doses), primaryUnit),
                style = MaterialTheme.typography.labelLarge,
                color = if (day.doses.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(6.dp))
        if (day.doses.isEmpty()) {
            Text(
                "No medicine taken on this day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                day.doses.forEach { dose ->
                    DoseChip(dose)
                }
            }
        }
    }
}

@Composable
private fun DoseChip(dose: DoseLog) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            "${dose.timestamp.toTimeString()} · ${formatAmount(dose.amount)} ${dose.unit}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 190.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle?.let {
            Spacer(Modifier.height(3.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReportNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

private data class TimeBlock(val label: String, val range: String)

private val TIME_BLOCKS = listOf(
    TimeBlock("Night", "00:00-05:59"),
    TimeBlock("Morning", "06:00-11:59"),
    TimeBlock("Afternoon", "12:00-17:59"),
    TimeBlock("Evening", "18:00-23:59")
)

private val REPORT_DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val REPORT_DAY_FULL_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val REPORT_FULL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val REPORT_GENERATED_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
private val REPORT_WEEKDAY_DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private fun reportPeriodLine(report: MedicineReport): String {
    val range = if (report.startDate.year == report.endDate.year) {
        "${report.startDate.format(REPORT_DAY_FULL_MONTH)} - ${report.endDate.format(REPORT_FULL_DATE)}"
    } else {
        "${report.startDate.format(REPORT_FULL_DATE)} - ${report.endDate.format(REPORT_FULL_DATE)}"
    }
    return "$range · ${report.dayCount} days"
}

private fun weekRangeLine(week: ReportWeek): String {
    val first = week.days.firstOrNull()?.date ?: week.weekStart
    val last = week.days.lastOrNull()?.date ?: week.weekStart
    return when {
        first == last -> first.format(REPORT_FULL_DATE)
        first.year == last.year && first.month == last.month ->
            "${first.dayOfMonth}-${last.format(REPORT_FULL_DATE)}"
        first.year == last.year ->
            "${first.format(REPORT_DAY_MONTH)} - ${last.format(REPORT_FULL_DATE)}"
        else ->
            "${first.format(REPORT_FULL_DATE)} - ${last.format(REPORT_FULL_DATE)}"
    }
}

private fun overviewLabels(days: List<ReportDay>): List<String> {
    if (days.isEmpty()) return emptyList()
    val step = max(1, (days.size + 3) / 6)
    var labelledMonth = 0
    return days.mapIndexed { index, day ->
        val last = index == days.lastIndex
        if (index % step != 0 && !(last && index % step >= 2)) {
            ""
        } else {
            val withMonth = day.date.monthValue != labelledMonth
            labelledMonth = day.date.monthValue
            if (withMonth) day.date.format(REPORT_DAY_MONTH) else day.date.dayOfMonth.toString()
        }
    }
}

private fun weekdayLabel(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())

private fun unitTotals(doses: List<DoseLog>): Map<String, Double> =
    doses.groupBy { it.unit }.mapValues { (_, unitDoses) -> unitDoses.sumOf { it.amount } }

private fun formatReportAverage(value: Double): String {
    if (value <= 0) return formatAmount(0.0)
    return BigDecimal.valueOf(value)
        .round(MathContext(3))
        .stripTrailingZeros()
        .toPlainString()
}

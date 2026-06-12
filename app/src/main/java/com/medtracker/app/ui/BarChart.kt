package com.medtracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medtracker.app.data.formatAmount

/**
 * Dependency-free bar chart. One bar per entry in [values]; [labels] are drawn
 * under the bars (empty string = no label). Bars get a soft vertical gradient
 * and rounded tops; the bar at [highlightIndex] (today) is drawn in full color
 * with its label emphasized. A dashed line marks [averageValue], or the period
 * average when no override is provided.
 */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int = values.lastIndex,
    showValues: Boolean = false,
    averageValue: Double? = null,
    emptyText: String = "No doses in this period"
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val highlightLabelStyle =
        TextStyle(fontSize = 10.sp, color = barColor, fontWeight = FontWeight.SemiBold)
    val valueStyle = TextStyle(fontSize = 9.sp, color = labelColor)
    val emptyStyle = TextStyle(fontSize = 12.sp, color = labelColor)

    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val labelHeight = 16.dp.toPx()
        val valueHeight = if (showValues) 14.dp.toPx() else 0f
        val chartBottom = size.height - labelHeight
        val chartHeight = chartBottom - valueHeight
        val slot = size.width / values.size
        val barWidth = slot * 0.58f
        val maxValue = (values.maxOrNull() ?: 0.0).takeIf { it > 0 }

        drawLine(gridColor, Offset(0f, chartBottom), Offset(size.width, chartBottom), 1.dp.toPx())

        if (maxValue == null) {
            val layout = textMeasurer.measure(AnnotatedString(emptyText), emptyStyle)
            drawText(
                layout,
                topLeft = Offset(
                    (size.width - layout.size.width) / 2f,
                    valueHeight + (chartHeight - layout.size.height) / 2f
                )
            )
            return@Canvas
        }

        // Dashed average line across the period.
        val average = averageValue ?: values.sum() / values.size
        if (average > 0) {
            val y = chartBottom - (average / maxValue * chartHeight).toFloat()
            drawLine(
                color = barColor.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx()))
            )
        }

        values.forEachIndexed { index, value ->
            val left = index * slot + (slot - barWidth) / 2f
            val highlighted = index == highlightIndex

            if (value > 0) {
                val barHeight =
                    (value / maxValue * chartHeight).toFloat().coerceAtLeast(3.dp.toPx())
                val top = chartBottom - barHeight
                val corner = CornerRadius(barWidth * 0.32f)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            Rect(left, top, left + barWidth, chartBottom),
                            topLeft = corner,
                            topRight = corner
                        )
                    )
                }
                val brush = if (highlighted) {
                    Brush.verticalGradient(
                        colors = listOf(barColor, barColor.copy(alpha = 0.8f)),
                        startY = top,
                        endY = chartBottom
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(barColor.copy(alpha = 0.45f), barColor.copy(alpha = 0.25f)),
                        startY = top,
                        endY = chartBottom
                    )
                }
                drawPath(path, brush)

                if (showValues) {
                    val layout = textMeasurer.measure(AnnotatedString(formatAmount(value)), valueStyle)
                    drawText(
                        layout,
                        topLeft = Offset(
                            index * slot + (slot - layout.size.width) / 2f,
                            top - layout.size.height - 2.dp.toPx()
                        )
                    )
                }
            }

            val label = labels.getOrNull(index).orEmpty()
            if (label.isNotEmpty()) {
                val layout = textMeasurer.measure(
                    AnnotatedString(label),
                    if (highlighted) highlightLabelStyle else labelStyle
                )
                drawText(
                    layout,
                    topLeft = Offset(
                        index * slot + (slot - layout.size.width) / 2f,
                        chartBottom + 2.dp.toPx()
                    )
                )
            }
        }
    }
}

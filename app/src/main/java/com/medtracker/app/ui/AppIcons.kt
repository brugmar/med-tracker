package com.medtracker.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn glyphs that echo the launcher's capsule pill, so the app keeps a
 * single visual motif without pulling in the extended icon library.
 * Icons are drawn in black; [androidx.compose.material3.Icon] tints them.
 */
object AppIcons {

    /** Vertical capsule with a solid lower half — the brand mark. */
    val Pill: ImageVector by lazy {
        ImageVector.Builder(
            name = "Pill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(7f, 9f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 17f, y1 = 9f)
                lineTo(17f, 15f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7f, y1 = 15f)
                close()
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 12f)
                lineTo(17f, 12f)
                lineTo(17f, 15f)
                arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 7f, y1 = 15f)
                close()
            }
        }.build()
    }

    /** Arrow rising out of a tray — share/export. */
    val Export: ImageVector by lazy {
        trayArrowIcon(name = "Export", arrowUp = true)
    }

    /** Arrow dropping into a tray — restore/import. */
    val Import: ImageVector by lazy {
        trayArrowIcon(name = "Import", arrowUp = false)
    }

    private fun trayArrowIcon(name: String, arrowUp: Boolean): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Tray.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(4.5f, 15f)
                lineTo(4.5f, 18.5f)
                lineTo(19.5f, 18.5f)
                lineTo(19.5f, 15f)
            }
            // Arrow shaft.
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(12f, 4f)
                lineTo(12f, 14f)
            }
            // Arrow head, pointing up (export) or down (import).
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                if (arrowUp) {
                    moveTo(8.5f, 7.5f)
                    lineTo(12f, 4f)
                    lineTo(15.5f, 7.5f)
                } else {
                    moveTo(8.5f, 10.5f)
                    lineTo(12f, 14f)
                    lineTo(15.5f, 10.5f)
                }
            }
        }.build()

    /** Three rounded bars of varying height. */
    val Chart: ImageVector by lazy {
        ImageVector.Builder(
            name = "Chart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            listOf(
                6f to 12.6f,
                12f to 7.4f,
                18f to 10.2f
            ).forEach { (x, top) ->
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 3.4f,
                    strokeLineCap = StrokeCap.Round
                ) {
                    moveTo(x, top)
                    lineTo(x, 18.3f)
                }
            }
        }.build()
    }

    /** Three slider tracks with offset handles — the settings/adjust mark. */
    val Settings: ImageVector by lazy {
        ImageVector.Builder(
            name = "Settings",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Horizontal tracks.
            listOf(7f, 12f, 17f).forEach { y ->
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round
                ) {
                    moveTo(4f, y)
                    lineTo(20f, y)
                }
            }
            // Handles riding the tracks, alternating sides; a filled disc reads
            // as a slider thumb where it sits on the line.
            listOf(15f to 7f, 9f to 12f, 15f to 17f).forEach { (cx, cy) ->
                val r = 2.6f
                path(fill = SolidColor(Color.Black)) {
                    moveTo(cx - r, cy)
                    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
                    arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
                    close()
                }
            }
        }.build()
    }
}

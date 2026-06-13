package com.medtracker.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.medtracker.app.data.Medicine
import java.util.Locale

// Brand palette: teal seed with teal-cast neutrals and a warm honey tertiary.

internal val LightColors = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1F2),
    onPrimaryContainer = Color(0xFF002020),
    inversePrimary = Color(0xFF80D4D6),
    secondary = Color(0xFF4A6363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E8),
    onSecondaryContainer = Color(0xFF051F20),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA6),
    onTertiaryContainer = Color(0xFF261A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAFDFC),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFFAFDFC),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E4),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceTint = Color(0xFF00696B),
    inverseSurface = Color(0xFF2B3231),
    inverseOnSurface = Color(0xFFECF2F1),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC9C8),
    surfaceBright = Color(0xFFFAFDFC),
    surfaceDim = Color(0xFFDAE4E3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F4),
    surfaceContainer = Color(0xFFEAF0EF),
    surfaceContainerHigh = Color(0xFFE4EAE9),
    surfaceContainerHighest = Color(0xFFDEE4E4),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF80D4D6),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFF9CF1F2),
    inversePrimary = Color(0xFF00696B),
    secondary = Color(0xFFB0CCCC),
    onSecondary = Color(0xFF1B3435),
    secondaryContainer = Color(0xFF324B4B),
    onSecondaryContainer = Color(0xFFCCE8E8),
    tertiary = Color(0xFFEDC148),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1514),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1514),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC9C8),
    surfaceTint = Color(0xFF80D4D6),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3231),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4949),
    surfaceBright = Color(0xFF343A3A),
    surfaceDim = Color(0xFF0E1514),
    surfaceContainerLowest = Color(0xFF090F0F),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF303636),
)

/**
 * Accent assigned to a single medicine: [solid] for emphasis text and chart bars,
 * [container]/[onContainer] for the monogram avatar and badges.
 */
@Immutable
data class MedicineAccent(
    val solid: Color,
    val container: Color,
    val onContainer: Color
)

@Immutable
data class MedicineColorOption(
    val key: String,
    val label: String,
    val light: MedicineAccent,
    val dark: MedicineAccent
)

val MedicineColorOptions = listOf(
    MedicineColorOption(
        key = "teal",
        label = "Teal",
        light = MedicineAccent(Color(0xFF00696B), Color(0xFF9CF1F2), Color(0xFF002020)),
        dark = MedicineAccent(Color(0xFF80D4D6), Color(0xFF004F51), Color(0xFF9CF1F2))
    ),
    MedicineColorOption(
        key = "indigo",
        label = "Indigo",
        light = MedicineAccent(Color(0xFF4E57A9), Color(0xFFDFE0FF), Color(0xFF030865)),
        dark = MedicineAccent(Color(0xFFBDC2FF), Color(0xFF363F90), Color(0xFFDFE0FF))
    ),
    MedicineColorOption(
        key = "honey",
        label = "Honey",
        light = MedicineAccent(Color(0xFF7A5900), Color(0xFFFFDEA6), Color(0xFF261A00)),
        dark = MedicineAccent(Color(0xFFEDC148), Color(0xFF5C4200), Color(0xFFFFDEA6))
    ),
    MedicineColorOption(
        key = "coral",
        label = "Coral",
        light = MedicineAccent(Color(0xFF9C413B), Color(0xFFFFDAD6), Color(0xFF410005)),
        dark = MedicineAccent(Color(0xFFFFB3AC), Color(0xFF7D2B26), Color(0xFFFFDAD6))
    ),
    MedicineColorOption(
        key = "lavender",
        label = "Lavender",
        light = MedicineAccent(Color(0xFF6D4EA2), Color(0xFFEBDCFF), Color(0xFF270057)),
        dark = MedicineAccent(Color(0xFFD4BBFF), Color(0xFF553788), Color(0xFFEBDCFF))
    ),
    MedicineColorOption(
        key = "sage",
        label = "Sage",
        light = MedicineAccent(Color(0xFF3E6837), Color(0xFFBFF0B1), Color(0xFF002204)),
        dark = MedicineAccent(Color(0xFFA3D399), Color(0xFF275022), Color(0xFFBFF0B1))
    )
)

@Immutable
data class MedicineRgb(val red: Int, val green: Int, val blue: Int)

/** Stable accent for a medicine, picked by id so it never changes for that medicine. */
@Composable
fun medicineAccent(id: Long): MedicineAccent {
    return medicineAccentForOption(defaultMedicineColorKey(id))
}

/** User-selected accent, falling back to the legacy id-based accent when unset. */
@Composable
fun medicineAccent(medicine: Medicine): MedicineAccent {
    return medicineAccentForOption(medicine.colorKey ?: defaultMedicineColorKey(medicine.id))
}

@Composable
fun medicineAccentForOption(key: String): MedicineAccent {
    val dark = LocalDarkTheme.current
    parseMedicineColorHex(key)?.let { rgb ->
        return customMedicineAccent(Color(rgb.red, rgb.green, rgb.blue), dark)
    }
    val fallback = MedicineColorOptions.first()
    val option = MedicineColorOptions.firstOrNull { it.key == key } ?: fallback
    return if (dark) option.dark else option.light
}

fun defaultMedicineColorKey(id: Long): String {
    val index = ((id % MedicineColorOptions.size) + MedicineColorOptions.size) % MedicineColorOptions.size
    return MedicineColorOptions[index.toInt()].key
}

fun defaultMedicineColorHex(id: Long): String =
    medicineColorValueToHex(defaultMedicineColorKey(id))

fun medicineColorValueToHex(value: String): String {
    normalizeMedicineColorHex(value)?.let { return it }
    val option = MedicineColorOptions.firstOrNull { it.key == value } ?: MedicineColorOptions.first()
    return option.light.solid.toHex()
}

fun medicineColorHex(red: Int, green: Int, blue: Int): String =
    "#%02X%02X%02X".format(
        Locale.ROOT,
        red.coerceIn(0, 255),
        green.coerceIn(0, 255),
        blue.coerceIn(0, 255)
    )

fun normalizeMedicineColorHex(value: String): String? {
    val hex = value.trim().removePrefix("#")
    if (!Regex("^[0-9A-Fa-f]{6}$").matches(hex)) return null
    return "#${hex.uppercase(Locale.ROOT)}"
}

fun parseMedicineColorHex(value: String): MedicineRgb? {
    val hex = normalizeMedicineColorHex(value)?.removePrefix("#") ?: return null
    return MedicineRgb(
        red = hex.substring(0, 2).toInt(16),
        green = hex.substring(2, 4).toInt(16),
        blue = hex.substring(4, 6).toInt(16)
    )
}

private fun Color.toHex(): String =
    medicineColorHex((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private fun customMedicineAccent(base: Color, dark: Boolean): MedicineAccent {
    val solid = when {
        dark && base.luminance() < 0.35f -> blend(base, Color.White, 0.35f)
        !dark && base.luminance() > 0.55f -> blend(base, Color.Black, 0.28f)
        else -> base
    }
    val container = if (dark) blend(base, Color.Black, 0.55f) else blend(base, Color.White, 0.78f)
    val onContainer = if (container.luminance() > 0.5f) Color(0xFF101414) else Color.White
    return MedicineAccent(solid = solid, container = container, onContainer = onContainer)
}

private fun blend(from: Color, to: Color, fraction: Float): Color {
    val clamped = fraction.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * clamped,
        green = from.green + (to.green - from.green) * clamped,
        blue = from.blue + (to.blue - from.blue) * clamped,
        alpha = 1f
    )
}

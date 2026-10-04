package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Base palette: the slate of a manual scoreboard, chalk numerals, floodlight saffron,
// and ball-leather red kept for wickets only.
val Slate950 = Color(0xFF0C1120)
val Slate900 = Color(0xFF111831)
val Slate800 = Color(0xFF1C2541)
val Slate700 = Color(0xFF2A3558)
val Chalk = Color(0xFFF3F5FB)
val Saffron = Color(0xFFF2A20C)
val SaffronDeep = Color(0xFF9A5B00)
val Leather = Color(0xFFD3364A)
val Indigo = Color(0xFF3B4FD8)
val IndigoSoft = Color(0xFFAAB6FF)
val Mist = Color(0xFFF2F4F9)
val FourBlue = Color(0xFF2A7DE1)
val DotGrey = Color(0xFF8B93A7)
val ExtraViolet = Color(0xFF8A5CD1)

/** Colours outside the Material scheme: scoreboard panel, ball outcomes, results. */
@Immutable
data class CricPalette(
    val panel: Color,
    val panelHigh: Color,
    val onPanel: Color,
    val onPanelMuted: Color,
    val accent: Color,
    val accentText: Color,
    val wicket: Color,
    val four: Color,
    val six: Color,
    val dot: Color,
    val extra: Color,
    val win: Color,
    val keyNeutral: Color,
    val onKeyNeutral: Color,
    val isDark: Boolean
)

val LightCricPalette = CricPalette(
    panel = Slate800,
    panelHigh = Slate700,
    onPanel = Chalk,
    onPanelMuted = Chalk.copy(alpha = 0.68f),
    accent = Saffron,
    accentText = SaffronDeep,
    wicket = Leather,
    four = FourBlue,
    six = Saffron,
    dot = DotGrey,
    extra = ExtraViolet,
    win = Color(0xFF17845A),
    keyNeutral = Color(0xFFE6E9F2),
    onKeyNeutral = Slate900,
    isDark = false
)

val DarkCricPalette = CricPalette(
    panel = Color(0xFF18203A),
    panelHigh = Color(0xFF253055),
    onPanel = Chalk,
    onPanelMuted = Chalk.copy(alpha = 0.66f),
    accent = Saffron,
    accentText = Saffron,
    wicket = Color(0xFFE5576A),
    four = Color(0xFF4D95F0),
    six = Saffron,
    dot = Color(0xFF6E7790),
    extra = Color(0xFFA27EE0),
    win = Color(0xFF3CC48D),
    keyNeutral = Color(0xFF222B45),
    onKeyNeutral = Chalk,
    isDark = true
)

val LocalCricPalette = staticCompositionLocalOf { LightCricPalette }

/** Distinct team colours offered in the team editor. */
val TeamColorOptions = listOf(
    "#3B4FD8", "#0E8FA3", "#E58A00", "#8E3FD1", "#D3364A", "#C2410C",
    "#2563EB", "#0F766E", "#B45309", "#BE185D", "#4D7C0F", "#475569"
)

fun parseTeamColor(hex: String?, fallbackSeed: Long = 0): Color {
    if (hex != null) {
        try {
            return Color(android.graphics.Color.parseColor(hex))
        } catch (_: Exception) {
            // fall through
        }
    }
    val pick = TeamColorOptions[((fallbackSeed % TeamColorOptions.size).toInt() + TeamColorOptions.size) % TeamColorOptions.size]
    return Color(android.graphics.Color.parseColor(pick))
}

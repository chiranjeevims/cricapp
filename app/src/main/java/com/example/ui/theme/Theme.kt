package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val label: String) {
    SYSTEM("Match my phone"),
    LIGHT("Light"),
    DARK("Dark")
}

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E5FF),
    onPrimaryContainer = Color(0xFF14207A),
    secondary = SaffronDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6BF),
    onSecondaryContainer = Color(0xFF3D2500),
    tertiary = Leather,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDADD),
    onTertiaryContainer = Color(0xFF5C0014),
    background = Mist,
    onBackground = Color(0xFF151A2D),
    surface = Color.White,
    onSurface = Color(0xFF151A2D),
    surfaceVariant = Color(0xFFE8EBF4),
    onSurfaceVariant = Color(0xFF525B73),
    outline = Color(0xFFC3C8D8),
    outlineVariant = Color(0xFFDCE0EA),
    error = Leather,
    onError = Color.White,
    inverseSurface = Slate800,
    inverseOnSurface = Chalk,
    inversePrimary = IndigoSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F8FC),
    surfaceContainer = Color(0xFFF0F2F8),
    surfaceContainerHigh = Color(0xFFEAEDF5),
    surfaceContainerHighest = Color(0xFFE3E7F0)
)

private val DarkColors = darkColorScheme(
    primary = IndigoSoft,
    onPrimary = Color(0xFF101C6B),
    primaryContainer = Color(0xFF2B3AA8),
    onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Saffron,
    onSecondary = Color(0xFF2E1B00),
    secondaryContainer = Color(0xFF5A3A00),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFFF8A97),
    onTertiary = Color(0xFF4A0010),
    tertiaryContainer = Color(0xFF7A1C2C),
    onTertiaryContainer = Color(0xFFFFDADD),
    background = Slate950,
    onBackground = Color(0xFFE4E7F2),
    surface = Color(0xFF131A2D),
    onSurface = Color(0xFFE4E7F2),
    surfaceVariant = Color(0xFF222A41),
    onSurfaceVariant = Color(0xFFA9B0C6),
    outline = Color(0xFF3A4462),
    outlineVariant = Color(0xFF2A3350),
    error = Color(0xFFFF8A97),
    onError = Color(0xFF4A0010),
    inverseSurface = Chalk,
    inverseOnSurface = Slate900,
    inversePrimary = Indigo,
    surfaceContainerLowest = Color(0xFF0A0F1C),
    surfaceContainerLow = Color(0xFF111829),
    surfaceContainer = Color(0xFF161D31),
    surfaceContainerHigh = Color(0xFF1C2439),
    surfaceContainerHighest = Color(0xFF232C43)
)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CricScoreTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = isAppInDarkTheme(themeMode)
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography
    ) {
        CompositionLocalProvider(LocalCricPalette provides if (dark) DarkCricPalette else LightCricPalette) {
            content()
        }
    }
}

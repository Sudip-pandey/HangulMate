package com.hangulmate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Indigo700,
    onPrimary = White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo900,
    secondary = TaegeukRed,
    onSecondary = White,
    secondaryContainer = Color(0xFFFDE8E8),
    onSecondaryContainer = Color(0xFF7F1D1D),
    tertiary = TaegeukBlue,
    onTertiary = White,
    background = Neutral50,
    onBackground = Neutral900,
    surface = White,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral700,
    outline = Neutral200,
    error = TaegeukRed,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Indigo900,
    primaryContainer = Indigo700,
    onPrimaryContainer = Indigo100,
    secondary = TaegeukRedDark,
    onSecondary = Neutral900,
    secondaryContainer = Color(0xFF7F1D1D),
    onSecondaryContainer = Color(0xFFFDE8E8),
    tertiary = TaegeukBlueLight,
    onTertiary = Neutral900,
    background = Neutral900,
    onBackground = Neutral50,
    surface = Neutral800,
    onSurface = Neutral50,
    surfaceVariant = Neutral700,
    onSurfaceVariant = Neutral200,
    outline = Neutral700,
    error = TaegeukRedDark,
)

/**
 * [themeMode] comes from settings: "system" (default), "light" or "dark".
 */
@Composable
fun HangulMateTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = HangulMateTypography,
        shapes = HangulMateShapes,
        content = content,
    )
}

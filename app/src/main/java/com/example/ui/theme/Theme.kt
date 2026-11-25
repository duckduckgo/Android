package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = DaxBlue50,
    onPrimary = Color.White,
    primaryContainer = DaxBlueContainerLight,
    onPrimaryContainer = DaxBlue50,
    secondary = DaxCoral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFDECE7),
    onSecondaryContainer = DaxCoral,
    tertiary = DaxGreen50,
    onTertiary = Color.White,
    tertiaryContainer = DaxGreenContainerLight,
    onTertiaryContainer = DaxGreen50,
    background = DaxLightBackground,
    onBackground = DaxLightTextPrimary,
    surface = DaxLightSurface,
    onSurface = DaxLightTextPrimary,
    surfaceVariant = DaxLightSurfaceVariant,
    onSurfaceVariant = DaxLightTextSecondary,
    surfaceContainer = DaxLightSurface,
    surfaceContainerHigh = DaxLightSurfaceVariant,
    surfaceContainerHighest = DaxLightContainer,
    outline = DaxLightLines,
    outlineVariant = DaxLightLines,
    error = DaxAlertRedLight,
    onError = Color.White,
    errorContainer = DaxAlertRedContainerLight,
    onErrorContainer = DaxAlertRedLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = DaxBlue30,
    onPrimary = Color(0xFF0A1B47),
    primaryContainer = DaxBlueContainerDark,
    onPrimaryContainer = DaxBlue30,
    secondary = DaxCoralBright,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF472118),
    onSecondaryContainer = Color(0xFFFFB4A1),
    tertiary = DaxGreen50,
    onTertiary = Color.White,
    tertiaryContainer = DaxGreenContainerDark,
    onTertiaryContainer = Color(0xFF81E6A1),
    background = DaxDarkBackground,
    onBackground = DaxDarkTextPrimary,
    surface = DaxDarkSurface,
    onSurface = DaxDarkTextPrimary,
    surfaceVariant = DaxDarkSurfaceVariant,
    onSurfaceVariant = DaxDarkTextSecondary,
    surfaceContainer = DaxDarkSurface,
    surfaceContainerHigh = DaxDarkSurfaceVariant,
    surfaceContainerHighest = DaxDarkContainer,
    outline = DaxDarkLines,
    outlineVariant = DaxDarkLines,
    error = DaxAlertRedDark,
    onError = Color(0xFF410002),
    errorContainer = DaxAlertRedContainerDark,
    onErrorContainer = DaxAlertRedDark,
)

val DaxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun DuckDuckGoBrowserTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        typography = DaxTypography,
        shapes = DaxShapes,
        content = content,
    )
}

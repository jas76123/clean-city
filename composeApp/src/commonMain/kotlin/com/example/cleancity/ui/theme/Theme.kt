package com.example.cleancity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CleanCityColorScheme = lightColorScheme(
    primary = Green600,
    onPrimary = Gray50,
    primaryContainer = Green100,
    onPrimaryContainer = Green900,
    secondary = Accent,
    onSecondary = Green900,
    secondaryContainer = Green50,
    onSecondaryContainer = Green800,
    tertiary = Purple,
    onTertiary = Gray50,
    error = Red,
    onError = Gray50,
    errorContainer = RedLight,
    background = Gray50,
    onBackground = Gray900,
    surface = Gray50,
    onSurface = Gray900,
    surfaceVariant = Gray100,
    onSurfaceVariant = Gray600,
    outline = Gray300,
    outlineVariant = Gray200,
)

@Composable
fun CleanCityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CleanCityColorScheme,
        typography = CleanCityTypography,
        content = content
    )
}

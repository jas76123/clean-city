package com.example.cleancity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun CleanCityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green700,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = Accent,
            onSecondary = Green900,
            background = Gray50,
            onBackground = Gray900,
            surface = androidx.compose.ui.graphics.Color.White,
            onSurface = Gray900,
            surfaceVariant = Gray100,
            onSurfaceVariant = Gray600,
            outline = Gray300,
            outlineVariant = Gray200,
            error = Red,
            onError = androidx.compose.ui.graphics.Color.White,
        ),
        typography = cleanCityTypography(),
        shapes = CleanCityShapes,
        content = content,
    )
}

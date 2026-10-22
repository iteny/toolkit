package com.example.toolkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    secondary = AppleBlueLight,
    background = DeviceBackground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    outline = Line,
)

/**
 * The design is a fixed, bright "liquid glass" look, so the app intentionally
 * does not follow the system dark theme or dynamic color.
 */
@Composable
fun ToolkitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content,
    )
}

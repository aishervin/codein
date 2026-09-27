package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CodeinColorScheme = darkColorScheme(
    primary = NeonOrange,
    onPrimary = Color.Black,
    primaryContainer = NeonOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = NeonOrangeSoft,
    onSecondary = Color.Black,
    background = InkBlack,
    onBackground = TextPrimary,
    surface = SurfaceBlack,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = BorderBlack,
    error = AccentRed,
    onError = Color.White
)

@Composable
fun CodeinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CodeinColorScheme,
        typography = Typography,
        content = content
    )
}

package com.pricelens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E7FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF00695C),
    secondaryContainer = Color(0xFFB2DFDB),
    background = Color(0xFFF7F9FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF2F7),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF06233F),
    primaryContainer = Color(0xFF1B3A5C),
    onPrimaryContainer = Color(0xFFD7E7FF),
    secondary = Color(0xFF80CBC4),
    secondaryContainer = Color(0xFF004D40),
    background = Color(0xFF0F1216),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF1E252E),
    error = Color(0xFFF2B8B5)
)

@Composable
fun PriceLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

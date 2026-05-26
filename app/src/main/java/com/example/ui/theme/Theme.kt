package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val HighDensityColorScheme = lightColorScheme(
    primary = Color(0xFF21005D),        // Deep Purple
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF), // Pastel Purple Container
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF381E72),      // Mid Purple
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8), // Saturated Pastel Purple
    onSecondaryContainer = Color(0xFF1D1B20),
    tertiary = Color(0xFF00A344),       // Accent Green
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFEF7FF),     // Clean Light Canvas
    onBackground = Color(0xFF1D1B20),   // High Contrast Charcoal
    surface = Color(0xFFF3EDF7),        // Soft light background
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFEADDFF),
    onSurfaceVariant = Color(0xFF49454F), // Medium Grey Text
    outline = Color(0xFFCAC4D0),        // Borders
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Force Light/Pastel High-Density Theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = HighDensityColorScheme,
        typography = Typography,
        content = content
    )
}

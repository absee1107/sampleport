package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = HighDensityPrimary,
    secondary = HighDensitySecondary,
    tertiary = HighDensityTertiary,
    background = Color(0xFF2B1D16), // Dark leather espresso brown
    surface = Color(0xFF3B2A1E),     // Warm dark wood card drawer
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFFAEDE0),
    onSurface = Color(0xFFFAEDE0)
)

private val LightColorScheme = lightColorScheme(
    primary = HighDensityPrimary,
    secondary = HighDensitySecondary,
    tertiary = HighDensityTertiary,
    background = HighDensityBackground,
    surface = HighDensitySurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = HighDensityOnBackground,
    onSurface = HighDensityOnSurface
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to enforce our beautiful cohesive finance colors
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

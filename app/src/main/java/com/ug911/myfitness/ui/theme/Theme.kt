package com.ug911.myfitness.ui.theme

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

private val Green = Color(0xFF1B5E4B)
private val GreenLight = Color(0xFF7FD1B4)
private val Sand = Color(0xFFF6F3EC)
private val Ink = Color(0xFF14201C)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenLight,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF4F6D62),
    background = Sand,
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val DarkColors = darkColorScheme(
    primary = GreenLight,
    onPrimary = Ink,
    primaryContainer = Green,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF9CC3B5),
    background = Color(0xFF101614),
    surface = Color(0xFF171F1C),
)

/** Colours used for the history heatmap and progress rows, light and dark. */
object TrackingColors {
    val empty: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val partial: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val good: Color @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    val full: Color @Composable get() = MaterialTheme.colorScheme.primary
    val warn: Color @Composable get() = Color(0xFFB3261E)
}

@Composable
fun MyFitnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

package com.ug911.myfitness.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Palette.section(com.ug911.myfitness.data.model.DaySection.NIGHT, dark = false),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = Palette.LightPlane,
    onBackground = Palette.LightInk,
    surface = Palette.LightCard,
    onSurface = Palette.LightInk,
    surfaceVariant = Palette.LightSurface,
    onSurfaceVariant = Palette.LightInkSecondary,
    outline = Palette.Muted,
    outlineVariant = Palette.LightGrid,
    error = Palette.Critical,
)

private val DarkColors = darkColorScheme(
    primary = Palette.section(com.ug911.myfitness.data.model.DaySection.NIGHT, dark = true),
    onPrimary = Palette.DarkSurface,
    background = Palette.DarkPlane,
    onBackground = Palette.DarkInk,
    surface = Palette.DarkCard,
    onSurface = Palette.DarkInk,
    surfaceVariant = Palette.DarkSurface,
    onSurfaceVariant = Palette.DarkInkSecondary,
    outline = Palette.Muted,
    outlineVariant = Palette.DarkGrid,
    error = Palette.Critical,
)

/** Generous corners; the app should read soft and modern rather than boxy. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * The system sans throughout, including the big numbers - a display face on a hero
 * figure reads as decoration. Weight and size carry the hierarchy instead.
 */
private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.4.sp,
        ),
    )
}

/**
 * Deliberately not Material You: the section hues are a validated set, and letting the
 * wallpaper repaint them would break the contrast and colour-blind separation they were
 * checked for.
 */
@Composable
fun MyFitnessTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}

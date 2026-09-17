package com.ug911.myfitness.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.ug911.myfitness.data.model.DaySection

/** True when the app is rendering its dark palette; set by [MyFitnessTheme]. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * The app's colour system.
 *
 * Both modes were validated with the data-viz palette checker against their own
 * surfaces rather than by eye: the seven section hues clear the lightness band, the
 * chroma floor, adjacent-pair separation under protanopia/deuteranopia/tritanopia
 * (worst adjacent dE 9.8 light / 11.3 dark against a target of 8), the normal-vision
 * floor of 15, and 3:1 contrast against the surface. Dark is a selected set of steps,
 * not a mechanical inversion of light.
 *
 * Section colour is identity: it follows the section, never its position or its value,
 * and every use sits beside the section's name and icon so hue never carries meaning
 * on its own.
 */
object Palette {

    // Surfaces the palette was validated against.
    val LightSurface = Color(0xFFFBFAF7)
    val LightPlane = Color(0xFFF2F0EA)
    val LightCard = Color(0xFFFFFFFF)
    val DarkSurface = Color(0xFF121214)
    val DarkPlane = Color(0xFF0A0A0B)
    val DarkCard = Color(0xFF1C1C21)

    // Ink and chrome.
    val LightInk = Color(0xFF0B0B0B)
    val LightInkSecondary = Color(0xFF52514E)
    val DarkInk = Color(0xFFF7F7F5)
    val DarkInkSecondary = Color(0xFFC3C2B7)
    val Muted = Color(0xFF898781)
    val LightGrid = Color(0xFFE6E4DC)
    val DarkGrid = Color(0xFF2C2C2A)

    // Status colours are reserved for state and never reused as an identity hue.
    val Good = Color(0xFF0CA30C)
    val Warning = Color(0xFFFAB219)
    val Critical = Color(0xFFD03B3B)

    private val lightSections = mapOf(
        DaySection.MORNING to Color(0xFFD97706),
        DaySection.SCHOOL_RUN to Color(0xFF0E9BBF),
        DaySection.BREAKFAST to Color(0xFFDC2F55),
        DaySection.OFFICE to Color(0xFF2563EB),
        DaySection.EVENING to Color(0xFFC026A3),
        DaySection.NIGHT to Color(0xFF6D3FE0),
        DaySection.BODY to Color(0xFF5B6675),
    )

    private val darkSections = mapOf(
        DaySection.MORNING to Color(0xFFC87A06),
        DaySection.SCHOOL_RUN to Color(0xFF1497B0),
        DaySection.BREAKFAST to Color(0xFFF05070),
        DaySection.OFFICE to Color(0xFF4A82EB),
        DaySection.EVENING to Color(0xFFCF3EAC),
        DaySection.NIGHT to Color(0xFF8A73EE),
        DaySection.BODY to Color(0xFF8B98AA),
    )

    /**
     * Sequential ramp for magnitude (the history heatmap): one hue, light to dark.
     * Never a rainbow - a multi-hue ramp invents categories inside a continuous scale.
     * Steps are monotone in lightness with visible gaps, and the lightest still clears
     * the surface so "logged a little" cannot be mistaken for "logged nothing".
     */
    private val lightRamp = listOf(
        Color(0xFFA594F5),
        Color(0xFF8570EF),
        Color(0xFF6D3FE0),
        Color(0xFF5227C4),
    )

    private val darkRamp = listOf(
        Color(0xFF9C88F2),
        Color(0xFF7C63EA),
        Color(0xFF6142DF),
        Color(0xFF4A2AC4),
    )

    fun section(section: DaySection, dark: Boolean): Color =
        (if (dark) darkSections else lightSections).getValue(section)

    fun rampSteps(dark: Boolean): List<Color> = if (dark) darkRamp else lightRamp

    fun ramp(level: Int, dark: Boolean): Color {
        val steps = rampSteps(dark)
        return steps[level.coerceIn(0, steps.lastIndex)]
    }
}

/** Section accent for the current mode. */
@Composable
@ReadOnlyComposable
fun DaySection.accent(): Color = Palette.section(this, LocalDarkTheme.current)

/** One step of the sequential ramp; [level] runs 0 (lowest) to 3 (highest). */
@Composable
@ReadOnlyComposable
fun rampStep(level: Int): Color = Palette.ramp(level, LocalDarkTheme.current)

@Composable
@ReadOnlyComposable
fun rampSteps(): List<Color> = Palette.rampSteps(LocalDarkTheme.current)

/** A day with nothing logged: recedes, and is deliberately not part of the ramp. */
@Composable
@ReadOnlyComposable
fun emptyCell(): Color = if (LocalDarkTheme.current) Palette.DarkGrid else Palette.LightGrid

/** Axis, label and caption ink; recessive by design. */
@Composable
@ReadOnlyComposable
fun mutedInkColor(): Color = Palette.Muted

@Composable
@ReadOnlyComposable
fun gridLine(): Color = if (LocalDarkTheme.current) Palette.DarkGrid else Palette.LightGrid

@Composable
@ReadOnlyComposable
fun cardSurface(): Color = if (LocalDarkTheme.current) Palette.DarkCard else Palette.LightCard

/** Ink that stays legible on a filled accent chip. */
@Composable
@ReadOnlyComposable
fun onAccentInk(): Color = if (LocalDarkTheme.current) Palette.DarkSurface else Color.White

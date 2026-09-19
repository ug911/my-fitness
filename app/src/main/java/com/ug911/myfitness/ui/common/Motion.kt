package com.ug911.myfitness.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Motion used across the app.
 *
 * The rule everywhere: movement explains a change, it never decorates one. Numbers count
 * rather than jump so a change is noticed, progress settles with a spring because it is
 * physical, and cards arrive in sequence so the eye has an order to read them in.
 */
object Motion {
    /** For anything physical - a ring filling, a bar growing. */
    fun <T> springy() = spring<T>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    const val ENTER_MILLIS = 320
    const val STAGGER_MILLIS = 45
}

/** A number that counts to its new value instead of blinking to it. */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    prefix: String = "",
    suffix: String = "",
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 420),
        label = "count",
    )
    Text(text = "$prefix$animated$suffix", style = style, color = color, modifier = modifier)
}

/** The same, for a value with a decimal - calories, kilos. */
@Composable
fun AnimatedDecimal(
    value: Double,
    modifier: Modifier = Modifier,
    suffix: String = "",
    decimals: Int = 0,
    style: TextStyle = MaterialTheme.typography.headlineSmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val animated by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = tween(durationMillis = 420),
        label = "decimal",
    )
    val text = if (decimals == 0) {
        animated.toInt().toString()
    } else {
        String.format("%.${decimals}f", animated)
    }
    Text(text = "$text$suffix", style = style, color = color, modifier = modifier)
}

/**
 * Fades and lifts an item into place, offset by its position in the list so a screen
 * assembles itself top-down rather than appearing all at once.
 */
@Composable
fun Modifier.enterFrom(index: Int): Modifier {
    var shown by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = Motion.ENTER_MILLIS,
            delayMillis = (index * Motion.STAGGER_MILLIS).coerceAtMost(260),
        ),
        label = "enter",
    )
    LaunchedEffect(Unit) { shown = true }
    return this
        .alpha(progress)
        .graphicsLayer { translationY = (1f - progress) * 18.dp.toPx() }
}

package com.ug911.myfitness.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.ui.theme.gridLine
import com.ug911.myfitness.ui.theme.mutedInkColor

/**
 * Chart marks for the app.
 *
 * Kept deliberately thin: 2dp lines, small markers, hairline baselines, and a single
 * hue per chart. One series never needs a legend - the row it sits in names it - and
 * values are direct-labelled only at the point that matters (the latest one), with the
 * rest carried by the summary text beside the chart.
 */

/** A week-by-week trend for one tracker. Null points are weeks with nothing logged. */
@Composable
fun TrendSparkline(
    points: List<Float?>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val known = points.filterNotNull()
    if (known.size < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "not enough weeks yet",
                style = MaterialTheme.typography.labelSmall,
                color = mutedInkColor(),
            )
        }
        return
    }
    val min = known.min()
    val max = known.max()
    val span = (max - min).takeIf { it > 0f } ?: 1f
    val baseline = gridLine()

    Canvas(modifier = modifier) {
        val stepX = if (points.size > 1) size.width / (points.size - 1) else size.width
        val top = 4.dp.toPx()
        val usable = size.height - top - 4.dp.toPx()

        fun yFor(value: Float) = top + usable - ((value - min) / span) * usable

        // Hairline baseline, solid and one shade off the surface - never dashed.
        drawLine(
            color = baseline,
            start = Offset(0f, size.height - 1f),
            end = Offset(size.width, size.height - 1f),
            strokeWidth = 1.dp.toPx(),
        )

        val line = Path()
        val area = Path()
        var started = false
        var lastPoint: Offset? = null
        points.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val point = Offset(stepX * index, yFor(value))
            if (!started) {
                line.moveTo(point.x, point.y)
                area.moveTo(point.x, size.height)
                area.lineTo(point.x, point.y)
                started = true
            } else {
                line.lineTo(point.x, point.y)
                area.lineTo(point.x, point.y)
            }
            lastPoint = point
        }
        lastPoint?.let { end ->
            area.lineTo(end.x, size.height)
            area.close()
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.02f)),
                ),
            )
            drawPath(
                path = line,
                color = accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
            // Only the latest point is marked; a dot on every week reads as noise.
            drawCircle(color = accent, radius = 4.dp.toPx(), center = end)
        }
    }
}

/**
 * Daily completion for one week. One hue, rounded data-ends anchored to the baseline,
 * and a 2dp surface gap between neighbouring bars instead of a border.
 */
@Composable
fun WeekBars(
    values: List<Float>,
    labels: List<String>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val baseline = gridLine()
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (values.isEmpty()) return@Canvas
            val gap = 2.dp.toPx()
            val slot = size.width / values.size
            val barWidth = (slot - gap).coerceAtLeast(2.dp.toPx())
            val radius = 4.dp.toPx()

            drawLine(
                color = baseline,
                start = Offset(0f, size.height - 1f),
                end = Offset(size.width, size.height - 1f),
                strokeWidth = 1.dp.toPx(),
            )

            values.forEachIndexed { index, raw ->
                val value = raw.coerceIn(0f, 1f)
                val left = slot * index + gap / 2
                if (value <= 0f) {
                    // An empty day still gets a mark, so the week reads as seven slots.
                    drawRoundRect(
                        color = baseline,
                        topLeft = Offset(left, size.height - 3.dp.toPx()),
                        size = Size(barWidth, 3.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    )
                } else {
                    val barHeight = (size.height - 2.dp.toPx()) * value
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedInkColor(),
                )
            }
        }
    }
}

/** A ring for "how much of today is logged". The number is the chart; the ring frames it. */
@Composable
fun ProgressRing(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Int = 8,
    content: @Composable () -> Unit,
) {
    val track = gridLine()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (fraction > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

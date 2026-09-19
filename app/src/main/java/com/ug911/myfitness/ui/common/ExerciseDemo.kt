package com.ug911.myfitness.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.DemoAnimation
import com.ug911.myfitness.data.model.DemoFrame

/**
 * The exercise demo: a stick figure moving between the keyframes in the knowledge
 * document.
 *
 * Drawn rather than filmed, which is the honest trade - it shows the shape of the
 * movement, the joint angles and the tempo, not what a real body looks like doing it.
 * The upside is that it is a few hundred bytes in a JSON file, it can be published by
 * an assistant through the MCP server, and it recolours itself with the section accent.
 */
@Composable
fun ExerciseDemoPlayer(
    demo: DemoAnimation,
    accent: Color,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
) {
    val poses = remember(demo) { Poses(demo) }
    val transition = rememberInfiniteTransition(label = "demo")
    val raw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // A lift is a there-and-back movement, so the default reverses rather than
            // snapping from the top of the rep to the bottom.
            animation = tween(demo.durationMillis, easing = LinearEasing),
            repeatMode = if (demo.pingPong) RepeatMode.Reverse else RepeatMode.Restart,
        ),
        label = "progress",
    )
    val progress = if (playing) raw else 0f
    val skeleton = poses.at(progress)

    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val ground = size.height * 0.97f
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(Color.Transparent, accent.copy(alpha = 0.35f), Color.Transparent),
                ),
                start = Offset(0f, ground),
                end = Offset(size.width, ground),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawFigure(skeleton, accent, mirrored = false)
            if (demo.isFrontView) drawFigure(skeleton, accent, mirrored = true)
        }
    }
}

/** A pose is every joint at one instant. */
private typealias Pose = Map<String, Offset>

private class Poses(demo: DemoAnimation) {
    /** Frames resolved so each one holds every joint, filling forward from the first. */
    private val frames: List<Pair<Float, Pose>> = buildList {
        var running = mutableMapOf<String, Offset>()
        demo.frames.sortedBy { it.at }.forEach { frame ->
            running = running.toMutableMap()
            frame.joints.forEach { (joint, point) ->
                if (point.size == 2) running[joint] = Offset(point[0], point[1])
            }
            add(frame.at to running.toMap())
        }
    }

    fun at(progress: Float): Pose {
        if (frames.isEmpty()) return emptyMap()
        if (frames.size == 1) return frames.first().second
        val clamped = progress.coerceIn(0f, 1f)
        val nextIndex = frames.indexOfFirst { it.first >= clamped }.takeIf { it > 0 } ?: 1
        val (fromAt, from) = frames[nextIndex - 1]
        val (toAt, to) = frames[nextIndex]
        val span = (toAt - fromAt).takeIf { it > 0f } ?: 1f
        val t = ((clamped - fromAt) / span).coerceIn(0f, 1f)
        // Ease within the segment so the figure settles into each position.
        val eased = t * t * (3f - 2f * t)
        return from.keys.associateWith { joint ->
            val a = from.getValue(joint)
            val b = to[joint] ?: a
            Offset(a.x + (b.x - a.x) * eased, a.y + (b.y - a.y) * eased)
        }
    }
}

private val LIMBS = listOf(
    "neck" to "shoulder",
    "shoulder" to "elbow",
    "elbow" to "wrist",
    "shoulder" to "hip",
    "hip" to "knee",
    "knee" to "ankle",
    "ankle" to "foot",
)

private fun DrawScope.drawFigure(pose: Pose, accent: Color, mirrored: Boolean) {
    if (pose.isEmpty()) return
    val inset = size.minDimension * 0.06f
    val boxWidth = size.width - inset * 2
    val boxHeight = size.height - inset * 2

    fun place(point: Offset): Offset {
        val x = if (mirrored) 1f - point.x else point.x
        return Offset(inset + x * boxWidth, inset + point.y * boxHeight)
    }

    val stroke = (size.minDimension * 0.035f).coerceIn(4f, 14f)

    // Torso first, as a slightly heavier line - it reads as the body rather than a limb.
    val shoulder = pose["shoulder"]
    val hip = pose["hip"]
    if (shoulder != null && hip != null) {
        drawLine(
            color = accent,
            start = place(shoulder),
            end = place(hip),
            strokeWidth = stroke * 1.25f,
            cap = StrokeCap.Round,
        )
    }

    LIMBS.forEach { (fromJoint, toJoint) ->
        val from = pose[fromJoint] ?: return@forEach
        val to = pose[toJoint] ?: return@forEach
        if (fromJoint == "shoulder" && toJoint == "hip") return@forEach
        drawLine(
            color = accent.copy(alpha = if (mirrored) 0.55f else 1f),
            start = place(from),
            end = place(to),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    pose["head"]?.let { head ->
        val centre = place(head)
        val radius = size.minDimension * 0.055f
        drawCircle(color = accent.copy(alpha = if (mirrored) 0.55f else 1f), radius = radius, center = centre)
    }

    // A soft mark under the feet so the figure looks planted rather than floating.
    pose["foot"]?.let { foot ->
        val centre = place(foot)
        drawOval(
            color = accent.copy(alpha = 0.16f),
            topLeft = Offset(centre.x - stroke * 1.6f, size.height * 0.955f),
            size = Size(stroke * 3.2f, stroke * 0.9f),
        )
    }
}

/** A still frame, for a list row where an animation would be noise. */
@Composable
fun ExerciseDemoThumbnail(
    demo: DemoAnimation,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val pose = remember(demo) { Poses(demo).at(0.62f) }
    Canvas(modifier) {
        drawFigure(pose, accent, mirrored = false)
        if (demo.isFrontView) drawFigure(pose, accent, mirrored = true)
    }
}

/** Exposed for tests: resolves the pose at a point in the animation. */
internal fun poseAt(demo: DemoAnimation, progress: Float): Map<String, Offset> = Poses(demo).at(progress)

internal fun frameCount(demo: DemoAnimation): Int = demo.frames.size

internal val DemoFrame.jointCount: Int get() = joints.size

package com.ug911.myfitness.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.EvidenceNote
import com.ug911.myfitness.data.model.EvidenceStrength
import com.ug911.myfitness.data.model.ExerciseDoc
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.ExerciseDemoPlayer
import com.ug911.myfitness.ui.common.Pill
import com.ug911.myfitness.ui.common.PlainCard
import com.ug911.myfitness.ui.common.ScreenHeader
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.common.enterFrom
import com.ug911.myfitness.ui.theme.Palette
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.cardSurface
import com.ug911.myfitness.ui.theme.mutedInkColor

/**
 * The coach for one movement: how to do it, what usually goes wrong, what the evidence
 * supports, and where to go next. Everything on this screen comes from the knowledge
 * base, so publishing a better document through the MCP server improves this page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExerciseGuideScreen(
    viewModel: ExerciseGuideViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val doc by viewModel.doc.collectAsState()
    val accent = DaySection.MORNING.accent()
    val exercise = doc

    if (exercise == null) {
        Column(modifier) {
            ScreenHeader(title = "Exercise", onBack = onBack)
            EmptyState(
                title = "Not in the knowledge base",
                body = "Publish it with the MCP server, or sync the bundle in Settings.",
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            ScreenHeader(
                title = exercise.name,
                subtitle = exercise.anchorExercise ?: exercise.pattern?.replace('_', ' '),
                onBack = onBack,
            )
        }

        exercise.demo?.let { demo ->
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.verticalGradient(
                                listOf(accent.copy(alpha = 0.16f), cardSurface()),
                            ),
                        )
                        .enterFrom(0),
                ) {
                    ExerciseDemoPlayer(demo = demo, accent = accent, modifier = Modifier.fillMaxWidth().height(240.dp))
                    Text(
                        text = if (demo.isFrontView) "front view" else "side view",
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedInkColor(),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    )
                }
            }
        }

        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp).enterFrom(1),
            ) {
                exercise.repRange?.let { Pill(it.label(), accent) }
                exercise.restSeconds?.let { Pill("${it / 60}:${"%02d".format(it % 60)} rest", accent) }
                exercise.tempo?.takeIf { it != "steady" }?.let { Pill("tempo $it", accent) }
                exercise.equipment?.let { Pill(it, DaySection.OFFICE.accent()) }
                exercise.difficulty?.let { Pill(it, DaySection.NIGHT.accent()) }
            }
        }

        item {
            PlainCard(modifier = Modifier.enterFrom(2)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Works", style = MaterialTheme.typography.labelMedium, color = mutedInkColor())
                    Text(
                        exercise.primaryMuscles.joinToString(", "),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (exercise.secondaryMuscles.isNotEmpty()) {
                        Text(
                            "and ${exercise.secondaryMuscles.joinToString(", ").lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedInkColor(),
                        )
                    }
                }
            }
        }

        if (exercise.setup.isNotEmpty() || exercise.execution.isNotEmpty()) {
            item { SectionHeader(title = "How to do it", accent = accent, icon = Icons.Filled.Bolt) }
            item {
                PlainCard(modifier = Modifier.enterFrom(3)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        exercise.setup.forEachIndexed { index, line -> NumberedLine(index + 1, line, accent) }
                        exercise.execution.forEachIndexed { index, line ->
                            NumberedLine(exercise.setup.size + index + 1, line, accent)
                        }
                        exercise.breathing?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = mutedInkColor())
                        }
                    }
                }
            }
        }

        if (exercise.cues.isNotEmpty()) {
            item { SectionHeader(title = "Coaching cues", accent = accent, icon = Icons.Filled.TipsAndUpdates) }
            item {
                PlainCard(modifier = Modifier.enterFrom(4)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        exercise.cues.forEach { cue ->
                            Row {
                                Box(
                                    Modifier.padding(top = 7.dp).size(6.dp)
                                        .clip(RoundedCornerShape(2.dp)).background(accent),
                                )
                                Text(
                                    cue,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (exercise.mistakes.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "What usually goes wrong",
                    accent = Palette.Warning,
                    icon = Icons.Filled.Warning,
                )
            }
            item {
                PlainCard(modifier = Modifier.enterFrom(5)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        exercise.mistakes.forEach { mistake ->
                            Column {
                                Text(mistake.mistake, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    mistake.fix,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedInkColor(),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (exercise.evidence.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "What the evidence says",
                    accent = DaySection.SCHOOL_RUN.accent(),
                    icon = Icons.Filled.Science,
                    subtitle = "General training guidance, not medical advice",
                )
            }
            item {
                PlainCard(modifier = Modifier.enterFrom(6)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        exercise.evidence.forEach { note -> EvidenceRow(note) }
                    }
                }
            }
        }

        if (exercise.progressions.isNotEmpty() || exercise.regressions.isNotEmpty()) {
            item { SectionHeader(title = "Where to go next", accent = DaySection.EVENING.accent()) }
            item {
                PlainCard(modifier = Modifier.enterFrom(7)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LadderRow("Harder", exercise.progressions, DaySection.EVENING.accent())
                        LadderRow("Easier", exercise.regressions, mutedInkColor())
                        if (exercise.alternatives.isNotEmpty()) {
                            LadderRow("Instead", exercise.alternatives, DaySection.OFFICE.accent())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberedLine(number: Int, text: String, accent: Color) {
    Row {
        Box(
            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, color = accent)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun EvidenceRow(note: EvidenceNote) {
    val colour = when (note.strength) {
        EvidenceStrength.STRONG -> Palette.Good
        EvidenceStrength.MODERATE -> DaySection.SCHOOL_RUN.accent()
        EvidenceStrength.LIMITED -> mutedInkColor()
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Confidence is spelled out, never left to colour alone.
            Pill(note.strength.label, colour)
        }
        Text(
            note.claim,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (note.detail.isNotBlank()) {
            Text(note.detail, style = MaterialTheme.typography.bodySmall, color = mutedInkColor())
        }
        note.source?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = mutedInkColor())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LadderRow(label: String, items: List<String>, accent: Color) {
    if (items.isEmpty()) return
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = mutedInkColor())
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            items.forEach { Pill(it, accent) }
        }
    }
}

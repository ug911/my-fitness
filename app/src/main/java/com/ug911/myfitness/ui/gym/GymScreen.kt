package com.ug911.myfitness.ui.gym

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.WorkoutSet
import com.ug911.myfitness.data.model.formatNumber
import com.ug911.myfitness.ui.common.AnimatedCount
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.ExerciseDemoThumbnail
import com.ug911.myfitness.ui.common.Pill
import com.ug911.myfitness.ui.common.PlainCard
import com.ug911.myfitness.ui.common.ScreenHeader
import com.ug911.myfitness.ui.common.StatTile
import com.ug911.myfitness.ui.common.StatTileRow
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.mutedInkColor
import com.ug911.myfitness.ui.theme.onAccentInk

/**
 * The gym screen. Today's session from the plan, each exercise expanding into a set
 * logger that already knows what you lifted last time - accept it, or change it and go.
 */
@Composable
fun GymScreen(
    viewModel: GymViewModel,
    onOpenExercise: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val accent = DaySection.MORNING.accent()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            ScreenHeader(
                title = state.day?.title?.takeIf { it.isNotBlank() } ?: "Gym",
                subtitle = state.plan?.name ?: "No training plan yet",
                actions = actions,
            )
        }

        item {
            StatTileRow {
                StatTile(
                    label = "Sets",
                    value = "${state.setsDone}${if (state.setsPlanned > 0) " / ${state.setsPlanned}" else ""}",
                    accent = accent,
                    hint = "logged today",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatTile(
                    label = "Volume",
                    value = if (state.volume > 0) "${formatNumber(state.volume)} kg" else "-",
                    accent = DaySection.OFFICE.accent(),
                    hint = "weight x reps",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                StatTile(
                    label = "Day",
                    value = state.date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    accent = DaySection.NIGHT.accent(),
                    hint = state.day?.title?.lowercase() ?: "off plan",
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }

        state.day?.warmup?.takeIf { it.isNotBlank() }?.let { warmup ->
            item {
                PlainCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Pill(text = "Warm-up", accent = accent)
                        Text(
                            text = warmup,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                }
            }
        }

        if (state.rows.isEmpty() && state.extraRows.isEmpty()) {
            item {
                EmptyState(
                    title = if (state.isRestDay) "Rest day" else "Nothing planned",
                    body = if (state.isRestDay) {
                        "The plan has you off today. Anything you log still counts."
                    } else {
                        "Sync the knowledge base in Settings to load your training week."
                    },
                )
            }
        }

        items(state.rows + state.extraRows, key = { it.item.exercise }) { row ->
            ExerciseCard(
                row = row,
                expanded = state.openExerciseId == row.item.exercise,
                onToggle = { viewModel.open(if (state.openExerciseId == row.item.exercise) null else row.item.exercise) },
                onLogSet = { weight, reps -> viewModel.logSet(row.item.exercise, weight, reps) },
                onDeleteSet = viewModel::deleteSet,
                onOpenGuide = { onOpenExercise(row.item.exercise) },
            )
        }

        item {
            Text(
                text = "Every set stops one to three reps short of failure. That is the dial that matters.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedInkColor(),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    row: GymRow,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLogSet: (Double?, Int) -> Unit,
    onDeleteSet: (Long) -> Unit,
    onOpenGuide: () -> Unit,
) {
    val accent = DaySection.MORNING.accent()
    var weight by remember(row.item.exercise, row.setsDone) {
        mutableDoubleStateOf(row.suggestedWeight() ?: 0.0)
    }
    var reps by remember(row.item.exercise, row.setsDone) { mutableIntStateOf(row.suggestedReps()) }

    PlainCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = if (row.complete) 0.22f else 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    row.doc?.demo?.let { demo ->
                        ExerciseDemoThumbnail(demo, accent, Modifier.size(38.dp))
                    } ?: Text(row.name.take(1), color = accent, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = buildString {
                            append(row.target)
                            row.lastTime?.topSet?.let { append("  ·  last ${it.label()}") }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedInkColor(),
                    )
                }
                if (row.setsDone > 0) {
                    Pill(
                        text = "${row.setsDone}${if (row.item.sets > 0) "/${row.item.sets}" else ""}",
                        accent = accent,
                        filled = row.complete,
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(animationSpec = spring(stiffness = 420f)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    row.logged?.sets?.let { sets ->
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 10.dp),
                        ) {
                            sets.forEach { set -> LoggedSetChip(set, accent) { onDeleteSet(set.id) } }
                        }
                    }

                    Stepper(
                        label = "Weight",
                        value = if (weight <= 0) "body" else "${formatNumber(weight)} kg",
                        accent = accent,
                        onLess = { weight = (weight - 2.5).coerceAtLeast(0.0) },
                        onMore = { weight += 2.5 },
                    )
                    Stepper(
                        label = "Reps",
                        value = "$reps",
                        accent = accent,
                        onLess = { reps = (reps - 1).coerceAtLeast(1) },
                        onMore = { reps += 1 },
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = { onLogSet(weight.takeIf { it > 0 }, reps) },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = onAccentInk(),
                            ),
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Log set")
                        }
                        TextButton(onClick = onOpenGuide) {
                            Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  How to")
                        }
                        Spacer(Modifier.weight(1f))
                        AnimatedCount(
                            value = row.logged?.totalReps ?: 0,
                            suffix = " reps",
                            style = MaterialTheme.typography.labelLarge,
                            color = mutedInkColor(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoggedSetChip(set: WorkoutSet, accent: androidx.compose.ui.graphics.Color, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = 0.14f))
            .clickable { onRemove() }
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(set.label(), style = MaterialTheme.typography.labelLarge, color = accent)
        Icon(
            Icons.Filled.Close,
            contentDescription = "Remove set",
            tint = accent.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp).padding(start = 2.dp),
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onLess) {
            Icon(Icons.Filled.Remove, contentDescription = "Less $label", tint = accent)
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(88.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.Add, contentDescription = "More $label", tint = accent)
        }
    }
}

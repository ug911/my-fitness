package com.ug911.myfitness.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.analysis.OutcomeSplit
import com.ug911.myfitness.analysis.SeriesPoint
import com.ug911.myfitness.analysis.TrackerStat
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.SectionCard
import com.ug911.myfitness.ui.common.SectionHeader

@Composable
fun InsightsScreen(viewModel: InsightsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    state.snapshotPreview?.let { snapshot ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSnapshot,
            confirmButton = { TextButton(onClick = viewModel::dismissSnapshot) { Text("Close") } },
            title = { Text("Exactly what would be sent") },
            text = {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    item { Text(snapshot, style = MaterialTheme.typography.bodySmall) }
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightsWindow.entries.forEach { window ->
                    FilterChip(
                        selected = state.window == window,
                        onClick = { viewModel.setWindow(window) },
                        label = { Text(window.label) },
                    )
                }
            }
        }

        item { ReviewCard(state = state, viewModel = viewModel) }

        state.latestAnalysis?.let { analysis ->
            item { SectionHeader(title = "Weekly review", subtitle = "${analysis.periodStart} to ${analysis.periodEnd}") }
            item {
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(analysis.review, style = MaterialTheme.typography.bodyMedium)
                        if (analysis.insights.isNotEmpty()) {
                            Text(
                                "Insights",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            analysis.insights.forEach { insight ->
                                Text("• $insight", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        analysis.planRationale?.let {
                            Text(
                                "Next week: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Text(
                            "${analysis.provider} / ${analysis.model}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }

        item { SectionHeader(title = "Trends", subtitle = "Last ${state.window.label}, one point per week") }
        if (state.stats.isEmpty()) {
            item { EmptyState("No data yet", "Log a few days and trends will show up here.") }
        } else {
            item {
                SectionCard {
                    state.stats.forEach { stat ->
                        StatRow(stat = stat, series = state.series[stat.tracker.id].orEmpty())
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Patterns",
                subtitle = "Descriptive only: a split of your own days, not a claim about cause",
            )
        }
        val patterns = state.sameDayPatterns + state.nextDayPatterns
        if (patterns.isEmpty()) {
            item {
                EmptyState(
                    "Not enough data",
                    "Patterns need at least ${com.ug911.myfitness.analysis.PatternAnalysis.MIN_DAYS_PER_SIDE} " +
                        "days on each side of a habit before they mean anything.",
                )
            }
        } else {
            item { SectionCard { patterns.forEach { PatternRow(it) } } }
        }
    }
}

@Composable
private fun ReviewCard(state: InsightsUiState, viewModel: InsightsViewModel) {
    SectionCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Review my week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Sends a summary of last week - aggregates and your notes, never the raw database - " +
                    "and gets back a review, insights and a proposed plan you can accept or reject.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            when (val review = state.review) {
                ReviewUiState.Idle -> Unit
                ReviewUiState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp))
                    Text("  Thinking about your week...", style = MaterialTheme.typography.bodySmall)
                }
                is ReviewUiState.Done -> Text(
                    buildString {
                        append("Done. ")
                        append(if (review.proposedPlan) "A plan is waiting for approval on the Plan tab." else "No plan was proposed.")
                        if (review.unmatched.isNotEmpty()) {
                            append(" Ignored unknown trackers: ${review.unmatched.joinToString()}.")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                is ReviewUiState.Error -> Text(
                    review.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = viewModel::reviewWeek,
                    enabled = state.review != ReviewUiState.Running && state.aiConfigured,
                ) {
                    Text("Review my week")
                }
                TextButton(onClick = viewModel::previewSnapshot) { Text("Preview data") }
            }
            if (!state.aiConfigured) {
                Text(
                    "Add an API key in Settings to enable reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun StatRow(stat: TrackerStat, series: List<SeriesPoint>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stat.tracker.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                stat.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Sparkline(series = series, modifier = Modifier.width(96.dp).height(28.dp))
    }
}

/** Minimal line chart: enough to see a direction, nothing more. */
@Composable
private fun Sparkline(series: List<SeriesPoint>, modifier: Modifier = Modifier) {
    val values = series.map { it.value ?: 0.0 }
    val color = MaterialTheme.colorScheme.primary
    if (values.size < 2) return
    val min = values.min()
    val max = values.max()
    val span = (max - min).takeIf { it > 0.0 } ?: 1.0

    Canvas(modifier = modifier) {
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = stepX * index
            val y = size.height - ((value - min) / span * size.height).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 3f))
        val lastX = size.width
        val lastY = size.height - ((values.last() - min) / span * size.height).toFloat()
        drawCircle(color = color, radius = 4f, center = Offset(lastX, lastY))
    }
}

@Composable
private fun PatternRow(split: OutcomeSplit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(split.describe(), style = MaterialTheme.typography.bodyMedium)
        Text(
            "${split.outcome.name}: ${split.averageWhenDone} with, ${split.averageWhenNotDone} without " +
                "(${split.totalDays} days, ${split.lagLabel})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

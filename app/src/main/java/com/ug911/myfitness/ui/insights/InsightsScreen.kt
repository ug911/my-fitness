package com.ug911.myfitness.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.analysis.OutcomeSplit
import com.ug911.myfitness.analysis.PatternAnalysis
import com.ug911.myfitness.analysis.SeriesPoint
import com.ug911.myfitness.analysis.TrackerStat
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.PlainCard
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.common.StatTile
import com.ug911.myfitness.ui.common.TrendSparkline
import com.ug911.myfitness.ui.icon
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.mutedInkColor
import com.ug911.myfitness.ui.theme.onAccentInk

@Composable
fun InsightsScreen(viewModel: InsightsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    state.snapshotPreview?.let { snapshot ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSnapshot,
            confirmButton = { TextButton(onClick = viewModel::dismissSnapshot) { Text("Close") } },
            title = { Text("Exactly what would be sent") },
            shape = MaterialTheme.shapes.large,
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
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = "Insights",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightsWindow.entries.forEach { window ->
                    val accent = DaySection.NIGHT.accent()
                    FilterChip(
                        selected = state.window == window,
                        onClick = { viewModel.setWindow(window) },
                        label = { Text(window.label) },
                        shape = MaterialTheme.shapes.small,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = accent.copy(alpha = 0.10f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = accent,
                            selectedLabelColor = onAccentInk(),
                        ),
                        border = null,
                    )
                }
            }
        }

        item { ReviewCard(state = state, viewModel = viewModel) }

        state.latestAnalysis?.let { analysis ->
            item {
                SectionHeader(
                    title = "Weekly review",
                    accent = DaySection.NIGHT.accent(),
                    icon = Icons.Filled.AutoAwesome,
                    subtitle = "${analysis.periodStart} to ${analysis.periodEnd}",
                )
            }
            item {
                PlainCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(analysis.review, style = MaterialTheme.typography.bodyMedium)
                        if (analysis.insights.isNotEmpty()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            analysis.insights.forEach { insight ->
                                Text("- $insight", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        analysis.planRationale?.let {
                            Text(
                                "Next week: $it",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "${analysis.provider} / ${analysis.model}",
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedInkColor(),
                        )
                    }
                }
            }
        }

        if (state.headlines.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    state.headlines.take(3).forEach { stat ->
                        StatTile(
                            label = stat.tracker.name,
                            value = stat.summary(),
                            accent = stat.tracker.section.accent(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        DaySection.entries.forEach { section ->
            val sectionStats = state.stats.filter { it.tracker.section == section }
            if (sectionStats.isNotEmpty()) {
                item(key = "trend-h-${section.name}") {
                    SectionHeader(
                        title = section.label,
                        accent = section.accent(),
                        icon = section.icon,
                        subtitle = "per week, last ${state.window.label}",
                    )
                }
                item(key = "trend-c-${section.name}") {
                    PlainCard {
                        sectionStats.forEachIndexed { index, stat ->
                            StatRow(
                                stat = stat,
                                series = state.series[stat.tracker.id].orEmpty(),
                                accent = section.accent(),
                            )
                            if (index != sectionStats.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Patterns",
                accent = DaySection.EVENING.accent(),
                icon = DaySection.EVENING.icon,
                subtitle = "A split of your own days, not a claim about cause",
            )
        }
        val patterns = state.sameDayPatterns + state.nextDayPatterns
        if (patterns.isEmpty()) {
            item {
                EmptyState(
                    "Not enough data yet",
                    "A pattern needs at least ${PatternAnalysis.MIN_DAYS_PER_SIDE} days on each side " +
                        "of a habit before it means anything.",
                )
            }
        } else {
            item {
                PlainCard {
                    patterns.forEachIndexed { index, split ->
                        PatternRow(split)
                        if (index != patterns.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(state: InsightsUiState, viewModel: InsightsViewModel) {
    val accent = DaySection.NIGHT.accent()
    PlainCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Review my week", style = MaterialTheme.typography.titleLarge)
            Text(
                "Sends a summary of last week - counts, averages and your notes, never the raw " +
                    "database - and gets back a review, insights and a plan you can accept or reject.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedInkColor(),
            )
            when (val review = state.review) {
                ReviewUiState.Idle -> Unit
                ReviewUiState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = accent,
                        modifier = Modifier.width(18.dp).height(18.dp),
                    )
                    Text(
                        "  Reading your week...",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is ReviewUiState.Done -> Text(
                    buildString {
                        append("Done. ")
                        append(
                            if (review.proposedPlan) {
                                "A plan is waiting for approval on the Plan tab."
                            } else {
                                "No plan was proposed."
                            },
                        )
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
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccentInk()),
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.width(18.dp))
                    Text("  Review my week")
                }
                TextButton(onClick = viewModel::previewSnapshot) { Text("Preview data") }
            }
            if (!state.aiConfigured) {
                Text(
                    "Add an API key in Settings to enable reviews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedInkColor(),
                )
            }
        }
    }
}

@Composable
private fun StatRow(stat: TrackerStat, series: List<SeriesPoint>, accent: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stat.tracker.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stat.summary(),
                style = MaterialTheme.typography.titleMedium,
                color = accent,
            )
        }
        TrendSparkline(
            points = series.map { point -> point.value?.toFloat() },
            accent = accent,
            modifier = Modifier.width(104.dp).height(38.dp),
        )
    }
}

@Composable
private fun PatternRow(split: OutcomeSplit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(split.describe(), style = MaterialTheme.typography.bodyMedium)
        Text(
            "${split.outcome.name}: ${split.averageWhenDone} with, ${split.averageWhenNotDone} without " +
                "(${split.totalDays} days, ${split.lagLabel})",
            style = MaterialTheme.typography.bodySmall,
            color = mutedInkColor(),
        )
    }
}

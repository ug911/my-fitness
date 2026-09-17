package com.ug911.myfitness.ui.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.PlanSource
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.ProgressRow
import com.ug911.myfitness.ui.common.SectionCard
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.theme.TrackingColors

@Composable
fun PlanScreen(viewModel: PlanViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val active = state.activePlan
        item {
            SectionHeader(
                title = "This week",
                subtitle = active?.let { "${it.plan.startDate} to ${it.plan.endDate}" }
                    ?: "No plan approved for this week",
            )
        }

        if (active == null) {
            item {
                EmptyState(
                    "No active plan",
                    "Run a weekly review on the Insights tab and a plan will show up here for approval.",
                )
            }
        } else {
            item {
                SectionCard {
                    active.plan.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (state.progress.isEmpty()) {
                        Text(
                            "This plan has no targets.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    state.progress.forEach { row ->
                        val detail = buildString {
                            val targetDays = row.targetDays
                            if (targetDays != null) {
                                append("${row.achievedDays}/$targetDays days")
                            } else {
                                append(
                                    row.achievedValue?.let { com.ug911.myfitness.data.model.formatNumber(it) }
                                        ?: "no data",
                                )
                                row.target.targetValue?.let {
                                    append(" of ${com.ug911.myfitness.data.model.formatNumber(it)}")
                                }
                            }
                        }
                        ProgressRow(
                            label = row.tracker.name,
                            detail = detail,
                            fraction = row.fraction,
                            color = if (row.met) TrackingColors.full else MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = if (active.plan.generatedBy == PlanSource.AI) {
                            "Proposed by AI, approved by you"
                        } else {
                            "Set by you"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val proposed = state.proposedPlan
        if (proposed != null) {
            item {
                SectionHeader(
                    title = "Proposed plan",
                    subtitle = "${proposed.plan.startDate} to ${proposed.plan.endDate} - waiting for your approval",
                )
            }
            item {
                SectionCard {
                    proposed.plan.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    state.proposedTargets.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.tracker.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    item.describe(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                item.target.note?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            if (item.target.targetFrequency != null) {
                                IconButton(onClick = { viewModel.adjustProposedFrequency(item.target, -1) }) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Fewer days")
                                }
                                IconButton(onClick = { viewModel.adjustProposedFrequency(item.target, 1) }) {
                                    Icon(Icons.Filled.Add, contentDescription = "More days")
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = viewModel::approveProposed) { Text("Accept plan") }
                        OutlinedButton(onClick = viewModel::rejectProposed) { Text("Reject") }
                    }
                    Text(
                        "Nothing here counts as a goal until you accept it, and accepting never " +
                            "changes anything you have already logged.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    )
                }
            }
        }

        item {
            Text(
                "Plans and history are separate records. A plan says what you intended; " +
                    "your entries say what happened.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

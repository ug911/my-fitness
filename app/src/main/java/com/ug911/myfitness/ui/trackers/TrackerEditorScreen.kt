package com.ug911.myfitness.ui.trackers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.HealthMetric
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerCategory
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.ui.common.SectionCard
import com.ug911.myfitness.ui.common.SectionHeader

/** Add or change a tracker. This is the app's extensibility story, so it is a real form. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackerEditorScreen(
    viewModel: TrackersViewModel,
    trackerId: Long,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackers by viewModel.state.collectAsState()
    val existing = trackers.firstOrNull { it.id == trackerId }

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: TrackerCategory.BEHAVIOUR) }
    var type by remember(existing?.id) { mutableStateOf(existing?.type ?: TrackerType.BOOLEAN) }
    var unit by remember(existing?.id) { mutableStateOf(existing?.unit.orEmpty()) }
    var options by remember(existing?.id) { mutableStateOf(existing?.options?.joinToString(", ").orEmpty()) }
    var ratingMax by remember(existing?.id) { mutableStateOf((existing?.ratingMax ?: 5).toString()) }
    var direction by remember(existing?.id) { mutableStateOf(existing?.direction ?: Direction.UP) }
    var aggregation by remember(existing?.id) {
        mutableStateOf(existing?.aggregation ?: Aggregation.defaultFor(TrackerType.BOOLEAN))
    }
    var healthMetric by remember(existing?.id) { mutableStateOf(existing?.healthMetric) }
    var active by remember(existing?.id) { mutableStateOf(existing?.active ?: true) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader(title = if (existing == null) "New tracker" else "Edit tracker") }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { ChipPicker("Category", TrackerCategory.entries, category, { it.label }) { category = it } }
        item {
            ChipPicker("Type", TrackerType.entries, type, { it.name.lowercase() }) {
                type = it
                aggregation = Aggregation.defaultFor(it)
            }
        }

        if (type == TrackerType.NUMBER || type == TrackerType.DURATION) {
            item {
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (kg, steps, min)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (type == TrackerType.SELECT) {
            item {
                OutlinedTextField(
                    value = options,
                    onValueChange = { options = it },
                    label = { Text("Options, comma separated (first one means 'none')") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (type == TrackerType.RATING) {
            item {
                OutlinedTextField(
                    value = ratingMax,
                    onValueChange = { ratingMax = it.filter(Char::isDigit).take(2) },
                    label = { Text("Top of scale") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item { ChipPicker("Better when", Direction.entries, direction, { it.label() }) { direction = it } }
        item {
            ChipPicker("Summarised as", Aggregation.entries, aggregation, { it.label() }) { aggregation = it }
        }

        item {
            SectionCard {
                Column(Modifier.padding(12.dp)) {
                    Text("Fill from Health Connect", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    ) {
                        FilterChip(
                            selected = healthMetric == null,
                            onClick = { healthMetric = null },
                            label = { Text("manual") },
                        )
                        HealthMetric.entries.forEach { metric ->
                            FilterChip(
                                selected = healthMetric == metric,
                                onClick = { healthMetric = metric },
                                label = { Text(metric.label()) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text("Show on Today", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = active, onCheckedChange = { active = it })
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.save(
                            Tracker(
                                id = existing?.id ?: 0,
                                name = name.trim(),
                                category = category,
                                type = type,
                                unit = unit.trim().takeIf { it.isNotBlank() },
                                active = active,
                                sortOrder = existing?.sortOrder ?: 100,
                                options = options.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                ratingMax = ratingMax.toIntOrNull()?.coerceIn(2, 10) ?: 5,
                                healthMetric = healthMetric,
                                direction = direction,
                                aggregation = aggregation,
                            ),
                        )
                        onDone()
                    },
                ) {
                    Text("Save")
                }
                TextButton(onClick = onDone) { Text("Cancel") }
                existing?.let { tracker ->
                    TextButton(
                        onClick = {
                            viewModel.delete(tracker)
                            onDone()
                        },
                    ) {
                        Text("Delete")
                    }
                }
            }
        }

        item {
            Text(
                "Deleting a tracker also deletes its history. Switching it off keeps everything " +
                    "and just hides it from Today.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipPicker(
    label: String,
    options: List<T>,
    selected: T,
    render: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(render(option)) },
                )
            }
        }
    }
}

private fun Direction.label(): String = when (this) {
    Direction.UP -> "more is better"
    Direction.DOWN -> "less is better"
    Direction.NEUTRAL -> "just observe"
}

private fun Aggregation.label(): String = when (this) {
    Aggregation.DAYS_COMPLETED -> "days done"
    Aggregation.SUM -> "total"
    Aggregation.AVERAGE -> "average"
    Aggregation.LATEST -> "latest"
    Aggregation.NONE -> "not summarised"
}

private fun HealthMetric.label(): String = name.lowercase().replace('_', ' ')

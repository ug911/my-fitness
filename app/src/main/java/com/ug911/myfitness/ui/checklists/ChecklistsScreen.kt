package com.ug911.myfitness.ui.checklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.ui.common.PlainCard
import com.ug911.myfitness.ui.common.ScreenHeader
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.icon
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.mutedInkColor
import com.ug911.myfitness.ui.trackers.TrackersViewModel

/**
 * Curating the tick-lists: the food you actually eat at each meal, the exercises you
 * actually do. Adding an item here is all it takes for it to appear on Today.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChecklistsScreen(
    viewModel: TrackersViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackers by viewModel.state.collectAsState()
    val checklists = trackers.filter { it.type == TrackerType.MULTI_SELECT }
    val drafts = remember { mutableStateMapOf<Long, String>() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            ScreenHeader(
                title = "Food and checklists",
                subtitle = "What shows up as a tick on Today",
                onBack = onBack,
            )
        }

        checklists.forEach { tracker ->
            item(key = "h-${tracker.id}") {
                SectionHeader(
                    title = tracker.name,
                    accent = tracker.section.accent(),
                    icon = tracker.section.icon,
                    subtitle = tracker.section.label,
                )
            }
            item(key = "c-${tracker.id}") {
                PlainCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (tracker.options.isEmpty()) {
                            Text(
                                "Nothing on this list yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = mutedInkColor(),
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tracker.options.forEach { option ->
                                OptionChip(
                                    label = option,
                                    onRemove = { viewModel.removeOption(tracker, option) },
                                )
                            }
                        }
                        AddRow(
                            tracker = tracker,
                            draft = drafts[tracker.id].orEmpty(),
                            onDraftChange = { drafts[tracker.id] = it },
                            onAdd = {
                                viewModel.addOption(tracker, drafts[tracker.id].orEmpty())
                                drafts[tracker.id] = ""
                            },
                        )
                    }
                }
            }
        }

        item {
            Text(
                "Removing an item only takes it off the list - days you already logged it keep " +
                    "their record.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedInkColor(),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun OptionChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = onRemove,
        label = { Text(label) },
        shape = MaterialTheme.shapes.small,
        colors = InputChipDefaults.inputChipColors(labelColor = MaterialTheme.colorScheme.onSurface),
        trailingIcon = {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove $label",
                modifier = Modifier.size(16.dp),
            )
        },
    )
}

@Composable
private fun AddRow(
    tracker: Tracker,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("Add to ${tracker.name.lowercase()}") },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdd, enabled = draft.isNotBlank()) { Text("Add") }
    }
}

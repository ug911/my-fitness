package com.ug911.myfitness.ui.trackers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerCategory
import com.ug911.myfitness.ui.common.SectionCard
import com.ug911.myfitness.ui.common.SectionHeader

@Composable
fun TrackersScreen(
    viewModel: TrackersViewModel,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackers by viewModel.state.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeader(
                    title = "Trackers",
                    subtitle = "Switch off what you do not care about; add anything you do",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onEdit(0L) }) { Text("New") }
            }
        }

        TrackerCategory.entries.forEach { category ->
            val group = trackers.filter { it.category == category }
            if (group.isNotEmpty()) {
                item(key = "h-${category.name}") { SectionHeader(title = category.label) }
                item(key = "c-${category.name}") {
                    SectionCard {
                        group.forEach { tracker ->
                            TrackerConfigRow(
                                tracker = tracker,
                                onToggle = { viewModel.setActive(tracker, it) },
                                onClick = { onEdit(tracker.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackerConfigRow(tracker: Tracker, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                buildString {
                    append(tracker.type.name.lowercase())
                    tracker.unit?.let { append(" - $it") }
                    if (tracker.isAutomatic) append(" - from Health Connect")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Switch(checked = tracker.active, onCheckedChange = onToggle)
    }
}

package com.ug911.myfitness.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerCategory
import com.ug911.myfitness.ui.common.SectionCard
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.common.TrackerInputRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HEADER_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMM")

/**
 * The screen the app exists for. Everything is on one scroll, grouped by category,
 * with no dialogs in the way: logging a day should take well under a minute.
 */
@Composable
fun TodayScreen(viewModel: TodayViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var journalDraft by remember(state.date) { mutableStateOf(state.dayLog?.journal?.text.orEmpty()) }

    LaunchedEffect(state.dayLog?.journal?.text, state.date) {
        val stored = state.dayLog?.journal?.text.orEmpty()
        if (stored != journalDraft && journalDraft.isBlank()) journalDraft = stored
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            DateHeader(
                date = state.date,
                isToday = state.isToday,
                loggedCount = state.loggedCount,
                onShift = viewModel::shiftDate,
                onToday = { viewModel.selectDate(LocalDate.now()) },
            )
        }

        TrackerCategory.entries.forEach { category ->
            val categoryTrackers = state.trackers.filter { it.category == category }
            if (categoryTrackers.isNotEmpty()) {
                item(key = "header-${category.name}") {
                    SectionHeader(title = category.label, subtitle = categorySubtitle(category))
                }
                item(key = "card-${category.name}") {
                    SectionCard {
                        categoryTrackers.forEach { tracker ->
                            TrackerRowFor(tracker = tracker, state = state, viewModel = viewModel)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(title = "Note", subtitle = "Anything worth remembering about today")
        }
        item {
            OutlinedTextField(
                value = journalDraft,
                onValueChange = { journalDraft = it },
                placeholder = { Text("Slept badly, long walk after lunch, knee felt fine...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { viewModel.saveJournal(journalDraft) }) { Text("Save note") }
            }
        }
    }
}

@Composable
private fun TrackerRowFor(tracker: Tracker, state: TodayUiState, viewModel: TodayViewModel) {
    val entry = state.dayLog?.entries?.get(tracker.id)
    TrackerInputRow(
        tracker = tracker,
        value = entry?.value,
        notes = entry?.notes,
        source = entry?.source,
        onValueChange = { viewModel.setValue(tracker, it) },
        onNotesChange = { viewModel.setNotes(tracker, it) },
    )
}

@Composable
private fun DateHeader(
    date: LocalDate,
    isToday: Boolean,
    loggedCount: Int,
    onShift: (Long) -> Unit,
    onToday: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isToday) "Today" else date.format(HEADER_FORMAT),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isToday) date.format(HEADER_FORMAT) else "$loggedCount logged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (!isToday) {
            TextButton(onClick = onToday) { Text("Today") }
        }
        IconButton(onClick = { onShift(1) }, enabled = !isToday) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
        }
    }
}

private fun categorySubtitle(category: TrackerCategory): String? = when (category) {
    TrackerCategory.EXERCISE -> "What you did"
    TrackerCategory.NUTRITION -> "What you ate"
    TrackerCategory.BEHAVIOUR -> "Habits"
    TrackerCategory.JOURNAL -> "How it felt"
    TrackerCategory.HEALTH -> "Only if you want them"
}

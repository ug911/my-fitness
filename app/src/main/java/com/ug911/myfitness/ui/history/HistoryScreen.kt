package com.ug911.myfitness.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.EntrySource
import com.ug911.myfitness.data.model.display
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.SectionCard
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.common.Swatch
import com.ug911.myfitness.ui.theme.TrackingColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMM yyyy")

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader(title = "History", subtitle = "Tap a day to see everything you logged") }

        item {
            Heatmap(
                state = state,
                onSelect = viewModel::select,
            )
        }

        item { Legend() }

        val selected = state.selectedDate
        if (selected != null) {
            item { SectionHeader(title = selected.format(DAY_FORMAT)) }
            val rows = state.entriesFor(selected)
            val journal = state.journalByDate[selected]
            if (rows.isEmpty() && journal == null) {
                item { EmptyState(title = "Nothing logged", body = "This day has no entries.") }
            } else {
                item {
                    SectionCard {
                        rows.forEach { (tracker, entry) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
                                    entry.notes?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                                Text(
                                    text = entry.value.display(tracker),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                                if (entry.source == EntrySource.HEALTH_CONNECT) {
                                    Text(
                                        text = " auto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                        journal?.let {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Note",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                )
                                Text(it.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Week-per-column calendar heatmap, oldest week on the left. */
@Composable
private fun Heatmap(state: HistoryUiState, onSelect: (LocalDate) -> Unit) {
    val firstMonday = state.rangeStart
    val weeks = (ChronoUnit.WEEKS.between(firstMonday, state.rangeEnd) + 1).toInt()

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.width(28.dp)) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(width = 24.dp, height = 17.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (0 until weeks).forEach { weekIndex ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0..6).forEach { dayIndex ->
                        val date = firstMonday.plusWeeks(weekIndex.toLong()).plusDays(dayIndex.toLong())
                        if (date.isAfter(state.rangeEnd)) {
                            Spacer(Modifier.size(14.dp))
                        } else {
                            DayCell(
                                date = date,
                                intensity = state.intensity(date),
                                selected = state.selectedDate == date,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, intensity: DayIntensity, selected: Boolean, onClick: () -> Unit) {
    val color = when (intensity) {
        DayIntensity.NONE -> TrackingColors.empty
        DayIntensity.PARTIAL -> TrackingColors.partial
        DayIntensity.GOOD -> TrackingColors.good
        DayIntensity.FULL -> TrackingColors.full
    }
    val border = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    Column(
        modifier = Modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(border)
            .padding(if (selected) 2.dp else 0.dp)
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier
                .size(if (selected) 10.dp else 14.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        ) {}
    }
}

@Composable
private fun Legend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(start = 28.dp),
    ) {
        Text("nothing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Swatch(TrackingColors.empty)
        Swatch(TrackingColors.partial)
        Swatch(TrackingColors.good)
        Swatch(TrackingColors.full)
        Text("mostly done", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

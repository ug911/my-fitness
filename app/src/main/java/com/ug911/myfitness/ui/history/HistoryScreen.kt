package com.ug911.myfitness.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.EntrySource
import com.ug911.myfitness.data.model.display
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.PlainCard
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.common.StatTile
import com.ug911.myfitness.ui.common.Swatch
import com.ug911.myfitness.ui.icon
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.emptyCell
import com.ug911.myfitness.ui.theme.mutedInkColor
import com.ug911.myfitness.ui.theme.rampStep
import com.ug911.myfitness.ui.theme.rampSteps
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d MMM yyyy")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM")

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Streak",
                    value = "${state.currentStreak}",
                    accent = DaySection.MORNING.accent(),
                    hint = if (state.currentStreak == 1) "day logged" else "days in a row",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Days logged",
                    value = "${state.daysLogged}",
                    accent = DaySection.NIGHT.accent(),
                    hint = "of ${state.totalDays}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Best day",
                    value = state.bestDayLabel,
                    accent = DaySection.EVENING.accent(),
                    hint = "most complete",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            PlainCard {
                Column(Modifier.padding(12.dp)) {
                    Heatmap(state = state, onSelect = viewModel::select)
                    Legend()
                }
            }
        }

        val selected = state.selectedDate
        if (selected == null) {
            item {
                EmptyState(
                    title = "Tap a day",
                    body = "Every square is a day. Tap one to see everything you logged.",
                )
            }
        } else {
            item {
                Text(
                    text = selected.format(DAY_FORMAT),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 4.dp, top = 10.dp),
                )
            }
            val rows = state.entriesFor(selected)
            val journal = state.journalByDate[selected]
            if (rows.isEmpty() && journal == null) {
                item { EmptyState(title = "Nothing logged", body = "This day has no entries.") }
            } else {
                DaySection.entries.forEach { section ->
                    val sectionRows = rows.filter { it.first.section == section }
                    if (sectionRows.isNotEmpty()) {
                        item(key = "h-$selected-${section.name}") {
                            SectionHeader(
                                title = section.label,
                                accent = section.accent(),
                                icon = section.icon,
                            )
                        }
                        item(key = "c-$selected-${section.name}") {
                            PlainCard {
                                sectionRows.forEachIndexed { index, (tracker, entry) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
                                            entry.notes?.let {
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = mutedInkColor(),
                                                )
                                            }
                                        }
                                        Text(
                                            text = entry.value.display(tracker),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = section.accent(),
                                        )
                                        if (entry.source == EntrySource.HEALTH_CONNECT) {
                                            Text(
                                                text = " auto",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = mutedInkColor(),
                                            )
                                        }
                                    }
                                    if (index != sectionRows.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                journal?.let {
                    item(key = "note-$selected") {
                        PlainCard {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Note",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = mutedInkColor(),
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

/**
 * Week-per-column heatmap, oldest week on the left. Magnitude is how complete the day
 * was, so it uses the single-hue sequential ramp; a day with nothing logged is a
 * neutral square outside the ramp, which is itself information.
 */
@Composable
private fun Heatmap(state: HistoryUiState, onSelect: (LocalDate) -> Unit) {
    val firstMonday = state.rangeStart
    val weeks = (ChronoUnit.WEEKS.between(firstMonday, state.rangeEnd) + 1).toInt()

    Column {
        Row(modifier = Modifier.padding(start = 26.dp, bottom = 3.dp)) {
            (0 until weeks).forEach { weekIndex ->
                val weekStart = firstMonday.plusWeeks(weekIndex.toLong())
                val isMonthStart = weekIndex == 0 || weekStart.month != weekStart.minusWeeks(1).month
                Box(modifier = Modifier.width(19.dp)) {
                    if (isMonthStart) {
                        Text(
                            text = weekStart.format(MONTH_FORMAT),
                            style = MaterialTheme.typography.labelSmall,
                            color = mutedInkColor(),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.width(26.dp)) {
                DayOfWeek.entries.forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = mutedInkColor(),
                        modifier = Modifier.size(width = 22.dp, height = 19.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (0 until weeks).forEach { weekIndex ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        (0..6).forEach { dayIndex ->
                            val date = firstMonday.plusWeeks(weekIndex.toLong()).plusDays(dayIndex.toLong())
                            if (date.isAfter(state.rangeEnd)) {
                                Spacer(Modifier.size(16.dp))
                            } else {
                                DayCell(
                                    level = state.level(date),
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
}

@Composable
private fun DayCell(level: Int, selected: Boolean, onClick: () -> Unit) {
    val fill = if (level <= 0) emptyCell() else rampStep(level - 1)
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) MaterialTheme.colorScheme.onBackground else fill)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fill),
            )
        }
    }
}

@Composable
private fun Legend() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(start = 26.dp, top = 10.dp),
    ) {
        Text("nothing", style = MaterialTheme.typography.labelSmall, color = mutedInkColor())
        Swatch(emptyCell())
        rampSteps().forEach { Swatch(it) }
        Text("full day", style = MaterialTheme.typography.labelSmall, color = mutedInkColor())
    }
}

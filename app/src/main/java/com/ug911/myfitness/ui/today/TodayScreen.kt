package com.ug911.myfitness.ui.today

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.formatNumber
import com.ug911.myfitness.ui.common.AccentCard
import com.ug911.myfitness.ui.common.Pill
import com.ug911.myfitness.ui.common.ProgressRing
import com.ug911.myfitness.ui.common.ScreenHeader
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.common.TrackerInputRow
import com.ug911.myfitness.ui.icon
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.cardSurface
import com.ug911.myfitness.ui.theme.gridLine
import com.ug911.myfitness.ui.theme.mutedInkColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Short on purpose: "Wed 16 Sept" fits on one line where the long form wrapped to three. */
private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * The screen the app exists for: the day in the order it happens, from waking up to
 * going to sleep. One scroll, no dialogs, and most rows are a single tap.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    actions: @Composable RowScope.() -> Unit = {},
    onOpenGym: () -> Unit = {},
    onOpenFood: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var journalDraft by remember(state.date) { mutableStateOf(state.dayLog?.journal?.text.orEmpty()) }

    LaunchedEffect(state.dayLog?.journal?.text, state.date) {
        val stored = state.dayLog?.journal?.text.orEmpty()
        if (stored != journalDraft && journalDraft.isBlank()) journalDraft = stored
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            DayHeader(
                date = state.date,
                isToday = state.isToday,
                logged = state.loggedCount,
                total = state.trackers.size,
                onShift = viewModel::shiftDate,
                onToday = { viewModel.selectDate(LocalDate.now()) },
                actions = actions,
            )
        }

        item { DayStrip(summary = state.summary, onOpenGym = onOpenGym, onOpenFood = onOpenFood) }

        DaySection.entries.forEach { section ->
            val sectionTrackers = state.trackers.filter { it.section == section }
            if (sectionTrackers.isNotEmpty()) {
                val done = sectionTrackers.count { state.dayLog?.entries?.containsKey(it.id) == true }
                item(key = "header-${section.name}") {
                    SectionHeader(
                        title = section.label,
                        accent = section.accent(),
                        icon = section.icon,
                        subtitle = section.subtitle,
                        trailing = {
                            if (done > 0) {
                                Pill(text = "$done/${sectionTrackers.size}", accent = section.accent())
                            }
                        },
                    )
                }
                item(key = "card-${section.name}") {
                    AccentCard(accent = section.accent()) {
                        sectionTrackers.forEach { tracker ->
                            TrackerRowFor(tracker, state, section, viewModel)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Anything else",
                accent = mutedInkColor(),
                subtitle = "One line about the day",
            )
        }
        item {
            OutlinedTextField(
                value = journalDraft,
                onValueChange = { journalDraft = it },
                placeholder = { Text("Slept badly, long walk after lunch, knee felt fine...") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
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

/**
 * The two things that are not checkboxes: what the gym owes you today, and what the
 * kitchen has given you so far. Each opens its own screen.
 */
@Composable
private fun DayStrip(summary: DaySummary, onOpenGym: () -> Unit, onOpenFood: () -> Unit) {
    val gymAccent = DaySection.MORNING.accent()
    val foodAccent = DaySection.BREAKFAST.accent()
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SummaryTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            accent = gymAccent,
            icon = Icons.Filled.FitnessCenter,
            label = when {
                summary.isRestDay -> "Rest day"
                summary.hasTraining -> summary.sessionTitle
                else -> "Gym"
            },
            value = if (summary.setsPlanned > 0) {
                "${summary.setsDone}/${summary.setsPlanned}"
            } else {
                "${summary.setsDone}"
            },
            hint = "sets",
            fraction = summary.setsFraction,
            onClick = onOpenGym,
        )
        SummaryTile(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            accent = foodAccent,
            icon = Icons.Filled.LocalDining,
            label = "Food",
            value = formatNumber(summary.macros.kcal),
            hint = if (summary.targets.proteinGrams > 0) {
                "kcal · ${formatNumber(summary.macros.protein)}/${summary.targets.proteinGrams} g protein"
            } else {
                "kcal · ${formatNumber(summary.macros.protein)} g protein"
            },
            fraction = summary.proteinFraction,
            onClick = onOpenFood,
        )
    }
}

@Composable
private fun SummaryTile(
    accent: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    hint: String,
    fraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(targetValue = fraction, label = "tile")
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        color = cardSurface(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = mutedInkColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = mutedInkColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(gridLine()),
            ) {
                Box(
                    Modifier.fillMaxWidth(animated).height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(accent),
                )
            }
        }
    }
}

@Composable
private fun TrackerRowFor(
    tracker: Tracker,
    state: TodayUiState,
    section: DaySection,
    viewModel: TodayViewModel,
) {
    val entry = state.dayLog?.entries?.get(tracker.id)
    TrackerInputRow(
        tracker = tracker,
        value = entry?.value,
        notes = entry?.notes,
        source = entry?.source,
        accent = section.accent(),
        onValueChange = { viewModel.setValue(tracker, it) },
        onNotesChange = { viewModel.setNotes(tracker, it) },
    )
}

/**
 * Title line with the screen's actions, then a compact day strip: arrows, the date, and
 * the ring showing how much of the day is filled in.
 */
@Composable
private fun DayHeader(
    date: LocalDate,
    isToday: Boolean,
    logged: Int,
    total: Int,
    onShift: (Long) -> Unit,
    onToday: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val accent = DaySection.MORNING.accent()
    Column(modifier = Modifier.fillMaxWidth()) {
        ScreenHeader(
            title = if (isToday) "Today" else date.format(DATE_FORMAT),
            subtitle = "$logged of $total logged",
            actions = actions,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 4.dp),
        ) {
            IconButton(onClick = { onShift(-1) }) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous day")
            }
            Text(
                text = date.format(DATE_FORMAT),
                style = MaterialTheme.typography.titleMedium,
                color = if (isToday) accent else MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            IconButton(onClick = { onShift(1) }, enabled = !isToday) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next day")
            }
            if (!isToday) {
                TextButton(onClick = onToday) { Text("Today") }
            }
            Spacer(Modifier.weight(1f))
            ProgressRing(
                fraction = if (total == 0) 0f else logged.toFloat() / total,
                accent = accent,
                modifier = Modifier.size(46.dp),
                strokeWidth = 6,
            ) {
                Text(
                    text = "$logged",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

private fun sectionHint(section: DaySection): String = section.subtitle

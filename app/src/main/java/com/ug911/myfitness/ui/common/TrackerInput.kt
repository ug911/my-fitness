package com.ug911.myfitness.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.EntrySource
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.model.display
import com.ug911.myfitness.data.model.formatNumber
import com.ug911.myfitness.ui.theme.Palette
import com.ug911.myfitness.ui.theme.mutedInkColor
import com.ug911.myfitness.ui.theme.onAccentInk
import java.time.LocalTime

/**
 * One row of the Today screen. Speed is the whole point: a habit is one tap, a time is
 * one tap on a suggested chip, and a checklist is a tap per item - no dialogs unless
 * you want an exact value.
 */
@Composable
fun TrackerInputRow(
    tracker: Tracker,
    value: TrackerValue?,
    notes: String?,
    source: EntrySource?,
    accent: Color,
    onValueChange: (TrackerValue?) -> Unit,
    onNotesChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNotes by remember(tracker.id) { mutableStateOf(!notes.isNullOrBlank()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                when (tracker.type) {
                    TrackerType.BOOLEAN -> BooleanRow(tracker, value, source, accent) { checked ->
                        onValueChange(if (checked) TrackerValue.Flag(true) else null)
                    }

                    TrackerType.RATING -> RatingRow(tracker, value, accent, onValueChange)

                    TrackerType.SELECT -> SelectRow(tracker, value, accent, onValueChange)

                    TrackerType.MULTI_SELECT -> ChecklistRow(tracker, value, accent, onValueChange)

                    TrackerType.TIME -> TimeRow(tracker, value, accent, onValueChange)

                    TrackerType.NUMBER, TrackerType.DURATION ->
                        NumericRow(tracker, value, source, accent, onValueChange)

                    TrackerType.TEXT -> TextRow(tracker, value, onValueChange)
                }
            }
            if (tracker.type != TrackerType.TEXT) {
                IconButton(onClick = { showNotes = !showNotes }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.NoteAdd,
                        contentDescription = if (showNotes) "Hide note" else "Add note",
                        tint = mutedInkColor(),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (showNotes && tracker.type != TrackerType.TEXT) {
            OutlinedTextField(
                value = notes.orEmpty(),
                onValueChange = { text -> onNotesChange(text.takeIf { it.isNotBlank() }) },
                label = { Text("Note") },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun BooleanRow(
    tracker: Tracker,
    value: TrackerValue?,
    source: EntrySource?,
    accent: Color,
    onToggle: (Boolean) -> Unit,
) {
    val checked = (value as? TrackerValue.Flag)?.checked == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(start = 16.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TickBox(checked = checked, accent = accent)
        Text(
            text = tracker.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = 12.dp).weight(1f),
        )
        AutomaticBadge(source)
    }
}

/** A filled tick rather than a checkbox: bigger target, and it reads at a glance. */
@Composable
private fun TickBox(checked: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .then(
                if (checked) {
                    Modifier.background(accent, MaterialTheme.shapes.extraSmall)
                } else {
                    Modifier.background(accent.copy(alpha = 0.12f), MaterialTheme.shapes.extraSmall)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = onAccentInk(),
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun RatingRow(
    tracker: Tracker,
    value: TrackerValue?,
    accent: Color,
    onPick: (TrackerValue?) -> Unit,
) {
    val score = (value as? TrackerValue.Rating)?.score
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tracker.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..tracker.ratingMax).forEach { option ->
                val selected = score != null && option <= score
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            if (selected) accent else accent.copy(alpha = 0.12f),
                            MaterialTheme.shapes.extraSmall,
                        )
                        .clickable { onPick(if (score == option) null else TrackerValue.Rating(option)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) onAccentInk() else accent,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectRow(
    tracker: Tracker,
    value: TrackerValue?,
    accent: Color,
    onPick: (TrackerValue?) -> Unit,
) {
    val selected = (value as? TrackerValue.Choice)?.option
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            tracker.options.forEach { option ->
                AccentChip(
                    label = option,
                    selected = selected == option,
                    accent = accent,
                    onClick = { onPick(if (selected == option) null else TrackerValue.Choice(option)) },
                )
            }
        }
    }
}

/** The gym checklist and breakfast: tick as many as apply. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChecklistRow(
    tracker: Tracker,
    value: TrackerValue?,
    accent: Color,
    onPick: (TrackerValue?) -> Unit,
) {
    val selected = (value as? TrackerValue.Choices)?.selected.orEmpty()
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tracker.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                Pill(text = "${selected.size} of ${tracker.options.size}", accent = accent)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            tracker.options.forEach { option ->
                val isOn = option in selected
                AccentChip(
                    label = option,
                    selected = isOn,
                    accent = accent,
                    onClick = {
                        val next = if (isOn) selected - option else selected + option
                        onPick(next.takeIf { it.isNotEmpty() }?.let(TrackerValue::Choices))
                    },
                )
            }
        }
    }
}

/**
 * A clock time. Suggestions come from the tracker's own target so the usual answer is
 * a single tap; the picker is there for everything else.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(
    tracker: Tracker,
    value: TrackerValue?,
    accent: Color,
    onPick: (TrackerValue?) -> Unit,
) {
    val current = value as? TrackerValue.Time
    var showPicker by remember(tracker.id) { mutableStateOf(false) }

    if (showPicker) {
        val initial = current?.minuteOfDay ?: tracker.targetValue?.toInt() ?: (LocalTime.now().hour * 60)
        val state = rememberTimePickerState(
            initialHour = (initial / 60) % 24,
            initialMinute = initial % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(TrackerValue.time(state.hour, state.minute))
                    showPicker = false
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
            title = { Text(tracker.name) },
            text = { TimePicker(state = state) },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
                tracker.targetLabel()?.let { target ->
                    val met = tracker.meetsTarget(value)
                    Text(
                        text = when (met) {
                            null -> "aim $target"
                            true -> "aim $target - hit it"
                            false -> "aim $target - missed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when (met) {
                            true -> Palette.Good
                            false -> Palette.Critical
                            null -> mutedInkColor()
                        },
                    )
                }
            }
            if (current != null) {
                Text(
                    text = current.display(tracker),
                    style = MaterialTheme.typography.headlineSmall,
                    color = accent,
                    modifier = Modifier.clickable { showPicker = true },
                )
                IconButton(onClick = { onPick(null) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear ${tracker.name}",
                        tint = mutedInkColor(),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (current == null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                suggestedTimes(tracker).forEach { minutes ->
                    AccentChip(
                        label = TrackerValue.Time(minutes).display(tracker),
                        selected = false,
                        accent = accent,
                        onClick = { onPick(TrackerValue.Time(minutes)) },
                    )
                }
                AccentChip(
                    label = "Now",
                    selected = false,
                    accent = accent,
                    onClick = {
                        val now = LocalTime.now()
                        onPick(TrackerValue.time(now.hour, now.minute))
                    },
                )
                AccentChip(
                    label = "Pick",
                    selected = false,
                    accent = accent,
                    onClick = { showPicker = true },
                )
            }
        }
    }
}

/** Times either side of the target, which is where the answer usually lands. */
private fun suggestedTimes(tracker: Tracker): List<Int> {
    val target = tracker.targetValue?.toInt() ?: return emptyList()
    return listOf(target - 30, target - 15, target, target + 15, target + 30)
        .map { (it + TrackerValue.MINUTES_PER_DAY) % TrackerValue.MINUTES_PER_DAY }
}

@Composable
private fun NumericRow(
    tracker: Tracker,
    value: TrackerValue?,
    source: EntrySource?,
    accent: Color,
    onChange: (TrackerValue?) -> Unit,
) {
    val amount = when (value) {
        is TrackerValue.Number -> value.amount
        is TrackerValue.Duration -> value.minutes.toDouble()
        else -> null
    }
    val initial = amount?.let(::formatNumber).orEmpty()
    var text by remember(tracker.id, initial) { mutableStateOf(initial) }

    fun emit(next: Double?) {
        text = next?.let(::formatNumber).orEmpty()
        onChange(
            when {
                next == null -> null
                tracker.type == TrackerType.DURATION -> TrackerValue.Duration(next.toInt())
                else -> TrackerValue.Number(next)
            },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
            val target = tracker.targetLabel()
            Text(
                text = buildString {
                    tracker.unit?.let { append(it) }
                    if (target != null) {
                        if (isNotEmpty()) append(" - ")
                        append("aim $target")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (tracker.meetsTarget(value) == true) Palette.Good else mutedInkColor(),
            )
        }
        AutomaticBadge(source)
        // A stepper for the common case, the field for an exact figure.
        IconButton(onClick = { emit(((amount ?: 0.0) - 1).coerceAtLeast(0.0)) }) {
            Icon(Icons.Filled.Remove, contentDescription = "Less", tint = accent, modifier = Modifier.size(18.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                val parsed = input.replace(',', '.').toDoubleOrNull()
                onChange(
                    when {
                        input.isBlank() || parsed == null -> null
                        tracker.type == TrackerType.DURATION -> TrackerValue.Duration(parsed.toInt())
                        else -> TrackerValue.Number(parsed)
                    },
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(92.dp),
        )
        IconButton(onClick = { emit((amount ?: 0.0) + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = "More", tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun TextRow(tracker: Tracker, value: TrackerValue?, onChange: (TrackerValue?) -> Unit) {
    val initial = (value as? TrackerValue.Text)?.text.orEmpty()
    var text by remember(tracker.id, initial) { mutableStateOf(initial) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it.takeIf { input -> input.isNotBlank() }?.let(TrackerValue::Text))
        },
        label = { Text(tracker.name) },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun AccentChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
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

@Composable
private fun AutomaticBadge(source: EntrySource?) {
    if (source == EntrySource.HEALTH_CONNECT) {
        Icon(
            imageVector = Icons.Filled.Sync,
            contentDescription = "From Health Connect",
            tint = mutedInkColor(),
            modifier = Modifier.size(14.dp),
        )
    }
}

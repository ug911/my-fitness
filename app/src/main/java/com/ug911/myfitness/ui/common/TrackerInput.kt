package com.ug911.myfitness.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.EntrySource
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.model.formatNumber

/**
 * One row of the Today screen. The whole point of the app is that this is fast, so a
 * boolean is a single tap and nothing here opens a dialog.
 */
@Composable
fun TrackerInputRow(
    tracker: Tracker,
    value: TrackerValue?,
    notes: String?,
    source: EntrySource?,
    onValueChange: (TrackerValue?) -> Unit,
    onNotesChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNotes by remember(tracker.id) { mutableStateOf(!notes.isNullOrBlank()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                when (tracker.type) {
                    TrackerType.BOOLEAN -> BooleanRow(tracker, value, source) { checked ->
                        onValueChange(if (checked) TrackerValue.Flag(true) else null)
                    }

                    TrackerType.RATING -> RatingRow(tracker, value) { onValueChange(it) }

                    TrackerType.SELECT -> SelectRow(tracker, value) { onValueChange(it) }

                    TrackerType.NUMBER, TrackerType.DURATION ->
                        NumericRow(tracker, value, source) { onValueChange(it) }

                    TrackerType.TEXT -> TextRow(tracker, value) { onValueChange(it) }
                }
            }
            if (tracker.type != TrackerType.TEXT) {
                NoteToggleButton(expanded = showNotes) { showNotes = !showNotes }
            }
        }

        if (showNotes && tracker.type != TrackerType.TEXT) {
            OutlinedTextField(
                value = notes.orEmpty(),
                onValueChange = { text -> onNotesChange(text.takeIf { it.isNotBlank() }) },
                label = { Text("Note") },
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
    onToggle: (Boolean) -> Unit,
) {
    val checked = (value as? TrackerValue.Flag)?.checked == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle(it) })
        Text(
            text = tracker.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp).weight(1f),
        )
        AutomaticBadge(source)
    }
}

@Composable
private fun RatingRow(tracker: Tracker, value: TrackerValue?, onPick: (TrackerValue?) -> Unit) {
    val score = (value as? TrackerValue.Rating)?.score
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(tracker.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..tracker.ratingMax).forEach { option ->
                FilterChip(
                    selected = score == option,
                    onClick = { onPick(if (score == option) null else TrackerValue.Rating(option)) },
                    label = { Text(option.toString()) },
                )
            }
        }
    }
}

@Composable
private fun SelectRow(tracker: Tracker, value: TrackerValue?, onPick: (TrackerValue?) -> Unit) {
    val selected = (value as? TrackerValue.Choice)?.option
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            tracker.options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onPick(if (selected == option) null else TrackerValue.Choice(option)) },
                    label = { Text(option) },
                )
            }
        }
    }
}

@Composable
private fun NumericRow(
    tracker: Tracker,
    value: TrackerValue?,
    source: EntrySource?,
    onChange: (TrackerValue?) -> Unit,
) {
    val initial = when (value) {
        is TrackerValue.Number -> formatNumber(value.amount)
        is TrackerValue.Duration -> value.minutes.toString()
        else -> ""
    }
    var text by remember(tracker.id, initial) { mutableStateOf(initial) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(tracker.name, style = MaterialTheme.typography.bodyLarge)
            tracker.unit?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        AutomaticBadge(source)
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                val parsed = input.replace(',', '.').toDoubleOrNull()
                onChange(
                    when {
                        input.isBlank() -> null
                        parsed == null -> null
                        tracker.type == TrackerType.DURATION -> TrackerValue.Duration(parsed.toInt())
                        else -> TrackerValue.Number(parsed)
                    },
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(110.dp),
        )
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun AutomaticBadge(source: EntrySource?) {
    if (source == EntrySource.HEALTH_CONNECT) {
        Icon(
            imageVector = Icons.Filled.Sync,
            contentDescription = "From Health Connect",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp).padding(end = 4.dp),
        )
    }
}

@Composable
fun NoteToggleButton(expanded: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.NoteAdd,
            contentDescription = if (expanded) "Hide note" else "Add note",
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

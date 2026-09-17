package com.ug911.myfitness.data.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * The value of one tracker on one day. Stored as text in a single column so a new
 * tracker type never needs a schema change; [encode]/[decode] are the only place
 * that knows the wire format.
 */
sealed interface TrackerValue {
    fun encode(): String

    data class Flag(val checked: Boolean) : TrackerValue {
        override fun encode() = if (checked) "1" else "0"
    }

    data class Number(val amount: Double) : TrackerValue {
        override fun encode() = amount.toString()
    }

    data class Duration(val minutes: Int) : TrackerValue {
        override fun encode() = minutes.toString()
    }

    data class Rating(val score: Int) : TrackerValue {
        override fun encode() = score.toString()
    }

    data class Text(val text: String) : TrackerValue {
        override fun encode() = text
    }

    data class Choice(val option: String) : TrackerValue {
        override fun encode() = option
    }

    /** A clock time, stored as "HH:mm" so the database stays readable. */
    data class Time(val minuteOfDay: Int) : TrackerValue {
        val hour: Int get() = (minuteOfDay / 60) % 24
        val minute: Int get() = minuteOfDay % 60

        override fun encode() = "%02d:%02d".format(hour, minute)
    }

    /** Several ticked options on one day: the gym checklist, what breakfast was. */
    data class Choices(val selected: List<String>) : TrackerValue {
        override fun encode(): String = Json.encodeToString(STRING_LIST, selected)
    }

    companion object {
        internal val STRING_LIST = ListSerializer(String.serializer())

        fun decode(type: TrackerType, raw: String?): TrackerValue? {
            if (raw == null) return null
            return when (type) {
                TrackerType.BOOLEAN -> Flag(raw == "1" || raw.equals("true", ignoreCase = true))
                TrackerType.NUMBER -> raw.toDoubleOrNull()?.let(::Number)
                TrackerType.DURATION -> raw.toIntOrNull()?.let(::Duration)
                TrackerType.RATING -> raw.toIntOrNull()?.let(::Rating)
                TrackerType.TEXT -> raw.takeIf { it.isNotBlank() }?.let(::Text)
                TrackerType.SELECT -> raw.takeIf { it.isNotBlank() }?.let(::Choice)
                TrackerType.TIME -> parseTime(raw)
                TrackerType.MULTI_SELECT -> parseChoices(raw)
            }
        }

        fun time(hour: Int, minute: Int) = Time(((hour % 24) * 60 + minute).coerceIn(0, MINUTES_PER_DAY - 1))

        const val MINUTES_PER_DAY = 24 * 60

        private fun parseTime(raw: String): Time? {
            val parts = raw.split(":")
            if (parts.size != 2) return raw.toIntOrNull()?.let { Time(it.coerceIn(0, MINUTES_PER_DAY - 1)) }
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return Time(hour * 60 + minute)
        }

        private fun parseChoices(raw: String): Choices? {
            val selected = runCatching { Json.decodeFromString(STRING_LIST, raw) }.getOrNull()
                ?: raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            return selected.takeIf { it.isNotEmpty() }?.let(::Choices)
        }
    }
}

/**
 * Whether this value counts as "did the thing" for a day. Used for streaks, adherence
 * and the `5/7 days` lines in an AI snapshot.
 */
fun TrackerValue.isCompleted(tracker: Tracker): Boolean = when (this) {
    is TrackerValue.Flag -> checked
    is TrackerValue.Number -> amount > 0
    is TrackerValue.Duration -> minutes > 0
    is TrackerValue.Rating -> score > 0
    is TrackerValue.Text -> text.isNotBlank()
    is TrackerValue.Choice -> option.isNotBlank() && tracker.options.indexOf(option) != 0
    is TrackerValue.Choices -> selected.isNotEmpty()
    // A recorded time is the log: whether it was a *good* time is the target's job.
    is TrackerValue.Time -> true
}

/** Numeric view of a value, for averages and sums. Null for values with no number in them. */
fun TrackerValue.numeric(): Double? = when (this) {
    is TrackerValue.Flag -> if (checked) 1.0 else 0.0
    is TrackerValue.Number -> amount
    is TrackerValue.Duration -> minutes.toDouble()
    is TrackerValue.Rating -> score.toDouble()
    is TrackerValue.Time -> minuteOfDay.toDouble()
    is TrackerValue.Choices -> selected.size.toDouble()
    is TrackerValue.Choice -> null
    is TrackerValue.Text -> null
}

/** Short human rendering used in lists and day details. */
fun TrackerValue.display(tracker: Tracker): String = when (this) {
    is TrackerValue.Flag -> if (checked) "Yes" else "No"
    is TrackerValue.Number -> formatNumber(amount) + (tracker.unit?.let { " $it" } ?: "")
    is TrackerValue.Duration -> "$minutes min"
    is TrackerValue.Rating -> "$score/${tracker.ratingMax}"
    is TrackerValue.Text -> text
    is TrackerValue.Choice -> option
    is TrackerValue.Time -> "%02d:%02d".format(hour, minute)
    is TrackerValue.Choices -> when {
        selected.isEmpty() -> "none"
        selected.size <= 3 -> selected.joinToString(", ")
        else -> "${selected.size} of ${tracker.options.size}"
    }
}

fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

/** Renders minutes past midnight as a clock time, for targets and averages. */
fun formatMinuteOfDay(minutes: Double): String {
    val rounded = ((Math.round(minutes).toInt()) % TrackerValue.MINUTES_PER_DAY + TrackerValue.MINUTES_PER_DAY) %
        TrackerValue.MINUTES_PER_DAY
    return "%02d:%02d".format(rounded / 60, rounded % 60)
}

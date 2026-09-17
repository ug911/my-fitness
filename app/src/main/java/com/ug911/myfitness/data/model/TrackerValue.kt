package com.ug911.myfitness.data.model

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

    companion object {
        fun decode(type: TrackerType, raw: String?): TrackerValue? {
            if (raw == null) return null
            return when (type) {
                TrackerType.BOOLEAN -> Flag(raw == "1" || raw.equals("true", ignoreCase = true))
                TrackerType.NUMBER -> raw.toDoubleOrNull()?.let(::Number)
                TrackerType.DURATION -> raw.toIntOrNull()?.let(::Duration)
                TrackerType.RATING -> raw.toIntOrNull()?.let(::Rating)
                TrackerType.TEXT -> raw.takeIf { it.isNotBlank() }?.let(::Text)
                TrackerType.SELECT -> raw.takeIf { it.isNotBlank() }?.let(::Choice)
            }
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
}

/** Numeric view of a value, for averages and sums. Null for values with no number in them. */
fun TrackerValue.numeric(): Double? = when (this) {
    is TrackerValue.Flag -> if (checked) 1.0 else 0.0
    is TrackerValue.Number -> amount
    is TrackerValue.Duration -> minutes.toDouble()
    is TrackerValue.Rating -> score.toDouble()
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
}

fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

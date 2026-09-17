package com.ug911.myfitness.analysis

import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.model.formatMinuteOfDay
import com.ug911.myfitness.data.model.formatNumber
import com.ug911.myfitness.data.model.isCompleted
import com.ug911.myfitness.data.model.numeric
import java.time.LocalDate

/**
 * The cheap local answer to "when I do X, what happens to Y?" — the question this app
 * exists to ask. Deliberately descriptive: it splits days by a condition and reports
 * the two averages. It does not claim causation, and it refuses to report a split with
 * too little data on either side.
 */
object PatternAnalysis {

    const val MIN_DAYS_PER_SIDE = 3

    fun compare(
        condition: Tracker,
        outcome: Tracker,
        entries: List<Entry>,
        lagDays: Long = 0,
    ): OutcomeSplit? {
        if (condition.id == outcome.id) return null
        val conditionDays = entries.filter { it.trackerId == condition.id }
            .associate { it.date to it.value.isCompleted(condition) }
        val outcomeValues = entries.filter { it.trackerId == outcome.id }
            .mapNotNull { entry -> entry.value.numeric()?.let { entry.date to it } }
            .toMap()

        val withCondition = mutableListOf<Double>()
        val withoutCondition = mutableListOf<Double>()
        for ((date, done) in conditionDays) {
            val outcomeValue = outcomeValues[date.plusDays(lagDays)] ?: continue
            if (done) withCondition += outcomeValue else withoutCondition += outcomeValue
        }
        if (withCondition.size < MIN_DAYS_PER_SIDE || withoutCondition.size < MIN_DAYS_PER_SIDE) return null

        val isTime = outcome.type == TrackerType.TIME
        return OutcomeSplit(
            condition = condition,
            outcome = outcome,
            lagDays = lagDays,
            averageWhenDone = mean(withCondition, isTime),
            averageWhenNotDone = mean(withoutCondition, isTime),
            daysWhenDone = withCondition.size,
            daysWhenNotDone = withoutCondition.size,
            isClockTime = isTime,
        )
    }

    /**
     * Every split worth showing, strongest difference first. Conditions are habit-style
     * trackers, outcomes are anything numeric (ratings, weight, sleep, steps).
     */
    fun rank(
        trackers: List<Tracker>,
        entries: List<Entry>,
        outcomes: List<Tracker>,
        lagDays: Long = 0,
        limit: Int = 5,
    ): List<OutcomeSplit> {
        val conditions = trackers.filter { it.isHabitLike() }
        return conditions
            .flatMap { condition -> outcomes.mapNotNull { compare(condition, it, entries, lagDays) } }
            .sortedByDescending { kotlin.math.abs(it.difference) }
            .take(limit)
    }

    private fun Tracker.isHabitLike(): Boolean =
        aggregation == com.ug911.myfitness.data.model.Aggregation.DAYS_COMPLETED

    /** Clock times average around the circle; everything else is a plain mean. */
    private fun mean(values: List<Double>, isTime: Boolean): Double = PeriodStats.round1(
        if (isTime) PeriodStats.circularMeanMinutes(values) else values.average(),
    )
}

data class OutcomeSplit(
    val condition: Tracker,
    val outcome: Tracker,
    val lagDays: Long,
    val averageWhenDone: Double,
    val averageWhenNotDone: Double,
    val daysWhenDone: Int,
    val daysWhenNotDone: Int,
    val isClockTime: Boolean = false,
) {
    /**
     * For a clock time the gap is the shortest way round the 24-hour circle, so a
     * bedtime moving from 23:50 to 00:10 reads as 20 minutes later, not 23 hours earlier.
     */
    val difference: Double
        get() = PeriodStats.round1(
            if (isClockTime) {
                shortestArc(averageWhenDone - averageWhenNotDone)
            } else {
                averageWhenDone - averageWhenNotDone
            },
        )

    val totalDays: Int get() = daysWhenDone + daysWhenNotDone

    /** Same-day vs. morning-after framing for the UI. */
    val lagLabel: String get() = if (lagDays == 0L) "same day" else "next day"

    /** The two averages as they should be read: a time as a time, a rating as a number. */
    fun formatWhenDone(): String = format(averageWhenDone)

    fun formatWhenNotDone(): String = format(averageWhenNotDone)

    private fun format(value: Double): String =
        if (isClockTime) formatMinuteOfDay(value) else formatNumber(value)

    fun describe(): String {
        val magnitude = kotlin.math.abs(difference)
        return if (isClockTime) {
            val direction = if (difference >= 0) "later" else "earlier"
            "${outcome.name} is ${magnitude.toInt()} min $direction the $lagLabel " +
                "you log ${condition.name} ($daysWhenDone vs $daysWhenNotDone days)"
        } else {
            val direction = if (difference >= 0) "higher" else "lower"
            "${outcome.name} averages ${PeriodStats.round1(magnitude)} $direction the $lagLabel " +
                "you log ${condition.name} ($daysWhenDone vs $daysWhenNotDone days)"
        }
    }
}

/** Maps a minute difference into -720..720, the shortest way around the clock. */
private fun shortestArc(delta: Double): Double {
    val half = TrackerValue.MINUTES_PER_DAY / 2.0
    var value = delta
    while (value > half) value -= TrackerValue.MINUTES_PER_DAY
    while (value < -half) value += TrackerValue.MINUTES_PER_DAY
    return value
}

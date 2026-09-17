package com.ug911.myfitness.analysis

import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.Tracker
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

        return OutcomeSplit(
            condition = condition,
            outcome = outcome,
            lagDays = lagDays,
            averageWhenDone = PeriodStats.round1(withCondition.average()),
            averageWhenNotDone = PeriodStats.round1(withoutCondition.average()),
            daysWhenDone = withCondition.size,
            daysWhenNotDone = withoutCondition.size,
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
}

data class OutcomeSplit(
    val condition: Tracker,
    val outcome: Tracker,
    val lagDays: Long,
    val averageWhenDone: Double,
    val averageWhenNotDone: Double,
    val daysWhenDone: Int,
    val daysWhenNotDone: Int,
) {
    val difference: Double get() = PeriodStats.round1(averageWhenDone - averageWhenNotDone)

    val totalDays: Int get() = daysWhenDone + daysWhenNotDone

    /** Same-day vs. morning-after framing for the UI. */
    val lagLabel: String get() = if (lagDays == 0L) "same day" else "next day"

    fun describe(): String {
        val direction = if (difference >= 0) "higher" else "lower"
        val magnitude = PeriodStats.round1(kotlin.math.abs(difference))
        return "${outcome.name} averages $magnitude $direction the ${lagLabel} " +
            "you log ${condition.name} ($daysWhenDone vs $daysWhenNotDone days)"
    }
}

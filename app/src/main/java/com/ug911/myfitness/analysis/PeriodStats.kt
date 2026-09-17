package com.ug911.myfitness.analysis

import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.model.formatNumber
import com.ug911.myfitness.data.model.isCompleted
import com.ug911.myfitness.data.model.numeric
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One tracker collapsed over one window of days. Pure data, no Android dependencies. */
data class TrackerStat(
    val tracker: Tracker,
    val days: Int,
    val daysLogged: Int,
    val daysCompleted: Int,
    val sum: Double?,
    val average: Double?,
    val min: Double?,
    val max: Double?,
    val latest: Double?,
    val optionCounts: Map<String, Int> = emptyMap(),
) {
    /** The number a review should quote for this tracker, per its aggregation. */
    val headline: Double?
        get() = when (tracker.aggregation) {
            Aggregation.DAYS_COMPLETED -> daysCompleted.toDouble()
            Aggregation.SUM -> sum
            Aggregation.AVERAGE -> average
            Aggregation.LATEST -> latest
            Aggregation.NONE -> null
        }

    /** Short human/AI-readable rendering: "5/7 days", "185 min", "3.4". */
    fun summary(): String = when (tracker.aggregation) {
        Aggregation.DAYS_COMPLETED -> "$daysCompleted/$days days"
        Aggregation.SUM -> sum?.let { formatNumber(it) + unitSuffix() } ?: "no data"
        Aggregation.AVERAGE -> average?.let { formatNumber(PeriodStats.round1(it)) + unitSuffix() } ?: "no data"
        Aggregation.LATEST -> latest?.let { formatNumber(it) + unitSuffix() } ?: "no data"
        Aggregation.NONE -> if (daysLogged > 0) "$daysLogged entries" else "no data"
    }

    private fun unitSuffix(): String = tracker.unit?.let { " $it" } ?: ""
}

object PeriodStats {

    fun dayCount(start: LocalDate, end: LocalDate): Int =
        (ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(0)

    fun computeAll(
        trackers: List<Tracker>,
        entries: List<Entry>,
        start: LocalDate,
        end: LocalDate,
    ): List<TrackerStat> {
        val byTracker = entries.filter { it.date in start..end }.groupBy { it.trackerId }
        val days = dayCount(start, end)
        return trackers.map { tracker -> compute(tracker, byTracker[tracker.id].orEmpty(), days) }
    }

    fun compute(tracker: Tracker, entries: List<Entry>, days: Int): TrackerStat {
        val numbers = entries.mapNotNull { it.value.numeric() }
        val completed = entries.count { it.value.isCompleted(tracker) }
        val options = if (tracker.type == TrackerType.SELECT) {
            entries.mapNotNull { (it.value as? TrackerValue.Choice)?.option }
                .groupingBy { it }
                .eachCount()
        } else {
            emptyMap()
        }
        return TrackerStat(
            tracker = tracker,
            days = days,
            daysLogged = entries.size,
            daysCompleted = completed,
            sum = numbers.takeIf { it.isNotEmpty() }?.sum(),
            average = numbers.takeIf { it.isNotEmpty() }?.average(),
            min = numbers.minOrNull(),
            max = numbers.maxOrNull(),
            latest = entries.maxByOrNull { it.date }?.value?.numeric(),
            optionCounts = options,
        )
    }

    /**
     * One point per week for a tracker, oldest first: what the Insights sparklines draw.
     * Each point is the tracker's own aggregation applied to that week.
     */
    fun weeklySeries(
        tracker: Tracker,
        entries: List<Entry>,
        end: LocalDate,
        weeks: Int,
    ): List<SeriesPoint> {
        val mine = entries.filter { it.trackerId == tracker.id }
        return (weeks - 1 downTo 0).map { back ->
            val weekEnd = end.minusWeeks(back.toLong())
            val weekStart = weekEnd.minusDays(6)
            val stat = compute(tracker, mine.filter { it.date in weekStart..weekEnd }, 7)
            SeriesPoint(weekStart, weekEnd, stat.headline, stat.daysLogged)
        }
    }

    fun round1(value: Double): Double = Math.round(value * 10.0) / 10.0
}

data class SeriesPoint(
    val start: LocalDate,
    val end: LocalDate,
    val value: Double?,
    val daysLogged: Int,
)

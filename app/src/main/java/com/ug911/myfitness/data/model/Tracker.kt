package com.ug911.myfitness.data.model

/**
 * A Tracker is the single configurable unit of the app. Adding something new to log
 * means adding a row, never a migration.
 */
data class Tracker(
    val id: Long = 0,
    val name: String,
    val section: DaySection,
    val type: TrackerType,
    val unit: String? = null,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    /** Options for [TrackerType.SELECT] and [TrackerType.MULTI_SELECT], in display order. */
    val options: List<String> = emptyList(),
    /** Top of the scale for [TrackerType.RATING]. */
    val ratingMax: Int = 5,
    /** When set, this tracker is filled in automatically from Health Connect. */
    val healthMetric: HealthMetric? = null,
    /** Which way is "better"; drives progress colouring and how the AI reads a trend. */
    val direction: Direction = Direction.UP,
    /** How a period of daily values collapses into one number for reviews. */
    val aggregation: Aggregation = Aggregation.defaultFor(type),
    /**
     * A standing personal target, shown on Today as "aim 05:00" or "aim 8 glasses".
     * For [TrackerType.TIME] it is minutes past midnight. This is deliberately separate
     * from a weekly [Plan] target: this is the habit you are always aiming at, a plan
     * target is what you committed to for one week.
     */
    val targetValue: Double? = null,
) {
    val isAutomatic: Boolean get() = healthMetric != null

    /** Stable snake_case key used in AI snapshots and anywhere a machine reads this tracker. */
    val key: String get() = slugify(name)

    /** The target rendered the way the tracker's own type reads. */
    fun targetLabel(): String? {
        val target = targetValue ?: return null
        return when (type) {
            TrackerType.TIME -> TrackerValue.Time(target.toInt()).display(this)
            TrackerType.DURATION -> "${target.toInt()} min"
            else -> formatNumber(target) + (unit?.let { " $it" } ?: "")
        }
    }

    /** Whether a logged value hits the standing target, given the tracker's direction. */
    fun meetsTarget(value: TrackerValue?): Boolean? {
        val target = targetValue ?: return null
        val actual = comparableValue(value) ?: return null
        return when (direction) {
            Direction.DOWN -> actual <= target
            Direction.UP -> actual >= target
            Direction.NEUTRAL -> true
        }
    }

    /**
     * A bedtime of 00:20 is 20 minutes past midnight but *later* than a 23:00 target,
     * so for an evening time target the small hours count as the previous day running on.
     */
    private fun comparableValue(value: TrackerValue?): Double? {
        val actual = value?.numeric() ?: return null
        val target = targetValue ?: return actual
        val eveningTarget = type == TrackerType.TIME && target >= EVENING_TARGET_FROM
        return if (eveningTarget && actual < SMALL_HOURS_UNTIL) {
            actual + TrackerValue.MINUTES_PER_DAY
        } else {
            actual
        }
    }

    companion object {
        /** A time target at or after 18:00 is an evening one, so it can wrap past midnight. */
        private const val EVENING_TARGET_FROM = 18 * 60.0
        private const val SMALL_HOURS_UNTIL = 6 * 60.0

        fun slugify(name: String): String = name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }
}

/**
 * Sections follow the shape of the day rather than abstract categories: you log in the
 * order things happen, from waking up to going to sleep.
 */
enum class DaySection(val label: String, val subtitle: String) {
    MORNING("Morning", "Wake up and the gym"),
    SCHOOL_RUN("School run", "Dropping Vihaan and back"),
    BREAKFAST("Breakfast", "What you ate and drank"),
    OFFICE("Office", "In, out, and the hours between"),
    EVENING("Evening", "Home, Vihaan, dinner"),
    NIGHT("Night", "Winding down and how the day felt"),
    BODY("Body", "Mostly filled in by Health Connect"),
    ;

    /** Key used for this section's object in the AI snapshot. */
    val key: String get() = name.lowercase()
}

enum class TrackerType {
    BOOLEAN,
    NUMBER,
    DURATION,
    RATING,
    TEXT,
    SELECT,

    /** A clock time: woke up, reached the office, went to sleep. */
    TIME,

    /** A checklist where several options can be ticked on the same day. */
    MULTI_SELECT,
}

enum class Direction {
    /** More/higher/later is better: steps, protein, playing with Vihaan. */
    UP,

    /** Less/lower/earlier is better: junk food, late meetings, wake-up time. */
    DOWN,

    /** Neither; just observe. */
    NEUTRAL,
}

enum class Aggregation {
    /** Count of days the tracker was completed (habits, checklists). */
    DAYS_COMPLETED,
    SUM,
    AVERAGE,
    LATEST,
    NONE,
    ;

    companion object {
        fun defaultFor(type: TrackerType): Aggregation = when (type) {
            TrackerType.BOOLEAN -> DAYS_COMPLETED
            TrackerType.DURATION -> SUM
            TrackerType.NUMBER -> AVERAGE
            TrackerType.RATING -> AVERAGE
            TrackerType.SELECT -> DAYS_COMPLETED
            TrackerType.MULTI_SELECT -> DAYS_COMPLETED
            TrackerType.TIME -> AVERAGE
            TrackerType.TEXT -> NONE
        }
    }
}

/** The Health Connect records the app knows how to turn into daily tracker values. */
enum class HealthMetric {
    STEPS,
    EXERCISE_MINUTES,
    EXERCISE_SESSIONS,
    SLEEP_DURATION,

    /** When the night's sleep began - fills in "Slept at" without typing. */
    SLEEP_START,

    /** When it ended - fills in "Woke up". */
    SLEEP_END,
    RESTING_HEART_RATE,
    ACTIVE_CALORIES,
    WEIGHT,
}

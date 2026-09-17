package com.ug911.myfitness.data.model

/**
 * A Tracker is the single configurable unit of the app: "Strength training", "Weight",
 * "Energy", "No alcohol". Adding something new to log means adding a row, never a migration.
 */
data class Tracker(
    val id: Long = 0,
    val name: String,
    val category: TrackerCategory,
    val type: TrackerType,
    val unit: String? = null,
    val active: Boolean = true,
    val sortOrder: Int = 0,
    /** Options for [TrackerType.SELECT], in display order. */
    val options: List<String> = emptyList(),
    /** Top of the scale for [TrackerType.RATING]. */
    val ratingMax: Int = 5,
    /** When set, this tracker is filled in automatically from Health Connect. */
    val healthMetric: HealthMetric? = null,
    /** Which way is "better"; drives progress colouring and how the AI reads a trend. */
    val direction: Direction = Direction.UP,
    /** How a period of daily values collapses into one number for reviews. */
    val aggregation: Aggregation = Aggregation.defaultFor(type),
) {
    val isAutomatic: Boolean get() = healthMetric != null

    /** Stable snake_case key used in AI snapshots and anywhere a machine reads this tracker. */
    val key: String get() = slugify(name)

    companion object {
        fun slugify(name: String): String = name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
    }
}

enum class TrackerCategory(val label: String) {
    EXERCISE("Exercise"),
    NUTRITION("Food"),
    BEHAVIOUR("Behaviour"),
    JOURNAL("Journal"),
    HEALTH("Health"),
    ;

    /** Key used for this category's object in the AI snapshot. */
    val key: String get() = name.lowercase()
}

enum class TrackerType {
    BOOLEAN,
    NUMBER,
    DURATION,
    RATING,
    TEXT,
    SELECT,
}

enum class Direction {
    /** More/higher is better: steps, protein, energy. */
    UP,

    /** Less/lower is better: junk food, late-night eating, resting heart rate. */
    DOWN,

    /** Neither; just observe. */
    NEUTRAL,
}

enum class Aggregation {
    /** Count of days the tracker was completed (booleans, habits). */
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
    RESTING_HEART_RATE,
    ACTIVE_CALORIES,
    WEIGHT,
}

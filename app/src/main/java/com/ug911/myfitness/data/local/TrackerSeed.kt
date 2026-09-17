package com.ug911.myfitness.data.local

import com.ug911.myfitness.data.model.Tracker

/**
 * Seeding and upgrading the tracker table, as plain SQL.
 *
 * A fresh install and an upgrade from the old category-based set run the *same*
 * statements, so the two paths cannot drift apart. Kept free of Android types so the
 * SQL can be executed against a plain SQLite connection in a unit test.
 */
object TrackerSeed {

    /** Executes one statement with positional arguments. */
    fun interface Executor {
        fun exec(sql: String, args: Array<Any?>)
    }

    private const val INSERT_IF_MISSING =
        "INSERT INTO trackers (name, category, type, unit, active, sortOrder, options, ratingMax, " +
            "healthMetric, direction, aggregation, targetValue) " +
            "SELECT ?,?,?,?,?,?,?,?,?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM trackers WHERE name = ?)"

    /**
     * Updates a tracker that already exists under the same name, rather than inserting a
     * duplicate. Entries point at tracker ids, so reusing the row keeps its history.
     */
    private const val UPDATE_EXISTING =
        "UPDATE trackers SET category = ?, type = ?, unit = ?, active = ?, sortOrder = ?, options = ?, " +
            "ratingMax = ?, healthMetric = ?, direction = ?, aggregation = ?, targetValue = ? WHERE name = ?"

    /** Everything the app no longer shows is switched off, never deleted. */
    private const val DEACTIVATE_ALL = "UPDATE trackers SET active = 0"

    /**
     * The pre-timeline release grouped trackers into Exercise/Food/Behaviour/Journal/Health.
     * Those rows are remapped onto the day-shaped sections so old entries stay readable.
     */
    private val LEGACY_SECTIONS = listOf(
        "EXERCISE" to "MORNING",
        "NUTRITION" to "BREAKFAST",
        "BEHAVIOUR" to "NIGHT",
        "JOURNAL" to "NIGHT",
        "HEALTH" to "BODY",
    )

    fun remapLegacySections(executor: Executor) {
        LEGACY_SECTIONS.forEach { (old, new) ->
            executor.exec("UPDATE trackers SET category = ? WHERE category = ?", arrayOf(new, old))
        }
    }

    fun deactivateAll(executor: Executor) = executor.exec(DEACTIVATE_ALL, emptyArray())

    /** Inserts the given trackers, or updates same-named rows in place. */
    fun upsert(executor: Executor, trackers: List<Tracker>, converters: Converters = Converters()) {
        trackers.forEach { tracker ->
            val options = converters.listToString(tracker.options)
            executor.exec(
                UPDATE_EXISTING,
                arrayOf(
                    tracker.section.name,
                    tracker.type.name,
                    tracker.unit,
                    if (tracker.active) 1 else 0,
                    tracker.sortOrder,
                    options,
                    tracker.ratingMax,
                    tracker.healthMetric?.name,
                    tracker.direction.name,
                    tracker.aggregation.name,
                    tracker.targetValue,
                    tracker.name,
                ),
            )
            executor.exec(
                INSERT_IF_MISSING,
                arrayOf(
                    tracker.name,
                    tracker.section.name,
                    tracker.type.name,
                    tracker.unit,
                    if (tracker.active) 1 else 0,
                    tracker.sortOrder,
                    options,
                    tracker.ratingMax,
                    tracker.healthMetric?.name,
                    tracker.direction.name,
                    tracker.aggregation.name,
                    tracker.targetValue,
                    tracker.name,
                ),
            )
        }
    }

    /** Fresh install: just the personal day. */
    fun seedFresh(executor: Executor) = upsert(executor, PersonalDay.trackers())

    /**
     * Upgrade from the category-based set: keep every entry, retire the old trackers,
     * and adopt the day-shaped ones - reusing rows where a name matches so that, say,
     * Weight keeps the readings already logged against it.
     */
    fun migrateToPersonalDay(executor: Executor) {
        remapLegacySections(executor)
        deactivateAll(executor)
        upsert(executor, PersonalDay.trackers())
    }
}

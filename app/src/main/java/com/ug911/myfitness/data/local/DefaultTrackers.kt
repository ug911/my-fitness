package com.ug911.myfitness.data.local

import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.HealthMetric
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerCategory
import com.ug911.myfitness.data.model.TrackerType

/**
 * The starter set of trackers. Everything here is editable or deletable in the app;
 * this is only the "sensible first day" configuration.
 */
object DefaultTrackers {

    fun all(): List<Tracker> = buildList {
        addAll(exercise())
        addAll(nutrition())
        addAll(behaviour())
        addAll(journal())
        addAll(health())
    }

    private fun exercise() = listOf(
        bool("Strength training", TrackerCategory.EXERCISE, 0),
        bool("Cardio", TrackerCategory.EXERCISE, 1),
        bool("Walk", TrackerCategory.EXERCISE, 2),
        bool("Mobility / stretching", TrackerCategory.EXERCISE, 3),
        Tracker(
            name = "Exercise duration",
            category = TrackerCategory.EXERCISE,
            type = TrackerType.DURATION,
            unit = "min",
            sortOrder = 4,
            aggregation = Aggregation.SUM,
        ),
        Tracker(
            name = "Steps",
            category = TrackerCategory.EXERCISE,
            type = TrackerType.NUMBER,
            unit = "steps",
            sortOrder = 5,
            healthMetric = HealthMetric.STEPS,
            aggregation = Aggregation.AVERAGE,
        ),
        Tracker(
            name = "Health Connect exercise minutes",
            category = TrackerCategory.EXERCISE,
            type = TrackerType.DURATION,
            unit = "min",
            sortOrder = 6,
            healthMetric = HealthMetric.EXERCISE_MINUTES,
            aggregation = Aggregation.SUM,
        ),
        Tracker(
            name = "Active calories",
            category = TrackerCategory.EXERCISE,
            type = TrackerType.NUMBER,
            unit = "kcal",
            active = false,
            sortOrder = 7,
            healthMetric = HealthMetric.ACTIVE_CALORIES,
            aggregation = Aggregation.AVERAGE,
        ),
    )

    private fun nutrition() = listOf(
        bool("Breakfast", TrackerCategory.NUTRITION, 0),
        bool("Lunch", TrackerCategory.NUTRITION, 1),
        bool("Dinner", TrackerCategory.NUTRITION, 2),
        bool("Protein target", TrackerCategory.NUTRITION, 3),
        bool("Vegetables", TrackerCategory.NUTRITION, 4),
        bool("Fruit", TrackerCategory.NUTRITION, 5),
        bool("Junk food", TrackerCategory.NUTRITION, 6, Direction.DOWN),
        bool("Late-night eating", TrackerCategory.NUTRITION, 7, Direction.DOWN),
        Tracker(
            name = "Alcohol",
            category = TrackerCategory.NUTRITION,
            type = TrackerType.SELECT,
            sortOrder = 8,
            options = listOf("None", "1", "2", "3+"),
            direction = Direction.DOWN,
            aggregation = Aggregation.DAYS_COMPLETED,
        ),
        Tracker(
            name = "Meal note",
            category = TrackerCategory.NUTRITION,
            type = TrackerType.TEXT,
            sortOrder = 9,
            direction = Direction.NEUTRAL,
            aggregation = Aggregation.NONE,
        ),
    )

    private fun behaviour() = listOf(
        bool("Bed before 11:30", TrackerCategory.BEHAVIOUR, 0),
        bool("Supplements", TrackerCategory.BEHAVIOUR, 1),
        bool("Meditation", TrackerCategory.BEHAVIOUR, 2),
        bool("Caffeine after 4pm", TrackerCategory.BEHAVIOUR, 3, Direction.DOWN),
        bool("Screen in bed", TrackerCategory.BEHAVIOUR, 4, Direction.DOWN),
        Tracker(
            name = "Sleep duration",
            category = TrackerCategory.BEHAVIOUR,
            type = TrackerType.DURATION,
            unit = "min",
            sortOrder = 5,
            healthMetric = HealthMetric.SLEEP_DURATION,
            aggregation = Aggregation.AVERAGE,
        ),
    )

    private fun journal() = listOf(
        rating("Energy", 0),
        rating("Mood", 1),
        rating("Hunger", 2, Direction.NEUTRAL),
        rating("Soreness", 3, Direction.DOWN),
        rating("Stress", 4, Direction.DOWN),
    )

    private fun health() = listOf(
        Tracker(
            name = "Weight",
            category = TrackerCategory.HEALTH,
            type = TrackerType.NUMBER,
            unit = "kg",
            sortOrder = 0,
            direction = Direction.NEUTRAL,
            aggregation = Aggregation.AVERAGE,
            healthMetric = HealthMetric.WEIGHT,
        ),
        Tracker(
            name = "Waist",
            category = TrackerCategory.HEALTH,
            type = TrackerType.NUMBER,
            unit = "cm",
            active = false,
            sortOrder = 1,
            direction = Direction.DOWN,
            aggregation = Aggregation.AVERAGE,
        ),
        Tracker(
            name = "Resting heart rate",
            category = TrackerCategory.HEALTH,
            type = TrackerType.NUMBER,
            unit = "bpm",
            sortOrder = 2,
            direction = Direction.DOWN,
            aggregation = Aggregation.AVERAGE,
            healthMetric = HealthMetric.RESTING_HEART_RATE,
        ),
    )

    private fun bool(
        name: String,
        category: TrackerCategory,
        order: Int,
        direction: Direction = Direction.UP,
    ) = Tracker(
        name = name,
        category = category,
        type = TrackerType.BOOLEAN,
        sortOrder = order,
        direction = direction,
    )

    private fun rating(name: String, order: Int, direction: Direction = Direction.UP) = Tracker(
        name = name,
        category = TrackerCategory.JOURNAL,
        type = TrackerType.RATING,
        sortOrder = order,
        ratingMax = 5,
        direction = direction,
    )
}

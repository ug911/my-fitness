package com.ug911.myfitness.data.local

import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.HealthMetric
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType

/**
 * The tracker set this app is built around: one person's actual day, in the order it
 * happens. Everything here is editable or removable in the app - it is the starting
 * configuration, not a schema.
 */
object PersonalDay {

    /** 05:00, as minutes past midnight. */
    private const val FIVE_AM = 5 * 60.0
    private const val ELEVEN_PM = 23 * 60.0

    val EXERCISES = listOf(
        "Shoulders",
        "Biceps",
        "Hamstrings",
        "Quads",
        "Calves",
        "Abs",
        "Back",
        "Lats",
        "Push-ups",
        "Sit-ups",
        "Jumping jacks",
    )

    val BREAKFAST_ITEMS = listOf("Chicken", "Eggs", "Dosa", "Bohara")

    fun trackers(): List<Tracker> = buildList {
        addAll(morning())
        addAll(schoolRun())
        addAll(breakfast())
        addAll(office())
        addAll(evening())
        addAll(night())
        addAll(body())
    }

    private fun morning() = listOf(
        Tracker(
            name = "Woke up",
            section = DaySection.MORNING,
            type = TrackerType.TIME,
            sortOrder = 0,
            direction = Direction.DOWN,
            targetValue = FIVE_AM,
            // Filled in from sleep tracking when there is any; typing over it always wins.
            healthMetric = HealthMetric.SLEEP_END,
        ),
        bool("Gym", DaySection.MORNING, 1),
        bool("Treadmill", DaySection.MORNING, 2),
        Tracker(
            name = "Exercises",
            section = DaySection.MORNING,
            type = TrackerType.MULTI_SELECT,
            sortOrder = 3,
            options = EXERCISES,
        ),
        Tracker(
            name = "Gym minutes",
            section = DaySection.MORNING,
            type = TrackerType.DURATION,
            unit = "min",
            active = false,
            sortOrder = 4,
            aggregation = Aggregation.SUM,
        ),
    )

    private fun schoolRun() = listOf(
        bool("Dropped Vihaan at school", DaySection.SCHOOL_RUN, 0),
        Tracker(
            name = "Left for school",
            section = DaySection.SCHOOL_RUN,
            type = TrackerType.TIME,
            sortOrder = 1,
            direction = Direction.NEUTRAL,
        ),
        Tracker(
            name = "Back from school",
            section = DaySection.SCHOOL_RUN,
            type = TrackerType.TIME,
            sortOrder = 2,
            direction = Direction.NEUTRAL,
        ),
    )

    private fun breakfast() = listOf(
        Tracker(
            name = "Breakfast",
            section = DaySection.BREAKFAST,
            type = TrackerType.MULTI_SELECT,
            sortOrder = 0,
            options = BREAKFAST_ITEMS,
        ),
        Tracker(
            name = "Coffee",
            section = DaySection.BREAKFAST,
            type = TrackerType.SELECT,
            sortOrder = 1,
            options = listOf("None", "Hot", "Cold"),
            direction = Direction.NEUTRAL,
        ),
        Tracker(
            name = "Coffee timing",
            section = DaySection.BREAKFAST,
            type = TrackerType.SELECT,
            sortOrder = 2,
            options = listOf("Before drop", "After drop"),
            direction = Direction.NEUTRAL,
            aggregation = Aggregation.NONE,
        ),
    )

    private fun office() = listOf(
        Tracker(
            name = "Reached office",
            section = DaySection.OFFICE,
            type = TrackerType.TIME,
            sortOrder = 0,
            direction = Direction.NEUTRAL,
        ),
        Tracker(
            name = "Left office",
            section = DaySection.OFFICE,
            type = TrackerType.TIME,
            sortOrder = 1,
            direction = Direction.NEUTRAL,
        ),
        bool("Ate at office", DaySection.OFFICE, 2, Direction.NEUTRAL),
        Tracker(
            name = "Water at office",
            section = DaySection.OFFICE,
            type = TrackerType.NUMBER,
            unit = "glasses",
            sortOrder = 3,
            aggregation = Aggregation.AVERAGE,
            targetValue = 8.0,
        ),
    )

    private fun evening() = listOf(
        bool("Played with Vihaan", DaySection.EVENING, 0),
        bool("Dinner", DaySection.EVENING, 1),
        bool("Late meetings", DaySection.EVENING, 2, Direction.DOWN),
        Tracker(
            name = "Instagram",
            section = DaySection.EVENING,
            type = TrackerType.MULTI_SELECT,
            sortOrder = 3,
            options = listOf("After school drop", "After office", "Late night"),
            direction = Direction.DOWN,
        ),
    )

    private fun night() = listOf(
        Tracker(
            name = "Slept at",
            section = DaySection.NIGHT,
            type = TrackerType.TIME,
            sortOrder = 0,
            direction = Direction.DOWN,
            targetValue = ELEVEN_PM,
            healthMetric = HealthMetric.SLEEP_START,
        ),
        rating("Energy", 1),
        rating("Mood", 2),
        rating("Stress", 3, Direction.DOWN),
    )

    private fun body() = listOf(
        Tracker(
            name = "Weight",
            section = DaySection.BODY,
            type = TrackerType.NUMBER,
            unit = "kg",
            sortOrder = 0,
            direction = Direction.NEUTRAL,
            aggregation = Aggregation.AVERAGE,
            healthMetric = HealthMetric.WEIGHT,
        ),
        Tracker(
            name = "Steps",
            section = DaySection.BODY,
            type = TrackerType.NUMBER,
            unit = "steps",
            sortOrder = 1,
            aggregation = Aggregation.AVERAGE,
            healthMetric = HealthMetric.STEPS,
            targetValue = 8000.0,
        ),
        Tracker(
            name = "Sleep duration",
            section = DaySection.BODY,
            type = TrackerType.DURATION,
            unit = "min",
            sortOrder = 2,
            aggregation = Aggregation.AVERAGE,
            healthMetric = HealthMetric.SLEEP_DURATION,
        ),
        Tracker(
            name = "Resting heart rate",
            section = DaySection.BODY,
            type = TrackerType.NUMBER,
            unit = "bpm",
            active = false,
            sortOrder = 3,
            direction = Direction.DOWN,
            aggregation = Aggregation.AVERAGE,
            healthMetric = HealthMetric.RESTING_HEART_RATE,
        ),
        Tracker(
            name = "Health Connect exercise minutes",
            section = DaySection.BODY,
            type = TrackerType.DURATION,
            unit = "min",
            active = false,
            sortOrder = 4,
            aggregation = Aggregation.SUM,
            healthMetric = HealthMetric.EXERCISE_MINUTES,
        ),
    )

    private fun bool(
        name: String,
        section: DaySection,
        order: Int,
        direction: Direction = Direction.UP,
    ) = Tracker(
        name = name,
        section = section,
        type = TrackerType.BOOLEAN,
        sortOrder = order,
        direction = direction,
    )

    private fun rating(name: String, order: Int, direction: Direction = Direction.UP) = Tracker(
        name = name,
        section = DaySection.NIGHT,
        type = TrackerType.RATING,
        sortOrder = order,
        ratingMax = 5,
        direction = direction,
    )
}

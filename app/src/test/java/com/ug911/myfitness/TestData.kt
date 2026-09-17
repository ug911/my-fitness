package com.ug911.myfitness

import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerCategory
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import java.time.LocalDate

/** Small fixtures shared by the unit tests. */
object TestData {

    val monday: LocalDate = LocalDate.of(2026, 9, 7)
    val sunday: LocalDate = LocalDate.of(2026, 9, 13)

    fun habit(id: Long, name: String, category: TrackerCategory = TrackerCategory.EXERCISE, direction: Direction = Direction.UP) =
        Tracker(
            id = id,
            name = name,
            category = category,
            type = TrackerType.BOOLEAN,
            direction = direction,
            aggregation = Aggregation.DAYS_COMPLETED,
        )

    fun rating(id: Long, name: String) = Tracker(
        id = id,
        name = name,
        category = TrackerCategory.JOURNAL,
        type = TrackerType.RATING,
        aggregation = Aggregation.AVERAGE,
    )

    fun number(id: Long, name: String, unit: String, aggregation: Aggregation = Aggregation.AVERAGE) = Tracker(
        id = id,
        name = name,
        category = TrackerCategory.HEALTH,
        type = TrackerType.NUMBER,
        unit = unit,
        aggregation = aggregation,
    )

    fun duration(id: Long, name: String) = Tracker(
        id = id,
        name = name,
        category = TrackerCategory.EXERCISE,
        type = TrackerType.DURATION,
        unit = "min",
        aggregation = Aggregation.SUM,
    )

    fun flag(tracker: Tracker, date: LocalDate, checked: Boolean = true) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Flag(checked))

    fun score(tracker: Tracker, date: LocalDate, score: Int) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Rating(score))

    fun amount(tracker: Tracker, date: LocalDate, amount: Double) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Number(amount))

    fun minutes(tracker: Tracker, date: LocalDate, minutes: Int) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Duration(minutes))

    fun week(): List<LocalDate> = (0..6).map { monday.plusDays(it.toLong()) }
}

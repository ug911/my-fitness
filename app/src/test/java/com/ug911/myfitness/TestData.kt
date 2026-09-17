package com.ug911.myfitness

import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import java.time.LocalDate

/** Small fixtures shared by the unit tests. */
object TestData {

    val monday: LocalDate = LocalDate.of(2026, 9, 7)
    val sunday: LocalDate = LocalDate.of(2026, 9, 13)

    fun habit(
        id: Long,
        name: String,
        section: DaySection = DaySection.MORNING,
        direction: Direction = Direction.UP,
    ) = Tracker(
        id = id,
        name = name,
        section = section,
        type = TrackerType.BOOLEAN,
        direction = direction,
        aggregation = Aggregation.DAYS_COMPLETED,
    )

    fun rating(id: Long, name: String) = Tracker(
        id = id,
        name = name,
        section = DaySection.NIGHT,
        type = TrackerType.RATING,
        aggregation = Aggregation.AVERAGE,
    )

    fun number(id: Long, name: String, unit: String, aggregation: Aggregation = Aggregation.AVERAGE) = Tracker(
        id = id,
        name = name,
        section = DaySection.BODY,
        type = TrackerType.NUMBER,
        unit = unit,
        aggregation = aggregation,
    )

    fun duration(id: Long, name: String) = Tracker(
        id = id,
        name = name,
        section = DaySection.MORNING,
        type = TrackerType.DURATION,
        unit = "min",
        aggregation = Aggregation.SUM,
    )

    fun clock(id: Long, name: String, targetMinutes: Double? = null, direction: Direction = Direction.DOWN) = Tracker(
        id = id,
        name = name,
        section = DaySection.MORNING,
        type = TrackerType.TIME,
        direction = direction,
        aggregation = Aggregation.AVERAGE,
        targetValue = targetMinutes,
    )

    fun checklist(id: Long, name: String, options: List<String>, section: DaySection = DaySection.MORNING) = Tracker(
        id = id,
        name = name,
        section = section,
        type = TrackerType.MULTI_SELECT,
        options = options,
        aggregation = Aggregation.DAYS_COMPLETED,
    )

    fun flag(tracker: Tracker, date: LocalDate, checked: Boolean = true) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Flag(checked))

    fun score(tracker: Tracker, date: LocalDate, score: Int) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Rating(score))

    fun amount(tracker: Tracker, date: LocalDate, amount: Double) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Number(amount))

    fun minutes(tracker: Tracker, date: LocalDate, minutes: Int) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Duration(minutes))

    fun at(tracker: Tracker, date: LocalDate, hour: Int, minute: Int = 0) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.time(hour, minute))

    fun ticked(tracker: Tracker, date: LocalDate, vararg options: String) =
        Entry(trackerId = tracker.id, date = date, value = TrackerValue.Choices(options.toList()))

    fun week(): List<LocalDate> = (0..6).map { monday.plusDays(it.toLong()) }
}

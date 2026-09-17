package com.ug911.myfitness.analysis

import com.ug911.myfitness.TestData
import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.model.isCompleted
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.TrackerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodStatsTest {

    private val strength = TestData.habit(1, "Strength training")
    private val energy = TestData.rating(2, "Energy")
    private val exerciseMinutes = TestData.duration(3, "Exercise duration")
    private val weight = TestData.number(4, "Weight", "kg")

    @Test
    fun `days completed counts only completed days`() {
        val entries = listOf(
            TestData.flag(strength, TestData.monday),
            TestData.flag(strength, TestData.monday.plusDays(2)),
            TestData.flag(strength, TestData.monday.plusDays(4)),
            TestData.flag(strength, TestData.monday.plusDays(5), checked = false),
        )
        val stat = PeriodStats.compute(strength, entries, days = 7)

        assertEquals(4, stat.daysLogged)
        assertEquals(3, stat.daysCompleted)
        assertEquals("3/7 days", stat.summary())
        assertEquals(3.0, stat.headline!!, 0.001)
    }

    @Test
    fun `average uses only logged days not the whole period`() {
        val entries = listOf(
            TestData.score(energy, TestData.monday, 4),
            TestData.score(energy, TestData.monday.plusDays(1), 3),
            TestData.score(energy, TestData.monday.plusDays(2), 2),
        )
        val stat = PeriodStats.compute(energy, entries, days = 7)

        assertEquals(3.0, stat.average!!, 0.001)
        assertEquals(3, stat.daysLogged)
        assertEquals("3", stat.summary())
    }

    @Test
    fun `duration aggregates as a total with its unit`() {
        val entries = listOf(
            TestData.minutes(exerciseMinutes, TestData.monday, 45),
            TestData.minutes(exerciseMinutes, TestData.monday.plusDays(2), 60),
            TestData.minutes(exerciseMinutes, TestData.monday.plusDays(4), 80),
        )
        val stat = PeriodStats.compute(exerciseMinutes, entries, days = 7)

        assertEquals(185.0, stat.sum!!, 0.001)
        assertEquals("185 min", stat.summary())
    }

    @Test
    fun `a tracker with no entries reports no data rather than zero`() {
        val stat = PeriodStats.compute(weight, emptyList(), days = 7)

        assertNull(stat.average)
        assertNull(stat.headline)
        assertEquals("no data", stat.summary())
    }

    @Test
    fun `select values count as done unless they are the first option`() {
        val alcohol = Tracker(
            id = 9,
            name = "Coffee",
            section = DaySection.BREAKFAST,
            type = TrackerType.SELECT,
            options = listOf("None", "Hot", "Cold"),
            aggregation = Aggregation.DAYS_COMPLETED,
        )
        assertFalse(TrackerValue.Choice("None").isCompleted(alcohol))
        assertTrue(TrackerValue.Choice("Cold").isCompleted(alcohol))

        val entries = listOf(
            Entry(trackerId = alcohol.id, date = TestData.monday, value = TrackerValue.Choice("None")),
            Entry(trackerId = alcohol.id, date = TestData.monday.plusDays(1), value = TrackerValue.Choice("Cold")),
            Entry(trackerId = alcohol.id, date = TestData.monday.plusDays(2), value = TrackerValue.Choice("Hot")),
        )
        val stat = PeriodStats.compute(alcohol, entries, days = 7)

        assertEquals(2, stat.daysCompleted)
        assertEquals(mapOf("None" to 1, "Cold" to 1, "Hot" to 1), stat.optionCounts)
    }

    @Test
    fun `weekly series returns one point per week oldest first`() {
        val entries = (0..13).map { offset ->
            TestData.score(energy, TestData.monday.plusDays(offset.toLong()), if (offset < 7) 2 else 4)
        }
        val series = PeriodStats.weeklySeries(energy, entries, end = TestData.monday.plusDays(13), weeks = 2)

        assertEquals(2, series.size)
        assertEquals(2.0, series.first().value!!, 0.001)
        assertEquals(4.0, series.last().value!!, 0.001)
        assertTrue(series.first().start.isBefore(series.last().start))
    }

    @Test
    fun `day count is inclusive of both ends`() {
        assertEquals(7, PeriodStats.dayCount(TestData.monday, TestData.sunday))
        assertEquals(1, PeriodStats.dayCount(TestData.monday, TestData.monday))
    }
}

package com.ug911.myfitness.data

import com.ug911.myfitness.TestData
import com.ug911.myfitness.analysis.PeriodStats
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.model.display
import com.ug911.myfitness.data.model.formatMinuteOfDay
import com.ug911.myfitness.data.model.isCompleted
import com.ug911.myfitness.data.model.numeric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two types the personal day added: clock times and tick-several checklists. */
class ClockAndChecklistTest {

    private val wokeUp = TestData.clock(1, "Woke up", targetMinutes = 5 * 60.0)
    private val sleptAt = TestData.clock(2, "Slept at", targetMinutes = 23 * 60.0)
    private val exercises = TestData.checklist(3, "Exercises", listOf("Shoulders", "Biceps", "Abs", "Lats"))

    @Test
    fun `times round trip through storage as HH mm`() {
        val value = TrackerValue.time(5, 10)

        assertEquals("05:10", value.encode())
        assertEquals(value, TrackerValue.decode(TrackerType.TIME, "05:10"))
        assertEquals("05:10", value.display(wokeUp))
        assertEquals(310.0, value.numeric()!!, 0.001)
    }

    @Test
    fun `an impossible time decodes to nothing rather than a wrong time`() {
        assertNull(TrackerValue.decode(TrackerType.TIME, "25:00"))
        assertNull(TrackerValue.decode(TrackerType.TIME, "07:61"))
        assertNull(TrackerValue.decode(TrackerType.TIME, "breakfast"))
    }

    @Test
    fun `checklists round trip and report how many were ticked`() {
        val value = TrackerValue.Choices(listOf("Shoulders", "Biceps", "Abs"))

        assertEquals(value, TrackerValue.decode(TrackerType.MULTI_SELECT, value.encode()))
        assertEquals(3.0, value.numeric()!!, 0.001)
        assertTrue(value.isCompleted(exercises))
        assertFalse(TrackerValue.Choices(emptyList()).isCompleted(exercises))
        assertEquals("Shoulders, Biceps, Abs", value.display(exercises))
        assertEquals("4 of 4", TrackerValue.Choices(exercises.options).display(exercises))
    }

    @Test
    fun `a wake-up target is met by getting up earlier, not later`() {
        assertTrue(wokeUp.meetsTarget(TrackerValue.time(4, 50))!!)
        assertTrue(wokeUp.meetsTarget(TrackerValue.time(5, 0))!!)
        assertFalse(wokeUp.meetsTarget(TrackerValue.time(5, 30))!!)
        assertNull(wokeUp.meetsTarget(null))
    }

    @Test
    fun `a bedtime after midnight counts as late, not early`() {
        assertTrue(sleptAt.meetsTarget(TrackerValue.time(22, 40))!!)
        assertFalse(sleptAt.meetsTarget(TrackerValue.time(23, 30))!!)
        // 00:20 is twenty minutes past midnight but well past an 23:00 target.
        assertFalse(sleptAt.meetsTarget(TrackerValue.time(0, 20))!!)
        assertFalse(sleptAt.meetsTarget(TrackerValue.time(1, 15))!!)
    }

    @Test
    fun `bedtimes either side of midnight average to midnight`() {
        val entries = listOf(
            TestData.at(sleptAt, TestData.monday, 23, 50),
            TestData.at(sleptAt, TestData.monday.plusDays(1), 0, 10),
        )
        val stat = PeriodStats.compute(sleptAt, entries, days = 7)

        // A plain mean would say 12:00, which is worse than no answer at all.
        assertEquals("00:00", formatMinuteOfDay(stat.average!!))
        assertEquals("00:00", stat.summary())
    }

    @Test
    fun `wake times average normally when they do not cross midnight`() {
        val entries = listOf(
            TestData.at(wokeUp, TestData.monday, 5, 0),
            TestData.at(wokeUp, TestData.monday.plusDays(1), 5, 30),
            TestData.at(wokeUp, TestData.monday.plusDays(2), 6, 0),
        )
        val stat = PeriodStats.compute(wokeUp, entries, days = 7)

        assertEquals("05:30", formatMinuteOfDay(stat.average!!))
    }

    @Test
    fun `a logged time always counts as logged, whatever the target says`() {
        // Waking at 07:00 misses the 05:00 target but is still a recorded day.
        val late = TrackerValue.time(7, 0)
        assertTrue(late.isCompleted(wokeUp))
        assertFalse(wokeUp.meetsTarget(late)!!)
    }

    @Test
    fun `checklist stats count how often each item was ticked`() {
        val days = TestData.week()
        val entries = listOf(
            TestData.ticked(exercises, days[0], "Shoulders", "Biceps"),
            TestData.ticked(exercises, days[1], "Shoulders", "Abs"),
            TestData.ticked(exercises, days[2], "Shoulders"),
        )
        val stat = PeriodStats.compute(exercises, entries, days = 7)

        assertEquals(3, stat.daysCompleted)
        assertEquals(mapOf("Shoulders" to 3, "Biceps" to 1, "Abs" to 1), stat.optionCounts)
        // Average items per gym day, which is what "am I doing a full session?" means.
        assertEquals(1.7, PeriodStats.round1(stat.average!!), 0.001)
    }

    @Test
    fun `a wake-up tracker reads its target back as a time`() {
        assertEquals("05:00", wokeUp.targetLabel())
        assertEquals(Direction.DOWN, wokeUp.direction)
    }
}

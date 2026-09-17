package com.ug911.myfitness.analysis

import com.ug911.myfitness.TestData
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.DaySection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternAnalysisTest {

    private val lateEating = TestData.habit(
        id = 1,
        name = "Late-night eating",
        section = DaySection.BREAKFAST,
        direction = Direction.DOWN,
    )
    private val energy = TestData.rating(2, "Energy")

    /** Six days: three with late eating, three without. */
    private fun entries(lagged: Boolean): List<Entry> {
        val days = TestData.week()
        val conditionDays = listOf(days[0], days[1], days[2])
        val calmDays = listOf(days[3], days[4], days[5])
        val condition = (conditionDays.map { TestData.flag(lateEating, it, true) }) +
            (calmDays.map { TestData.flag(lateEating, it, false) })
        val outcomes = days.mapIndexed { index, date ->
            val score = when {
                lagged && index in 1..3 -> 2
                !lagged && index in 0..2 -> 2
                else -> 4
            }
            TestData.score(energy, date, score)
        }
        return condition + outcomes
    }

    @Test
    fun `same day split reports both averages and the difference`() {
        val split = PatternAnalysis.compare(lateEating, energy, entries(lagged = false))

        assertNotNull(split)
        assertEquals(2.0, split!!.averageWhenDone, 0.001)
        assertEquals(4.0, split.averageWhenNotDone, 0.001)
        assertEquals(-2.0, split.difference, 0.001)
        assertEquals(3, split.daysWhenDone)
        assertEquals(3, split.daysWhenNotDone)
        assertTrue(split.describe().contains("lower"))
    }

    @Test
    fun `next day split shifts the outcome by one day`() {
        val laggedData = entries(lagged = true)

        val sameDay = PatternAnalysis.compare(lateEating, energy, laggedData, lagDays = 0)
        val nextDay = PatternAnalysis.compare(lateEating, energy, laggedData, lagDays = 1)

        assertNotNull(nextDay)
        // With a one-day lag the low-energy days line up with the late-eating nights.
        assertEquals(2.0, nextDay!!.averageWhenDone, 0.001)
        assertTrue(nextDay.difference < (sameDay?.difference ?: 0.0))
        assertEquals("next day", nextDay.lagLabel)
    }

    @Test
    fun `a split with too few days on one side is refused`() {
        val days = TestData.week()
        val sparse = listOf(
            TestData.flag(lateEating, days[0], true),
            TestData.flag(lateEating, days[1], false),
            TestData.flag(lateEating, days[2], false),
            TestData.flag(lateEating, days[3], false),
        ) + days.take(4).map { TestData.score(energy, it, 3) }

        assertNull(PatternAnalysis.compare(lateEating, energy, sparse))
    }

    @Test
    fun `comparing a tracker with itself is refused`() {
        assertNull(PatternAnalysis.compare(lateEating, lateEating, entries(lagged = false)))
    }

    @Test
    fun `a bedtime outcome is compared around the clock, not across it`() {
        val gym = TestData.habit(7, "Gym")
        val sleptAt = TestData.clock(8, "Slept at", targetMinutes = 23 * 60.0)
        val days = TestData.week()
        // Gym days: bed at 23:40, 23:50, 23:30. Rest days: 00:20, 00:40, 00:10 - later,
        // even though the raw minute numbers are far smaller.
        val entries = listOf(
            TestData.flag(gym, days[0]), TestData.flag(gym, days[1]), TestData.flag(gym, days[2]),
            TestData.flag(gym, days[3], false), TestData.flag(gym, days[4], false),
            TestData.flag(gym, days[5], false),
            TestData.at(sleptAt, days[0], 23, 40),
            TestData.at(sleptAt, days[1], 23, 50),
            TestData.at(sleptAt, days[2], 23, 30),
            TestData.at(sleptAt, days[3], 0, 20),
            TestData.at(sleptAt, days[4], 0, 40),
            TestData.at(sleptAt, days[5], 0, 10),
        )

        val split = PatternAnalysis.compare(gym, sleptAt, entries)!!

        assertTrue(split.isClockTime)
        assertEquals("23:40", split.formatWhenDone())
        assertEquals("00:23", split.formatWhenNotDone())
        // 43 minutes earlier, not 23 hours later.
        assertEquals(-43.0, split.difference, 1.0)
        assertTrue(split.describe().contains("earlier"))
    }

    @Test
    fun `ranking puts the largest difference first`() {
        val weakHabit = TestData.habit(3, "Stretching")
        val days = TestData.week()
        // Stretching lines up only loosely with energy, late eating lines up exactly.
        val data = entries(lagged = false) +
            listOf(
                TestData.flag(weakHabit, days[0], true),
                TestData.flag(weakHabit, days[3], true),
                TestData.flag(weakHabit, days[4], true),
                TestData.flag(weakHabit, days[1], false),
                TestData.flag(weakHabit, days[2], false),
                TestData.flag(weakHabit, days[5], false),
            )

        val ranked = PatternAnalysis.rank(
            trackers = listOf(lateEating, weakHabit, energy),
            entries = data,
            outcomes = listOf(energy),
        )

        assertEquals(2, ranked.size)
        assertEquals(lateEating.id, ranked.first().condition.id)
        assertTrue(kotlin.math.abs(ranked.first().difference) > kotlin.math.abs(ranked.last().difference))
    }
}

package com.ug911.myfitness.ai

import com.ug911.myfitness.TestData
import com.ug911.myfitness.data.local.PersonalDay
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.JournalEntry
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the model receives for a day-shaped week: sections in day order, clock times as
 * times, and the gym checklist broken down per exercise.
 */
class PersonalDaySnapshotTest {

    private val wokeUp = TestData.clock(1, "Woke up", targetMinutes = 5 * 60.0)
    private val gym = TestData.habit(2, "Gym", DaySection.MORNING)
    private val exercises = TestData.checklist(3, "Exercises", PersonalDay.EXERCISES)
    private val water = TestData.number(4, "Water at office", "glasses").copy(section = DaySection.OFFICE)
    private val vihaan = TestData.habit(5, "Played with Vihaan", DaySection.EVENING)
    private val sleptAt = TestData.clock(6, "Slept at", targetMinutes = 23 * 60.0)
        .copy(section = DaySection.NIGHT)

    private val trackers = listOf(wokeUp, gym, exercises, water, vihaan, sleptAt)

    private fun week(): PeriodData {
        val days = TestData.week()
        val entries = buildList {
            add(TestData.at(wokeUp, days[0], 5, 0))
            add(TestData.at(wokeUp, days[1], 5, 20))
            add(TestData.at(wokeUp, days[2], 6, 10))
            add(TestData.flag(gym, days[0]))
            add(TestData.flag(gym, days[1]))
            add(TestData.flag(gym, days[2]))
            add(TestData.ticked(exercises, days[0], "Shoulders", "Biceps", "Abs"))
            add(TestData.ticked(exercises, days[1], "Quads", "Calves", "Abs"))
            add(TestData.ticked(exercises, days[2], "Back", "Lats", "Abs"))
            add(TestData.amount(water, days[0], 6.0))
            add(TestData.amount(water, days[1], 8.0))
            (0..4).forEach { add(TestData.flag(vihaan, days[it])) }
            add(TestData.at(sleptAt, days[0], 23, 10))
            add(TestData.at(sleptAt, days[1], 23, 50))
        }
        return PeriodData(
            start = TestData.monday,
            end = TestData.sunday,
            trackers = trackers,
            entries = entries,
            journal = listOf(JournalEntry(days[0], "Good session, Vihaan slept early")),
        )
    }

    @Test
    fun `sections appear under their own names in day order`() {
        val snapshot = AiContextBuilder().build(week())

        val keys = snapshot.keys.toList()
        assertTrue("morning" in keys)
        assertTrue("office" in keys)
        assertTrue("evening" in keys)
        assertTrue("night" in keys)
        assertTrue(keys.indexOf("morning") < keys.indexOf("office"))
        assertTrue(keys.indexOf("office") < keys.indexOf("night"))
    }

    @Test
    fun `clock times are sent as times, not as minute counts`() {
        val snapshot = AiContextBuilder().build(week())

        val morning = snapshot["morning"]!!.jsonObject
        assertEquals("05:30", morning["woke_up"]!!.jsonPrimitive.content)
        assertEquals("23:30", snapshot["night"]!!.jsonObject["slept_at"]!!.jsonPrimitive.content)
    }

    @Test
    fun `the gym checklist is broken down per exercise`() {
        val snapshot = AiContextBuilder().build(week())
        val morning = snapshot["morning"]!!.jsonObject

        assertEquals("3/7 days", morning["exercises"]!!.jsonPrimitive.content)
        assertEquals(3.0, morning["exercises_items_per_day"]!!.jsonPrimitive.content.toDouble(), 0.001)

        val breakdown = morning["exercises_breakdown"]!!.jsonObject
        // Abs on all three gym days; shoulders only on the first.
        assertEquals(3, breakdown["abs"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, breakdown["shoulders"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, breakdown["lats"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `habits and numbers keep their plain-language forms`() {
        val snapshot = AiContextBuilder().build(week())

        assertEquals("3/7 days", snapshot["morning"]!!.jsonObject["gym"]!!.jsonPrimitive.content)
        assertEquals("5/7 days", snapshot["evening"]!!.jsonObject["played_with_vihaan"]!!.jsonPrimitive.content)
        assertEquals(7.0, snapshot["office"]!!.jsonObject["water_at_office"]!!.jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(2, snapshot["office"]!!.jsonObject["water_at_office_days_logged"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `the whole personal day survives a round trip through the snapshot`() {
        val full = PeriodData(
            start = TestData.monday,
            end = TestData.sunday,
            trackers = PersonalDay.trackers().mapIndexed { index, tracker -> tracker.copy(id = index + 1L) },
            entries = emptyList(),
            journal = emptyList(),
        )

        val text = AiContextBuilder().buildJson(full)

        assertTrue(text.contains("\"woke_up\""))
        assertTrue(text.contains("\"dropped_vihaan_at_school\""))
        assertTrue(text.contains("\"water_at_office\""))
        assertTrue(text.contains("\"slept_at\""))
    }
}

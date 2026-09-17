package com.ug911.myfitness.ai

import com.ug911.myfitness.TestData
import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.JournalEntry
import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanSource
import com.ug911.myfitness.data.model.PlanStatus
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.model.PlanWithTargets
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiContextBuilderTest {

    private val strength = TestData.habit(1, "Strength training")
    private val cardio = TestData.habit(2, "Cardio")
    private val steps = Tracker(
        id = 3,
        name = "Steps",
        section = DaySection.MORNING,
        type = TrackerType.NUMBER,
        unit = "steps",
        aggregation = Aggregation.AVERAGE,
    )
    private val exerciseMinutes = TestData.duration(4, "Exercise duration")
    private val protein = TestData.habit(5, "Protein target", DaySection.BREAKFAST)
    private val energy = TestData.rating(6, "Energy")
    private val mealNote = Tracker(
        id = 7,
        name = "Meal note",
        section = DaySection.BREAKFAST,
        type = TrackerType.TEXT,
        aggregation = Aggregation.NONE,
    )

    private val trackers = listOf(strength, cardio, steps, exerciseMinutes, protein, energy, mealNote)

    private fun week(): PeriodData {
        val days = TestData.week()
        val entries = buildList {
            add(TestData.flag(strength, days[0]))
            add(TestData.flag(strength, days[2]))
            add(TestData.flag(strength, days[4]))
            add(TestData.flag(cardio, days[5]))
            days.forEach { add(TestData.amount(steps, it, 7120.0)) }
            add(TestData.minutes(exerciseMinutes, days[0], 60))
            add(TestData.minutes(exerciseMinutes, days[2], 60))
            add(TestData.minutes(exerciseMinutes, days[4], 65))
            (0..4).forEach { add(TestData.flag(protein, days[it])) }
            add(TestData.score(energy, days[0], 4))
            add(TestData.score(energy, days[1], 3))
            add(TestData.score(energy, days[2], 3))
            add(
                Entry(
                    trackerId = mealNote.id,
                    date = days[3],
                    value = TrackerValue.Text("big pasta dinner"),
                ),
            )
        }
        return PeriodData(
            start = TestData.monday,
            end = TestData.sunday,
            trackers = trackers,
            entries = entries,
            journal = listOf(
                JournalEntry(days[0], "Good session, slept well"),
                JournalEntry(days[5], "Long day, ate late"),
            ),
        )
    }

    @Test
    fun `snapshot reports habits as days completed and numbers as aggregates`() {
        val snapshot = AiContextBuilder().build(week())

        assertEquals("2026-09-07/2026-09-13", snapshot["period"]!!.jsonPrimitive.content)
        assertEquals(7, snapshot["days"]!!.jsonPrimitive.content.toInt())

        val exercise = snapshot["morning"]!!.jsonObject
        assertEquals("3/7 days", exercise["strength_training"]!!.jsonPrimitive.content)
        assertEquals("1/7 days", exercise["cardio"]!!.jsonPrimitive.content)
        assertEquals(7120.0, exercise["steps"]!!.jsonPrimitive.content.toDouble(), 0.001)
        assertEquals(185.0, exercise["exercise_duration"]!!.jsonPrimitive.content.toDouble(), 0.001)

        val nutrition = snapshot["breakfast"]!!.jsonObject
        assertEquals("5/7 days", nutrition["protein_target"]!!.jsonPrimitive.content)

        val subjective = snapshot["night"]!!.jsonObject
        assertEquals(3.3, subjective["energy"]!!.jsonPrimitive.content.toDouble(), 0.001)
        // Averages over partial weeks carry the number of days they are based on.
        assertEquals(3, subjective["energy_days_logged"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `free text lands in notes and journal, never in the section aggregates`() {
        val snapshot = AiContextBuilder().build(week())

        assertNull(snapshot["breakfast"]!!.jsonObject["meal_note"])
        val notes = snapshot["notes"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(notes.any { it.contains("big pasta dinner") })

        val journal = snapshot["journal"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(2, journal.size)
        assertTrue(journal.first().startsWith("Mon 07 Sep"))
    }

    @Test
    fun `plan targets appear next to what actually happened`() {
        val plan = PlanWithTargets(
            plan = Plan(
                id = 11,
                startDate = TestData.monday,
                endDate = TestData.sunday,
                generatedBy = PlanSource.AI,
                status = PlanStatus.ACTIVE,
            ),
            targets = listOf(
                PlanTarget(id = 1, planId = 11, trackerId = strength.id, targetFrequency = 4),
                PlanTarget(id = 2, planId = 11, trackerId = steps.id, targetValue = 8000.0),
            ),
        )
        val snapshot = AiContextBuilder().build(week().copy(plan = plan))

        val targets = snapshot["plan"]!!.jsonObject["targets"]!!.jsonArray.map { it.jsonObject }
        assertEquals(2, targets.size)

        val strengthTarget = targets.first { it["tracker"]!!.jsonPrimitive.content == "Strength training" }
        assertEquals(4, strengthTarget["target_frequency"]!!.jsonPrimitive.content.toInt())
        assertEquals(3, strengthTarget["actual_days"]!!.jsonPrimitive.content.toInt())
        assertFalse(strengthTarget["met"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `previous period is summarised for comparison`() {
        val priorDays = (0..6).map { TestData.monday.minusDays(7).plusDays(it.toLong()) }
        val previous = PeriodData(
            start = TestData.monday.minusDays(7),
            end = TestData.monday.minusDays(1),
            trackers = trackers,
            entries = listOf(
                TestData.flag(strength, priorDays[0]),
                TestData.flag(strength, priorDays[3]),
            ),
            journal = emptyList(),
        )
        val snapshot = AiContextBuilder().build(week(), previous)

        val prior = snapshot["previous_period"]!!.jsonObject
        assertEquals("2026-08-31/2026-09-06", prior["period"]!!.jsonPrimitive.content)
        assertEquals("2/7 days", prior["morning"]!!.jsonObject["strength_training"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an empty week says no data instead of inventing zeroes`() {
        val empty = PeriodData(
            start = TestData.monday,
            end = TestData.sunday,
            trackers = trackers,
            entries = emptyList(),
            journal = emptyList(),
        )
        val snapshot = AiContextBuilder().build(empty)

        assertEquals("no data", snapshot["morning"]!!.jsonObject["steps"]!!.jsonPrimitive.content)
        assertEquals(0, snapshot["journal"]!!.jsonArray.size)
    }

    @Test
    fun `snapshot serialises to valid json`() {
        val text = AiContextBuilder().buildJson(week())

        assertTrue(text.trimStart().startsWith("{"))
        assertTrue(text.contains("\"strength_training\""))
    }
}

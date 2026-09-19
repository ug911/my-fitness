package com.ug911.myfitness.data

import com.ug911.myfitness.data.model.ExerciseLog
import com.ug911.myfitness.data.model.WorkoutSet
import com.ug911.myfitness.ui.gym.GymRow
import com.ug911.myfitness.data.model.TrainingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Sets, volume, and the numbers the gym screen suggests for the next one. */
class WorkoutModelTest {

    private val date = LocalDate.of(2026, 9, 18)

    private fun set(index: Int, weight: Double?, reps: Int) =
        WorkoutSet(id = index.toLong(), date = date, exerciseId = "quads", setIndex = index, weightKg = weight, reps = reps)

    @Test
    fun `a set reads as weight by reps, or just reps for bodyweight`() {
        assertEquals("60 kg x 8", set(1, 60.0, 8).label())
        assertEquals("12 reps", set(1, null, 12).label())
        assertEquals("12 reps", set(1, 0.0, 12).label())
    }

    @Test
    fun `volume is weight times reps and ignores bodyweight`() {
        val log = ExerciseLog("quads", listOf(set(1, 60.0, 8), set(2, 60.0, 6), set(3, null, 10)))

        assertEquals(840.0, log.totalVolume, 0.001)
        assertEquals(24, log.totalReps)
        assertEquals(60.0, log.topSet!!.weightKg!!, 0.001)
    }

    @Test
    fun `the next set starts from what you just did`() {
        val row = GymRow(
            item = TrainingItem("quads", sets = 4, reps = "5-10"),
            doc = null,
            logged = ExerciseLog("quads", listOf(set(1, 60.0, 8))),
            lastTime = ExerciseLog("quads", listOf(set(1, 55.0, 8))),
        )

        assertEquals(60.0, row.suggestedWeight()!!, 0.001)
        assertEquals(8, row.suggestedReps())
    }

    @Test
    fun `with nothing logged today it starts from last session's top set`() {
        val row = GymRow(
            item = TrainingItem("quads", sets = 4, reps = "5-10"),
            doc = null,
            logged = null,
            lastTime = ExerciseLog("quads", listOf(set(1, 55.0, 9), set(2, 62.5, 5))),
        )

        assertEquals(62.5, row.suggestedWeight()!!, 0.001)
        assertEquals(9, row.suggestedReps())
    }

    @Test
    fun `a first ever session falls back to the bottom of the planned rep range`() {
        val row = GymRow(TrainingItem("quads", sets = 4, reps = "5-10"), null, null, null)

        assertEquals(null, row.suggestedWeight())
        assertEquals(5, row.suggestedReps())
    }

    @Test
    fun `an exercise is complete once its planned sets are done`() {
        val item = TrainingItem("quads", sets = 3, reps = "8")
        val twoSets = ExerciseLog("quads", listOf(set(1, 40.0, 8), set(2, 40.0, 8)))

        assertFalse(GymRow(item, null, twoSets, null).complete)
        assertTrue(GymRow(item, null, ExerciseLog("quads", twoSets.sets + set(3, 40.0, 8)), null).complete)
    }
}

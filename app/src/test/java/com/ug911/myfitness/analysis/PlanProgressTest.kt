package com.ug911.myfitness.analysis

import com.ug911.myfitness.TestData
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanSource
import com.ug911.myfitness.data.model.PlanStatus
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.model.PlanWithTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanProgressTest {

    private val strength = TestData.habit(1, "Strength training")
    private val steps = TestData.number(2, "Steps", "steps")
    private val restingHr = TestData.number(3, "Resting heart rate", "bpm").copy(direction = Direction.DOWN)

    private fun plan(vararg targets: PlanTarget) = PlanWithTargets(
        plan = Plan(
            id = 1,
            startDate = TestData.monday,
            endDate = TestData.sunday,
            generatedBy = PlanSource.AI,
            status = PlanStatus.ACTIVE,
        ),
        targets = targets.toList(),
    )

    @Test
    fun `frequency targets count completed days inside the plan window`() {
        val entries = listOf(
            TestData.flag(strength, TestData.monday),
            TestData.flag(strength, TestData.monday.plusDays(2)),
            TestData.flag(strength, TestData.monday.plusDays(3)),
            // Outside the plan window, so it must not count.
            TestData.flag(strength, TestData.sunday.plusDays(3)),
        )
        val progress = PlanProgress.compute(
            plan(PlanTarget(id = 1, planId = 1, trackerId = strength.id, targetFrequency = 4)),
            listOf(strength),
            entries,
        )

        val row = progress.single()
        assertEquals(3, row.achievedDays)
        assertEquals(0.75f, row.fraction, 0.001f)
        assertFalse(row.met)
    }

    @Test
    fun `a met target reports a full bar and does not overflow`() {
        val entries = (0..5).map { TestData.flag(strength, TestData.monday.plusDays(it.toLong())) }
        val progress = PlanProgress.compute(
            plan(PlanTarget(id = 1, planId = 1, trackerId = strength.id, targetFrequency = 4)),
            listOf(strength),
            entries,
        )

        assertTrue(progress.single().met)
        assertEquals(1f, progress.single().fraction, 0.001f)
    }

    @Test
    fun `value targets compare averages and respect the tracker's direction`() {
        val stepEntries = (0..6).map { TestData.amount(steps, TestData.monday.plusDays(it.toLong()), 6000.0) }
        val hrEntries = (0..6).map { TestData.amount(restingHr, TestData.monday.plusDays(it.toLong()), 52.0) }

        val progress = PlanProgress.compute(
            plan(
                PlanTarget(id = 1, planId = 1, trackerId = steps.id, targetValue = 8000.0),
                PlanTarget(id = 2, planId = 1, trackerId = restingHr.id, targetValue = 55.0),
            ),
            listOf(steps, restingHr),
            stepEntries + hrEntries,
        )

        val stepRow = progress.first { it.tracker.id == steps.id }
        assertEquals(0.75f, stepRow.fraction, 0.001f)
        assertFalse(stepRow.met)

        // 52 bpm against a target of 55 is better than the target when lower is better.
        val hrRow = progress.first { it.tracker.id == restingHr.id }
        assertTrue(hrRow.met)
    }

    @Test
    fun `targets for deleted trackers are skipped rather than crashing`() {
        val progress = PlanProgress.compute(
            plan(PlanTarget(id = 1, planId = 1, trackerId = 999, targetFrequency = 3)),
            listOf(strength),
            emptyList(),
        )

        assertTrue(progress.isEmpty())
    }
}

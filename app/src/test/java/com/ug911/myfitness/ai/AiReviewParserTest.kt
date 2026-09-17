package com.ug911.myfitness.ai

import com.ug911.myfitness.TestData
import com.ug911.myfitness.data.model.PlanSource
import com.ug911.myfitness.data.model.PlanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReviewParserTest {

    private val strength = TestData.habit(1, "Strength training")
    private val steps = TestData.number(2, "Steps", "steps")

    private val goodReply = """
        {
          "review": "You trained three times, up from two last week.",
          "insights": ["Energy was highest after early nights."],
          "plan": {
            "rationale": "Keep the volume, add one walk.",
            "targets": [
              {"tracker": "Strength training", "target_frequency": 3},
              {"tracker": "Steps", "target_value": 8000, "target_frequency": 5, "note": "weekdays"}
            ]
          }
        }
    """.trimIndent()

    @Test
    fun `parses a clean reply`() {
        val payload = AiReviewParser.parse(goodReply).getOrThrow()

        assertTrue(payload.review.startsWith("You trained"))
        assertEquals(1, payload.insights.size)
        assertEquals(2, payload.plan!!.targets.size)
        assertEquals(8000.0, payload.plan!!.targets[1].targetValue!!, 0.001)
    }

    @Test
    fun `parses a reply wrapped in prose and code fences`() {
        val messy = "Sure! Here is your review:\n```json\n$goodReply\n```\nHope that helps."

        val payload = AiReviewParser.parse(messy).getOrThrow()

        assertEquals(2, payload.plan!!.targets.size)
    }

    @Test
    fun `ignores unknown fields instead of failing`() {
        val extra = """{"review": "ok", "insights": [], "confidence": 0.7, "plan": null}"""

        assertEquals("ok", AiReviewParser.parse(extra).getOrThrow().review)
    }

    @Test
    fun `an empty or non json reply fails cleanly`() {
        assertTrue(AiReviewParser.parse("I could not do that").isFailure)
        assertTrue(AiReviewParser.parse("{}").isFailure)
    }

    @Test
    fun `extracting json handles braces inside strings`() {
        val tricky = """prefix {"review": "a } brace and \"quote\"", "insights": []} suffix"""

        val extracted = AiReviewParser.extractJsonObject(tricky)

        assertEquals("""{"review": "a } brace and \"quote\"", "insights": []}""", extracted)
    }

    @Test
    fun `targets resolve by name and unknown trackers are reported not dropped`() {
        val payload = AiReviewParser.parse(goodReply).getOrThrow().plan!!
            .let { it.copy(targets = it.targets + AiTargetPayload(tracker = "Cold plunge", targetFrequency = 2)) }

        val resolution = AiReviewParser.resolveTargets(payload, listOf(strength, steps))

        assertEquals(2, resolution.targets.size)
        assertEquals(listOf("Cold plunge"), resolution.unmatchedTrackerNames)
        assertEquals(strength.id, resolution.targets.first().trackerId)
        assertEquals(3, resolution.targets.first().targetFrequency)
    }

    @Test
    fun `tracker names match loosely on case and punctuation`() {
        val payload = AiPlanPayload(targets = listOf(AiTargetPayload(tracker = "strength-training")))

        val resolution = AiReviewParser.resolveTargets(payload, listOf(strength))

        assertEquals(1, resolution.targets.size)
        assertTrue(resolution.unmatchedTrackerNames.isEmpty())
    }

    @Test
    fun `a proposed plan always covers the week after the review and starts unapproved`() {
        val payload = AiReviewParser.parse(goodReply).getOrThrow()

        val plan = AiReviewParser.buildProposedPlan(payload, reviewedPeriodEnd = TestData.sunday, analysisId = 42)

        assertEquals(TestData.sunday.plusDays(1), plan.startDate)
        assertEquals(TestData.sunday.plusDays(7), plan.endDate)
        assertEquals(PlanStatus.PROPOSED, plan.status)
        assertEquals(PlanSource.AI, plan.generatedBy)
        assertEquals(42L, plan.analysisId)
    }

    @Test
    fun `plan dates from the model are ignored in favour of the app's own week`() {
        val withBadDates = """
            {
              "review": "ok",
              "insights": [],
              "plan": {"start_date": "1999-01-01", "end_date": "1999-01-07", "targets": []}
            }
        """.trimIndent()
        val payload = AiReviewParser.parse(withBadDates).getOrThrow()

        val plan = AiReviewParser.buildProposedPlan(payload, TestData.sunday, null)

        assertEquals(TestData.sunday.plusDays(1), plan.startDate)
        assertNull(plan.analysisId)
    }
}

package com.ug911.myfitness.knowledge

import com.ug911.myfitness.data.model.DemoAnimation
import com.ug911.myfitness.data.model.DemoFrame
import com.ug911.myfitness.data.model.ExerciseDoc
import com.ug911.myfitness.data.model.KnowledgeBundle
import com.ug911.myfitness.ui.common.poseAt
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bundle the app ships, and the animation it drives. */
class KnowledgeParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val bundleJson = """
        {
          "version": 1,
          "exercises": [
            {
              "id": "push_ups", "name": "Push-ups",
              "primaryMuscles": ["Chest"],
              "cues": ["Squeeze the glutes"],
              "mistakes": [{"mistake": "Hips sag", "fix": "Brace"}],
              "evidence": [{"claim": "Effort matters", "detail": "", "confidence": "strong"}],
              "repRange": {"min": 8, "max": 25},
              "demo": {
                "view": "side", "durationMillis": 2000, "loop": "pingpong",
                "frames": [
                  {"at": 0.0, "joints": {"hip": [0.5, 0.5], "head": [0.2, 0.5]}},
                  {"at": 1.0, "joints": {"hip": [0.5, 0.7]}}
                ]
              }
            }
          ],
          "foods": [
            {"id": "poha", "name": "Poha", "meals": ["breakfast"],
             "portion": {"label": "1 plate", "grams": 200},
             "per": {"kcal": 270, "protein": 6, "carbs": 48, "fat": 6, "fibre": 3}}
          ],
          "plans": [
            {"id": "week", "name": "Week", "days": [
              {"day": "MONDAY", "title": "Push", "items": [{"exercise": "push_ups", "sets": 3, "reps": "10-20"}]},
              {"day": "SUNDAY", "title": "Rest", "items": []}
            ], "targets": {"proteinGramsPerDay": 130, "kcalPerDay": 2200}}
          ]
        }
    """.trimIndent()

    private fun bundle() = json.decodeFromString(KnowledgeBundle.serializer(), bundleJson)

    @Test
    fun `a published bundle parses into the app's model`() {
        val parsed = bundle()

        assertEquals(1, parsed.exercises.size)
        val pushUps = parsed.exercises.first()
        assertEquals("Push-ups", pushUps.name)
        assertEquals("8-25 reps", pushUps.repRange!!.label())
        assertEquals("Well established", pushUps.evidence.first().strength.label)
        assertEquals(270.0, parsed.foods.first().per.kcal, 0.001)
    }

    @Test
    fun `unknown fields in a newer document do not break an installed app`() {
        val withExtras = """
            {"id": "x", "name": "X", "primaryMuscles": ["Chest"], "cues": ["c"],
             "videoUrl": "https://example.com/clip.mp4", "somethingNew": {"nested": 1}}
        """.trimIndent()

        val doc = json.decodeFromString(ExerciseDoc.serializer(), withExtras)

        assertEquals("X", doc.name)
    }

    @Test
    fun `a plan knows which day is which, and which is a rest day`() {
        val plan = bundle().plans.first()

        assertEquals("Push", plan.dayFor(java.time.DayOfWeek.MONDAY)!!.title)
        assertTrue(plan.dayFor(java.time.DayOfWeek.SUNDAY)!!.isRestDay)
        assertEquals(130, plan.targets.proteinGrams)
        assertEquals(2200, plan.targets.kcal)
    }

    @Test
    fun `a demo frame fills forward, so later frames only list what moves`() {
        val demo = bundle().exercises.first().demo!!

        val start = poseAt(demo, 0f)
        val end = poseAt(demo, 1f)

        // The head is only given in the first frame but is still placed at the end.
        assertNotNull(end["head"])
        assertEquals(start["head"], end["head"])
        assertEquals(0.5f, end["hip"]!!.x, 0.001f)
        assertEquals(0.7f, end["hip"]!!.y, 0.001f)
    }

    @Test
    fun `the pose halfway through sits between the keyframes`() {
        val demo = bundle().exercises.first().demo!!

        val middle = poseAt(demo, 0.5f)

        val y = middle["hip"]!!.y
        assertTrue("expected the hip between 0.5 and 0.7 but was $y", y > 0.5f && y < 0.7f)
    }

    @Test
    fun `a demo with one frame still renders that pose`() {
        val single = DemoAnimation(
            frames = listOf(DemoFrame(0f, mapOf("hip" to listOf(0.4f, 0.6f)))),
        )

        assertEquals(0.4f, poseAt(single, 0.9f)["hip"]!!.x, 0.001f)
    }
}

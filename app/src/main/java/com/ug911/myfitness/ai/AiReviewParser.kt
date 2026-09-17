package com.ug911.myfitness.ai

import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanSource
import com.ug911.myfitness.data.model.PlanStatus
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.model.Tracker
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Reads a model's reply. Models wrap JSON in prose or code fences often enough that
 * parsing has to be forgiving about the envelope while staying strict about the shape.
 */
object AiReviewParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(raw: String): Result<AiReviewPayload> = runCatching {
        val body = extractJsonObject(raw) ?: error("No JSON object found in the model reply")
        val payload = json.decodeFromString(AiReviewPayload.serializer(), body)
        if (payload.review.isBlank() && payload.insights.isEmpty() && payload.plan == null) {
            error("Model reply contained no review, insights or plan")
        }
        payload
    }

    /** Pulls out the outermost {...} block, ignoring fences and any commentary around it. */
    fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val char = raw[index]
            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                }
            }
        }
        return null
    }

    /**
     * Maps proposed targets onto real trackers. Unmatched names are reported rather than
     * dropped silently, so a proposal never looks like it was accepted in full when it wasn't.
     */
    fun resolveTargets(payload: AiPlanPayload, trackers: List<Tracker>): TargetResolution {
        val byKey = trackers.associateBy { it.key }
        val resolved = mutableListOf<PlanTarget>()
        val unmatched = mutableListOf<String>()
        payload.targets.forEach { target ->
            val tracker = byKey[Tracker.slugify(target.tracker)]
                ?: trackers.firstOrNull { it.name.equals(target.tracker.trim(), ignoreCase = true) }
            if (tracker == null) {
                unmatched += target.tracker
            } else {
                resolved += PlanTarget(
                    planId = 0,
                    trackerId = tracker.id,
                    targetValue = target.targetValue,
                    targetFrequency = target.targetFrequency,
                    note = target.note?.takeIf { it.isNotBlank() },
                )
            }
        }
        return TargetResolution(resolved, unmatched)
    }

    /**
     * Builds the proposal record. Dates come from the app, not the model: a plan always
     * covers the week after the reviewed period, whatever the model wrote.
     */
    fun buildProposedPlan(
        payload: AiReviewPayload,
        reviewedPeriodEnd: LocalDate,
        analysisId: Long?,
    ): Plan {
        val start = reviewedPeriodEnd.plusDays(1)
        return Plan(
            startDate = start,
            endDate = start.plusDays(6),
            generatedBy = PlanSource.AI,
            status = PlanStatus.PROPOSED,
            note = payload.plan?.rationale?.takeIf { it.isNotBlank() },
            analysisId = analysisId,
        )
    }
}

data class TargetResolution(
    val targets: List<PlanTarget>,
    val unmatchedTrackerNames: List<String>,
)

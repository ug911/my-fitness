package com.ug911.myfitness.data.model

import java.time.LocalDate

/**
 * What you intend to do. Deliberately a different table from [Entry]: the AI may
 * propose and modify plans, but it can never rewrite history.
 */
data class Plan(
    val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val generatedBy: PlanSource,
    val status: PlanStatus,
    val note: String? = null,
    val analysisId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
)

enum class PlanSource {
    USER,
    AI,
}

enum class PlanStatus {
    /** Suggested by the AI and waiting for approval. Never counted as a goal. */
    PROPOSED,

    /** Approved; this is the current week's target set. */
    ACTIVE,

    /** Finished or superseded. */
    ARCHIVED,

    /** Explicitly turned down. Kept so the AI's suggestion history stays honest. */
    REJECTED,
}

data class PlanTarget(
    val id: Long = 0,
    val planId: Long,
    val trackerId: Long,
    /** e.g. 8000 steps, 74.0 kg, 45 minutes. */
    val targetValue: Double? = null,
    /** e.g. 4 days out of the plan's week. */
    val targetFrequency: Int? = null,
    val note: String? = null,
)

data class PlanWithTargets(
    val plan: Plan,
    val targets: List<PlanTarget>,
)

/** A target next to what actually happened, for the Plan screen's progress rows. */
data class TargetProgress(
    val tracker: Tracker,
    val target: PlanTarget,
    val achievedDays: Int,
    val achievedValue: Double?,
) {
    val targetDays: Int? get() = target.targetFrequency

    val fraction: Float
        get() {
            val days = target.targetFrequency
            if (days != null && days > 0) return (achievedDays.toFloat() / days).coerceIn(0f, 1f)
            val value = target.targetValue ?: return 0f
            val achieved = achievedValue ?: return 0f
            if (value == 0.0) return 0f
            return when (tracker.direction) {
                Direction.DOWN -> (value / achieved.coerceAtLeast(0.0001)).coerceIn(0.0, 1.0).toFloat()
                else -> (achieved / value).coerceIn(0.0, 1.0).toFloat()
            }
        }

    val met: Boolean get() = fraction >= 1f
}

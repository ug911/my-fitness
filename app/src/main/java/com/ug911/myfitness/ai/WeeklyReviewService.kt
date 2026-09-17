package com.ug911.myfitness.ai

import com.ug911.myfitness.data.model.AiAnalysis
import com.ug911.myfitness.data.repository.AnalysisRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The "Review my week" pipeline:
 *
 *   history -> snapshot JSON -> model -> stored analysis + PROPOSED plan
 *
 * The service is constructed from a read-only [HistorySource] and a
 * [PlanProposalSink]; it has no way to write an entry, and the plan it writes is a
 * proposal that does nothing until the person approves it.
 */
class WeeklyReviewService(
    private val history: HistorySource,
    private val proposals: PlanProposalSink,
    private val analyses: AnalysisRepository,
    private val contextBuilder: AiContextBuilder = AiContextBuilder(),
) {

    /** Builds the snapshot without calling out to anything, for preview and for tests. */
    suspend fun buildSnapshot(period: ReviewPeriod): String {
        val current = history.load(period.start, period.end)
        val previous = history.load(period.start.minusDays(7), period.start.minusDays(1))
        return contextBuilder.buildJson(current, previous.takeIf { it.entries.isNotEmpty() })
    }

    suspend fun review(period: ReviewPeriod, client: AiClient): ReviewOutcome {
        val current = history.load(period.start, period.end)
        if (current.entries.isEmpty() && current.journal.isEmpty()) {
            return ReviewOutcome.NothingLogged
        }
        val previous = history.load(period.start.minusDays(7), period.start.minusDays(1))
        val snapshot = contextBuilder.buildJson(current, previous.takeIf { it.entries.isNotEmpty() })

        val raw = runCatching { client.complete(Prompts.SYSTEM, Prompts.userMessage(snapshot)) }
            .getOrElse { return ReviewOutcome.Failed(it.message ?: "The AI request failed", snapshot) }

        val payload = AiReviewParser.parse(raw)
            .getOrElse { return ReviewOutcome.Failed("Could not read the model's reply: ${it.message}", snapshot) }

        val analysisId = analyses.save(
            AiAnalysis(
                periodStart = period.start,
                periodEnd = period.end,
                inputSnapshot = snapshot,
                review = payload.review,
                insights = payload.insights,
                planRationale = payload.plan?.rationale,
                provider = client.providerId,
                model = client.model,
            ),
        )

        var proposedPlanId: Long? = null
        var unmatched: List<String> = emptyList()
        payload.plan?.let { planPayload ->
            val resolution = AiReviewParser.resolveTargets(planPayload, current.trackers)
            unmatched = resolution.unmatchedTrackerNames
            if (resolution.targets.isNotEmpty()) {
                val plan = AiReviewParser.buildProposedPlan(payload, period.end, analysisId)
                proposedPlanId = proposals.propose(plan, resolution.targets)
            }
        }

        return ReviewOutcome.Success(
            analysisId = analysisId,
            proposedPlanId = proposedPlanId,
            unmatchedTargets = unmatched,
            snapshot = snapshot,
        )
    }
}

sealed interface ReviewOutcome {
    data class Success(
        val analysisId: Long,
        val proposedPlanId: Long?,
        val unmatchedTargets: List<String>,
        val snapshot: String,
    ) : ReviewOutcome

    data class Failed(val message: String, val snapshot: String) : ReviewOutcome

    data object NothingLogged : ReviewOutcome
}

/** A review window. Weeks run Monday to Sunday. */
data class ReviewPeriod(val start: LocalDate, val end: LocalDate) {
    val label: String get() = "$start to $end"

    companion object {
        fun weekContaining(date: LocalDate): ReviewPeriod {
            val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return ReviewPeriod(start, start.plusDays(6))
        }

        fun lastCompleteWeek(today: LocalDate): ReviewPeriod =
            weekContaining(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(1))

        /** The last seven days ending yesterday: what the button offers when mid-week. */
        fun trailingWeek(today: LocalDate): ReviewPeriod =
            ReviewPeriod(today.minusDays(7), today.minusDays(1))
    }
}

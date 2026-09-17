package com.ug911.myfitness.data.repository

import com.ug911.myfitness.ai.HistorySource
import com.ug911.myfitness.ai.PeriodData
import com.ug911.myfitness.ai.PlanProposalSink
import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanStatus
import com.ug911.myfitness.data.model.PlanTarget
import java.time.LocalDate

/**
 * Adapters that give the AI layer exactly two capabilities: read history, propose a plan.
 * They are the only bridge between [com.ug911.myfitness.ai] and the repositories.
 */
class RepositoryHistorySource(
    private val trackers: TrackerRepository,
    private val logs: LogRepository,
    private val plans: PlanRepository,
) : HistorySource {

    override suspend fun load(start: LocalDate, end: LocalDate): PeriodData {
        val allTrackers = trackers.all()
        val types = allTrackers.associate { it.id to it.type }
        return PeriodData(
            start = start,
            end = end,
            trackers = allTrackers,
            entries = logs.entriesBetween(start, end, types),
            journal = logs.journalBetween(start, end),
            plan = plans.activeFor(start),
        )
    }
}

class ProposalOnlyPlanSink(private val plans: PlanRepository) : PlanProposalSink {

    /** Forces PROPOSED status regardless of what the caller passed in. */
    override suspend fun propose(plan: Plan, targets: List<PlanTarget>): Long =
        plans.create(plan.copy(status = PlanStatus.PROPOSED), targets)
}

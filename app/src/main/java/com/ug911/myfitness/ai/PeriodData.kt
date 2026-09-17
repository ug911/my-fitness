package com.ug911.myfitness.ai

import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.JournalEntry
import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.model.PlanWithTargets
import com.ug911.myfitness.data.model.Tracker
import java.time.LocalDate

/** Everything the AI layer is allowed to see about one window of time. */
data class PeriodData(
    val start: LocalDate,
    val end: LocalDate,
    val trackers: List<Tracker>,
    val entries: List<Entry>,
    val journal: List<JournalEntry>,
    val plan: PlanWithTargets? = null,
)

/**
 * Read-only history, handed to the review service instead of a repository.
 * The AI path has no type it could use to write an [Entry] even by accident.
 */
interface HistorySource {
    suspend fun load(start: LocalDate, end: LocalDate): PeriodData
}

/**
 * The single write the AI path is allowed: proposing a plan. Proposals land as
 * [com.ug911.myfitness.data.model.PlanStatus.PROPOSED] and only become goals when
 * the person approves them.
 */
interface PlanProposalSink {
    suspend fun propose(plan: Plan, targets: List<PlanTarget>): Long
}

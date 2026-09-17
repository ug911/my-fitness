package com.ug911.myfitness.analysis

import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.PlanWithTargets
import com.ug911.myfitness.data.model.TargetProgress
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.isCompleted

/** Plan targets next to what actually happened. Never mutates either side. */
object PlanProgress {

    fun compute(
        plan: PlanWithTargets,
        trackers: List<Tracker>,
        entries: List<Entry>,
    ): List<TargetProgress> {
        val byId = trackers.associateBy { it.id }
        val inPeriod = entries.filter { it.date >= plan.plan.startDate && it.date <= plan.plan.endDate }
        return plan.targets.mapNotNull { target ->
            val tracker = byId[target.trackerId] ?: return@mapNotNull null
            val mine = inPeriod.filter { it.trackerId == tracker.id }
            val stat = PeriodStats.compute(tracker, mine, PeriodStats.dayCount(plan.plan.startDate, plan.plan.endDate))
            TargetProgress(
                tracker = tracker,
                target = target,
                achievedDays = mine.count { it.value.isCompleted(tracker) },
                achievedValue = stat.headline,
            )
        }
    }
}

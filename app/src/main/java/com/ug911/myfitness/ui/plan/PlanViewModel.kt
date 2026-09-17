package com.ug911.myfitness.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.analysis.PlanProgress
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.model.PlanWithTargets
import com.ug911.myfitness.data.model.TargetProgress
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.repository.LogRepository
import com.ug911.myfitness.data.repository.PlanRepository
import com.ug911.myfitness.data.repository.TrackerRepository
import com.ug911.myfitness.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class PlanViewModel(
    trackers: TrackerRepository,
    logs: LogRepository,
    private val plans: PlanRepository,
) : ViewModel() {

    private val today = LocalDate.now()

    val state: StateFlow<PlanUiState> = combine(
        trackers.observeAll(),
        plans.observeActive(today),
        plans.observeProposed(),
        logs.observeEntriesBetween(today.minusDays(21), today.plusDays(7)),
    ) { allTrackers, active, proposed, entries ->
        PlanUiState(
            activePlan = active,
            progress = active?.let { PlanProgress.compute(it, allTrackers, entries) }.orEmpty(),
            proposedPlan = proposed,
            proposedTargets = proposed?.targets.orEmpty().mapNotNull { target ->
                allTrackers.firstOrNull { it.id == target.trackerId }?.let { ProposedTarget(it, target) }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    /** Approval is the only path from proposal to goal. */
    fun approveProposed() {
        val planId = state.value.proposedPlan?.plan?.id ?: return
        viewModelScope.launch { plans.approve(planId) }
    }

    fun rejectProposed() {
        val planId = state.value.proposedPlan?.plan?.id ?: return
        viewModelScope.launch { plans.reject(planId) }
    }

    /** Targets stay editable before approval: the suggestion is a draft, not a decree. */
    fun adjustProposedFrequency(target: PlanTarget, delta: Int) {
        val proposed = state.value.proposedPlan ?: return
        val updated = proposed.targets.map { existing ->
            if (existing.id == target.id) {
                val current = existing.targetFrequency ?: 0
                existing.copy(targetFrequency = (current + delta).coerceIn(0, 7))
            } else {
                existing
            }
        }
        viewModelScope.launch { plans.replaceTargets(proposed.plan.id, updated) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlanViewModel(container.trackers, container.logs, container.plans) }
        }
    }
}

data class PlanUiState(
    val activePlan: PlanWithTargets? = null,
    val progress: List<TargetProgress> = emptyList(),
    val proposedPlan: PlanWithTargets? = null,
    val proposedTargets: List<ProposedTarget> = emptyList(),
)

data class ProposedTarget(val tracker: Tracker, val target: PlanTarget) {
    fun describe(): String = buildString {
        target.targetFrequency?.let { append("$it days") }
        target.targetValue?.let {
            if (isNotEmpty()) append(" x ")
            append(com.ug911.myfitness.data.model.formatNumber(it))
            tracker.unit?.let { unit -> append(" $unit") }
        }
        if (isEmpty()) append("no target set")
    }
}

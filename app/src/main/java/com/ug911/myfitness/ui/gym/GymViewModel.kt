package com.ug911.myfitness.ui.gym

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.data.model.ExerciseDoc
import com.ug911.myfitness.data.model.ExerciseLog
import com.ug911.myfitness.data.model.TrainingDay
import com.ug911.myfitness.data.model.TrainingItem
import com.ug911.myfitness.data.model.TrainingPlanDoc
import com.ug911.myfitness.data.repository.KnowledgeRepository
import com.ug911.myfitness.data.repository.WorkoutRepository
import com.ug911.myfitness.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The gym session: what the plan says to do today, what you have logged so far, and what
 * you managed last time - which is the only number that makes progression visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GymViewModel(
    knowledge: KnowledgeRepository,
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())
    private val openExercise = MutableStateFlow<String?>(null)
    private val lastSessions = MutableStateFlow<Map<String, ExerciseLog>>(emptyMap())

    val state: StateFlow<GymUiState> = combine(
        date,
        knowledge.observePlan(),
        knowledge.observeExercises(),
        date.flatMapLatest { workouts.observeDay(it) },
        combine(openExercise, lastSessions) { open, last -> open to last },
    ) { day, plan, exercises, logged, openAndLast ->
        val (open, last) = openAndLast
        val trainingDay = plan?.dayFor(day.dayOfWeek)
        val byId = exercises.associateBy { it.id }
        val planned = trainingDay?.items.orEmpty()
        GymUiState(
            date = day,
            plan = plan,
            day = trainingDay,
            rows = planned.map { item ->
                GymRow(
                    item = item,
                    doc = byId[item.exercise],
                    logged = logged[item.exercise],
                    lastTime = last[item.exercise],
                )
            },
            extraRows = logged
                .filterKeys { id -> planned.none { it.exercise == id } }
                .map { (id, log) -> GymRow(TrainingItem(id, 0, "extra"), byId[id], log, last[id]) },
            allExercises = exercises,
            openExerciseId = open,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GymUiState())

    init {
        refreshLastSessions()
    }

    /** Looks up the previous session for everything trained recently. */
    private fun refreshLastSessions() {
        viewModelScope.launch {
            val today = date.value
            val recent = workouts.between(today.minusDays(90), today).map { it.exerciseId }
            val planned = state.value.rows.map { it.item.exercise }
            lastSessions.value = (recent + planned).distinct().mapNotNull { id ->
                workouts.lastSession(id, today)?.let { id to it }
            }.toMap()
        }
    }

    fun open(exerciseId: String?) {
        openExercise.value = exerciseId
        if (exerciseId != null) refreshLastSessions()
    }

    fun logSet(exerciseId: String, weightKg: Double?, reps: Int) {
        if (reps <= 0) return
        viewModelScope.launch {
            workouts.addSet(date.value, exerciseId, weightKg, reps)
            refreshLastSessions()
        }
    }

    fun deleteSet(id: Long) {
        viewModelScope.launch { workouts.deleteSet(id) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GymViewModel(container.knowledge, container.workouts) }
        }
    }
}

data class GymUiState(
    val date: LocalDate = LocalDate.now(),
    val plan: TrainingPlanDoc? = null,
    val day: TrainingDay? = null,
    val rows: List<GymRow> = emptyList(),
    val extraRows: List<GymRow> = emptyList(),
    val allExercises: List<ExerciseDoc> = emptyList(),
    val openExerciseId: String? = null,
) {
    val setsDone: Int get() = (rows + extraRows).sumOf { it.setsDone }
    val setsPlanned: Int get() = rows.sumOf { it.item.sets }
    val volume: Double get() = (rows + extraRows).sumOf { it.logged?.totalVolume ?: 0.0 }
    val isRestDay: Boolean get() = day?.isRestDay ?: false
}

/** One exercise in today's session: the target, what is logged, and last time's numbers. */
data class GymRow(
    val item: TrainingItem,
    val doc: ExerciseDoc?,
    val logged: ExerciseLog?,
    val lastTime: ExerciseLog?,
) {
    val name: String
        get() = doc?.name ?: item.exercise.replace('_', ' ').replaceFirstChar { it.uppercase() }

    val setsDone: Int get() = logged?.sets?.size ?: 0
    val complete: Boolean get() = item.sets > 0 && setsDone >= item.sets
    val target: String get() = if (item.sets > 0) "${item.sets} x ${item.reps}" else "extra"

    /** The obvious next attempt: what you did last, ready to accept or change. */
    fun suggestedWeight(): Double? =
        logged?.sets?.lastOrNull()?.weightKg ?: lastTime?.topSet?.weightKg

    fun suggestedReps(): Int =
        logged?.sets?.lastOrNull()?.reps
            ?: lastTime?.sets?.firstOrNull()?.reps
            ?: item.reps.substringBefore('-').toIntOrNull()
            ?: 10
}

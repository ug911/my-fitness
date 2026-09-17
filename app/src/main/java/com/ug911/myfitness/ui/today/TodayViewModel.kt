package com.ug911.myfitness.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.data.model.DayLog
import com.ug911.myfitness.data.model.PlanWithTargets
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.repository.LogRepository
import com.ug911.myfitness.data.repository.PlanRepository
import com.ug911.myfitness.data.repository.TrackerRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val trackers: TrackerRepository,
    private val logs: LogRepository,
    plans: PlanRepository,
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    val state: StateFlow<TodayUiState> = combine(
        selectedDate,
        trackers.observeActive(),
        selectedDate.flatMapLatest { logs.observeDay(it) },
        selectedDate.flatMapLatest { plans.observeActive(it) },
    ) { date, activeTrackers, dayLog, plan ->
        TodayUiState(
            date = date,
            trackers = activeTrackers,
            dayLog = dayLog,
            activePlan = plan,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun shiftDate(days: Long) {
        val next = selectedDate.value.plusDays(days)
        // Logging the future is never what you meant.
        if (!next.isAfter(LocalDate.now())) selectedDate.value = next
    }

    fun setValue(tracker: Tracker, value: TrackerValue?) {
        val date = selectedDate.value
        val existingNotes = state.value.dayLog?.notesFor(tracker)
        viewModelScope.launch { logs.setValue(tracker, date, value, notes = existingNotes) }
    }

    fun setNotes(tracker: Tracker, notes: String?) {
        val date = selectedDate.value
        viewModelScope.launch {
            val current = state.value.dayLog?.valueFor(tracker)
            if (current == null) {
                // A note on its own still needs a value to hang off; keep the day honest
                // by ignoring notes for trackers that have not been logged.
                return@launch
            }
            logs.setNotes(tracker, date, notes)
        }
    }

    fun saveJournal(text: String) {
        val date = selectedDate.value
        viewModelScope.launch { logs.saveJournal(date, text) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TodayViewModel(container.trackers, container.logs, container.plans) }
        }
    }
}

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val trackers: List<Tracker> = emptyList(),
    val dayLog: DayLog? = null,
    val activePlan: PlanWithTargets? = null,
    val loading: Boolean = true,
) {
    val isToday: Boolean get() = date == LocalDate.now()

    val loggedCount: Int get() = dayLog?.entries?.size ?: 0
}

package com.ug911.myfitness.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.JournalEntry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.isCompleted
import com.ug911.myfitness.data.repository.LogRepository
import com.ug911.myfitness.data.repository.TrackerRepository
import com.ug911.myfitness.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class HistoryViewModel(
    trackers: TrackerRepository,
    logs: LogRepository,
    private val weeksShown: Int = 18,
) : ViewModel() {

    private val selectedDay = MutableStateFlow<LocalDate?>(null)

    private val rangeEnd = LocalDate.now()
    private val rangeStart = rangeEnd
        .minusWeeks((weeksShown - 1).toLong())
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    val state: StateFlow<HistoryUiState> = combine(
        trackers.observeAll(),
        logs.observeEntriesBetween(rangeStart, rangeEnd),
        logs.observeJournalBetween(rangeStart, rangeEnd),
        selectedDay,
    ) { allTrackers, entries, journals, selected ->
        HistoryUiState(
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            trackers = allTrackers,
            entriesByDate = entries.groupBy { it.date },
            journalByDate = journals.associateBy { it.date },
            selectedDate = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState(rangeStart, rangeEnd))

    fun select(date: LocalDate?) {
        selectedDay.value = if (selectedDay.value == date) null else date
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container.trackers, container.logs) }
        }
    }
}

data class HistoryUiState(
    val rangeStart: LocalDate,
    val rangeEnd: LocalDate,
    val trackers: List<Tracker> = emptyList(),
    val entriesByDate: Map<LocalDate, List<Entry>> = emptyMap(),
    val journalByDate: Map<LocalDate, JournalEntry> = emptyMap(),
    val selectedDate: LocalDate? = null,
) {
    private val trackersById = trackers.associateBy { it.id }

    /**
     * How "complete" a day looks in the heatmap: the share of that day's logged
     * trackers that counted as done. A day with nothing logged is empty, which is
     * itself information.
     */
    fun intensity(date: LocalDate): DayIntensity {
        val entries = entriesByDate[date].orEmpty()
        if (entries.isEmpty()) return DayIntensity.NONE
        val completed = entries.count { entry ->
            trackersById[entry.trackerId]?.let { entry.value.isCompleted(it) } == true
        }
        val ratio = completed.toFloat() / entries.size
        return when {
            ratio >= 0.8f -> DayIntensity.FULL
            ratio >= 0.5f -> DayIntensity.GOOD
            else -> DayIntensity.PARTIAL
        }
    }

    fun entriesFor(date: LocalDate): List<Pair<Tracker, Entry>> =
        entriesByDate[date].orEmpty().mapNotNull { entry ->
            trackersById[entry.trackerId]?.let { it to entry }
        }.sortedWith(compareBy({ it.first.category.ordinal }, { it.first.sortOrder }))
}

enum class DayIntensity {
    NONE,
    PARTIAL,
    GOOD,
    FULL,
}

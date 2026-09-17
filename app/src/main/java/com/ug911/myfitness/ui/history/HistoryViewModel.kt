package com.ug911.myfitness.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.JournalEntry
import com.ug911.myfitness.data.model.Tracker
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
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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

    /** Active trackers are the denominator: switched-off ones are not owed a value. */
    private val expectedPerDay: Int = trackers.count { it.active }.coerceAtLeast(1)

    val totalDays: Int = (ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1).toInt()

    val daysLogged: Int = entriesByDate.count { it.value.isNotEmpty() }

    /** Consecutive days with something logged, counting back from today. */
    val currentStreak: Int = run {
        var streak = 0
        var day = rangeEnd
        while (!day.isBefore(rangeStart) && entriesByDate[day].orEmpty().isNotEmpty()) {
            streak++
            day = day.minusDays(1)
        }
        streak
    }

    val bestDayLabel: String = entriesByDate
        .maxByOrNull { it.value.size }
        ?.let { (date, entries) ->
            if (entries.isEmpty()) "-" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        ?: "-"

    /**
     * Heat level for a day: 0 means nothing logged, 1-4 step up the sequential ramp by
     * how much of the day was filled in. Four bins, because past about seven classes
     * adjacent shades stop being distinguishable.
     */
    fun level(date: LocalDate): Int {
        val entries = entriesByDate[date].orEmpty()
        if (entries.isEmpty()) return 0
        val share = entries.size.toFloat() / expectedPerDay
        return when {
            share >= 0.75f -> 4
            share >= 0.5f -> 3
            share >= 0.25f -> 2
            else -> 1
        }
    }

    fun entriesFor(date: LocalDate): List<Pair<Tracker, Entry>> =
        entriesByDate[date].orEmpty().mapNotNull { entry ->
            trackersById[entry.trackerId]?.let { it to entry }
        }.sortedWith(compareBy({ it.first.section.ordinal }, { it.first.sortOrder }))
}

package com.ug911.myfitness.ui.trackers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.repository.TrackerRepository
import com.ug911.myfitness.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Trackers are data, so this screen is the whole "schema editor": adding a habit is a
 * row, not a release.
 */
class TrackersViewModel(private val trackers: TrackerRepository) : ViewModel() {

    val state: StateFlow<List<Tracker>> = trackers.observeAll()
        .map { list -> list.sortedWith(compareBy({ it.section.ordinal }, { it.sortOrder }, { it.name })) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setActive(tracker: Tracker, active: Boolean) {
        viewModelScope.launch { trackers.setActive(tracker.id, active) }
    }

    fun save(tracker: Tracker) {
        viewModelScope.launch { trackers.save(tracker) }
    }

    /** Adds an option to a checklist, ignoring blanks and duplicates. */
    fun addOption(tracker: Tracker, option: String) {
        val trimmed = option.trim()
        if (trimmed.isEmpty() || tracker.options.any { it.equals(trimmed, ignoreCase = true) }) return
        viewModelScope.launch { trackers.save(tracker.copy(options = tracker.options + trimmed)) }
    }

    /**
     * Takes an option off a checklist. Entries keep the text they were logged with, so
     * removing an item never rewrites a day that had it.
     */
    fun removeOption(tracker: Tracker, option: String) {
        viewModelScope.launch { trackers.save(tracker.copy(options = tracker.options - option)) }
    }

    fun delete(tracker: Tracker) {
        viewModelScope.launch { trackers.delete(tracker) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { TrackersViewModel(container.trackers) }
        }
    }
}

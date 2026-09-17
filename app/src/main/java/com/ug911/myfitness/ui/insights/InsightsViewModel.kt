package com.ug911.myfitness.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.ai.ReviewOutcome
import com.ug911.myfitness.ai.ReviewPeriod
import com.ug911.myfitness.ai.WeeklyReviewService
import com.ug911.myfitness.analysis.OutcomeSplit
import com.ug911.myfitness.analysis.PatternAnalysis
import com.ug911.myfitness.analysis.PeriodStats
import com.ug911.myfitness.analysis.SeriesPoint
import com.ug911.myfitness.analysis.TrackerStat
import com.ug911.myfitness.data.model.AiAnalysis
import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.repository.AnalysisRepository
import com.ug911.myfitness.data.repository.LogRepository
import com.ug911.myfitness.data.repository.TrackerRepository
import com.ug911.myfitness.di.AppContainer
import com.ug911.myfitness.settings.SettingsStore
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
class InsightsViewModel(
    trackers: TrackerRepository,
    private val logs: LogRepository,
    analyses: AnalysisRepository,
    private val settings: SettingsStore,
    private val reviewService: WeeklyReviewService,
    private val clientFactory: (com.ug911.myfitness.settings.AppSettings) -> com.ug911.myfitness.ai.AiClient,
) : ViewModel() {

    private val window = MutableStateFlow(InsightsWindow.WEEK)
    private val reviewState = MutableStateFlow<ReviewUiState>(ReviewUiState.Idle)
    private val snapshotPreview = MutableStateFlow<String?>(null)

    val state: StateFlow<InsightsUiState> = combine(
        window,
        trackers.observeActive(),
        window.flatMapLatest { w -> logs.observeEntriesBetween(LocalDate.now().minusDays(w.days - 1L), LocalDate.now()) },
        analyses.observeLatest(),
        combine(reviewState, snapshotPreview, settings.settings) { review, snapshot, appSettings ->
            Triple(review, snapshot, appSettings.hasApiKey)
        },
    ) { selectedWindow, activeTrackers, entries, latestAnalysis, (review, snapshot, hasKey) ->
        val end = LocalDate.now()
        val start = end.minusDays(selectedWindow.days - 1L)
        val stats = PeriodStats.computeAll(activeTrackers, entries, start, end)
            .filter { it.tracker.aggregation != Aggregation.NONE }
        val outcomes = activeTrackers.filter { it.isOutcomeLike() }

        InsightsUiState(
            window = selectedWindow,
            headlines = headlineStats(stats),
            stats = stats.sortedWith(compareBy({ it.tracker.section.ordinal }, { it.tracker.sortOrder })),
            series = stats.filter { it.tracker.aggregation != Aggregation.NONE }.associate { stat ->
                stat.tracker.id to PeriodStats.weeklySeries(
                    tracker = stat.tracker,
                    entries = entries,
                    end = end,
                    weeks = (selectedWindow.days / 7).coerceAtLeast(2),
                )
            },
            sameDayPatterns = PatternAnalysis.rank(activeTrackers, entries, outcomes, lagDays = 0),
            nextDayPatterns = PatternAnalysis.rank(activeTrackers, entries, outcomes, lagDays = 1),
            latestAnalysis = latestAnalysis,
            review = review,
            snapshotPreview = snapshot,
            aiConfigured = hasKey,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    fun setWindow(next: InsightsWindow) {
        window.value = next
    }

    fun dismissSnapshot() {
        snapshotPreview.value = null
    }

    fun previewSnapshot() {
        viewModelScope.launch {
            snapshotPreview.value = reviewService.buildSnapshot(currentPeriod())
        }
    }

    /** "Review my week": builds the snapshot, calls the model, stores the result. */
    fun reviewWeek() {
        if (reviewState.value == ReviewUiState.Running) return
        viewModelScope.launch {
            reviewState.value = ReviewUiState.Running
            val appSettings = settings.current()
            if (!appSettings.hasApiKey) {
                reviewState.value = ReviewUiState.Error("Add an API key in Settings first")
                return@launch
            }
            val outcome = runCatching { reviewService.review(currentPeriod(), clientFactory(appSettings)) }
                .getOrElse { ReviewOutcome.Failed(it.message ?: "The review failed", "") }

            reviewState.value = when (outcome) {
                is ReviewOutcome.Success -> ReviewUiState.Done(
                    proposedPlan = outcome.proposedPlanId != null,
                    unmatched = outcome.unmatchedTargets,
                )
                is ReviewOutcome.Failed -> ReviewUiState.Error(outcome.message)
                ReviewOutcome.NothingLogged -> ReviewUiState.Error("Nothing logged in this period yet")
            }
        }
    }

    fun clearReviewState() {
        reviewState.value = ReviewUiState.Idle
    }

    /** Reviews the last complete Monday-Sunday week, or the trailing week if that is empty. */
    private fun currentPeriod(): ReviewPeriod = ReviewPeriod.lastCompleteWeek(LocalDate.now())

    private fun Tracker.isOutcomeLike(): Boolean =
        type == TrackerType.RATING || (type == TrackerType.NUMBER && aggregation == Aggregation.AVERAGE) ||
            type == TrackerType.DURATION || type == TrackerType.TIME

    /**
     * The three numbers worth putting at the top: the ones with a standing target,
     * since those are the habits being aimed at.
     */
    private fun headlineStats(stats: List<TrackerStat>): List<TrackerStat> {
        val withTargets = stats.filter { it.tracker.targetValue != null && it.daysLogged > 0 }
        return withTargets.take(3).ifEmpty { stats.filter { it.daysLogged > 0 }.take(3) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                InsightsViewModel(
                    trackers = container.trackers,
                    logs = container.logs,
                    analyses = container.analyses,
                    settings = container.settings,
                    reviewService = container.weeklyReview,
                    clientFactory = container::aiClient,
                )
            }
        }
    }
}

enum class InsightsWindow(val days: Int, val label: String) {
    WEEK(7, "7 days"),
    MONTH(30, "30 days"),
    QUARTER(90, "90 days"),
}

data class InsightsUiState(
    val window: InsightsWindow = InsightsWindow.WEEK,
    val headlines: List<TrackerStat> = emptyList(),
    val stats: List<TrackerStat> = emptyList(),
    val series: Map<Long, List<SeriesPoint>> = emptyMap(),
    val sameDayPatterns: List<OutcomeSplit> = emptyList(),
    val nextDayPatterns: List<OutcomeSplit> = emptyList(),
    val latestAnalysis: AiAnalysis? = null,
    val review: ReviewUiState = ReviewUiState.Idle,
    val snapshotPreview: String? = null,
    val aiConfigured: Boolean = false,
)

sealed interface ReviewUiState {
    data object Idle : ReviewUiState
    data object Running : ReviewUiState
    data class Done(val proposedPlan: Boolean, val unmatched: List<String>) : ReviewUiState
    data class Error(val message: String) : ReviewUiState
}

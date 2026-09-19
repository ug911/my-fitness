package com.ug911.myfitness.di

import android.content.Context
import com.ug911.myfitness.ai.AiClient
import com.ug911.myfitness.ai.AnthropicClient
import com.ug911.myfitness.ai.GeminiClient
import com.ug911.myfitness.ai.OpenAiClient
import com.ug911.myfitness.ai.WeeklyReviewService
import com.ug911.myfitness.data.local.MyFitnessDatabase
import com.ug911.myfitness.data.repository.AnalysisRepository
import com.ug911.myfitness.data.repository.KnowledgeRepository
import com.ug911.myfitness.data.repository.NutritionRepository
import com.ug911.myfitness.data.repository.WorkoutRepository
import com.ug911.myfitness.knowledge.KnowledgeSync
import com.ug911.myfitness.data.repository.LogRepository
import com.ug911.myfitness.data.repository.PlanRepository
import com.ug911.myfitness.data.repository.ProposalOnlyPlanSink
import com.ug911.myfitness.data.repository.RepositoryHistorySource
import com.ug911.myfitness.data.repository.TrackerRepository
import com.ug911.myfitness.health.HealthConnectManager
import com.ug911.myfitness.health.HealthSyncCoordinator
import com.ug911.myfitness.settings.AiProvider
import com.ug911.myfitness.settings.AppSettings
import com.ug911.myfitness.settings.SettingsStore

/**
 * Hand-rolled dependency graph. One user, one process, no need for a DI framework —
 * and the wiring stays readable, which matters for the AI boundary below.
 */
class AppContainer(context: Context) {

    private val database = MyFitnessDatabase.build(context)

    val trackers = TrackerRepository(database.trackerDao())
    val logs = LogRepository(database.entryDao(), database.journalDao(), database.trackerDao())
    val plans = PlanRepository(database.planDao())
    val analyses = AnalysisRepository(database.aiAnalysisDao())
    val knowledge = KnowledgeRepository(database.knowledgeDao())
    val workouts = WorkoutRepository(database.workoutDao())
    val nutrition = NutritionRepository(database.foodLogDao())
    val knowledgeSync = KnowledgeSync(context, knowledge)

    val settings = SettingsStore(context)
    val health = HealthConnectManager(context)
    val healthSync = HealthSyncCoordinator(health, trackers, logs)

    /**
     * Note what is passed in: a read-only history source and a proposal-only plan sink.
     * The review service cannot reach [logs] or write an entry.
     */
    val weeklyReview = WeeklyReviewService(
        history = RepositoryHistorySource(trackers, logs, plans),
        proposals = ProposalOnlyPlanSink(plans),
        analyses = analyses,
    )

    fun aiClient(settings: AppSettings): AiClient = when (settings.provider) {
        AiProvider.ANTHROPIC -> AnthropicClient(settings.apiKey, settings.model)
        AiProvider.GEMINI -> GeminiClient(settings.apiKey, settings.model)
        AiProvider.OPENAI -> OpenAiClient(settings.apiKey, settings.model)
    }
}

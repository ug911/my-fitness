package com.ug911.myfitness.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ug911.myfitness.ai.AnthropicClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * App settings. The API key lives in this app's private storage; it is only ever sent
 * to the provider it belongs to, and only when a review is explicitly requested.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val PROVIDER = stringPreferencesKey("ai_provider")
        val API_KEY = stringPreferencesKey("ai_api_key")
        val MODEL = stringPreferencesKey("ai_model")
        val HEALTH_SYNC = booleanPreferencesKey("health_sync_enabled")
        val LAST_SYNC = longPreferencesKey("last_sync_millis")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val provider = prefs[Keys.PROVIDER]?.let { stored ->
            AiProvider.entries.firstOrNull { it.name == stored }
        } ?: AiProvider.ANTHROPIC
        AppSettings(
            provider = provider,
            apiKey = prefs[Keys.API_KEY].orEmpty(),
            model = prefs[Keys.MODEL]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            healthSyncEnabled = prefs[Keys.HEALTH_SYNC] ?: true,
            lastSyncMillis = prefs[Keys.LAST_SYNC] ?: 0L,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun healthSyncEnabled(): Boolean = current().healthSyncEnabled

    suspend fun setProvider(provider: AiProvider) = context.dataStore.edit { prefs ->
        prefs[Keys.PROVIDER] = provider.name
        prefs[Keys.MODEL] = provider.defaultModel
    }

    suspend fun setApiKey(key: String) = context.dataStore.edit { it[Keys.API_KEY] = key.trim() }

    suspend fun setModel(model: String) = context.dataStore.edit { it[Keys.MODEL] = model.trim() }

    suspend fun setHealthSyncEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.HEALTH_SYNC] = enabled }

    suspend fun setLastSyncMillis(millis: Long) = context.dataStore.edit { it[Keys.LAST_SYNC] = millis }
}

data class AppSettings(
    val provider: AiProvider,
    val apiKey: String,
    val model: String,
    val healthSyncEnabled: Boolean,
    val lastSyncMillis: Long,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
}

enum class AiProvider(val label: String, val defaultModel: String, val keyHint: String) {
    ANTHROPIC("Claude", AnthropicClient.DEFAULT_MODEL, "console.anthropic.com API key"),
    GEMINI("Gemini", com.ug911.myfitness.ai.GeminiClient.DEFAULT_MODEL, "Google AI Studio API key"),
    OPENAI("OpenAI", com.ug911.myfitness.ai.OpenAiClient.DEFAULT_MODEL, "platform.openai.com API key"),
}

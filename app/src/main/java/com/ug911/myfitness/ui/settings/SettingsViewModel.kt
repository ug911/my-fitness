package com.ug911.myfitness.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.di.AppContainer
import com.ug911.myfitness.health.HealthAvailability
import com.ug911.myfitness.health.HealthConnectManager
import com.ug911.myfitness.health.HealthSyncCoordinator
import com.ug911.myfitness.health.SyncResult
import com.ug911.myfitness.settings.AiProvider
import com.ug911.myfitness.settings.AppSettings
import com.ug911.myfitness.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class SettingsViewModel(
    private val settings: SettingsStore,
    private val health: HealthConnectManager,
    private val healthSync: HealthSyncCoordinator,
) : ViewModel() {

    private val syncMessage = MutableStateFlow<String?>(null)
    private val permissionsGranted = MutableStateFlow(false)

    val permissions: Set<String> get() = health.permissions

    val state: StateFlow<SettingsUiState> = combine(
        settings.settings,
        syncMessage,
        permissionsGranted,
    ) { appSettings, message, granted ->
        SettingsUiState(
            settings = appSettings,
            healthAvailability = health.availability(),
            healthPermissionsGranted = granted,
            syncMessage = message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        viewModelScope.launch { permissionsGranted.value = health.hasAllPermissions() }
    }

    fun setProvider(provider: AiProvider) = viewModelScope.launch { settings.setProvider(provider) }

    fun setApiKey(key: String) = viewModelScope.launch { settings.setApiKey(key) }

    fun setModel(model: String) = viewModelScope.launch { settings.setModel(model) }

    fun setHealthSync(enabled: Boolean) = viewModelScope.launch { settings.setHealthSyncEnabled(enabled) }

    fun syncNow() {
        viewModelScope.launch {
            syncMessage.value = "Syncing..."
            syncMessage.value = when (val result = healthSync.sync(LocalDate.now())) {
                is SyncResult.Synced -> {
                    settings.setLastSyncMillis(System.currentTimeMillis())
                    "Updated ${result.values} values across ${result.days} days"
                }
                SyncResult.PermissionsMissing -> "Health Connect permissions not granted yet"
                SyncResult.Unavailable -> "Health Connect is not available on this device"
            }
            refreshPermissions()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.health, container.healthSync) }
        }
    }
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(AiProvider.ANTHROPIC, "", AiProvider.ANTHROPIC.defaultModel, true, 0L),
    val healthAvailability: HealthAvailability = HealthAvailability.UNAVAILABLE,
    val healthPermissionsGranted: Boolean = false,
    val syncMessage: String? = null,
)

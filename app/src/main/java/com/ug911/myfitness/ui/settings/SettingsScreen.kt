package com.ug911.myfitness.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.ug911.myfitness.health.HealthAvailability
import com.ug911.myfitness.settings.AiProvider
import com.ug911.myfitness.ui.common.PlainCard
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.mutedInkColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SYNC_FORMAT = DateTimeFormatter.ofPattern("d MMM HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    var apiKeyDraft by remember(state.settings.apiKey) { mutableStateOf(state.settings.apiKey) }
    var modelDraft by remember(state.settings.model) { mutableStateOf(state.settings.model) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract(),
    ) { viewModel.refreshPermissions() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader(
                title = "Health Connect",
                accent = DaySection.BODY.accent(),
                subtitle = "Steps, workouts, sleep and heart rate",
            ) }
        item {
            PlainCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (state.healthAvailability) {
                            HealthAvailability.AVAILABLE ->
                                if (state.healthPermissionsGranted) {
                                    "Connected"
                                } else {
                                    "Available, permissions not granted yet"
                                }
                            HealthAvailability.UPDATE_REQUIRED -> "Health Connect needs updating"
                            HealthAvailability.UNAVAILABLE -> "Not available on this device"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Background sync", Modifier.weight(1f))
                        Switch(
                            checked = state.settings.healthSyncEnabled,
                            onCheckedChange = viewModel::setHealthSync,
                        )
                    }
                    if (state.settings.lastSyncMillis > 0) {
                        Text(
                            "Last sync: " + SYNC_FORMAT.format(
                                Instant.ofEpochMilli(state.settings.lastSyncMillis).atZone(ZoneId.systemDefault()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedInkColor(),
                        )
                    }
                    state.syncMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = state.healthAvailability == HealthAvailability.AVAILABLE,
                            onClick = { permissionLauncher.launch(viewModel.permissions) },
                        ) {
                            Text(if (state.healthPermissionsGranted) "Review permissions" else "Grant permissions")
                        }
                        TextButton(onClick = viewModel::syncNow) { Text("Sync now") }
                    }
                }
            }
        }

        item { SectionHeader(
                title = "AI review",
                accent = DaySection.NIGHT.accent(),
                subtitle = "Used only when you press Review my week",
            ) }
        item {
            PlainCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = state.settings.provider == provider,
                                onClick = { viewModel.setProvider(provider) },
                                label = { Text(provider.label) },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = apiKeyDraft,
                        onValueChange = { apiKeyDraft = it },
                        label = { Text(state.settings.provider.keyHint) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = modelDraft,
                        onValueChange = { modelDraft = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.setApiKey(apiKeyDraft)
                                viewModel.setModel(modelDraft)
                            },
                        ) {
                            Text("Save")
                        }
                    }
                    Text(
                        "The key is kept in this app's private storage and is only sent to the provider " +
                            "you picked. Reviews send aggregates and your own notes, never the database file.",
                        style = MaterialTheme.typography.bodySmall,
                        color = mutedInkColor(),
                    )
                }
            }
        }

        item { SectionHeader(title = "About", accent = DaySection.EVENING.accent()) }
        item {
            PlainCard {
                Text(
                    "A personal health experiment journal: log in under a minute a day, then look for " +
                        "patterns over weeks. Everything lives on this device in a local database.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

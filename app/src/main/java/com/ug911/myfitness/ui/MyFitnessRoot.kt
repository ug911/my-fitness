package com.ug911.myfitness.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ug911.myfitness.di.AppContainer
import com.ug911.myfitness.ui.history.HistoryScreen
import com.ug911.myfitness.ui.history.HistoryViewModel
import com.ug911.myfitness.ui.insights.InsightsScreen
import com.ug911.myfitness.ui.insights.InsightsViewModel
import com.ug911.myfitness.ui.plan.PlanScreen
import com.ug911.myfitness.ui.plan.PlanViewModel
import com.ug911.myfitness.ui.settings.SettingsScreen
import com.ug911.myfitness.ui.settings.SettingsViewModel
import com.ug911.myfitness.ui.today.TodayScreen
import com.ug911.myfitness.ui.today.TodayViewModel
import com.ug911.myfitness.ui.trackers.TrackerEditorScreen
import com.ug911.myfitness.ui.trackers.TrackersScreen
import com.ug911.myfitness.ui.trackers.TrackersViewModel

private enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.CheckCircle),
    HISTORY("history", "History", Icons.Filled.CalendarMonth),
    INSIGHTS("insights", "Insights", Icons.Filled.Insights),
    PLAN("plan", "Plan", Icons.Filled.Flag),
}

private const val ROUTE_TRACKERS = "trackers"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TRACKER_EDITOR = "tracker-editor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFitnessRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Destination.entries.firstOrNull { it.route == currentRoute }?.label ?: "My Fitness") },
                actions = {
                    IconButton(onClick = { navController.navigate(ROUTE_TRACKERS) }) {
                        Icon(Icons.Filled.Tune, contentDescription = "Trackers")
                    }
                    IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(Destination.TODAY.route)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.TODAY.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.TODAY.route) {
                TodayScreen(viewModel = viewModel(factory = TodayViewModel.factory(container)))
            }
            composable(Destination.HISTORY.route) {
                HistoryScreen(viewModel = viewModel(factory = HistoryViewModel.factory(container)))
            }
            composable(Destination.INSIGHTS.route) {
                InsightsScreen(viewModel = viewModel(factory = InsightsViewModel.factory(container)))
            }
            composable(Destination.PLAN.route) {
                PlanScreen(viewModel = viewModel(factory = PlanViewModel.factory(container)))
            }
            composable(ROUTE_TRACKERS) {
                TrackersScreen(
                    viewModel = viewModel(factory = TrackersViewModel.factory(container)),
                    onEdit = { id -> navController.navigate("$ROUTE_TRACKER_EDITOR/$id") },
                )
            }
            composable(
                route = "$ROUTE_TRACKER_EDITOR/{trackerId}",
                arguments = listOf(navArgument("trackerId") { type = NavType.LongType }),
            ) { entry ->
                TrackerEditorScreen(
                    viewModel = viewModel(factory = TrackersViewModel.factory(container)),
                    trackerId = entry.arguments?.getLong("trackerId") ?: 0L,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(viewModel = viewModel(factory = SettingsViewModel.factory(container)))
            }
        }
    }
}

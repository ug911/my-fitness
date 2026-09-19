package com.ug911.myfitness.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.di.AppContainer
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.cardSurface
import com.ug911.myfitness.ui.theme.mutedInkColor
import com.ug911.myfitness.ui.theme.onAccentInk
import com.ug911.myfitness.ui.checklists.ChecklistsScreen
import com.ug911.myfitness.ui.exercise.ExerciseGuideScreen
import com.ug911.myfitness.ui.exercise.ExerciseGuideViewModel
import com.ug911.myfitness.ui.gym.GymScreen
import com.ug911.myfitness.ui.gym.GymViewModel
import com.ug911.myfitness.ui.nutrition.NutritionScreen
import com.ug911.myfitness.ui.nutrition.NutritionViewModel
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

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Which section's hue this tab borrows, so the bar carries the same identity. */
    val section: DaySection,
) {
    TODAY("today", "Today", Icons.Filled.CheckCircle, DaySection.MORNING),
    GYM("gym", "Gym", Icons.Filled.FitnessCenter, DaySection.MORNING),
    FOOD("nutrition", "Food", Icons.Filled.LocalDining, DaySection.BREAKFAST),
    HISTORY("history", "History", Icons.Filled.CalendarMonth, DaySection.NIGHT),
    INSIGHTS("insights", "Insights", Icons.Filled.Insights, DaySection.EVENING),
}

private const val ROUTE_TRACKERS = "trackers"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_TRACKER_EDITOR = "tracker-editor"
private const val ROUTE_CHECKLISTS = "checklists"
private const val ROUTE_EXERCISE = "exercise"
private const val ROUTE_PLAN = "plan"

@Composable
fun MyFitnessRoot(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    /** The two chrome actions, rendered inside whichever screen header is showing. */
    val headerActions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = { navController.navigate(ROUTE_PLAN) }) {
            Icon(Icons.Filled.Flag, contentDescription = "Plan", tint = mutedInkColor())
        }
        IconButton(onClick = { navController.navigate(ROUTE_TRACKERS) }) {
            Icon(Icons.Filled.Tune, contentDescription = "Trackers", tint = mutedInkColor())
        }
        IconButton(onClick = { navController.navigate(ROUTE_SETTINGS) }) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = mutedInkColor())
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = cardSurface()) {
                Destination.entries.forEach { destination ->
                    val accent = destination.section.accent()
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
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = onAccentInk(),
                            selectedTextColor = accent,
                            indicatorColor = accent,
                        ),
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
                TodayScreen(
                    viewModel = viewModel(factory = TodayViewModel.factory(container)),
                    actions = headerActions,
                    onOpenGym = { navController.navigate(Destination.GYM.route) },
                    onOpenFood = { navController.navigate(Destination.FOOD.route) },
                )
            }
            composable(Destination.GYM.route) {
                GymScreen(
                    viewModel = viewModel(factory = GymViewModel.factory(container)),
                    onOpenExercise = { id -> navController.navigate("$ROUTE_EXERCISE/$id") },
                    actions = headerActions,
                )
            }
            composable(Destination.FOOD.route) {
                NutritionScreen(
                    viewModel = viewModel(factory = NutritionViewModel.factory(container)),
                    actions = headerActions,
                )
            }
            composable(
                route = "$ROUTE_EXERCISE/{exerciseId}",
                arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("exerciseId").orEmpty()
                ExerciseGuideScreen(
                    viewModel = viewModel(factory = ExerciseGuideViewModel.factory(container, id)),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Destination.HISTORY.route) {
                HistoryScreen(
                    viewModel = viewModel(factory = HistoryViewModel.factory(container)),
                    actions = headerActions,
                )
            }
            composable(Destination.INSIGHTS.route) {
                InsightsScreen(
                    viewModel = viewModel(factory = InsightsViewModel.factory(container)),
                    actions = headerActions,
                )
            }
            composable(ROUTE_PLAN) {
                PlanScreen(
                    viewModel = viewModel(factory = PlanViewModel.factory(container)),
                    actions = headerActions,
                )
            }
            composable(ROUTE_TRACKERS) {
                TrackersScreen(
                    viewModel = viewModel(factory = TrackersViewModel.factory(container)),
                    onEdit = { id -> navController.navigate("$ROUTE_TRACKER_EDITOR/$id") },
                    onBack = { navController.popBackStack() },
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
            composable(ROUTE_CHECKLISTS) {
                ChecklistsScreen(
                    viewModel = viewModel(factory = TrackersViewModel.factory(container)),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                    onBack = { navController.popBackStack() },
                    onOpenChecklists = { navController.navigate(ROUTE_CHECKLISTS) },
                )
            }
        }
    }
}

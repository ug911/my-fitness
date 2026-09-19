package com.ug911.myfitness.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.FoodDoc
import com.ug911.myfitness.data.model.Macros
import com.ug911.myfitness.data.model.Meal
import com.ug911.myfitness.data.model.formatNumber
import com.ug911.myfitness.ui.common.AnimatedDecimal
import com.ug911.myfitness.ui.common.EmptyState
import com.ug911.myfitness.ui.common.Pill
import com.ug911.myfitness.ui.common.PlainCard
import com.ug911.myfitness.ui.common.ScreenHeader
import com.ug911.myfitness.ui.common.SectionHeader
import com.ug911.myfitness.ui.common.enterFrom
import com.ug911.myfitness.ui.theme.Palette
import com.ug911.myfitness.ui.theme.accent
import com.ug911.myfitness.ui.theme.gridLine
import com.ug911.myfitness.ui.theme.mutedInkColor

/**
 * Calories and protein for the foods on your own list. Portions, not grams: a katori,
 * a plate, two eggs. Every number is an estimate and editable, which is the honest way
 * to do this without weighing food.
 */
@Composable
fun NutritionScreen(
    viewModel: NutritionViewModel,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val accent = DaySection.BREAKFAST.accent()

    state.editingFood?.let { food ->
        FoodEditorDialog(
            food = food,
            onDismiss = { viewModel.edit(null) },
            onSave = { macros, label -> viewModel.saveOverride(food, macros, label) },
            onReset = { viewModel.resetFood(food) },
        )
    }

    state.addingTo?.let { meal ->
        FoodPickerDialog(
            meal = meal,
            choices = state.choicesFor(meal),
            onDismiss = { viewModel.startAdding(null) },
            onPick = { food ->
                viewModel.add(food, meal)
                viewModel.startAdding(null)
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            ScreenHeader(
                title = "Nutrition",
                subtitle = if (state.anythingLogged) "Today so far" else "Nothing logged yet",
                actions = actions,
            )
        }

        item {
            PlainCard(modifier = Modifier.enterFrom(0)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedDecimal(
                            value = state.total.kcal,
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (state.targets.kcal > 0) " of ${state.targets.kcal} kcal" else " kcal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = mutedInkColor(),
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                        )
                    }
                    MacroBar("Calories", state.kcalFraction, accent)
                    MacroBar("Protein", state.proteinFraction, DaySection.OFFICE.accent())
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MacroChip("Protein", state.total.protein, "g", DaySection.OFFICE.accent())
                        MacroChip("Carbs", state.total.carbs, "g", DaySection.MORNING.accent())
                        MacroChip("Fat", state.total.fat, "g", DaySection.EVENING.accent())
                        MacroChip("Fibre", state.total.fibre, "g", Palette.Good)
                    }
                    if (state.targets.proteinGrams > 0) {
                        Text(
                            text = "Protein target ${state.targets.proteinGrams} g. " +
                                "${formatNumber(state.total.protein)} g so far.",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedInkColor(),
                        )
                    }
                }
            }
        }

        Meal.entries.forEachIndexed { index, meal ->
            val logged = state.meals[meal].orEmpty()
            if (logged.isEmpty() && meal == Meal.SNACK) return@forEachIndexed
            item(key = "h-${meal.name}") {
                SectionHeader(
                    title = meal.label,
                    accent = accent,
                    subtitle = if (logged.isEmpty()) "nothing yet" else
                        "${formatNumber(state.mealMacros(meal).kcal)} kcal · " +
                            "${formatNumber(state.mealMacros(meal).protein)} g protein",
                    trailing = {
                        IconButton(onClick = { viewModel.startAdding(meal) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add to ${meal.label}", tint = accent)
                        }
                    },
                )
            }
            if (logged.isNotEmpty()) {
                item(key = "c-${meal.name}") {
                    PlainCard(modifier = Modifier.enterFrom(index + 1)) {
                        Column {
                            logged.forEach { item ->
                                LoggedFoodRow(
                                    logged = item,
                                    accent = accent,
                                    onLess = { viewModel.adjust(item.entry, -0.5) },
                                    onMore = { viewModel.adjust(item.entry, 0.5) },
                                    onEdit = { viewModel.edit(item.food.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!state.anythingLogged) {
            item {
                EmptyState(
                    title = "Nothing logged today",
                    body = "Tap the plus beside a meal. The list is only the food you actually eat - " +
                        "add more of it in Settings, Food and checklists.",
                )
            }
        }

        item {
            Text(
                text = "Every figure is a household estimate for how you eat it, not a lab value. " +
                    "Tap a food to correct it - your numbers win.",
                style = MaterialTheme.typography.bodySmall,
                color = mutedInkColor(),
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun MacroBar(label: String, fraction: Float, accent: Color) {
    val animated by animateFloatAsState(targetValue = fraction, label = "macro")
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = mutedInkColor())
            Text(
                "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = mutedInkColor(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(gridLine()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun MacroChip(label: String, value: Double, unit: String, accent: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(accent))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = mutedInkColor(),
                modifier = Modifier.padding(start = 5.dp),
            )
        }
        Text(
            "${formatNumber(value)}$unit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LoggedFoodRow(
    logged: LoggedFood,
    accent: Color,
    onLess: () -> Unit,
    onMore: () -> Unit,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(logged.food.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${logged.portionLabel} · ${formatNumber(logged.macros.kcal)} kcal · " +
                    "${formatNumber(logged.macros.protein)} g protein",
                style = MaterialTheme.typography.bodySmall,
                color = mutedInkColor(),
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Filled.Edit,
                contentDescription = "Correct ${logged.food.name}",
                tint = mutedInkColor(),
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(onClick = onLess) {
            Icon(Icons.Filled.Remove, contentDescription = "Less", tint = accent, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.Add, contentDescription = "More", tint = accent, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodPickerDialog(
    meal: Meal,
    choices: List<FoodDoc>,
    onDismiss: () -> Unit,
    onPick: (FoodDoc) -> Unit,
) {
    val accent = DaySection.BREAKFAST.accent()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        shape = MaterialTheme.shapes.large,
        title = { Text("Add to ${meal.label.lowercase()}") },
        text = {
            LazyColumn(modifier = Modifier.height(400.dp)) {
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        choices.forEach { food ->
                            Column(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(accent.copy(alpha = 0.10f))
                                    .clickable { onPick(food) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(food.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${food.portion.label} · ${formatNumber(food.per.kcal)} kcal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedInkColor(),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FoodEditorDialog(
    food: FoodDoc,
    onDismiss: () -> Unit,
    onSave: (Macros, String) -> Unit,
    onReset: () -> Unit,
) {
    var label by remember(food.id) { mutableStateOf(food.portion.label) }
    var kcal by remember(food.id) { mutableStateOf(formatNumber(food.per.kcal)) }
    var protein by remember(food.id) { mutableStateOf(formatNumber(food.per.protein)) }
    var carbs by remember(food.id) { mutableStateOf(formatNumber(food.per.carbs)) }
    var fat by remember(food.id) { mutableStateOf(formatNumber(food.per.fat)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                food.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = mutedInkColor())
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Portion") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("kcal", kcal, Modifier.weight(1f)) { kcal = it }
                    NumberField("protein", protein, Modifier.weight(1f)) { protein = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("carbs", carbs, Modifier.weight(1f)) { carbs = it }
                    NumberField("fat", fat, Modifier.weight(1f)) { fat = it }
                }
                if (food.confidence == "yours") {
                    Pill("your numbers", Palette.Good)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    Macros(
                        kcal = kcal.toDoubleOrNull() ?: food.per.kcal,
                        protein = protein.toDoubleOrNull() ?: food.per.protein,
                        carbs = carbs.toDoubleOrNull() ?: food.per.carbs,
                        fat = fat.toDoubleOrNull() ?: food.per.fat,
                        fibre = food.per.fibre,
                    ),
                    label,
                )
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (food.confidence == "yours") {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.width(120.dp),
    )
}

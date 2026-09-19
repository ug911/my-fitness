package com.ug911.myfitness.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ug911.myfitness.data.model.FoodDoc
import com.ug911.myfitness.data.model.FoodLogEntry
import com.ug911.myfitness.data.model.Macros
import com.ug911.myfitness.data.model.Meal
import com.ug911.myfitness.data.model.PlanTargets
import com.ug911.myfitness.data.repository.KnowledgeRepository
import com.ug911.myfitness.data.repository.NutritionRepository
import com.ug911.myfitness.data.repository.totalMacros
import com.ug911.myfitness.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * The nutrition calculator: only the foods actually eaten, counted in the portions they
 * are actually eaten in. A katori of dal, two eggs, one plate of poha.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    private val knowledge: KnowledgeRepository,
    private val nutrition: NutritionRepository,
) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())
    private val addingTo = MutableStateFlow<Meal?>(null)
    private val editing = MutableStateFlow<String?>(null)

    val state: StateFlow<NutritionUiState> = combine(
        date,
        knowledge.observeFoods(),
        date.flatMapLatest { nutrition.observeDay(it) },
        knowledge.observePlan(),
        combine(addingTo, editing) { adding, edit -> adding to edit },
    ) { day, foods, entries, plan, addingAndEditing ->
        val (adding, editingId) = addingAndEditing
        val byId = foods.associateBy { it.id }
        NutritionUiState(
            date = day,
            foods = foods,
            meals = Meal.entries.associateWith { meal ->
                entries.filter { it.meal == meal }.mapNotNull { entry ->
                    byId[entry.foodId]?.let { LoggedFood(entry, it) }
                }
            },
            total = totalMacros(entries, foods),
            targets = plan?.targets ?: PlanTargets(),
            addingTo = adding,
            editingFoodId = editingId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState())

    fun selectDate(day: LocalDate) {
        date.value = day
    }

    fun startAdding(meal: Meal?) {
        addingTo.value = meal
    }

    fun edit(foodId: String?) {
        editing.value = foodId
    }

    fun add(food: FoodDoc, meal: Meal, portions: Double = 1.0) {
        viewModelScope.launch {
            nutrition.addPortions(date.value, food.id, meal, portions)
        }
    }

    fun adjust(entry: FoodLogEntry, delta: Double) {
        viewModelScope.launch {
            nutrition.addPortions(entry.date, entry.foodId, entry.meal, delta)
        }
    }

    /** Your correction to a food's numbers. It outranks the published document. */
    fun saveOverride(food: FoodDoc, macros: Macros, portionLabel: String) {
        viewModelScope.launch {
            knowledge.overrideFood(food, macros, portionLabel)
            editing.value = null
        }
    }

    fun resetFood(food: FoodDoc) {
        viewModelScope.launch {
            knowledge.clearOverride(food.id)
            editing.value = null
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NutritionViewModel(container.knowledge, container.nutrition) }
        }
    }
}

data class NutritionUiState(
    val date: LocalDate = LocalDate.now(),
    val foods: List<FoodDoc> = emptyList(),
    val meals: Map<Meal, List<LoggedFood>> = emptyMap(),
    val total: Macros = Macros.EMPTY,
    val targets: PlanTargets = PlanTargets(),
    val addingTo: Meal? = null,
    val editingFoodId: String? = null,
) {
    val anythingLogged: Boolean get() = meals.values.any { it.isNotEmpty() }

    val kcalFraction: Float
        get() = if (targets.kcal <= 0) 0f else (total.kcal / targets.kcal).toFloat().coerceIn(0f, 1f)

    val proteinFraction: Float
        get() = if (targets.proteinGrams <= 0) 0f else {
            (total.protein / targets.proteinGrams).toFloat().coerceIn(0f, 1f)
        }

    fun mealMacros(meal: Meal): Macros =
        meals[meal].orEmpty().fold(Macros.EMPTY) { running, logged -> running + logged.macros }

    /** Foods worth offering for this meal, the ones tagged for it first. */
    fun choicesFor(meal: Meal): List<FoodDoc> {
        val tag = meal.name.lowercase()
        val (tagged, rest) = foods.partition { it.meals.any { m -> m.equals(tag, ignoreCase = true) } }
        return tagged + rest
    }

    val editingFood: FoodDoc? get() = foods.firstOrNull { it.id == editingFoodId }
}

data class LoggedFood(val entry: FoodLogEntry, val food: FoodDoc) {
    val macros: Macros get() = food.per * entry.portions

    val portionLabel: String
        get() = when (entry.portions) {
            1.0 -> food.portion.label
            else -> "${com.ug911.myfitness.data.model.formatNumber(entry.portions)} x ${food.portion.label}"
        }
}

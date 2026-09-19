package com.ug911.myfitness.data.repository

import com.ug911.myfitness.data.local.dao.FoodLogDao
import com.ug911.myfitness.data.model.FoodDoc
import com.ug911.myfitness.data.model.FoodLogEntry
import com.ug911.myfitness.data.model.Macros
import com.ug911.myfitness.data.model.Meal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** What was eaten, in portions of the foods you actually eat. */
class NutritionRepository(private val dao: FoodLogDao) {

    fun observeDay(date: LocalDate): Flow<List<FoodLogEntry>> =
        dao.observeDay(date).map { rows -> rows.map { it.toModel() } }

    suspend fun between(start: LocalDate, end: LocalDate): List<FoodLogEntry> =
        dao.between(start, end).map { it.toModel() }

    /** Adds (or removes, with a negative number) portions of one food in one meal. */
    suspend fun addPortions(date: LocalDate, foodId: String, meal: Meal, portions: Double) =
        dao.addPortions(date, foodId, meal, portions)

    suspend fun remove(id: Long) = dao.delete(id)
}

/** Totals a day's food log against the foods it refers to. */
fun totalMacros(entries: List<FoodLogEntry>, foods: List<FoodDoc>): Macros {
    val byId = foods.associateBy { it.id }
    return entries.fold(Macros.EMPTY) { running, entry ->
        val food = byId[entry.foodId] ?: return@fold running
        running + (food.per * entry.portions)
    }
}

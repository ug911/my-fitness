package com.ug911.myfitness.nutrition

import com.ug911.myfitness.data.model.FoodDoc
import com.ug911.myfitness.data.model.FoodLogEntry
import com.ug911.myfitness.data.model.FoodPortion
import com.ug911.myfitness.data.model.Macros
import com.ug911.myfitness.data.model.Meal
import com.ug911.myfitness.data.repository.applying
import com.ug911.myfitness.data.local.entity.FoodOverrideEntity
import com.ug911.myfitness.data.repository.totalMacros
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Counting portions of the foods actually eaten, and honouring corrections to them. */
class NutritionMathTest {

    private val date = LocalDate.of(2026, 9, 18)

    private val poha = FoodDoc(
        id = "poha", name = "Poha", meals = listOf("breakfast"),
        portion = FoodPortion("1 plate", 200),
        per = Macros(kcal = 270.0, protein = 6.0, carbs = 48.0, fat = 6.0, fibre = 3.0),
    )
    private val eggs = FoodDoc(
        id = "eggs", name = "Eggs", meals = listOf("breakfast"),
        portion = FoodPortion("2 eggs", 100),
        per = Macros(kcal = 155.0, protein = 13.0, carbs = 1.0, fat = 11.0),
    )

    private fun log(foodId: String, portions: Double, meal: Meal = Meal.BREAKFAST) =
        FoodLogEntry(date = date, foodId = foodId, meal = meal, portions = portions)

    @Test
    fun `a day totals the portions of each food`() {
        val total = totalMacros(listOf(log("poha", 1.0), log("eggs", 1.0)), listOf(poha, eggs))

        assertEquals(425.0, total.kcal, 0.001)
        assertEquals(19.0, total.protein, 0.001)
        assertEquals(49.0, total.carbs, 0.001)
    }

    @Test
    fun `half and double portions scale`() {
        val total = totalMacros(listOf(log("poha", 0.5), log("eggs", 2.0)), listOf(poha, eggs))

        assertEquals(135.0 + 310.0, total.kcal, 0.001)
        assertEquals(3.0 + 26.0, total.protein, 0.001)
    }

    @Test
    fun `a food the bundle no longer carries is skipped, not counted as zero-crash`() {
        val total = totalMacros(listOf(log("poha", 1.0), log("vanished", 3.0)), listOf(poha))

        assertEquals(270.0, total.kcal, 0.001)
    }

    @Test
    fun `an empty day is an empty total`() {
        assertEquals(Macros.EMPTY, totalMacros(emptyList(), listOf(poha)))
    }

    @Test
    fun `your correction outranks the published numbers`() {
        val override = FoodOverrideEntity(
            foodId = "poha", portionLabel = "1 big plate",
            kcal = 340.0, protein = 8.0, carbs = 60.0, fat = 7.0,
            updatedAtMillis = 0,
        )

        val corrected = poha.applying(override)

        assertEquals(340.0, corrected.per.kcal, 0.001)
        assertEquals("1 big plate", corrected.portion.label)
        assertEquals("yours", corrected.confidence)
        // Fibre was not part of the correction, so the published value stays.
        assertEquals(3.0, corrected.per.fibre, 0.001)
    }

    @Test
    fun `no correction leaves a food exactly as published`() {
        assertEquals(poha, poha.applying(null))
    }

    @Test
    fun `meals map onto the day's sections`() {
        assertEquals(Meal.BREAKFAST, Meal.fromSection(com.ug911.myfitness.data.model.DaySection.BREAKFAST))
        assertEquals(Meal.LUNCH, Meal.fromSection(com.ug911.myfitness.data.model.DaySection.OFFICE))
        assertEquals(Meal.DINNER, Meal.fromSection(com.ug911.myfitness.data.model.DaySection.EVENING))
        assertEquals(Meal.SNACK, Meal.fromSection(com.ug911.myfitness.data.model.DaySection.NIGHT))
    }
}

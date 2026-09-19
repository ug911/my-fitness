package com.ug911.myfitness.data.model

import java.time.LocalDate

/**
 * One set, as it happened. Sets are the historical record for training, in the same way
 * entries are for the rest of the day: written by you, never by the AI layer.
 */
data class WorkoutSet(
    val id: Long = 0,
    val date: LocalDate,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int,
    val note: String? = null,
    val doneAtMillis: Long = System.currentTimeMillis(),
) {
    /** Weight moved by this set; bodyweight work counts its reps rather than nothing. */
    val volume: Double get() = (weightKg ?: 0.0) * reps

    fun label(): String = when {
        weightKg == null || weightKg == 0.0 -> "$reps reps"
        else -> "${formatNumber(weightKg)} kg x $reps"
    }
}

/** A day's training for one exercise, with the numbers that make progression visible. */
data class ExerciseLog(
    val exerciseId: String,
    val sets: List<WorkoutSet>,
) {
    val totalReps: Int get() = sets.sumOf { it.reps }
    val totalVolume: Double get() = sets.sumOf { it.volume }
    val topSet: WorkoutSet? get() = sets.maxByOrNull { it.weightKg ?: 0.0 }
    val isEmpty: Boolean get() = sets.isEmpty()
}

/** What one food contributed to a meal on one day. */
data class FoodLogEntry(
    val id: Long = 0,
    val date: LocalDate,
    val foodId: String,
    val meal: Meal,
    val portions: Double,
)

enum class Meal(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack"),
    ;

    companion object {
        fun fromSection(section: DaySection): Meal = when (section) {
            DaySection.BREAKFAST -> BREAKFAST
            DaySection.OFFICE -> LUNCH
            DaySection.EVENING -> DINNER
            else -> SNACK
        }
    }
}

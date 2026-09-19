package com.ug911.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ug911.myfitness.data.model.FoodLogEntry
import com.ug911.myfitness.data.model.Meal
import com.ug911.myfitness.data.model.WorkoutSet
import java.time.LocalDate

/**
 * Knowledge documents are stored as the JSON they arrived as, with only the fields the
 * database needs to index pulled out. A new field in a published document then needs no
 * migration here - the app either understands it or ignores it.
 */
@Entity(tableName = "knowledge_docs", indices = [Index(value = ["kind"])])
data class KnowledgeDocEntity(
    @PrimaryKey val key: String,
    val kind: String,
    val docId: String,
    val name: String,
    val json: String,
    val updatedAtMillis: Long,
) {
    companion object {
        const val KIND_EXERCISE = "exercise"
        const val KIND_FOOD = "food"
        const val KIND_PLAN = "plan"

        fun keyFor(kind: String, id: String) = "$kind:$id"
    }
}

/**
 * A food's numbers as corrected by the person eating it. Kept in its own table so a
 * knowledge sync can replace the published document without touching the correction.
 */
@Entity(tableName = "food_overrides")
data class FoodOverrideEntity(
    @PrimaryKey val foodId: String,
    val portionLabel: String?,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "workout_sets",
    indices = [Index(value = ["date"]), Index(value = ["exerciseId", "date"])],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val exerciseId: String,
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int,
    val note: String?,
    val doneAtMillis: Long,
) {
    fun toModel() = WorkoutSet(id, date, exerciseId, setIndex, weightKg, reps, note, doneAtMillis)
}

fun WorkoutSet.toEntity() = WorkoutSetEntity(id, date, exerciseId, setIndex, weightKg, reps, note, doneAtMillis)

@Entity(
    tableName = "food_log",
    indices = [Index(value = ["date"]), Index(value = ["date", "meal"])],
)
data class FoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val foodId: String,
    val meal: Meal,
    val portions: Double,
) {
    fun toModel() = FoodLogEntry(id, date, foodId, meal, portions)
}

fun FoodLogEntry.toEntity() = FoodLogEntity(id, date, foodId, meal, portions)

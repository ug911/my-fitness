package com.ug911.myfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ug911.myfitness.data.local.entity.FoodLogEntity
import com.ug911.myfitness.data.local.entity.FoodOverrideEntity
import com.ug911.myfitness.data.local.entity.KnowledgeDocEntity
import com.ug911.myfitness.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_docs WHERE kind = :kind ORDER BY name")
    fun observeKind(kind: String): Flow<List<KnowledgeDocEntity>>

    @Query("SELECT * FROM knowledge_docs WHERE kind = :kind ORDER BY name")
    suspend fun ofKind(kind: String): List<KnowledgeDocEntity>

    @Query("SELECT * FROM knowledge_docs WHERE key = :key")
    fun observeByKey(key: String): Flow<KnowledgeDocEntity?>

    @Query("SELECT COUNT(*) FROM knowledge_docs")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(docs: List<KnowledgeDocEntity>)

    @Query("SELECT * FROM food_overrides")
    fun observeOverrides(): Flow<List<FoodOverrideEntity>>

    @Upsert
    suspend fun upsertOverride(override: FoodOverrideEntity)

    @Query("DELETE FROM food_overrides WHERE foodId = :foodId")
    suspend fun clearOverride(foodId: String)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sets WHERE date = :date ORDER BY exerciseId, setIndex")
    fun observeDay(date: LocalDate): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date BETWEEN :start AND :end ORDER BY date, setIndex")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_sets WHERE date BETWEEN :start AND :end ORDER BY date, setIndex")
    suspend fun between(start: LocalDate, end: LocalDate): List<WorkoutSetEntity>

    /**
     * The last day this exercise was trained before [before] - what you have to beat.
     */
    @Query(
        "SELECT * FROM workout_sets WHERE exerciseId = :exerciseId AND date < :before " +
            "AND date = (SELECT MAX(date) FROM workout_sets WHERE exerciseId = :exerciseId AND date < :before) " +
            "ORDER BY setIndex",
    )
    suspend fun lastSessionFor(exerciseId: String, before: LocalDate): List<WorkoutSetEntity>

    @Query("SELECT MAX(setIndex) FROM workout_sets WHERE exerciseId = :exerciseId AND date = :date")
    suspend fun lastSetIndex(exerciseId: String, date: LocalDate): Int?

    @Upsert
    suspend fun upsert(set: WorkoutSetEntity): Long

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun addSet(set: WorkoutSetEntity): Long {
        val next = (lastSetIndex(set.exerciseId, set.date) ?: 0) + 1
        return upsert(set.copy(setIndex = next))
    }
}

@Dao
interface FoodLogDao {
    @Query("SELECT * FROM food_log WHERE date = :date ORDER BY meal, id")
    fun observeDay(date: LocalDate): Flow<List<FoodLogEntity>>

    @Query("SELECT * FROM food_log WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun between(start: LocalDate, end: LocalDate): List<FoodLogEntity>

    @Query("SELECT * FROM food_log WHERE date = :date AND foodId = :foodId AND meal = :meal")
    suspend fun find(date: LocalDate, foodId: String, meal: com.ug911.myfitness.data.model.Meal): FoodLogEntity?

    @Upsert
    suspend fun upsert(entry: FoodLogEntity)

    @Query("DELETE FROM food_log WHERE id = :id")
    suspend fun delete(id: Long)

    /** Adds to an existing portion rather than creating a second row for the same food. */
    @Transaction
    suspend fun addPortions(
        date: LocalDate,
        foodId: String,
        meal: com.ug911.myfitness.data.model.Meal,
        portions: Double,
    ) {
        val existing = find(date, foodId, meal)
        val total = (existing?.portions ?: 0.0) + portions
        when {
            total <= 0.0 && existing != null -> delete(existing.id)
            existing == null && total > 0.0 ->
                upsert(FoodLogEntity(date = date, foodId = foodId, meal = meal, portions = total))
            existing != null -> upsert(existing.copy(portions = total))
        }
    }
}

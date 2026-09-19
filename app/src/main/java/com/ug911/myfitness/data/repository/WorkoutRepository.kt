package com.ug911.myfitness.data.repository

import com.ug911.myfitness.data.local.dao.WorkoutDao
import com.ug911.myfitness.data.local.entity.WorkoutSetEntity
import com.ug911.myfitness.data.model.ExerciseLog
import com.ug911.myfitness.data.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** The sets you actually lifted. Written by you; the AI layer never touches it. */
class WorkoutRepository(private val dao: WorkoutDao) {

    fun observeDay(date: LocalDate): Flow<Map<String, ExerciseLog>> =
        dao.observeDay(date).map { rows ->
            rows.map { it.toModel() }
                .groupBy { it.exerciseId }
                .mapValues { (id, sets) -> ExerciseLog(id, sets.sortedBy { it.setIndex }) }
        }

    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<WorkoutSet>> =
        dao.observeBetween(start, end).map { rows -> rows.map { it.toModel() } }

    suspend fun between(start: LocalDate, end: LocalDate): List<WorkoutSet> =
        dao.between(start, end).map { it.toModel() }

    /** The same exercise last time it was trained - the number to beat today. */
    suspend fun lastSession(exerciseId: String, before: LocalDate): ExerciseLog? {
        val sets = dao.lastSessionFor(exerciseId, before).map { it.toModel() }
        return sets.takeIf { it.isNotEmpty() }?.let { ExerciseLog(exerciseId, it) }
    }

    suspend fun addSet(date: LocalDate, exerciseId: String, weightKg: Double?, reps: Int, note: String? = null) {
        dao.addSet(
            WorkoutSetEntity(
                date = date,
                exerciseId = exerciseId,
                setIndex = 0,
                weightKg = weightKg,
                reps = reps,
                note = note,
                doneAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteSet(id: Long) = dao.delete(id)
}

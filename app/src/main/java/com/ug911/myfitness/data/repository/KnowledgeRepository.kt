package com.ug911.myfitness.data.repository

import com.ug911.myfitness.data.local.dao.KnowledgeDao
import com.ug911.myfitness.data.local.entity.FoodOverrideEntity
import com.ug911.myfitness.data.local.entity.KnowledgeDocEntity
import com.ug911.myfitness.data.model.ExerciseDoc
import com.ug911.myfitness.data.model.FoodDoc
import com.ug911.myfitness.data.model.FoodPortion
import com.ug911.myfitness.data.model.KnowledgeBundle
import com.ug911.myfitness.data.model.Macros
import com.ug911.myfitness.data.model.TrainingPlanDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * The published knowledge, plus the corrections made to it on this phone.
 *
 * A sync replaces documents wholesale; overrides live in their own table and are
 * re-applied on read, so correcting the calories in a katori of dal survives the next
 * time the bundle is rebuilt.
 */
class KnowledgeRepository(private val dao: KnowledgeDao) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun observeExercises(): Flow<List<ExerciseDoc>> =
        dao.observeKind(KnowledgeDocEntity.KIND_EXERCISE).map { rows ->
            rows.mapNotNull { row -> decode<ExerciseDoc>(row.json) }
        }

    fun observeFoods(): Flow<List<FoodDoc>> =
        combine(
            dao.observeKind(KnowledgeDocEntity.KIND_FOOD),
            dao.observeOverrides(),
        ) { rows, overrides ->
            val byId = overrides.associateBy { it.foodId }
            rows.mapNotNull { row -> decode<FoodDoc>(row.json)?.applying(byId[row.docId]) }
        }

    fun observePlan(): Flow<TrainingPlanDoc?> =
        dao.observeKind(KnowledgeDocEntity.KIND_PLAN).map { rows ->
            rows.firstNotNullOfOrNull { row -> decode<TrainingPlanDoc>(row.json) }
        }

    suspend fun exercises(): List<ExerciseDoc> =
        dao.ofKind(KnowledgeDocEntity.KIND_EXERCISE).mapNotNull { decode<ExerciseDoc>(it.json) }

    suspend fun foods(): List<FoodDoc> =
        dao.ofKind(KnowledgeDocEntity.KIND_FOOD).mapNotNull { decode<FoodDoc>(it.json) }

    suspend fun isEmpty(): Boolean = dao.count() == 0

    /** Replaces the published documents with a freshly built bundle. */
    suspend fun replaceWith(bundle: KnowledgeBundle) {
        val now = System.currentTimeMillis()
        val rows = buildList {
            bundle.exercises.forEach { doc ->
                add(row(KnowledgeDocEntity.KIND_EXERCISE, doc.id, doc.name, json.encodeToString(ExerciseDoc.serializer(), doc), now))
            }
            bundle.foods.forEach { doc ->
                add(row(KnowledgeDocEntity.KIND_FOOD, doc.id, doc.name, json.encodeToString(FoodDoc.serializer(), doc), now))
            }
            bundle.plans.forEach { doc ->
                add(row(KnowledgeDocEntity.KIND_PLAN, doc.id, doc.name, json.encodeToString(TrainingPlanDoc.serializer(), doc), now))
            }
        }
        dao.upsertAll(rows)
    }

    /** Your correction to a food's numbers, which outranks whatever the bundle says. */
    suspend fun overrideFood(food: FoodDoc, macros: Macros, portionLabel: String) {
        dao.upsertOverride(
            FoodOverrideEntity(
                foodId = food.id,
                portionLabel = portionLabel,
                kcal = macros.kcal,
                protein = macros.protein,
                carbs = macros.carbs,
                fat = macros.fat,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun clearOverride(foodId: String) = dao.clearOverride(foodId)

    private fun row(kind: String, id: String, name: String, body: String, now: Long) =
        KnowledgeDocEntity(
            key = KnowledgeDocEntity.keyFor(kind, id),
            kind = kind,
            docId = id,
            name = name,
            json = body,
            updatedAtMillis = now,
        )

    private inline fun <reified T> decode(body: String): T? =
        runCatching { json.decodeFromString<T>(body) }.getOrNull()
}

/** Applies a local correction to a published food. */
fun FoodDoc.applying(override: FoodOverrideEntity?): FoodDoc {
    if (override == null) return this
    return copy(
        portion = FoodPortion(override.portionLabel ?: portion.label, portion.grams),
        per = Macros(
            kcal = override.kcal ?: per.kcal,
            protein = override.protein ?: per.protein,
            carbs = override.carbs ?: per.carbs,
            fat = override.fat ?: per.fat,
            fibre = per.fibre,
        ),
        confidence = "yours",
    )
}

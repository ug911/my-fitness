package com.ug911.myfitness.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The knowledge base: what the app knows that is not your own logged data.
 *
 * These mirror the JSON in `knowledge/` exactly, so a document published by Claude or
 * ChatGPT through the MCP server lands here without a translation step. Unknown fields
 * are ignored on purpose - the knowledge base can grow new ones without breaking an
 * installed app.
 */
@Serializable
data class KnowledgeBundle(
    val version: Int = 1,
    val builtAt: String? = null,
    val exercises: List<ExerciseDoc> = emptyList(),
    val foods: List<FoodDoc> = emptyList(),
    val plans: List<TrainingPlanDoc> = emptyList(),
)

@Serializable
data class ExerciseDoc(
    val id: String,
    val name: String,
    val anchorExercise: String? = null,
    val alternatives: List<String> = emptyList(),
    val pattern: String? = null,
    val equipment: String? = null,
    val difficulty: String? = null,
    val primaryMuscles: List<String> = emptyList(),
    val secondaryMuscles: List<String> = emptyList(),
    val setup: List<String> = emptyList(),
    val execution: List<String> = emptyList(),
    val cues: List<String> = emptyList(),
    val mistakes: List<CoachingMistake> = emptyList(),
    val breathing: String? = null,
    val evidence: List<EvidenceNote> = emptyList(),
    val progressions: List<String> = emptyList(),
    val regressions: List<String> = emptyList(),
    val repRange: RepRange? = null,
    val restSeconds: Int? = null,
    val tempo: String? = null,
    val demo: DemoAnimation? = null,
)

@Serializable
data class CoachingMistake(val mistake: String, val fix: String)

/**
 * A claim with how much weight to put on it. [source] is deliberately optional and
 * usually absent: the notes describe what the training literature broadly agrees on
 * rather than citing papers that may not say what a citation implies.
 */
@Serializable
data class EvidenceNote(
    val claim: String,
    val detail: String = "",
    val confidence: String = "moderate",
    val source: String? = null,
) {
    val strength: EvidenceStrength
        get() = when (confidence.lowercase()) {
            "strong" -> EvidenceStrength.STRONG
            "limited" -> EvidenceStrength.LIMITED
            else -> EvidenceStrength.MODERATE
        }
}

enum class EvidenceStrength(val label: String) {
    STRONG("Well established"),
    MODERATE("Reasonably supported"),
    LIMITED("Thin evidence"),
}

@Serializable
data class RepRange(val min: Int, val max: Int) {
    fun label(): String = if (min == max) "$min reps" else "$min-$max reps"
}

/**
 * A looping stick-figure demo. Joints are normalised into a unit box with y pointing
 * down; a frame lists only the joints that move, and anything omitted holds its
 * previous position.
 */
@Serializable
data class DemoAnimation(
    val view: String = "side",
    val durationMillis: Int = 2400,
    val loop: String = "pingpong",
    val frames: List<DemoFrame> = emptyList(),
) {
    val isFrontView: Boolean get() = view == "front"
    val pingPong: Boolean get() = loop != "restart"
}

@Serializable
data class DemoFrame(
    val at: Float,
    val joints: Map<String, List<Float>> = emptyMap(),
)

@Serializable
data class FoodDoc(
    val id: String,
    val name: String,
    val meals: List<String> = emptyList(),
    val portion: FoodPortion = FoodPortion(),
    val per: Macros = Macros(),
    val note: String? = null,
    val confidence: String = "estimate",
)

@Serializable
data class FoodPortion(val label: String = "1 serving", val grams: Int? = null)

@Serializable
data class Macros(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fibre: Double = 0.0,
) {
    operator fun times(portions: Double) = Macros(
        kcal = kcal * portions,
        protein = protein * portions,
        carbs = carbs * portions,
        fat = fat * portions,
        fibre = fibre * portions,
    )

    operator fun plus(other: Macros) = Macros(
        kcal = kcal + other.kcal,
        protein = protein + other.protein,
        carbs = carbs + other.carbs,
        fat = fat + other.fat,
        fibre = fibre + other.fibre,
    )

    companion object {
        val EMPTY = Macros()
    }
}

@Serializable
data class TrainingPlanDoc(
    val id: String,
    val name: String,
    val summary: String = "",
    val notes: List<String> = emptyList(),
    val days: List<TrainingDay> = emptyList(),
    val targets: PlanTargets = PlanTargets(),
) {
    /** The day's training, or null on a rest day. */
    fun dayFor(dayOfWeek: java.time.DayOfWeek): TrainingDay? =
        days.firstOrNull { it.day.equals(dayOfWeek.name, ignoreCase = true) }
}

@Serializable
data class TrainingDay(
    val day: String,
    val title: String = "",
    val warmup: String = "",
    val items: List<TrainingItem> = emptyList(),
) {
    val isRestDay: Boolean get() = items.isEmpty()
}

@Serializable
data class TrainingItem(
    val exercise: String,
    val sets: Int = 3,
    val reps: String = "8-12",
    val note: String? = null,
)

@Serializable
data class PlanTargets(
    @SerialName("proteinGramsPerDay") val proteinGrams: Int = 0,
    @SerialName("kcalPerDay") val kcal: Int = 0,
    @SerialName("waterGlassesPerDay") val waterGlasses: Int = 0,
    val note: String = "",
)

package com.ug911.myfitness.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.ug911.myfitness.data.model.AiAnalysis
import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanSource
import com.ug911.myfitness.data.model.PlanStatus
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.model.PlanWithTargets
import java.time.LocalDate

@Entity(tableName = "plans", indices = [Index(value = ["startDate"])])
data class PlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val generatedBy: PlanSource,
    val status: PlanStatus,
    val note: String?,
    val analysisId: Long?,
    val createdAtMillis: Long,
) {
    fun toModel() = Plan(id, startDate, endDate, generatedBy, status, note, analysisId, createdAtMillis)
}

fun Plan.toEntity() = PlanEntity(id, startDate, endDate, generatedBy, status, note, analysisId, createdAtMillis)

@Entity(
    tableName = "plan_targets",
    foreignKeys = [
        ForeignKey(
            entity = PlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["planId"]), Index(value = ["trackerId"])],
)
data class PlanTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val trackerId: Long,
    val targetValue: Double?,
    val targetFrequency: Int?,
    val note: String?,
) {
    fun toModel() = PlanTarget(id, planId, trackerId, targetValue, targetFrequency, note)
}

fun PlanTarget.toEntity() = PlanTargetEntity(id, planId, trackerId, targetValue, targetFrequency, note)

data class PlanWithTargetsEntity(
    @Embedded val plan: PlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId")
    val targets: List<PlanTargetEntity>,
) {
    fun toModel() = PlanWithTargets(plan.toModel(), targets.map { it.toModel() })
}

@Entity(tableName = "ai_analyses", indices = [Index(value = ["periodStart"])])
data class AiAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val inputSnapshot: String,
    val review: String,
    val insights: List<String>,
    val planRationale: String?,
    val provider: String,
    val model: String,
    val createdAtMillis: Long,
) {
    fun toModel() = AiAnalysis(
        id, periodStart, periodEnd, inputSnapshot, review, insights, planRationale, provider, model, createdAtMillis,
    )
}

fun AiAnalysis.toEntity() = AiAnalysisEntity(
    id, periodStart, periodEnd, inputSnapshot, review, insights, planRationale, provider, model, createdAtMillis,
)

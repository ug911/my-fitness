package com.ug911.myfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ug911.myfitness.data.local.entity.AiAnalysisEntity
import com.ug911.myfitness.data.local.entity.PlanEntity
import com.ug911.myfitness.data.local.entity.PlanTargetEntity
import com.ug911.myfitness.data.local.entity.PlanWithTargetsEntity
import com.ug911.myfitness.data.model.PlanStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface PlanDao {
    @Transaction
    @Query("SELECT * FROM plans WHERE status = :status ORDER BY startDate DESC LIMIT 1")
    fun observeLatestWithStatus(status: PlanStatus): Flow<PlanWithTargetsEntity?>

    @Transaction
    @Query(
        "SELECT * FROM plans WHERE status = 'ACTIVE' AND startDate <= :date AND endDate >= :date " +
            "ORDER BY startDate DESC LIMIT 1",
    )
    fun observeActiveFor(date: LocalDate): Flow<PlanWithTargetsEntity?>

    @Transaction
    @Query("SELECT * FROM plans ORDER BY startDate DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlanWithTargetsEntity>>

    @Transaction
    @Query("SELECT * FROM plans WHERE id = :id")
    suspend fun getWithTargets(id: Long): PlanWithTargetsEntity?

    @Transaction
    @Query(
        "SELECT * FROM plans WHERE status = 'ACTIVE' AND startDate <= :date AND endDate >= :date " +
            "ORDER BY startDate DESC LIMIT 1",
    )
    suspend fun getActiveFor(date: LocalDate): PlanWithTargetsEntity?

    @Insert
    suspend fun insertPlan(plan: PlanEntity): Long

    @Upsert
    suspend fun upsertPlan(plan: PlanEntity)

    @Insert
    suspend fun insertTargets(targets: List<PlanTargetEntity>)

    @Query("DELETE FROM plan_targets WHERE planId = :planId")
    suspend fun deleteTargets(planId: Long)

    @Query("UPDATE plans SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: PlanStatus)

    @Query(
        "UPDATE plans SET status = 'ARCHIVED' WHERE status = 'ACTIVE' AND id != :keepId " +
            "AND startDate <= :end AND endDate >= :start",
    )
    suspend fun archiveOverlappingActive(keepId: Long, start: LocalDate, end: LocalDate)

    @Transaction
    suspend fun insertPlanWithTargets(plan: PlanEntity, targets: List<PlanTargetEntity>): Long {
        val planId = insertPlan(plan)
        if (targets.isNotEmpty()) insertTargets(targets.map { it.copy(planId = planId) })
        return planId
    }

    /** Approving a plan is the only way a target becomes a goal. */
    @Transaction
    suspend fun approve(planId: Long) {
        val plan = getWithTargets(planId) ?: return
        setStatus(planId, PlanStatus.ACTIVE)
        archiveOverlappingActive(planId, plan.plan.startDate, plan.plan.endDate)
    }
}

@Dao
interface AiAnalysisDao {
    @Query("SELECT * FROM ai_analyses ORDER BY periodStart DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<AiAnalysisEntity>>

    @Query("SELECT * FROM ai_analyses ORDER BY createdAtMillis DESC LIMIT 1")
    fun observeLatest(): Flow<AiAnalysisEntity?>

    @Insert
    suspend fun insert(analysis: AiAnalysisEntity): Long
}

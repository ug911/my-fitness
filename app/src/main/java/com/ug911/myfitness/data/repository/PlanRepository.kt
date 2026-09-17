package com.ug911.myfitness.data.repository

import com.ug911.myfitness.data.local.dao.PlanDao
import com.ug911.myfitness.data.local.entity.toEntity
import com.ug911.myfitness.data.model.Plan
import com.ug911.myfitness.data.model.PlanStatus
import com.ug911.myfitness.data.model.PlanTarget
import com.ug911.myfitness.data.model.PlanWithTargets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class PlanRepository(private val dao: PlanDao) {

    fun observeActive(date: LocalDate): Flow<PlanWithTargets?> =
        dao.observeActiveFor(date).map { it?.toModel() }

    fun observeProposed(): Flow<PlanWithTargets?> =
        dao.observeLatestWithStatus(PlanStatus.PROPOSED).map { it?.toModel() }

    fun observeRecent(limit: Int = 10): Flow<List<PlanWithTargets>> =
        dao.observeRecent(limit).map { list -> list.map { it.toModel() } }

    suspend fun activeFor(date: LocalDate): PlanWithTargets? = dao.getActiveFor(date)?.toModel()

    suspend fun create(plan: Plan, targets: List<PlanTarget>): Long =
        dao.insertPlanWithTargets(plan.toEntity(), targets.map { it.toEntity() })

    /** Turns an approved proposal into the live goal set, archiving whatever it replaces. */
    suspend fun approve(planId: Long) = dao.approve(planId)

    suspend fun reject(planId: Long) = dao.setStatus(planId, PlanStatus.REJECTED)

    suspend fun archive(planId: Long) = dao.setStatus(planId, PlanStatus.ARCHIVED)

    suspend fun replaceTargets(planId: Long, targets: List<PlanTarget>) {
        dao.deleteTargets(planId)
        if (targets.isNotEmpty()) dao.insertTargets(targets.map { it.toEntity().copy(planId = planId) })
    }
}

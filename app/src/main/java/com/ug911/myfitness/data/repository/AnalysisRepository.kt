package com.ug911.myfitness.data.repository

import com.ug911.myfitness.data.local.dao.AiAnalysisDao
import com.ug911.myfitness.data.local.entity.toEntity
import com.ug911.myfitness.data.model.AiAnalysis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AnalysisRepository(private val dao: AiAnalysisDao) {

    fun observeAll(): Flow<List<AiAnalysis>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeLatest(): Flow<AiAnalysis?> = dao.observeLatest().map { it?.toModel() }

    suspend fun save(analysis: AiAnalysis): Long = dao.insert(analysis.toEntity())
}

package com.ug911.myfitness.data.repository

import com.ug911.myfitness.data.local.dao.TrackerDao
import com.ug911.myfitness.data.local.entity.toEntity
import com.ug911.myfitness.data.model.Tracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TrackerRepository(private val dao: TrackerDao) {

    fun observeAll(): Flow<List<Tracker>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeActive(): Flow<List<Tracker>> = dao.observeActive().map { list -> list.map { it.toModel() } }

    suspend fun all(): List<Tracker> = dao.getAll().map { it.toModel() }

    suspend fun byId(id: Long): Tracker? = dao.getById(id)?.toModel()

    suspend fun save(tracker: Tracker): Long = dao.upsert(tracker.toEntity())

    suspend fun setActive(id: Long, active: Boolean) = dao.setActive(id, active)

    suspend fun delete(tracker: Tracker) = dao.delete(tracker.toEntity())
}

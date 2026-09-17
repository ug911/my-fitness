package com.ug911.myfitness.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.ug911.myfitness.data.local.entity.EntryEntity
import com.ug911.myfitness.data.local.entity.JournalEntryEntity
import com.ug911.myfitness.data.local.entity.TrackerEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TrackerDao {
    @Query("SELECT * FROM trackers ORDER BY category, sortOrder, name")
    fun observeAll(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers WHERE active = 1 ORDER BY category, sortOrder, name")
    fun observeActive(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers ORDER BY category, sortOrder, name")
    suspend fun getAll(): List<TrackerEntity>

    @Query("SELECT * FROM trackers WHERE id = :id")
    suspend fun getById(id: Long): TrackerEntity?

    @Query("SELECT COUNT(*) FROM trackers")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(tracker: TrackerEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(trackers: List<TrackerEntity>)

    @Delete
    suspend fun delete(tracker: TrackerEntity)

    @Query("UPDATE trackers SET active = :active WHERE id = :id")
    suspend fun setActive(id: Long, active: Boolean)
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE date = :date")
    fun observeByDate(date: LocalDate): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :start AND :end ORDER BY date")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE trackerId = :trackerId AND date = :date")
    suspend fun getFor(trackerId: Long, date: LocalDate): EntryEntity?

    @Query("SELECT MIN(date) FROM entries")
    suspend fun earliestDate(): LocalDate?

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE trackerId = :trackerId AND date = :date")
    suspend fun delete(trackerId: Long, date: LocalDate)

    /**
     * Writes a value while preserving anything entered by hand: a Health Connect sync
     * must never overwrite a manual correction.
     */
    @Transaction
    suspend fun upsertKeepingManual(entry: EntryEntity) {
        val existing = getFor(entry.trackerId, entry.date)
        if (existing != null && existing.source == com.ug911.myfitness.data.model.EntrySource.MANUAL) return
        upsert(if (existing == null) entry else entry.copy(id = existing.id))
    }

    @Transaction
    suspend fun upsertManual(entry: EntryEntity) {
        val existing = getFor(entry.trackerId, entry.date)
        upsert(if (existing == null) entry else entry.copy(id = existing.id))
    }
}

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries WHERE date = :date")
    fun observeByDate(date: LocalDate): Flow<JournalEntryEntity?>

    @Query("SELECT * FROM journal_entries WHERE date BETWEEN :start AND :end ORDER BY date")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<JournalEntryEntity>

    @Upsert
    suspend fun upsert(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE date = :date")
    suspend fun delete(date: LocalDate)
}

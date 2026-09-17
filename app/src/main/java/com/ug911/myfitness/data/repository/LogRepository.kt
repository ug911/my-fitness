package com.ug911.myfitness.data.repository

import com.ug911.myfitness.data.local.dao.EntryDao
import com.ug911.myfitness.data.local.dao.JournalDao
import com.ug911.myfitness.data.local.dao.TrackerDao
import com.ug911.myfitness.data.local.entity.EntryEntity
import com.ug911.myfitness.data.local.entity.toEntity
import com.ug911.myfitness.data.model.DayLog
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.EntrySource
import com.ug911.myfitness.data.model.JournalEntry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Reads and writes the historical record. This is the only class in the app that
 * writes [Entry] rows, and it is never handed to the AI layer.
 */
class LogRepository(
    private val entryDao: EntryDao,
    private val journalDao: JournalDao,
    private val trackerDao: TrackerDao,
) {

    fun observeDay(date: LocalDate): Flow<DayLog> =
        combine(
            trackerDao.observeAll(),
            entryDao.observeByDate(date),
            journalDao.observeByDate(date),
        ) { trackers, entries, journal ->
            val types = trackers.associate { it.id to it.type }
            DayLog(
                date = date,
                entries = entries.mapNotNull { row -> types[row.trackerId]?.let(row::toModel) }
                    .associateBy { it.trackerId },
                journal = journal?.toModel(),
            )
        }

    fun observeEntriesBetween(start: LocalDate, end: LocalDate): Flow<List<Entry>> =
        combine(trackerDao.observeAll(), entryDao.observeBetween(start, end)) { trackers, entries ->
            val types = trackers.associate { it.id to it.type }
            entries.mapNotNull { row -> types[row.trackerId]?.let(row::toModel) }
        }

    fun observeJournalBetween(start: LocalDate, end: LocalDate): Flow<List<JournalEntry>> =
        journalDao.observeBetween(start, end).map { list -> list.map { it.toModel() } }

    suspend fun entriesBetween(start: LocalDate, end: LocalDate, types: Map<Long, TrackerType>): List<Entry> =
        entryDao.getBetween(start, end).mapNotNull { row -> types[row.trackerId]?.let(row::toModel) }

    suspend fun journalBetween(start: LocalDate, end: LocalDate): List<JournalEntry> =
        journalDao.getBetween(start, end).map { it.toModel() }

    suspend fun earliestLoggedDate(): LocalDate? = entryDao.earliestDate()

    suspend fun setValue(tracker: Tracker, date: LocalDate, value: TrackerValue?, notes: String? = null) {
        if (value == null) {
            entryDao.delete(tracker.id, date)
            return
        }
        entryDao.upsertManual(
            Entry(
                trackerId = tracker.id,
                date = date,
                value = value,
                notes = notes,
                source = EntrySource.MANUAL,
            ).toEntity(),
        )
    }

    suspend fun setNotes(tracker: Tracker, date: LocalDate, notes: String?) {
        val existing = entryDao.getFor(tracker.id, date) ?: return
        entryDao.upsert(existing.copy(notes = notes, updatedAtMillis = System.currentTimeMillis()))
    }

    /** Used by the Health Connect sync; will not clobber a manual entry. */
    suspend fun setAutomaticValue(trackerId: Long, date: LocalDate, value: TrackerValue) {
        entryDao.upsertKeepingManual(
            EntryEntity(
                trackerId = trackerId,
                date = date,
                value = value.encode(),
                notes = null,
                source = EntrySource.HEALTH_CONNECT,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveJournal(date: LocalDate, text: String) {
        if (text.isBlank()) {
            journalDao.delete(date)
        } else {
            journalDao.upsert(JournalEntry(date, text).toEntity())
        }
    }
}

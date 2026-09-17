package com.ug911.myfitness.data.model

import java.time.LocalDate

/**
 * What actually happened: one tracker, one day, one value. Entries are the historical
 * record and are only ever written by the person using the app or by a Health Connect
 * sync — never by the AI layer.
 */
data class Entry(
    val id: Long = 0,
    val trackerId: Long,
    val date: LocalDate,
    val value: TrackerValue,
    val notes: String? = null,
    val source: EntrySource = EntrySource.MANUAL,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

enum class EntrySource {
    MANUAL,
    HEALTH_CONNECT,
}

data class JournalEntry(
    val date: LocalDate,
    val text: String,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

/** Everything logged on a single day, as the Today and History screens need it. */
data class DayLog(
    val date: LocalDate,
    val entries: Map<Long, Entry>,
    val journal: JournalEntry?,
) {
    fun valueFor(tracker: Tracker): TrackerValue? = entries[tracker.id]?.value
    fun notesFor(tracker: Tracker): String? = entries[tracker.id]?.notes
}

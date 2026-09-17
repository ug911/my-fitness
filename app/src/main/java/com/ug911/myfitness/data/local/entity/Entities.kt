package com.ug911.myfitness.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Direction
import com.ug911.myfitness.data.model.Entry
import com.ug911.myfitness.data.model.EntrySource
import com.ug911.myfitness.data.model.HealthMetric
import com.ug911.myfitness.data.model.JournalEntry
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerCategory
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import java.time.LocalDate

@Entity(tableName = "trackers")
data class TrackerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: TrackerCategory,
    val type: TrackerType,
    val unit: String?,
    val active: Boolean,
    val sortOrder: Int,
    val options: List<String>,
    val ratingMax: Int,
    val healthMetric: HealthMetric?,
    val direction: Direction,
    val aggregation: Aggregation,
) {
    fun toModel() = Tracker(
        id = id,
        name = name,
        category = category,
        type = type,
        unit = unit,
        active = active,
        sortOrder = sortOrder,
        options = options,
        ratingMax = ratingMax,
        healthMetric = healthMetric,
        direction = direction,
        aggregation = aggregation,
    )
}

fun Tracker.toEntity() = TrackerEntity(
    id = id,
    name = name,
    category = category,
    type = type,
    unit = unit,
    active = active,
    sortOrder = sortOrder,
    options = options,
    ratingMax = ratingMax,
    healthMetric = healthMetric,
    direction = direction,
    aggregation = aggregation,
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = TrackerEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["trackerId", "date"], unique = true),
        Index(value = ["date"]),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackerId: Long,
    val date: LocalDate,
    val value: String,
    val notes: String?,
    val source: EntrySource,
    val updatedAtMillis: Long,
) {
    fun toModel(type: TrackerType): Entry? {
        val decoded = TrackerValue.decode(type, value) ?: return null
        return Entry(
            id = id,
            trackerId = trackerId,
            date = date,
            value = decoded,
            notes = notes,
            source = source,
            updatedAtMillis = updatedAtMillis,
        )
    }
}

fun Entry.toEntity() = EntryEntity(
    id = id,
    trackerId = trackerId,
    date = date,
    value = value.encode(),
    notes = notes,
    source = source,
    updatedAtMillis = updatedAtMillis,
)

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val date: LocalDate,
    val text: String,
    val updatedAtMillis: Long,
) {
    fun toModel() = JournalEntry(date = date, text = text, updatedAtMillis = updatedAtMillis)
}

fun JournalEntry.toEntity() = JournalEntryEntity(date, text, updatedAtMillis)

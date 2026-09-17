package com.ug911.myfitness.ai

import com.ug911.myfitness.analysis.PeriodStats
import com.ug911.myfitness.analysis.PlanProgress
import com.ug911.myfitness.analysis.TrackerStat
import com.ug911.myfitness.data.model.Aggregation
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.DaySection
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.formatMinuteOfDay
import com.ug911.myfitness.data.model.formatNumber
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Turns local records into the compact JSON snapshot that is sent to a model.
 *
 * This is the app's privacy and correctness boundary: the model never touches the
 * database, it only ever sees what this class emits. Everything here is a pure
 * function of [PeriodData], so a snapshot can be inspected before it is sent and
 * replayed afterwards.
 */
class AiContextBuilder(
    private val json: Json = Json { prettyPrint = true },
) {

    fun buildJson(current: PeriodData, previous: PeriodData? = null): String =
        json.encodeToString(JsonObject.serializer(), build(current, previous))

    fun build(current: PeriodData, previous: PeriodData? = null): JsonObject {
        val stats = PeriodStats.computeAll(current.trackers, current.entries, current.start, current.end)
        val bySection = stats.groupBy { it.tracker.section }

        return buildJsonObject {
            put("period", "${current.start}/${current.end}")
            put("days", PeriodStats.dayCount(current.start, current.end))

            DaySection.entries.forEach { section ->
                val sectionStats = bySection[section].orEmpty().filter { it.isReportable() }
                if (sectionStats.isNotEmpty()) {
                    put(snapshotKey(section), sectionObject(sectionStats))
                }
            }

            val notes = textNotes(current)
            if (notes.isNotEmpty()) put("notes", notes)
            put("journal", journalArray(current))

            current.plan?.let { plan ->
                put("plan", planObject(current, plan))
            }

            previous?.let { prior ->
                val priorStats = PeriodStats.computeAll(prior.trackers, prior.entries, prior.start, prior.end)
                put("previous_period", previousObject(prior, priorStats))
            }
        }
    }

    /** Sections appear in a snapshot under their own names, in day order. */
    private fun snapshotKey(section: DaySection): String = section.key

    private fun sectionObject(stats: List<TrackerStat>): JsonObject = buildJsonObject {
        stats.forEach { stat ->
            val tracker = stat.tracker
            when {
                stat.daysLogged == 0 -> put(tracker.key, JsonPrimitive("no data"))

                tracker.aggregation == Aggregation.DAYS_COMPLETED ->
                    put(tracker.key, JsonPrimitive("${stat.daysCompleted}/${stat.days} days"))

                tracker.aggregation == Aggregation.SUM ->
                    put(tracker.key, JsonPrimitive(stat.sum ?: 0.0))

                // A time reads as a time; 05:12 is legible where "312.0" is not.
                tracker.type == TrackerType.TIME ->
                    put(tracker.key, JsonPrimitive(stat.average?.let(::formatMinuteOfDay) ?: "no data"))

                tracker.aggregation == Aggregation.AVERAGE ->
                    put(tracker.key, JsonPrimitive(PeriodStats.round1(stat.average ?: 0.0)))

                tracker.aggregation == Aggregation.LATEST ->
                    put(tracker.key, JsonPrimitive(stat.latest ?: 0.0))

                else -> put(tracker.key, JsonPrimitive(stat.summary()))
            }
            if (tracker.type == TrackerType.MULTI_SELECT && stat.daysLogged > 0) {
                put("${tracker.key}_items_per_day", PeriodStats.round1(stat.average ?: 0.0))
            }
            if (stat.optionCounts.isNotEmpty()) {
                put(
                    "${tracker.key}_breakdown",
                    buildJsonObject {
                        stat.optionCounts.forEach { (option, count) ->
                            put(Tracker.slugify(option), count)
                        }
                    },
                )
            }
            if (stat.daysLogged in 1 until stat.days && tracker.aggregation != Aggregation.DAYS_COMPLETED) {
                put("${tracker.key}_days_logged", stat.daysLogged)
            }
        }
    }

    private fun journalArray(data: PeriodData): JsonArray = buildJsonArray {
        data.journal.sortedBy { it.date }.forEach { entry ->
            if (entry.text.isNotBlank()) add(JsonPrimitive("${dayLabel(entry.date)}: ${entry.text.trim()}"))
        }
    }

    /** Free-text tracker values (meal notes and the like) and any per-entry notes. */
    private fun textNotes(data: PeriodData): JsonArray {
        val byId = data.trackers.associateBy { it.id }
        val lines = data.entries
            .sortedBy { it.date }
            .mapNotNull { entry ->
                val tracker = byId[entry.trackerId] ?: return@mapNotNull null
                val parts = buildList {
                    if (tracker.type == TrackerType.TEXT) add(entry.value.encode().trim())
                    entry.notes?.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
                }.filter { it.isNotEmpty() }
                if (parts.isEmpty()) null else "${dayLabel(entry.date)} ${tracker.name}: ${parts.joinToString(" / ")}"
            }
        return buildJsonArray { lines.forEach { add(JsonPrimitive(it)) } }
    }

    private fun planObject(data: PeriodData, plan: com.ug911.myfitness.data.model.PlanWithTargets): JsonObject {
        val progress = PlanProgress.compute(plan, data.trackers, data.entries)
        return buildJsonObject {
            put("period", "${plan.plan.startDate}/${plan.plan.endDate}")
            put("source", plan.plan.generatedBy.name.lowercase())
            put(
                "targets",
                buildJsonArray {
                    progress.forEach { row ->
                        add(
                            buildJsonObject {
                                put("tracker", row.tracker.name)
                                row.target.targetFrequency?.let { put("target_frequency", it) }
                                row.target.targetValue?.let { put("target_value", it) }
                                put("actual_days", row.achievedDays)
                                row.achievedValue?.let { put("actual_value", PeriodStats.round1(it)) }
                                put("met", row.met)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun previousObject(prior: PeriodData, stats: List<TrackerStat>): JsonObject = buildJsonObject {
        put("period", "${prior.start}/${prior.end}")
        stats.groupBy { it.tracker.section }.forEach { (section, sectionStats) ->
            val reportable = sectionStats.filter { it.isReportable() && it.daysLogged > 0 }
            if (reportable.isNotEmpty()) {
                put(
                    snapshotKey(section),
                    buildJsonObject {
                        reportable.forEach { stat ->
                            put(stat.tracker.key, JsonPrimitive(stat.summary()))
                        }
                    },
                )
            }
        }
    }

    private fun TrackerStat.isReportable(): Boolean =
        tracker.type != TrackerType.TEXT && tracker.aggregation != Aggregation.NONE

    private fun dayLabel(date: java.time.LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + date.format(DAY_FORMAT)

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
    }
}

/** Convenience used by tests and the debug snapshot viewer. */
fun formatHeadline(stat: TrackerStat): String = stat.headline?.let(::formatNumber) ?: "no data"

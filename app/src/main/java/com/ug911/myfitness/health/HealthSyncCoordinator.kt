package com.ug911.myfitness.health

import com.ug911.myfitness.data.model.HealthMetric
import com.ug911.myfitness.data.model.Tracker
import com.ug911.myfitness.data.model.TrackerType
import com.ug911.myfitness.data.model.TrackerValue
import com.ug911.myfitness.data.repository.LogRepository
import com.ug911.myfitness.data.repository.TrackerRepository
import java.time.LocalDate

/**
 * Fills in the automatic trackers from Health Connect. A manual entry always wins:
 * if you corrected a value by hand, a later sync leaves it alone.
 */
class HealthSyncCoordinator(
    private val health: HealthConnectManager,
    private val trackers: TrackerRepository,
    private val logs: LogRepository,
) {

    suspend fun sync(today: LocalDate, daysBack: Int = DEFAULT_DAYS_BACK): SyncResult {
        if (health.availability() != HealthAvailability.AVAILABLE) return SyncResult.Unavailable
        if (!health.hasAllPermissions()) return SyncResult.PermissionsMissing

        val automatic = trackers.all().filter { it.active && it.healthMetric != null }
        if (automatic.isEmpty()) return SyncResult.Synced(0, 0)

        var daysTouched = 0
        var valuesWritten = 0
        for (offset in 0 until daysBack) {
            val date = today.minusDays(offset.toLong())
            val data = health.readDay(date) ?: continue
            var wroteForDay = false
            automatic.forEach { tracker ->
                val value = valueFor(tracker, data)
                if (value != null) {
                    logs.setAutomaticValue(tracker.id, date, value)
                    valuesWritten++
                    wroteForDay = true
                }
            }
            if (wroteForDay) daysTouched++
        }
        return SyncResult.Synced(daysTouched, valuesWritten)
    }

    /** Maps a Health Connect reading onto the tracker's own type. */
    private fun valueFor(tracker: Tracker, data: DailyHealthData): TrackerValue? {
        val raw: Double = when (tracker.healthMetric) {
            HealthMetric.SLEEP_START -> data.sleepStartMinuteOfDay?.toDouble()
            HealthMetric.SLEEP_END -> data.sleepEndMinuteOfDay?.toDouble()
            HealthMetric.STEPS -> data.steps?.toDouble()
            HealthMetric.EXERCISE_MINUTES -> data.exerciseMinutes?.toDouble()
            HealthMetric.EXERCISE_SESSIONS -> data.exerciseSessions.takeIf { it > 0 }?.toDouble()
            HealthMetric.SLEEP_DURATION -> data.sleepMinutes?.toDouble()
            HealthMetric.RESTING_HEART_RATE -> data.restingHeartRate
            HealthMetric.ACTIVE_CALORIES -> data.activeCalories
            HealthMetric.WEIGHT -> data.weightKg
            null -> null
        } ?: return null

        return when (tracker.type) {
            TrackerType.NUMBER -> TrackerValue.Number(round1(raw))
            TrackerType.DURATION -> TrackerValue.Duration(raw.toInt())
            TrackerType.BOOLEAN -> TrackerValue.Flag(raw > 0)
            TrackerType.RATING -> TrackerValue.Rating(raw.toInt().coerceIn(0, tracker.ratingMax))
            TrackerType.TIME -> TrackerValue.Time(raw.toInt().coerceIn(0, TrackerValue.MINUTES_PER_DAY - 1))
            TrackerType.TEXT -> TrackerValue.Text(round1(raw).toString())
            // Nothing in Health Connect maps onto a hand-made checklist or a pick-one.
            TrackerType.SELECT, TrackerType.MULTI_SELECT -> null
        }
    }

    private fun round1(value: Double) = Math.round(value * 10.0) / 10.0

    companion object {
        /** A week back, so a phone that was offline for a few days still catches up. */
        const val DEFAULT_DAYS_BACK = 7
    }
}

sealed interface SyncResult {
    data class Synced(val days: Int, val values: Int) : SyncResult
    data object PermissionsMissing : SyncResult
    data object Unavailable : SyncResult
}

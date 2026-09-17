package com.ug911.myfitness.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Everything that talks to Health Connect. Read-only: the app never writes health
 * records back, so a bad sync can only ever affect this app's own database.
 */
class HealthConnectManager(private val context: Context) {

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        else -> HealthAvailability.UNAVAILABLE
    }

    private fun clientOrNull(): HealthConnectClient? =
        if (availability() == HealthAvailability.AVAILABLE) {
            runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
        } else {
            null
        }

    suspend fun hasAllPermissions(): Boolean {
        val client = clientOrNull() ?: return false
        val granted = runCatching { client.permissionController.getGrantedPermissions() }.getOrNull() ?: return false
        return granted.containsAll(permissions)
    }

    suspend fun grantedPermissions(): Set<String> {
        val client = clientOrNull() ?: return emptySet()
        return runCatching { client.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
    }

    /**
     * Reads one calendar day. Sleep is attributed to the day you woke up, which is how
     * you would answer "did I sleep well last night?" when logging in the morning.
     */
    suspend fun readDay(date: LocalDate): DailyHealthData? {
        val client = clientOrNull() ?: return null
        val dayStart = date.atStartOfDay()
        val dayEnd = date.plusDays(1).atStartOfDay()
        val dayFilter = TimeRangeFilter.between(dayStart, dayEnd)

        val dayTotals: AggregationResult? = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        StepsRecord.COUNT_TOTAL,
                        ExerciseSessionRecord.EXERCISE_DURATION_TOTAL,
                        RestingHeartRateRecord.BPM_AVG,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                        WeightRecord.WEIGHT_AVG,
                    ),
                    timeRangeFilter = dayFilter,
                ),
            )
        }.getOrNull()

        val sleepFilter = TimeRangeFilter.between(
            LocalDateTime.of(date.minusDays(1), LocalTime.of(18, 0)),
            LocalDateTime.of(date, LocalTime.NOON),
        )
        val sleepTotals = runCatching {
            client.aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), sleepFilter))
        }.getOrNull()

        val sessions = runCatching {
            client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, dayFilter)).records.size
        }.getOrDefault(0)

        return DailyHealthData(
            date = date,
            steps = dayTotals?.get(StepsRecord.COUNT_TOTAL),
            exerciseMinutes = dayTotals?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)?.toMinutes(),
            exerciseSessions = sessions,
            sleepMinutes = sleepTotals?.get(SleepSessionRecord.SLEEP_DURATION_TOTAL)?.toMinutes(),
            restingHeartRate = dayTotals?.get(RestingHeartRateRecord.BPM_AVG)?.toDouble(),
            activeCalories = dayTotals?.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories,
            weightKg = dayTotals?.get(WeightRecord.WEIGHT_AVG)?.inKilograms,
        )
    }
}

enum class HealthAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE,
}

data class DailyHealthData(
    val date: LocalDate,
    val steps: Long?,
    val exerciseMinutes: Long?,
    val exerciseSessions: Int,
    val sleepMinutes: Long?,
    val restingHeartRate: Double?,
    val activeCalories: Double?,
    val weightKg: Double?,
)

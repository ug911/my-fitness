package com.ug911.myfitness.health

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ug911.myfitness.MyFitnessApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Occasional background catch-up sync. WorkManager rather than a service: the app only
 * needs the data to be roughly fresh, and a missed window is harmless because each run
 * re-reads the last few days.
 */
class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as MyFitnessApp).container
        if (!container.settings.healthSyncEnabled()) return Result.success()
        return when (container.healthSync.sync(LocalDate.now())) {
            is SyncResult.Synced -> {
                container.settings.setLastSyncMillis(System.currentTimeMillis())
                Result.success()
            }
            SyncResult.PermissionsMissing, SyncResult.Unavailable -> Result.success()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "health-connect-periodic-sync"
        private const val ONE_OFF_NAME = "health-connect-sync-now"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun syncNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_OFF_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<HealthSyncWorker>().build(),
            )
        }
    }
}

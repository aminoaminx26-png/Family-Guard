package com.example.core.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.data.local.PreferencesManager
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.domain.model.AppRole
import com.example.core.usage.UsageStatsHelper
import com.example.core.utils.NotificationHelper
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit

class UsageSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = PreferencesManager(applicationContext)
        val snapshot = prefs.getSnapshot()
        if (snapshot.role != AppRole.CHILD || snapshot.linkedDeviceId.isBlank()) {
            return Result.success()
        }
        if (Firebase.auth.currentUser == null) {
            return Result.retry()
        }

        val repository = FamilyGuardRepository(applicationContext)
        val hasPermission = UsageStatsHelper.hasUsageAccessPermission(applicationContext)
        val usageList = if (hasPermission) {
            UsageStatsHelper.collectTodayAppUsage(applicationContext)
        } else {
            emptyList()
        }

        NotificationHelper.showTransparentProtectionNotification(applicationContext)

        val syncResult = repository.syncChildUsage(
            deviceId = snapshot.linkedDeviceId,
            collectedApps = usageList,
            usageAccessGranted = hasPermission
        )

        return syncResult.fold(
            onSuccess = { exceededLimits ->
                exceededLimits.filter { it.warningEnabled }.forEach { limit ->
                    NotificationHelper.showAppLimitWarning(
                        context = applicationContext,
                        appName = limit.appName,
                        limitMinutes = limit.dailyLimitMinutes.toInt()
                    )
                }
                Result.success()
            },
            onFailure = { err ->
                if (err is FamilyGuardRepository.PairingException) {
                    prefs.clearChildPairing()
                    Result.failure()
                } else {
                    Result.retry()
                }
            }
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "family_guard_periodic_usage_sync"
        private const val ONE_TIME_WORK_NAME = "family_guard_immediate_usage_sync"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(
                15,
                TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }
    }
}

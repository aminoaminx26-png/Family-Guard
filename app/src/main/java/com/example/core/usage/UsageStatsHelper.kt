package com.example.core.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import com.example.core.domain.model.CollectedAppUsage
import java.util.Calendar

object UsageStatsHelper {

    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun buildUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun collectTodayAppUsage(context: Context): List<CollectedAppUsage> {
        if (!hasUsageAccessPermission(context)) return emptyList()
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyList()

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        ).orEmpty()

        // Count foreground move events per package when available via official API
        val launchCounts = mutableMapOf<String, Int>()
        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue
                launchCounts[pkg] = (launchCounts[pkg] ?: 0) + 1
            }
        }

        val pm = context.packageManager
        val aggregated = mutableMapOf<String, CollectedAppUsage>()

        for (stat in stats) {
            val pkg = stat.packageName ?: continue
            val totalTime = stat.totalTimeInForeground
            if (totalTime <= 0L) continue

            val appName = resolveAppLabel(pm, pkg)
            val existing = aggregated[pkg]
            val launches = launchCounts[pkg] ?: 0
            if (existing == null) {
                aggregated[pkg] = CollectedAppUsage(
                    packageName = pkg,
                    appName = appName,
                    usageTimeMs = totalTime,
                    launchCount = launches,
                    lastUsedAt = stat.lastTimeUsed
                )
            } else {
                aggregated[pkg] = existing.copy(
                    usageTimeMs = maxOf(existing.usageTimeMs, totalTime),
                    launchCount = maxOf(existing.launchCount, launches),
                    lastUsedAt = maxOf(existing.lastUsedAt, stat.lastTimeUsed)
                )
            }
        }

        return aggregated.values
            .sortedByDescending { it.usageTimeMs }
            .take(50)
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        return try {
            val info: ApplicationInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(info).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}

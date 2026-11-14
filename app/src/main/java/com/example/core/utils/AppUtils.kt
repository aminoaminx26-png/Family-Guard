package com.example.core.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    fun formatHms(durationMs: Long): String {
        val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun formatShortDuration(durationMs: Long): String {
        val totalMinutes = (durationMs.coerceAtLeast(0L) / 60_000L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun todayDateKey(nowMs: Long = System.currentTimeMillis()): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMs))
    }

    fun formatRelativeTime(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
}

object NetworkUtils {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

object NotificationHelper {
    private const val CHANNEL_TRANSPARENCY = "fg_transparency"
    private const val CHANNEL_ALERTS = "fg_alerts"
    private const val CHANNEL_LIMITS = "fg_limits"

    private const val NOTIF_ID_TRANSPARENCY = 1001
    private const val NOTIF_ID_LIMIT_BASE = 2000
    private const val NOTIF_ID_ALERT_BASE = 3000

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_TRANSPARENCY,
                    context.getString(R.string.notif_channel_transparency_name),
                    NotificationManager.IMPORTANCE_LOW
                ),
                NotificationChannel(
                    CHANNEL_ALERTS,
                    context.getString(R.string.notif_channel_alerts_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ),
                NotificationChannel(
                    CHANNEL_LIMITS,
                    context.getString(R.string.notif_channel_limits_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
            manager.createNotificationChannels(channels)
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showTransparentProtectionNotification(context: Context) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSPARENCY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.transparency_active_title))
            .setContentText(context.getString(R.string.transparency_active_subtitle))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_TRANSPARENCY, notification)
    }

    fun showAppLimitWarning(context: Context, appName: String, limitMinutes: Int) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_LIMITS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_limit_warning_title, appName))
            .setContentText(context.getString(R.string.notif_limit_warning_body, limitMinutes, appName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val id = NOTIF_ID_LIMIT_BASE + (appName.hashCode() and 0xFF)
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun showNonSensitiveSettingsSyncNotification(context: Context) {
        ensureChannels(context)
        if (!canPostNotifications(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_settings_updated))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIF_ID_ALERT_BASE, notification)
    }
}

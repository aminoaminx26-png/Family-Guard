package com.example.core.domain.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AppRole(val wireValue: String) {
    UNSELECTED("unselected"),
    PARENT("parent"),
    CHILD("child");

    companion object {
        fun fromWire(value: String?): AppRole = entries.firstOrNull { it.wireValue == value } ?: UNSELECTED
    }
}

sealed interface AppError {
    data object NoInternet : AppError
    data object FirebaseUnavailable : AppError
    data object InvalidPairingCode : AppError
    data object ExpiredPairingCode : AppError
    data object AlreadyUsedPairingCode : AppError
    data object Unauthorized : AppError
    data object UsageAccessDisabled : AppError
    data object PermissionDenied : AppError
    data object TokenExpired : AppError
    data object DeviceUnlinked : AppError
    data class Unknown(val message: String) : AppError
}

sealed interface AppResult<out T> {
    data object Loading : AppResult<Nothing>
    data object Empty : AppResult<Nothing>
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val error: AppError) : AppResult<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val privacyModeEnabled: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val preferredRole: String = "parent",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PairingCodeRecord(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val label: String = "",
    val used: Boolean = false,
    val claimedByUid: String? = null,
    val claimedDeviceId: String? = null,
    val expiresAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class GeneratedPairingCode(
    val rawCode: String,
    val codeHash: String,
    val expiresAtMillis: Long,
    val label: String
)

@IgnoreExtraProperties
data class ChildDevice(
    @DocumentId val id: String = "",
    val parentUid: String = "",
    val childAuthUid: String = "",
    val memberUids: List<String> = emptyList(),
    val childName: String = "",
    val deviceName: String = "",
    val androidVersion: String = "",
    val monitoringActive: Boolean = true,
    val usageAccessGranted: Boolean = false,
    val isOnline: Boolean = true,
    val totalScreenTimeMs: Long = 0L,
    val appsUsedCount: Long = 0L,
    val activeAlertsCount: Long = 0L,
    val lastSeenAt: Timestamp? = null,
    val lastSyncAt: Timestamp? = null,
    val syncStatus: String = "pending",
    val pairingCodeHash: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DailyUsage(
    @DocumentId val id: String = "",
    val deviceId: String = "",
    val parentUid: String = "",
    val memberUids: List<String> = emptyList(),
    val date: String = "",
    val totalScreenTimeMs: Long = 0L,
    val appsCount: Long = 0L,
    val topAppName: String = "",
    val topAppPackage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppUsageEntry(
    @DocumentId val id: String = "",
    val deviceId: String = "",
    val parentUid: String = "",
    val memberUids: List<String> = emptyList(),
    val packageName: String = "",
    val appName: String = "",
    val usageTimeMs: Long = 0L,
    val launchCount: Long = 0L,
    val lastUsedAt: Long = 0L,
    val date: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DeviceAlert(
    @DocumentId val id: String = "",
    val deviceId: String = "",
    val parentUid: String = "",
    val memberUids: List<String> = emptyList(),
    val type: String = "limit_exceeded",
    val title: String = "",
    val message: String = "",
    val packageName: String? = null,
    val severity: String = "warning",
    val acknowledged: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppLimit(
    @DocumentId val id: String = "",
    val deviceId: String = "",
    val parentUid: String = "",
    val memberUids: List<String> = emptyList(),
    val packageName: String = "",
    val appName: String = "",
    val dailyLimitMinutes: Long = 30L,
    val warningEnabled: Boolean = true,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CollectedAppUsage(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val launchCount: Int,
    val lastUsedAt: Long
)

package com.example.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage")
data class UsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val launchCount: Int = 0,
    val date: String,
    val timestamp: Long
)

@Entity(tableName = "child_devices")
data class ChildDeviceEntity(
    @PrimaryKey
    val deviceId: String,
    val parentUid: String,
    val childName: String,
    val deviceName: String,
    val androidVersion: String,
    val monitoringActive: Boolean,
    val isOnline: Boolean,
    val totalScreenTimeMs: Long,
    val lastSyncTimestamp: Long
)

@Entity(tableName = "sync_status")
data class SyncEntity(
    @PrimaryKey
    val id: Int = 1,
    val lastAttemptTimestamp: Long,
    val lastSuccessTimestamp: Long,
    val status: String,
    val errorMessage: String? = null
)

@Entity(tableName = "app_info")
data class AppInfoEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val lastUpdatedTimestamp: Long
)

@Entity(tableName = "limits")
data class LimitEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val warningEnabled: Boolean,
    val isActive: Boolean
)

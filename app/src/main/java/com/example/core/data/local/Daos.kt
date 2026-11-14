package com.example.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage WHERE date = :date ORDER BY usageTimeMs DESC")
    fun observeUsageForDate(date: String): Flow<List<UsageEntity>>

    @Query("SELECT * FROM usage WHERE date = :date ORDER BY usageTimeMs DESC")
    suspend fun getUsageForDate(date: String): List<UsageEntity>

    @Query("DELETE FROM usage WHERE date = :date")
    suspend fun deleteUsageForDate(date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<UsageEntity>)

    @Query("DELETE FROM usage")
    suspend fun clearAll()
}

@Dao
interface ChildDeviceDao {
    @Query("SELECT * FROM child_devices ORDER BY lastSyncTimestamp DESC")
    fun observeDevices(): Flow<List<ChildDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<ChildDeviceEntity>)

    @Query("DELETE FROM child_devices WHERE deviceId = :deviceId")
    suspend fun deleteById(deviceId: String)

    @Query("DELETE FROM child_devices")
    suspend fun clearAll()
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_status WHERE id = 1")
    fun observeSyncStatus(): Flow<SyncEntity?>

    @Query("SELECT * FROM sync_status WHERE id = 1")
    suspend fun getSyncStatus(): SyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(syncEntity: SyncEntity)

    @Query("DELETE FROM sync_status")
    suspend fun clearAll()
}

@Dao
interface AppInfoDao {
    @Query("SELECT * FROM app_info ORDER BY appName ASC")
    fun observeAll(): Flow<List<AppInfoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(apps: List<AppInfoEntity>)

    @Query("DELETE FROM app_info")
    suspend fun clearAll()
}

@Dao
interface LimitDao {
    @Query("SELECT * FROM limits ORDER BY appName ASC")
    fun observeAll(): Flow<List<LimitEntity>>

    @Query("SELECT * FROM limits WHERE isActive = 1")
    suspend fun getActiveLimits(): List<LimitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(limits: List<LimitEntity>)

    @Query("DELETE FROM limits")
    suspend fun clearAll()
}

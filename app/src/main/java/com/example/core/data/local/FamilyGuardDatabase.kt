package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UsageEntity::class,
        ChildDeviceEntity::class,
        SyncEntity::class,
        AppInfoEntity::class,
        LimitEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FamilyGuardDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun childDeviceDao(): ChildDeviceDao
    abstract fun syncDao(): SyncDao
    abstract fun appInfoDao(): AppInfoDao
    abstract fun limitDao(): LimitDao

    companion object {
        @Volatile
        private var INSTANCE: FamilyGuardDatabase? = null

        fun getInstance(context: Context): FamilyGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FamilyGuardDatabase::class.java,
                    "family_guard_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

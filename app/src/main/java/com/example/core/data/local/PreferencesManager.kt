package com.example.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.domain.model.AppRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "family_guard_prefs")

data class LocalSessionPrefs(
    val role: AppRole = AppRole.UNSELECTED,
    val linkedDeviceId: String = "",
    val linkedParentUid: String = "",
    val childName: String = "",
    val monitoringActive: Boolean = true,
    val lastSyncEpochMs: Long = 0L
)

class PreferencesManager(private val context: Context) {
    private object Keys {
        val ROLE = stringPreferencesKey("app_role")
        val LINKED_DEVICE_ID = stringPreferencesKey("linked_device_id")
        val LINKED_PARENT_UID = stringPreferencesKey("linked_parent_uid")
        val CHILD_NAME = stringPreferencesKey("child_name")
        val MONITORING_ACTIVE = booleanPreferencesKey("monitoring_active")
        val LAST_SYNC_EPOCH_MS = longPreferencesKey("last_sync_epoch_ms")
    }

    val prefsFlow: Flow<LocalSessionPrefs> = context.dataStore.data.map { prefs ->
        LocalSessionPrefs(
            role = AppRole.fromWire(prefs[Keys.ROLE]),
            linkedDeviceId = prefs[Keys.LINKED_DEVICE_ID].orEmpty(),
            linkedParentUid = prefs[Keys.LINKED_PARENT_UID].orEmpty(),
            childName = prefs[Keys.CHILD_NAME].orEmpty(),
            monitoringActive = prefs[Keys.MONITORING_ACTIVE] ?: true,
            lastSyncEpochMs = prefs[Keys.LAST_SYNC_EPOCH_MS] ?: 0L
        )
    }

    suspend fun getSnapshot(): LocalSessionPrefs = prefsFlow.first()

    suspend fun setRole(role: AppRole) {
        context.dataStore.edit { it[Keys.ROLE] = role.wireValue }
    }

    suspend fun saveChildPairing(deviceId: String, parentUid: String, childName: String) {
        context.dataStore.edit {
            it[Keys.LINKED_DEVICE_ID] = deviceId
            it[Keys.LINKED_PARENT_UID] = parentUid
            it[Keys.CHILD_NAME] = childName
            it[Keys.MONITORING_ACTIVE] = true
        }
    }

    suspend fun updateSyncTimestamp(epochMs: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_EPOCH_MS] = epochMs }
    }

    suspend fun setMonitoringActive(active: Boolean) {
        context.dataStore.edit { it[Keys.MONITORING_ACTIVE] = active }
    }

    suspend fun clearChildPairing() {
        context.dataStore.edit {
            it.remove(Keys.LINKED_DEVICE_ID)
            it.remove(Keys.LINKED_PARENT_UID)
            it.remove(Keys.CHILD_NAME)
            it.remove(Keys.LAST_SYNC_EPOCH_MS)
        }
    }
}

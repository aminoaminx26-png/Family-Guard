package com.example.core.data.remote

import android.content.Context
import android.os.Build
import com.example.R
import com.example.core.data.local.AppInfoEntity
import com.example.core.data.local.ChildDeviceEntity
import com.example.core.data.local.FamilyGuardDatabase
import com.example.core.data.local.LimitEntity
import com.example.core.data.local.PreferencesManager
import com.example.core.data.local.SyncEntity
import com.example.core.data.local.UsageEntity
import com.example.core.domain.model.AppError
import com.example.core.domain.model.AppLimit
import com.example.core.domain.model.AppRole
import com.example.core.domain.model.AppUsageEntry
import com.example.core.domain.model.ChildDevice
import com.example.core.domain.model.CollectedAppUsage
import com.example.core.domain.model.DailyUsage
import com.example.core.domain.model.DeviceAlert
import com.example.core.domain.model.GeneratedPairingCode
import com.example.core.domain.model.PairingCodeRecord
import com.example.core.domain.model.UserProfile
import com.example.core.security.SecurityUtils
import com.example.core.utils.FormatUtils
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class FamilyGuardRepository(
    private val db: FirebaseFirestore,
    private val localDb: FamilyGuardDatabase,
    private val prefs: PreferencesManager,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        localDb = FamilyGuardDatabase.getInstance(context.applicationContext),
        prefs = PreferencesManager(context.applicationContext)
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- User Profile ---

    suspend fun ensureUserProfile(preferredRole: AppRole? = null): Result<UserProfile> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not authenticated"))
        val docRef = db.collection("users").document(user.uid)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                val profile = snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = user.uid)
                Result.success(profile)
            } else {
                val payload = mapOf(
                    "userId" to user.uid,
                    "displayName" to (user.displayName?.take(100) ?: "Parent"),
                    "email" to (user.email?.take(254) ?: ""),
                    "privacyModeEnabled" to false,
                    "notificationsEnabled" to true,
                    "preferredRole" to (preferredRole?.takeIf { it != AppRole.UNSELECTED }?.wireValue ?: "parent"),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                val created = docRef.get().await().toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: UserProfile(userId = user.uid)
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    snap.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    suspend fun updatePrivacyMode(uid: String, enabled: Boolean): Result<Unit> {
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "privacyModeEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    // --- Secure One-Time Pairing System ---

    suspend fun generatePairingCode(childNickname: String): Result<GeneratedPairingCode> {
        val uid = requireUserId()
        val rawCode = SecurityUtils.generatePairingCode()
        val codeHash = SecurityUtils.normalizeAndHashPairingCode(rawCode)
        val expiresAtMillis = System.currentTimeMillis() + PAIRING_CODE_TTL_MS
        val label = childNickname.trim().ifBlank { "Child Device" }.take(60)

        val docRef = db.collection("pairingCodes").document(codeHash)
        val payload = mapOf(
            "parentUid" to uid,
            "label" to label,
            "used" to false,
            "expiresAt" to Timestamp(Date(expiresAtMillis)),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(
                GeneratedPairingCode(
                    rawCode = rawCode,
                    codeHash = codeHash,
                    expiresAtMillis = expiresAtMillis,
                    label = label
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun redeemPairingCode(
        rawCode: String,
        childNameInput: String,
        usageAccessGranted: Boolean
    ): kotlin.Result<ChildDevice> {
        val childAuthUid = requireUserId()
        if (!SecurityUtils.isValidPairingCodeFormat(rawCode)) {
            return kotlin.Result.failure(PairingException(AppError.InvalidPairingCode))
        }
        val codeHash = SecurityUtils.normalizeAndHashPairingCode(rawCode)
        val codeRef = db.collection("pairingCodes").document(codeHash)

        return try {
            val snap = codeRef.get().await()
            if (!snap.exists()) {
                return kotlin.Result.failure(PairingException(AppError.InvalidPairingCode))
            }
            val record = snap.toObject(
                PairingCodeRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return kotlin.Result.failure(PairingException(AppError.InvalidPairingCode))

            if (record.used) {
                return kotlin.Result.failure(PairingException(AppError.AlreadyUsedPairingCode))
            }
            val expiresAtMs = record.expiresAt?.toDate()?.time ?: 0L
            if (expiresAtMs <= System.currentTimeMillis()) {
                return kotlin.Result.failure(PairingException(AppError.ExpiredPairingCode))
            }

            val deviceId = SecurityUtils.generateNonSensitiveDeviceId()
            val deviceRef = db.collection("devices").document(deviceId)
            val parentUid = record.parentUid
            val members = listOf(parentUid, childAuthUid).distinct()
            val resolvedChildName = childNameInput.trim()
                .ifBlank { record.label.ifBlank { "Child" } }
                .take(60)
            val deviceModel = "${Build.MANUFACTURER.orEmpty()} ${Build.MODEL.orEmpty()}"
                .trim()
                .ifBlank { "Android Device" }
                .take(80)
            val androidVer = Build.VERSION.RELEASE.orEmpty().ifBlank { "14" }.take(32)

            val batch = db.batch()
            batch.update(
                codeRef,
                mapOf(
                    "used" to true,
                    "claimedByUid" to childAuthUid,
                    "claimedDeviceId" to deviceId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                deviceRef,
                mapOf(
                    "parentUid" to parentUid,
                    "childAuthUid" to childAuthUid,
                    "memberUids" to members,
                    "childName" to resolvedChildName,
                    "deviceName" to deviceModel,
                    "androidVersion" to androidVer,
                    "monitoringActive" to true,
                    "usageAccessGranted" to usageAccessGranted,
                    "isOnline" to true,
                    "totalScreenTimeMs" to 0L,
                    "appsUsedCount" to 0L,
                    "activeAlertsCount" to 0L,
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "syncStatus" to "pending",
                    "pairingCodeHash" to codeHash,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            prefs.saveChildPairing(
                deviceId = deviceId,
                parentUid = parentUid,
                childName = resolvedChildName
            )
            localDb.childDeviceDao().upsertAll(
                listOf(
                    ChildDeviceEntity(
                        deviceId = deviceId,
                        parentUid = parentUid,
                        childName = resolvedChildName,
                        deviceName = deviceModel,
                        androidVersion = androidVer,
                        monitoringActive = true,
                        isOnline = true,
                        totalScreenTimeMs = 0L,
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                )
            )

            val created = deviceRef.get().await().toObject(
                ChildDevice::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: ChildDevice(
                id = deviceId,
                parentUid = parentUid,
                childAuthUid = childAuthUid,
                memberUids = members,
                childName = resolvedChildName,
                deviceName = deviceModel,
                androidVersion = androidVer
            )
            kotlin.Result.success(created)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, codeRef.path, auth)
            kotlin.Result.failure(e)
        }
    }

    // --- Parent Observations ---

    fun observeParentDevices(parentUid: String): Flow<List<ChildDevice>> = flow {
        val path = "devices"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", parentUid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ChildDevice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).filter { it.parentUid == parentUid }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == parentUid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeDevice(deviceId: String): Flow<ChildDevice?> = flow {
        val docRef = db.collection("devices").document(deviceId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(
                        ChildDevice::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeTodayApps(deviceId: String, uid: String): Flow<List<AppUsageEntry>> = flow {
        val path = "devices/$deviceId/apps"
        emitAll(
            db.collection("devices").document(deviceId).collection("apps")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AppUsageEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.usageTimeMs }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeAlerts(deviceId: String, uid: String): Flow<List<DeviceAlert>> = flow {
        val path = "devices/$deviceId/alerts"
        emitAll(
            db.collection("devices").document(deviceId).collection("alerts")
                .whereArrayContains("memberUids", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        DeviceAlert::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    fun observeLimits(deviceId: String, uid: String): Flow<List<AppLimit>> = flow {
        val path = "devices/$deviceId/limits"
        emitAll(
            db.collection("devices").document(deviceId).collection("limits")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AppLimit::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.appName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<com.google.firebase.firestore.FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path, auth)
                    }
                    throw error
                }
        )
    }

    // --- Parent Actions ---

    suspend fun setDeviceMonitoring(deviceId: String, active: Boolean): Result<Unit> {
        val docRef = db.collection("devices").document(deviceId)
        return try {
            docRef.update(
                mapOf(
                    "monitoringActive" to active,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun upsertAppLimit(
        device: ChildDevice,
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        warningEnabled: Boolean = true,
        isActive: Boolean = true
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanPkg = packageName.trim()
        val docRef = db.collection("devices").document(device.id)
            .collection("limits").document(cleanPkg)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "appName" to appName.trim().ifBlank { cleanPkg }.take(100),
                        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(1, 1440),
                        "warningEnabled" to warningEnabled,
                        "isActive" to isActive,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val members = device.memberUids.ifEmpty { listOf(uid) }
                docRef.set(
                    mapOf(
                        "deviceId" to device.id,
                        "parentUid" to device.parentUid,
                        "memberUids" to members,
                        "packageName" to cleanPkg,
                        "appName" to appName.trim().ifBlank { cleanPkg }.take(100),
                        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(1, 1440),
                        "warningEnabled" to warningEnabled,
                        "isActive" to isActive,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteAppLimit(deviceId: String, packageName: String): Result<Unit> {
        val docRef = db.collection("devices").document(deviceId)
            .collection("limits").document(packageName)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun acknowledgeAlert(deviceId: String, alertId: String): Result<Unit> {
        val docRef = db.collection("devices").document(deviceId)
            .collection("alerts").document(alertId)
        return try {
            docRef.update(
                mapOf(
                    "acknowledged" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteChildDeviceData(deviceId: String): Result<Unit> {
        val uid = requireUserId()
        val deviceRef = db.collection("devices").document(deviceId)
        return try {
            val subcollections = listOf("usage", "apps", "alerts", "limits")
            for (sub in subcollections) {
                val docs = deviceRef.collection(sub)
                    .whereArrayContains("memberUids", uid)
                    .get()
                    .await()
                if (!docs.isEmpty) {
                    val batch = db.batch()
                    docs.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }
            }
            deviceRef.delete().await()
            localDb.childDeviceDao().deleteById(deviceId)
            val currentPrefs = prefs.getSnapshot()
            if (currentPrefs.linkedDeviceId == deviceId) {
                prefs.clearChildPairing()
                localDb.usageDao().clearAll()
                localDb.limitDao().clearAll()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, deviceRef.path, auth)
            Result.failure(e)
        }
    }

    suspend fun deleteParentAccountData(uid: String): Result<Unit> {
        return try {
            val devices = db.collection("devices")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
            for (doc in devices.documents) {
                if (doc.getString("parentUid") == uid) {
                    deleteChildDeviceData(doc.id)
                }
            }
            db.collection("users").document(uid).delete().await()
            localDb.usageDao().clearAll()
            localDb.childDeviceDao().clearAll()
            localDb.syncDao().clearAll()
            localDb.appInfoDao().clearAll()
            localDb.limitDao().clearAll()
            prefs.clearChildPairing()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid", auth)
            Result.failure(e)
        }
    }

    // --- Child Sync (Room + Firestore + Limits evaluation) ---

    suspend fun syncChildUsage(
        deviceId: String,
        collectedApps: List<CollectedAppUsage>,
        usageAccessGranted: Boolean
    ): Result<List<AppLimit>> {
        val uid = requireUserId()
        val nowMs = System.currentTimeMillis()
        val today = FormatUtils.todayDateKey(nowMs)
        val totalTimeMs = collectedApps.sumOf { it.usageTimeMs }

        // 1. Update local Room cache immediately
        localDb.usageDao().deleteUsageForDate(today)
        localDb.usageDao().insertAll(
            collectedApps.map {
                UsageEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    usageTimeMs = it.usageTimeMs,
                    launchCount = it.launchCount,
                    date = today,
                    timestamp = nowMs
                )
            }
        )
        localDb.appInfoDao().upsertAll(
            collectedApps.map {
                AppInfoEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    lastUpdatedTimestamp = nowMs
                )
            }
        )

        val deviceRef = db.collection("devices").document(deviceId)
        return try {
            val deviceSnap = deviceRef.get().await()
            if (!deviceSnap.exists()) {
                localDb.syncDao().upsert(
                    SyncEntity(
                        lastAttemptTimestamp = nowMs,
                        lastSuccessTimestamp = prefs.getSnapshot().lastSyncEpochMs,
                        status = "failed",
                        errorMessage = "Device unlinked"
                    )
                )
                return Result.failure(PairingException(AppError.DeviceUnlinked))
            }

            val device = deviceSnap.toObject(
                ChildDevice::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(PairingException(AppError.DeviceUnlinked))

            val members = device.memberUids.ifEmpty { listOf(device.parentUid, uid).distinct() }

            // Fetch parent-configured limits from Firestore & cache in Room
            val remoteLimits = deviceRef.collection("limits")
                .whereArrayContains("memberUids", uid)
                .get()
                .await()
                .toObjects(AppLimit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            localDb.limitDao().clearAll()
            localDb.limitDao().upsertAll(
                remoteLimits.map {
                    LimitEntity(
                        packageName = it.packageName,
                        appName = it.appName,
                        dailyLimitMinutes = it.dailyLimitMinutes.toInt(),
                        warningEnabled = it.warningEnabled,
                        isActive = it.isActive
                    )
                }
            )

            // Check exceeded limits & usage access revoked alerts
            val exceededLimits = mutableListOf<AppLimit>()
            val usageByPkg = collectedApps.associateBy { it.packageName }
            for (limit in remoteLimits) {
                if (!limit.isActive) continue
                val appUsage = usageByPkg[limit.packageName] ?: continue
                if (appUsage.usageTimeMs >= limit.dailyLimitMinutes * 60_000L) {
                    exceededLimits.add(limit)
                    val alertId = "limit_${today}_${limit.packageName.replace(".", "_")}"
                    val alertRef = deviceRef.collection("alerts").document(alertId)
                    if (!alertRef.get().await().exists()) {
                        alertRef.set(
                            mapOf(
                                "deviceId" to deviceId,
                                "parentUid" to device.parentUid,
                                "memberUids" to members,
                                "type" to "limit_exceeded",
                                "title" to "${limit.appName} daily limit reached",
                                "message" to "${limit.appName} exceeded ${limit.dailyLimitMinutes} min today.",
                                "packageName" to limit.packageName,
                                "severity" to "warning",
                                "acknowledged" to false,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }

            if (!usageAccessGranted) {
                val revokedAlertId = "usage_revoked_$today"
                val alertRef = deviceRef.collection("alerts").document(revokedAlertId)
                if (!alertRef.get().await().exists()) {
                    alertRef.set(
                        mapOf(
                            "deviceId" to deviceId,
                            "parentUid" to device.parentUid,
                            "memberUids" to members,
                            "type" to "usage_access_revoked",
                            "title" to "Usage Access disabled",
                            "message" to "Usage Access permission is off on ${device.childName}'s device.",
                            "severity" to "critical",
                            "acknowledged" to false,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            // Update device summary
            deviceRef.update(
                mapOf(
                    "usageAccessGranted" to usageAccessGranted,
                    "isOnline" to true,
                    "totalScreenTimeMs" to totalTimeMs,
                    "appsUsedCount" to collectedApps.size.toLong(),
                    "lastSeenAt" to FieldValue.serverTimestamp(),
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                    "syncStatus" to "synced",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Update daily usage summary
            val topApp = collectedApps.firstOrNull()
            val dailyRef = deviceRef.collection("usage").document(today)
            val dailySnap = dailyRef.get().await()
            if (dailySnap.exists()) {
                dailyRef.update(
                    mapOf(
                        "totalScreenTimeMs" to totalTimeMs,
                        "appsCount" to collectedApps.size.toLong(),
                        "topAppName" to (topApp?.appName ?: ""),
                        "topAppPackage" to (topApp?.packageName ?: ""),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                dailyRef.set(
                    mapOf(
                        "deviceId" to deviceId,
                        "parentUid" to device.parentUid,
                        "memberUids" to members,
                        "date" to today,
                        "totalScreenTimeMs" to totalTimeMs,
                        "appsCount" to collectedApps.size.toLong(),
                        "topAppName" to (topApp?.appName ?: ""),
                        "topAppPackage" to (topApp?.packageName ?: ""),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Upsert top 20 apps for today
            for (app in collectedApps.take(20)) {
                val appRef = deviceRef.collection("apps").document(app.packageName)
                val appSnap = appRef.get().await()
                if (appSnap.exists()) {
                    appRef.update(
                        mapOf(
                            "appName" to app.appName.take(100),
                            "usageTimeMs" to app.usageTimeMs,
                            "launchCount" to app.launchCount.toLong(),
                            "lastUsedAt" to app.lastUsedAt,
                            "date" to today,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    appRef.set(
                        mapOf(
                            "deviceId" to deviceId,
                            "parentUid" to device.parentUid,
                            "memberUids" to members,
                            "packageName" to app.packageName,
                            "appName" to app.appName.take(100),
                            "usageTimeMs" to app.usageTimeMs,
                            "launchCount" to app.launchCount.toLong(),
                            "lastUsedAt" to app.lastUsedAt,
                            "date" to today,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            prefs.updateSyncTimestamp(nowMs)
            localDb.syncDao().upsert(
                SyncEntity(
                    lastAttemptTimestamp = nowMs,
                    lastSuccessTimestamp = nowMs,
                    status = "synced",
                    errorMessage = null
                )
            )
            Result.success(exceededLimits)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, deviceRef.path, auth)
            localDb.syncDao().upsert(
                SyncEntity(
                    lastAttemptTimestamp = nowMs,
                    lastSuccessTimestamp = prefs.getSnapshot().lastSyncEpochMs,
                    status = "failed",
                    errorMessage = e.localizedMessage
                )
            )
            Result.failure(e)
        }
    }

    fun observeLocalTodayUsage(): Flow<List<UsageEntity>> =
        localDb.usageDao().observeUsageForDate(FormatUtils.todayDateKey())

    fun observeLocalSyncStatus(): Flow<SyncEntity?> =
        localDb.syncDao().observeSyncStatus()

    fun observeLocalLimits(): Flow<List<LimitEntity>> =
        localDb.limitDao().observeAll()

    class PairingException(val appError: AppError) : Exception()

    private companion object {
        const val PAIRING_CODE_TTL_MS = 15 * 60 * 1000L
    }
}

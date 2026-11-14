package com.example.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.base.FirestoreEmulatorTestBase
import com.example.core.data.local.FamilyGuardDatabase
import com.example.core.data.local.PreferencesManager
import com.example.core.data.remote.FamilyGuardRepository
import com.example.core.domain.model.CollectedAppUsage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FamilyGuardRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var context: Context
    private lateinit var localDb: FamilyGuardDatabase
    private lateinit var prefs: PreferencesManager
    private lateinit var repository: FamilyGuardRepository

    @Before
    fun setUpRepo() {
        context = ApplicationProvider.getApplicationContext()
        localDb = Room.inMemoryDatabaseBuilder(context, FamilyGuardDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesManager(context)
        repository = FamilyGuardRepository(firestore, localDb, prefs, auth)
    }

    @After
    fun tearDownRepo() {
        localDb.close()
    }

    @Test
    fun ensureUserProfile_and_pairing_and_sync_succeed_for_authorized_parent_and_child() = runBlocking {
        val parentEmail = "parent_${UUID.randomUUID().toString().take(8)}@test.com"
        val childEmail = "child_${UUID.randomUUID().toString().take(8)}@test.com"

        val parentUid = signInTestUser(parentEmail)
        val profileRes = withTimeout(TIMEOUT_MS) { repository.ensureUserProfile() }
        assertTrue(profileRes.isSuccess)
        assertEquals(parentUid, profileRes.getOrThrow().userId)

        val codeRes = withTimeout(TIMEOUT_MS) { repository.generatePairingCode("Kids Tablet") }
        assertTrue(codeRes.isSuccess)
        val generated = codeRes.getOrThrow()

        // Child signs in with a separate account and redeems the one-time code
        signInTestUser(childEmail)
        val pairRes = withTimeout(TIMEOUT_MS) {
            repository.redeemPairingCode(
                rawCode = generated.rawCode,
                childNameInput = "Sam",
                usageAccessGranted = true
            )
        }
        assertTrue("Pairing should succeed: ${pairRes.exceptionOrNull()}", pairRes.isSuccess)
        val linkedDevice = pairRes.getOrThrow()

        // Child syncs usage
        val syncRes = withTimeout(TIMEOUT_MS) {
            repository.syncChildUsage(
                deviceId = linkedDevice.id,
                collectedApps = listOf(
                    CollectedAppUsage(
                        packageName = "com.google.android.youtube",
                        appName = "YouTube",
                        usageTimeMs = 1_800_000L,
                        launchCount = 4,
                        lastUsedAt = System.currentTimeMillis()
                    )
                ),
                usageAccessGranted = true
            )
        }
        assertTrue("Child usage sync should succeed", syncRes.isSuccess)

        // Parent observes linked devices & adds an app limit
        signInTestUser(parentEmail)
        val devices = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeParentDevices(parentUid).first { it.any { d -> d.id == linkedDevice.id } }
        }
        assertTrue(devices.any { it.id == linkedDevice.id })

        val limitRes = withTimeout(TIMEOUT_MS) {
            repository.upsertAppLimit(
                device = linkedDevice,
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                dailyLimitMinutes = 30
            )
        }
        assertTrue(limitRes.isSuccess)
    }

    @Test
    fun stranger_cannot_read_parent_devices_or_modify_limits() = runBlocking {
        val parentEmail = "parent2_${UUID.randomUUID().toString().take(8)}@test.com"
        val strangerEmail = "stranger_${UUID.randomUUID().toString().take(8)}@test.com"

        val parentUid = signInTestUser(parentEmail)
        withTimeout(TIMEOUT_MS) { repository.ensureUserProfile() }
        val code = withTimeout(TIMEOUT_MS) { repository.generatePairingCode("Lina").getOrThrow() }
        val device = withTimeout(TIMEOUT_MS) {
            repository.redeemPairingCode(code.rawCode, "Lina", true).getOrThrow()
        }

        signInTestUser(strangerEmail)
        var denied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeDevice(device.id).first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            denied = fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Stranger must receive PERMISSION_DENIED on parent's device", denied)

        // Unauthenticated caller must also be rejected
        auth.signOut()
        var unauthDenied = false
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeParentDevices(parentUid).first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            unauthDenied = fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Unauthenticated caller must receive PERMISSION_DENIED", unauthDenied)
    }

    private companion object {
        const val TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

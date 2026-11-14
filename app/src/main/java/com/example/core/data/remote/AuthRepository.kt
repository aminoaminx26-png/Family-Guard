package com.example.core.data.remote

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class AuthRepository(
    private val auth: FirebaseAuth = Firebase.auth
) {
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = auth.authStateFlow()

    /**
     * Silent auto-sign-in using GetGoogleIdOption (one option per request).
     */
    suspend fun attemptAutoSignIn(context: Context): Boolean {
        if (auth.currentUser != null) return true
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return false
        }
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(authCredential).await()
                true
            } else {
                false
            }
        } catch (_: NoCredentialException) {
            false
        } catch (e: Exception) {
            Log.w(TAG, "Auto sign-in skipped: ${e.message}")
            false
        }
    }

    /**
     * Interactive Google Sign-In using GetSignInWithGoogleOption (one option per request).
     */
    suspend fun signInWithGoogleInteractive(activityContext: Context): Result<FirebaseUser> {
        val clientId = try {
            activityContext.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Google Sign-In configuration missing"))
        }

        val credentialManager = CredentialManager.create(activityContext)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val activity = activityContext as? Activity
                ?: return Result.failure(IllegalArgumentException("Activity context required"))
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("Signed-in user is null"))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            val credentialManager = CredentialManager.create(context)
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    suspend fun deleteFirebaseUser(context: Context): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            user.delete().await()
            withContext(NonCancellable) {
                try {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                } catch (_: Exception) {
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}

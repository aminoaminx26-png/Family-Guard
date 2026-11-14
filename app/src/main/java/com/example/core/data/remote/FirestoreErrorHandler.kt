package com.example.core.data.remote

import android.util.Log
import com.example.core.domain.model.AppError
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?,
    auth: FirebaseAuth = FirebaseAuth.getInstance()
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val currentUser = auth.currentUser
    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

fun mapToAppError(throwable: Throwable): AppError {
    val causes = generateSequence(throwable) { it.cause }.toList()
    val firestoreException = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
    if (firestoreException != null) {
        return when (firestoreException.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.PermissionDenied
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> AppError.Unauthorized
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.FirebaseUnavailable
            else -> AppError.Unknown(firestoreException.localizedMessage ?: "Firestore error")
        }
    }
    if (causes.any { it is FirebaseNetworkException || it is IOException }) {
        return AppError.NoInternet
    }
    if (causes.any { it is FirebaseException && it.message?.contains("token", ignoreCase = true) == true }) {
        return AppError.TokenExpired
    }
    return AppError.Unknown(throwable.localizedMessage ?: "Unexpected error")
}

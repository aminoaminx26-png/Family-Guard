package com.example.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object SecurityUtils {
    // Unambiguous uppercase alphanumeric alphabet (excludes O/0, I/1, L)
    private const val PAIRING_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val CODE_LENGTH = 8
    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically random 8-character pairing code formatted as XXXX-XXXX.
     * Never log the returned raw code to Logcat.
     */
    fun generatePairingCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            val index = secureRandom.nextInt(PAIRING_ALPHABET.length)
            sb.append(PAIRING_ALPHABET[index])
        }
        return "${sb.substring(0, 4)}-${sb.substring(4, 8)}"
    }

    /**
     * Normalizes user-entered pairing code (strips hyphens/whitespace, uppercases)
     * and returns its SHA-256 hex digest for secure Firestore lookup/storage.
     */
    fun normalizeAndHashPairingCode(rawCode: String): String {
        val normalized = normalizePairingCode(rawCode)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun normalizePairingCode(rawCode: String): String {
        return rawCode.trim().replace("-", "").replace(" ", "").uppercase()
    }

    fun isValidPairingCodeFormat(rawCode: String): Boolean {
        val normalized = normalizePairingCode(rawCode)
        return normalized.length == CODE_LENGTH && normalized.all { it in PAIRING_ALPHABET }
    }

    /**
     * Generates a non-sensitive, resettable device identifier for Firestore document keys.
     */
    fun generateNonSensitiveDeviceId(): String {
        return "dev_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    }
}

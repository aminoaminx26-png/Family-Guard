package com.example

import com.example.core.security.SecurityUtils
import com.example.core.utils.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyGuardUnitTest {

    @Test
    fun pairingCode_formatAndHash_areConsistentAndNonPredictable() {
        val code1 = SecurityUtils.generatePairingCode()
        val code2 = SecurityUtils.generatePairingCode()

        assertTrue(SecurityUtils.isValidPairingCodeFormat(code1))
        assertTrue(SecurityUtils.isValidPairingCodeFormat(code2))
        assertNotEquals(code1, code2)

        // Hashing normalizes hyphen and lowercase
        val hashA = SecurityUtils.normalizeAndHashPairingCode(code1)
        val hashB = SecurityUtils.normalizeAndHashPairingCode(code1.replace("-", "").lowercase())
        assertEquals(hashA, hashB)
        assertEquals(64, hashA.length)
    }

    @Test
    fun pairingCode_invalidFormat_isRejected() {
        assertFalse(SecurityUtils.isValidPairingCodeFormat("1234"))
        assertFalse(SecurityUtils.isValidPairingCodeFormat("OOOO-IIII"))
    }

    @Test
    fun formatHms_formatsExpectedDuration() {
        val threeHours42Min18Sec = ((3 * 3600) + (42 * 60) + 18) * 1000L
        assertEquals("03:42:18", FormatUtils.formatHms(threeHours42Min18Sec))
        assertEquals("00:00:00", FormatUtils.formatHms(-50L))
    }
}

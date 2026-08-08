package com.fushan.bustracking.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareIdentityTest {
    @Test
    fun decimalSerial_preservesLegacyHexadecimalFormat() {
        assertEquals("QCM2290-FF", HardwareIdentity.format("255", "QCM2290"))
    }

    @Test
    fun textSerial_isNormalized() {
        assertEquals("QCM2290-CARD-01", HardwareIdentity.format(" card 01 ", "qcm2290"))
    }

    @Test
    fun missingSerialOrModel_isRejected() {
        assertNull(HardwareIdentity.format(null, "QCM2290"))
        assertNull(HardwareIdentity.format("123", "   "))
    }
}

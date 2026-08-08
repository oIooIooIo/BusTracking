package com.fushan.bustracking.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {
    @Test
    fun enrollmentSpecificId_producesStableBackendCompatibleIdentity() {
        val first = DeviceIdentity.fromEnrollmentSpecificId("test-enrollment-id")
        val second = DeviceIdentity.fromEnrollmentSpecificId(" test-enrollment-id ")

        assertEquals(first, second)
        assertTrue(first?.matches(Regex("ANDROID-ESID-[0-9A-F]{64}")) == true)
    }

    @Test
    fun enrollmentSpecificId_distinguishesDevices() {
        assertNotEquals(
            DeviceIdentity.fromEnrollmentSpecificId("device-one"),
            DeviceIdentity.fromEnrollmentSpecificId("device-two"),
        )
    }

    @Test
    fun enrollmentSpecificId_rejectsMissingValue() {
        assertNull(DeviceIdentity.fromEnrollmentSpecificId(null))
        assertNull(DeviceIdentity.fromEnrollmentSpecificId("   "))
    }
}

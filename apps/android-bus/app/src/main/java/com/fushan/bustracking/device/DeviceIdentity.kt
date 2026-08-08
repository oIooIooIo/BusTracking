package com.fushan.bustracking.device

import android.content.Context
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

object DeviceIdentity {
    fun value(context: Context): String? =
        fromEnrollmentSpecificId(
            DedicatedDeviceController.enrollmentSpecificId(context),
        ) ?: HardwareIdentity.value()

    internal fun fromEnrollmentSpecificId(enrollmentSpecificId: String?): String? {
        val source = enrollmentSpecificId?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte ->
                String.format(Locale.ROOT, "%02X", byte.toInt() and 0xFF)
            }
        return "ANDROID-ESID-$digest"
    }
}

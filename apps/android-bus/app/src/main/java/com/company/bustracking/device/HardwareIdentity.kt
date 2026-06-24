package com.company.bustracking.device

import android.os.Build
import java.io.File
import java.util.Locale

object HardwareIdentity {
    private val serialFile = File("/sys/devices/soc0/serial_number")

    val serial: String by lazy {
        val rawSerial = serialFile.readText().trim()
        check(rawSerial.isNotEmpty()) { "Hardware serial is empty" }

        val serial = rawSerial.toULongOrNull()
            ?.toString(16)
            ?.uppercase(Locale.ROOT)
            ?: normalize(rawSerial)
        val socModel = normalize(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL
            } else {
                Build.HARDWARE
            },
        )
        "$socModel-$serial"
    }

    private fun normalize(value: String): String =
        value.trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9._-]"), "-")
            .trim('-')
            .ifEmpty { "UNKNOWN" }
}

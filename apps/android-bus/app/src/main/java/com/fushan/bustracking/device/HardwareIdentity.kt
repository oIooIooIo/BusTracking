package com.fushan.bustracking.device

import android.os.Build
import java.io.File
import java.util.Locale

object HardwareIdentity {
    private val serialFile = File("/sys/devices/soc0/serial_number")

    fun value(): String? = runCatching { serialFile.readText() }
        .getOrNull()
        ?.let { rawSerial -> format(rawSerial, socModel()) }

    internal fun format(rawSerial: String?, socModel: String?): String? {
        val raw = rawSerial?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val serial = raw.toULongOrNull()
            ?.toString(16)
            ?.uppercase(Locale.ROOT)
            ?: normalize(raw).takeIf(String::isNotEmpty)
            ?: return null
        val model = normalize(socModel.orEmpty()).takeIf(String::isNotEmpty) ?: return null
        return "$model-$serial"
    }

    private fun socModel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            Build.HARDWARE
        }

    private fun normalize(value: String): String =
        value.trim()
            .uppercase(Locale.ROOT)
            .replace(Regex("[^A-Z0-9._-]"), "-")
            .trim('-')
}

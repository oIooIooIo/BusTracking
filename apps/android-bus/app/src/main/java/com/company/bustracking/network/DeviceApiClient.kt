package com.company.bustracking.network

import com.company.bustracking.BuildConfig
import com.company.bustracking.data.LocalStore
import com.company.bustracking.data.PendingBoardingEventEntity
import com.company.bustracking.data.PendingGpsEntity
import com.company.bustracking.data.PermissionEntity
import com.company.bustracking.device.HardwareIdentity
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class DeviceApiClient {
    fun permissionSnapshot(): LocalStore.PermissionSnapshotData {
        val json = request("permissions", "GET")
        val bus = json.getJSONObject("bus")
        val employees = json.getJSONArray("employees")
        return LocalStore.PermissionSnapshotData(
            version = json.getLong("version"),
            busId = bus.getString("id"),
            busCode = bus.getString("code"),
            busName = bus.getString("name"),
            employees = List(employees.length()) { index ->
                val employee = employees.getJSONObject(index)
                PermissionEntity(
                    cardSn = employee.getString("cardSn").trim(),
                    employeeId = employee.getString("id"),
                    employeeNo = employee.getString("employeeNo"),
                    employeeName = employee.getString("name"),
                )
            },
        )
    }

    fun uploadGps(points: List<PendingGpsEntity>): GpsUploadResponse {
        val array = JSONArray()
        points.forEach { point ->
            array.put(
                JSONObject()
                    .put("sequenceNo", point.sequenceNo)
                    .put("recordedAt", Instant.ofEpochMilli(point.recordedAt).toString())
                    .put("latitude", point.latitude)
                    .put("longitude", point.longitude)
                    .put("accuracyMeters", point.accuracyMeters ?: JSONObject.NULL),
            )
        }
        val json = request(
            path = "gps-points/batch",
            method = "POST",
            body = JSONObject().put("points", array),
        )
        return GpsUploadResponse(
            accepted = json.getJSONArray("acceptedSequenceNos").longValues(),
            rejected = json.getJSONArray("rejected").objectValues().map {
                RejectedGps(
                    sequenceNo = it.getLong("sequenceNo"),
                    code = it.getString("code"),
                    retryable = it.getBoolean("retryable"),
                )
            },
        )
    }

    fun uploadEvents(events: List<PendingBoardingEventEntity>): EventUploadResponse {
        val array = JSONArray()
        events.forEach { event ->
            array.put(
                JSONObject()
                    .put("id", event.id)
                    .put("cardSn", event.cardSn)
                    .put("employeeId", event.employeeId ?: JSONObject.NULL)
                    .put("result", event.result)
                    .put("scannedAt", Instant.ofEpochMilli(event.scannedAt).toString())
                    .put("permissionVersion", event.permissionVersion ?: JSONObject.NULL),
            )
        }
        val json = request(
            path = "boarding-events/batch",
            method = "POST",
            body = JSONObject().put("events", array),
        )
        return EventUploadResponse(
            accepted = json.getJSONArray("acceptedIds").stringValues(),
            rejected = json.getJSONArray("rejected").objectValues().map {
                RejectedEvent(
                    id = it.getString("id"),
                    code = it.getString("code"),
                    retryable = it.getBoolean("retryable"),
                )
            },
        )
    }

    private fun request(path: String, method: String, body: JSONObject? = null): JSONObject {
        val connection = (URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.DEVICE_API_KEY}")
            connection.setRequestProperty(
                HARDWARE_SERIAL_HEADER,
                HardwareIdentity.serial,
            )
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                require(bytes.size <= MAX_PAYLOAD_BYTES) { "Upload payload exceeds 1 MB" }
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IOException("Server returned HTTP $status: $responseBody")
            }
            return if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    data class GpsUploadResponse(
        val accepted: List<Long>,
        val rejected: List<RejectedGps>,
    )

    data class RejectedGps(
        val sequenceNo: Long,
        val code: String,
        val retryable: Boolean,
    )

    data class EventUploadResponse(
        val accepted: List<String>,
        val rejected: List<RejectedEvent>,
    )

    data class RejectedEvent(
        val id: String,
        val code: String,
        val retryable: Boolean,
    )

    companion object {
        private const val MAX_PAYLOAD_BYTES = 1024 * 1024
        private const val HARDWARE_SERIAL_HEADER = "X-Device-Hardware-Serial"
    }
}

private fun JSONArray.longValues(): List<Long> =
    List(length()) { index -> getLong(index) }

private fun JSONArray.stringValues(): List<String> =
    List(length()) { index -> getString(index) }

private fun JSONArray.objectValues(): List<JSONObject> =
    List(length()) { index -> getJSONObject(index) }

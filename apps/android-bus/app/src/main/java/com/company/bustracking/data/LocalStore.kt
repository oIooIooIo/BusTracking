package com.company.bustracking.data

import android.location.Location
import androidx.room.withTransaction
import java.util.UUID

class LocalStore(private val database: BusDatabase) {
    suspend fun recordLocation(location: Location): Long {
        return database.withTransaction {
            val current = database.deviceState().get() ?: DeviceStateEntity()
            val next = current.sequenceCounter + 1
            database.deviceState().put(current.copy(sequenceCounter = next))
            database.gps().insert(
                PendingGpsEntity(
                    sequenceNo = next,
                    recordedAt = location.time,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                ),
            )
            next
        }
    }

    suspend fun checkCard(cardSn: String): CardDecision {
        val state = database.deviceState().get()
        if (state?.permissionVersion == null || state.routeIds.isBlank()) {
            return CardDecision(
                result = "AUTH_DATA_NOT_READY",
                employee = null,
                permissionVersion = null,
                routeIds = "",
            )
        }
        val employee = database.permissions().find(cardSn)
        return if (employee == null) {
            CardDecision("DENIED_NO_PERMISSION", null, state.permissionVersion, state.routeIds)
        } else {
            CardDecision("ALLOWED", employee, state.permissionVersion, employee.routeIds)
        }
    }

    suspend fun recordBoarding(
        cardSn: String,
        decision: CardDecision,
        scannedAt: Long,
        capture: com.company.bustracking.tracking.ScanLocationProvider.Capture,
    ): String {
        val id = UUID.randomUUID().toString()
        val location = capture.location
        database.boardingEvents().insert(
            PendingBoardingEventEntity(
                id = id,
                cardSn = cardSn,
                employeeId = decision.employee?.employeeId,
                result = decision.result,
                scannedAt = scannedAt,
                permissionVersion = decision.permissionVersion,
                employeeNoSnapshot = decision.employee?.employeeNo,
                employeeNameSnapshot = decision.employee?.employeeName,
                employeeDepartmentSnapshot = decision.employee?.department,
                latitude = location?.latitude,
                longitude = location?.longitude,
                locationRecordedAt = location?.time,
                locationSource = capture.source,
                accuracyMeters = location?.takeIf { it.hasAccuracy() }?.accuracy,
                routeIds = decision.routeIds,
            ),
        )
        return id
    }

    suspend fun replacePermissions(snapshot: PermissionSnapshotData) {
        database.withTransaction {
            database.permissions().clear()
            database.permissions().insertAll(snapshot.employees)
            val current = database.deviceState().get() ?: DeviceStateEntity()
            database.deviceState().put(
                current.copy(
                    permissionVersion = snapshot.version,
                    busId = snapshot.busId,
                    busCode = snapshot.busCode,
                    busName = snapshot.busName,
                    routeIds = snapshot.routes.joinToString(",") { it.id },
                    routeNames = snapshot.routes.joinToString(", ") { it.name },
                    permissionSyncedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun pruneOldGps() {
        val cutoff = System.currentTimeMillis() - THIRTY_DAYS_MS
        database.withTransaction {
            val dropped = database.gps().countOlderThan(cutoff)
            if (dropped > 0) {
                database.gps().deleteOlderThan(cutoff)
                val current = database.deviceState().get() ?: DeviceStateEntity()
                database.deviceState().put(
                    current.copy(droppedGpsCount = current.droppedGpsCount + dropped),
                )
            }
        }
    }

    data class CardDecision(
        val result: String,
        val employee: PermissionEntity?,
        val permissionVersion: Long?,
        val routeIds: String,
    )

    data class PermissionSnapshotData(
        val version: Long,
        val busId: String,
        val busCode: String,
        val busName: String,
        val routes: List<RouteData>,
        val employees: List<PermissionEntity>,
    )

    data class RouteData(val id: String, val code: String, val name: String)

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}

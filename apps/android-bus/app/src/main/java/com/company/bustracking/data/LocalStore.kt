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
        if (state?.permissionVersion == null) {
            return CardDecision(
                result = "AUTH_DATA_NOT_READY",
                employee = null,
                permissionVersion = null,
            )
        }
        val employee = database.permissions().find(cardSn)
        return if (employee == null) {
            CardDecision("DENIED_NO_PERMISSION", null, state.permissionVersion)
        } else {
            CardDecision("ALLOWED", employee, state.permissionVersion)
        }
    }

    suspend fun recordBoarding(cardSn: String, decision: CardDecision): String {
        val id = UUID.randomUUID().toString()
        database.boardingEvents().insert(
            PendingBoardingEventEntity(
                id = id,
                cardSn = cardSn,
                employeeId = decision.employee?.employeeId,
                result = decision.result,
                scannedAt = System.currentTimeMillis(),
                permissionVersion = decision.permissionVersion,
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
    )

    data class PermissionSnapshotData(
        val version: Long,
        val busId: String,
        val busCode: String,
        val busName: String,
        val employees: List<PermissionEntity>,
    )

    companion object {
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}

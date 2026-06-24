package com.company.bustracking.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

object SyncStatus {
    const val PENDING = "PENDING"
    const val DEAD = "DEAD"
}

@Entity(tableName = "device_state")
data class DeviceStateEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "sequence_counter") val sequenceCounter: Long = 0,
    @ColumnInfo(name = "permission_version") val permissionVersion: Long? = null,
    @ColumnInfo(name = "bus_id") val busId: String? = null,
    @ColumnInfo(name = "bus_code") val busCode: String? = null,
    @ColumnInfo(name = "bus_name") val busName: String? = null,
    @ColumnInfo(name = "permission_synced_at") val permissionSyncedAt: Long? = null,
    @ColumnInfo(name = "dropped_gps_count") val droppedGpsCount: Long = 0,
)

@Entity(tableName = "permission_cache")
data class PermissionEntity(
    @PrimaryKey
    @ColumnInfo(name = "card_sn")
    val cardSn: String,
    @ColumnInfo(name = "employee_id")
    val employeeId: String,
    @ColumnInfo(name = "employee_no")
    val employeeNo: String,
    @ColumnInfo(name = "employee_name")
    val employeeName: String,
)

@Entity(tableName = "pending_gps")
data class PendingGpsEntity(
    @PrimaryKey
    @ColumnInfo(name = "sequence_no")
    val sequenceNo: Long,
    @ColumnInfo(name = "recorded_at")
    val recordedAt: Long,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "accuracy_meters")
    val accuracyMeters: Float?,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = SyncStatus.PENDING,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
)

@Entity(tableName = "pending_boarding_event")
data class PendingBoardingEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "card_sn")
    val cardSn: String,
    @ColumnInfo(name = "employee_id")
    val employeeId: String?,
    val result: String,
    @ColumnInfo(name = "scanned_at")
    val scannedAt: Long,
    @ColumnInfo(name = "permission_version")
    val permissionVersion: Long?,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = SyncStatus.PENDING,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
)

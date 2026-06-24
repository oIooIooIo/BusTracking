package com.company.bustracking.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DeviceStateDao {
    @Query("SELECT * FROM device_state WHERE id = 1")
    suspend fun get(): DeviceStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(state: DeviceStateEntity)
}

@Dao
interface PermissionDao {
    @Query("SELECT * FROM permission_cache WHERE card_sn = :cardSn")
    suspend fun find(cardSn: String): PermissionEntity?

    @Query("DELETE FROM permission_cache")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(employees: List<PermissionEntity>)

    @Query("SELECT COUNT(*) FROM permission_cache")
    suspend fun count(): Int
}

@Dao
interface GpsDao {
    @Insert
    suspend fun insert(point: PendingGpsEntity)

    @Query("""
        SELECT * FROM pending_gps
        WHERE sync_status = 'PENDING'
        ORDER BY sequence_no
        LIMIT :limit
    """)
    suspend fun pending(limit: Int): List<PendingGpsEntity>

    @Query("DELETE FROM pending_gps WHERE sequence_no IN (:sequenceNos)")
    suspend fun deleteAccepted(sequenceNos: List<Long>)

    @Query("""
        UPDATE pending_gps
        SET sync_status = 'DEAD', error_code = :code, last_attempt_at = :attemptedAt
        WHERE sequence_no = :sequenceNo
    """)
    suspend fun markDead(sequenceNo: Long, code: String, attemptedAt: Long)

    @Query("""
        UPDATE pending_gps
        SET retry_count = retry_count + 1, last_attempt_at = :attemptedAt
        WHERE sequence_no IN (:sequenceNos)
    """)
    suspend fun markAttempt(sequenceNos: List<Long>, attemptedAt: Long)

    @Query("SELECT COUNT(*) FROM pending_gps WHERE recorded_at < :cutoff")
    suspend fun countOlderThan(cutoff: Long): Long

    @Query("DELETE FROM pending_gps WHERE recorded_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM pending_gps WHERE sync_status = 'PENDING'")
    suspend fun pendingCount(): Int
}

@Dao
interface BoardingEventDao {
    @Insert
    suspend fun insert(event: PendingBoardingEventEntity)

    @Query("""
        SELECT * FROM pending_boarding_event
        WHERE sync_status = 'PENDING'
        ORDER BY scanned_at
        LIMIT :limit
    """)
    suspend fun pending(limit: Int): List<PendingBoardingEventEntity>

    @Query("DELETE FROM pending_boarding_event WHERE id IN (:ids)")
    suspend fun deleteAccepted(ids: List<String>)

    @Query("""
        UPDATE pending_boarding_event
        SET sync_status = 'DEAD', error_code = :code, last_attempt_at = :attemptedAt
        WHERE id = :id
    """)
    suspend fun markDead(id: String, code: String, attemptedAt: Long)

    @Query("""
        UPDATE pending_boarding_event
        SET retry_count = retry_count + 1, last_attempt_at = :attemptedAt
        WHERE id IN (:ids)
    """)
    suspend fun markAttempt(ids: List<String>, attemptedAt: Long)

    @Query("SELECT COUNT(*) FROM pending_boarding_event WHERE sync_status = 'PENDING'")
    suspend fun pendingCount(): Int
}

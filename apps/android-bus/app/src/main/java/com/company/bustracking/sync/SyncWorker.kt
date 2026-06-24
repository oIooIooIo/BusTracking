package com.company.bustracking.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.company.bustracking.data.BusDatabase
import com.company.bustracking.data.LocalStore
import com.company.bustracking.network.DeviceApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    private val database = BusDatabase.get(appContext)
    private val localStore = LocalStore(database)
    private val api = DeviceApiClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            localStore.pruneOldGps()
            localStore.replacePermissions(api.permissionSnapshot())
            uploadGpsQueue()
            uploadEventQueue()
            Result.success()
        } catch (exception: Exception) {
            Result.retry()
        }
    }

    private suspend fun uploadGpsQueue() {
        while (true) {
            val batch = database.gps().pending(GPS_BATCH_SIZE)
            if (batch.isEmpty()) return
            val attemptedAt = System.currentTimeMillis()
            database.gps().markAttempt(batch.map { it.sequenceNo }, attemptedAt)
            val response = api.uploadGps(batch)
            if (response.accepted.isNotEmpty()) {
                database.gps().deleteAccepted(response.accepted)
            }
            response.rejected.forEach { rejected ->
                if (rejected.retryable) {
                    throw RetryableRowException(rejected.code)
                }
                database.gps().markDead(rejected.sequenceNo, rejected.code, attemptedAt)
            }
            ensureComplete(
                sent = batch.map { it.sequenceNo }.toSet(),
                handled = (response.accepted + response.rejected.map { it.sequenceNo }).toSet(),
            )
        }
    }

    private suspend fun uploadEventQueue() {
        while (true) {
            val batch = database.boardingEvents().pending(EVENT_BATCH_SIZE)
            if (batch.isEmpty()) return
            val attemptedAt = System.currentTimeMillis()
            database.boardingEvents().markAttempt(batch.map { it.id }, attemptedAt)
            val response = api.uploadEvents(batch)
            if (response.accepted.isNotEmpty()) {
                database.boardingEvents().deleteAccepted(response.accepted)
            }
            response.rejected.forEach { rejected ->
                if (rejected.retryable) {
                    throw RetryableRowException(rejected.code)
                }
                database.boardingEvents().markDead(rejected.id, rejected.code, attemptedAt)
            }
            ensureComplete(
                sent = batch.map { it.id }.toSet(),
                handled = (response.accepted + response.rejected.map { it.id }).toSet(),
            )
        }
    }

    private fun <T> ensureComplete(sent: Set<T>, handled: Set<T>) {
        if (sent != handled) {
            throw IllegalStateException("Server acknowledgement did not cover the complete batch")
        }
    }

    private class RetryableRowException(code: String) : Exception(code)

    companion object {
        private const val GPS_BATCH_SIZE = 500
        private const val EVENT_BATCH_SIZE = 200
    }
}

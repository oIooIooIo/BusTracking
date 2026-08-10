package com.fushan.bustracking.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fushan.bustracking.BuildConfig
import java.util.UUID
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private val syncConstraint: Constraints
        get() = if (BuildConfig.API_BASE_URL.startsWith("http://127.0.0.1") ||
            BuildConfig.API_BASE_URL.startsWith("http://localhost")
        ) {
            Constraints.Builder().build()
        } else {
            networkConstraint
        }

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(syncConstraint)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "bus-periodic-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueNow(context: Context) {
        enqueueNow(context, manual = false)
    }

    fun enqueueManualNow(context: Context): UUID = enqueueNow(context, manual = true)

    private fun enqueueNow(context: Context, manual: Boolean): UUID {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.INPUT_MANUAL_SYNC to manual))
            .setConstraints(syncConstraint)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "bus-sync-now",
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request.id
    }

    private const val MIN_BACKOFF_SECONDS = 30L
}

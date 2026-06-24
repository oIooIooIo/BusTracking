package com.company.bustracking.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraint)
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
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                MIN_BACKOFF_SECONDS,
                TimeUnit.SECONDS,
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "bus-sync-now",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private const val MIN_BACKOFF_SECONDS = 30L
}

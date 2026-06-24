package com.company.bustracking.tracking

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.company.bustracking.sync.SyncScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        SyncScheduler.schedulePeriodic(context)
        SyncScheduler.enqueueNow(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationTrackingService::class.java),
            )
        }
    }
}

package com.company.bustracking.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.company.bustracking.MainActivity
import com.company.bustracking.R
import com.company.bustracking.data.BusDatabase
import com.company.bustracking.data.LocalStore
import com.company.bustracking.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private lateinit var localStore: LocalStore

    override fun onCreate() {
        super.onCreate()
        localStore = LocalStore(BusDatabase.get(this))
        locationManager = getSystemService(LocationManager::class.java)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, notification())
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startLocationUpdates()
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        scope.launch {
            localStore.recordLocation(location)
            localStore.pruneOldGps()
            SyncScheduler.enqueueNow(this@LocationTrackingService)
        }
    }

    @Deprecated("Deprecated by Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    override fun onDestroy() {
        locationManager.removeUpdates(this)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        locationManager.removeUpdates(this)
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            LOCATION_INTERVAL_MS,
            0f,
            this,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.tracking_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.tracking_notification))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .build()

    companion object {
        private const val CHANNEL_ID = "bus-location"
        private const val NOTIFICATION_ID = 1001
        private const val LOCATION_INTERVAL_MS = 10_000L
    }
}

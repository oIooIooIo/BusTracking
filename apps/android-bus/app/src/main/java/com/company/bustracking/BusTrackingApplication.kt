package com.company.bustracking

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.company.bustracking.sync.SyncScheduler

class BusTrackingApplication : Application() {
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            SyncScheduler.enqueueNow(this@BusTrackingApplication)
        }
    }

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.enqueueNow(this)
        getSystemService(ConnectivityManager::class.java)
            .registerDefaultNetworkCallback(networkCallback)
    }
}

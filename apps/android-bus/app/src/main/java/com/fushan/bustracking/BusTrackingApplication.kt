package com.fushan.bustracking

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import com.fushan.bustracking.device.DedicatedDeviceController
import com.fushan.bustracking.sync.SyncScheduler

class BusTrackingApplication : Application() {
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            SyncScheduler.enqueueNow(this@BusTrackingApplication)
        }
    }

    override fun onCreate() {
        super.onCreate()
        DedicatedDeviceController.configureIfDeviceOwner(this)
        SyncScheduler.schedulePeriodic(this)
        SyncScheduler.enqueueNow(this)
        getSystemService(ConnectivityManager::class.java)
            .registerDefaultNetworkCallback(networkCallback)
    }
}

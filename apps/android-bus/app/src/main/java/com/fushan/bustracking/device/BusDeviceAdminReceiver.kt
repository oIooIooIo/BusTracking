package com.fushan.bustracking.device

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class BusDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        DedicatedDeviceController.configureIfDeviceOwner(context)
    }
}

package com.fushan.bustracking.device

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.fushan.bustracking.MainActivity

object DedicatedDeviceController {
    const val ORGANIZATION_ID = "Fushan"
    private const val ORGANIZATION_NAME = "Fushan"
    private const val TAG = "DedicatedDevice"

    fun isDeviceOwner(context: Context): Boolean =
        policyManager(context).isDeviceOwnerApp(context.packageName)

    fun configureIfDeviceOwner(context: Context): Boolean {
        val manager = policyManager(context)
        if (!manager.isDeviceOwnerApp(context.packageName)) {
            return false
        }

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                manager.enrollmentSpecificId.isBlank()
            ) {
                manager.setOrganizationId(ORGANIZATION_ID)
            }
            val admin = adminComponent(context)
            manager.setOrganizationName(admin, ORGANIZATION_NAME)
            manager.setLockTaskPackages(admin, arrayOf(context.packageName))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.setLockTaskFeatures(
                    admin,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE,
                )
            }
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            manager.addPersistentPreferredActivity(
                admin,
                homeFilter,
                ComponentName(context, MainActivity::class.java),
            )
            true
        }.getOrElse { exception ->
            Log.e(TAG, "Unable to configure dedicated device", exception)
            false
        }
    }

    fun enterLockTask(activity: Activity) {
        if (!configureIfDeviceOwner(activity)) {
            return
        }
        val activityManager = activity.getSystemService(ActivityManager::class.java)
        if (activityManager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            activity.startLockTask()
        }
    }

    fun enrollmentSpecificId(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !configureIfDeviceOwner(context)
        ) {
            return null
        }
        return runCatching { policyManager(context).enrollmentSpecificId }
            .onFailure { Log.e(TAG, "Unable to read enrollment-specific ID", it) }
            .getOrNull()
            ?.trim()
            ?.takeIf(String::isNotEmpty)
    }

    fun adminComponent(context: Context): ComponentName =
        ComponentName(context, BusDeviceAdminReceiver::class.java)

    private fun policyManager(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)
}

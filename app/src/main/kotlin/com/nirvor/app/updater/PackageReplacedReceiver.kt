package com.nirvor.app.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered automatically by Android when Nirvor is updated
 * (MY_PACKAGE_REPLACED). Automatically purges downloaded update APKs from
 * the cache and storage so no obsolete installer files clutter the device.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppUpdater.cleanupOldApks(context)
        }
    }
}

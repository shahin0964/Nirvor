package com.nirvor.app.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = BackupManager.saveOfflineBackup(context, isAuto = true)
                BackupNotifications.show(
                    context,
                    title = "দৈনিক অটো-ব্যাকআপ সম্পন্ন হয়েছে (১১:৩০ PM)",
                    body = "সব ডেটা সফলভাবে সংরক্ষিত: ${file.name}"
                )
            } catch (_: Exception) {
            } finally {
                AutoBackupScheduler.schedule(context)
                pending.finish()
            }
        }
    }
}

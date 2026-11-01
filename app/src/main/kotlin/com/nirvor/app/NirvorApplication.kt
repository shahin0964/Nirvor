package com.nirvor.app

import android.app.Application
import com.nirvor.app.data.NirvorDatabase
import com.nirvor.app.data.Prefs
import com.nirvor.app.browser.BrowserSettings
import com.nirvor.app.downloader.DownloadNotifications
import com.nirvor.app.downloader.MediaEngine
import com.nirvor.app.islamic.PrayerAlarms
import com.nirvor.app.reminders.Notifications

class NirvorApplication : Application() {

    lateinit var db: NirvorDatabase
        private set

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        db = NirvorDatabase.build(this)
        prefs = Prefs(this)
        Notifications.ensureChannel(this)
        DownloadNotifications.ensureChannels(this)
        BrowserSettings.init(this)
        // Islamic Tools: adhan / jama'ah notifications follow the day's prayer times.
        PrayerAlarms.ensureChannels(this)
        PrayerAlarms.reschedule(this)
        // Unpack the download engine now, so the first download starts without waiting.
        MediaEngine.warmUp(this)
    }
}

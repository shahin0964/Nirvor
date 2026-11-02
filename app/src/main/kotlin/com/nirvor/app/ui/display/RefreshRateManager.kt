package com.nirvor.app.ui.display

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.view.Window
import android.view.WindowManager
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Manages display refresh rates seamlessly and silently in the background (30 FPS - 120+ FPS).
 * Connects directly with the Android system and device display policy so the app automatically
 * synchronizes with whatever refresh rate / FPS mode the phone is currently in (30, 60, 90, 120 FPS).
 *
 * No UI meters, popups, or user settings are required - everything operates 100% automatically.
 */
object RefreshRateManager {

    private val registeredActivities = WeakHashMap<Activity, DisplayManager.DisplayListener>()

    /**
     * Connects the activity's window to the system display manager and power manager,
     * ensuring the app automatically matches whatever refresh rate the phone is currently running in.
     */
    fun applyOptimalRefreshRate(activity: Activity) {
        val window = activity.window ?: return
        applyToWindow(activity, window)

        // Register a listener for system display changes (e.g. user toggles 60Hz/120Hz or power mode)
        // only once per activity instance to prevent memory leaks or redundant callbacks.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !registeredActivities.containsKey(activity)) {
                val dm = activity.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
                val weakActivity = WeakReference(activity)
                val listener = object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) {}
                    override fun onDisplayRemoved(displayId: Int) {}
                    override fun onDisplayChanged(displayId: Int) {
                        val act = weakActivity.get() ?: return
                        if (!act.isFinishing && !act.isDestroyed) {
                            act.runOnUiThread {
                                applyToWindow(act, act.window)
                            }
                        }
                    }
                }
                dm.registerDisplayListener(listener, null)
                registeredActivities[activity] = listener
            }
        } catch (_: Exception) {}
    }

    /**
     * Applies optimal display mode and refresh rate parameters to [window], fully
     * synchronized with the phone's system state.
     */
    fun applyToWindow(context: Context, window: Window) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display ?: window.windowManager.defaultDisplay
            } else {
                @Suppress("DEPRECATION")
                window.windowManager.defaultDisplay
            } ?: return

            val modes = display.supportedModes ?: return
            if (modes.isEmpty()) return

            val currentMode = display.mode
            // Prefer modes that match physical screen resolution to avoid unnecessary scaling
            val matchingModes = modes.filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }

            val bestMode = matchingModes.maxByOrNull { it.refreshRate }
                ?: modes.maxByOrNull { it.refreshRate }
                ?: return

            // Check if phone is currently in Battery Saver mode
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isPowerSave = powerManager?.isPowerSaveMode == true

            val params = window.attributes

            if (isPowerSave) {
                // In battery saver mode, respect system power saving and don't force high refresh rates
                params.preferredDisplayModeId = 0
                params.preferredRefreshRate = 0f
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        val fieldMin = WindowManager.LayoutParams::class.java.getField("preferredMinDisplayRefreshRate")
                        val fieldMax = WindowManager.LayoutParams::class.java.getField("preferredMaxDisplayRefreshRate")
                        fieldMin.setFloat(params, 30f)
                        fieldMax.setFloat(params, 60f)
                    } catch (_: Throwable) {}
                }
            } else {
                // Normal mode: Enable maximum supported refresh rate (e.g. 90 FPS, 120 FPS, 144 FPS)
                // while seamlessly allowing the system to drop down to 30 FPS / 60 FPS when idle.
                params.preferredDisplayModeId = bestMode.modeId
                params.preferredRefreshRate = bestMode.refreshRate

                // API 34+ (Android 14): Support seamless dynamic refresh rate range (30Hz - 120Hz+)
                if (Build.VERSION.SDK_INT >= 34) {
                    try {
                        val fieldMin = WindowManager.LayoutParams::class.java.getField("preferredMinDisplayRefreshRate")
                        val fieldMax = WindowManager.LayoutParams::class.java.getField("preferredMaxDisplayRefreshRate")
                        fieldMin.setFloat(params, 30f)
                        fieldMax.setFloat(params, maxOf(60f, bestMode.refreshRate))
                    } catch (_: Throwable) {}
                }
            }

            window.attributes = params
        } catch (_: Exception) {}
    }
}

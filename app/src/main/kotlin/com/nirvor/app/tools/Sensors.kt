package com.nirvor.app.tools

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Latest values of one sensor, as Compose state. */
class SensorReading {
    var values by mutableStateOf<FloatArray?>(null)
    var accuracy by mutableIntStateOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
    var available by mutableStateOf(true)
}

fun hasSensor(context: Context, type: Int): Boolean =
    context.getSystemService(SensorManager::class.java)?.getDefaultSensor(type) != null

/**
 * Listens to a sensor while the screen is shown and the app is in front.
 * [smoothing] (0..1, 1 = none) evens out shaky readings.
 */
@Composable
fun rememberSensor(
    type: Int,
    smoothing: Float = 1f,
    delay: Int = SensorManager.SENSOR_DELAY_GAME,
): SensorReading {
    val context = LocalContext.current
    val reading = remember(type) { SensorReading() }
    LifecycleResumeEffect(type) {
        val sm = context.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(type)
        reading.available = sensor != null
        var last: FloatArray? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                val prev = last
                val next = if (prev == null || prev.size != v.size || smoothing >= 1f) {
                    v.clone()
                } else {
                    FloatArray(v.size) { i -> prev[i] + smoothing * (v[i] - prev[i]) }
                }
                last = next
                reading.values = next
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                reading.accuracy = accuracy
            }
        }
        if (sm != null && sensor != null) sm.registerListener(listener, sensor, delay)
        onPauseOrDispose { sm?.unregisterListener(listener) }
    }
    return reading
}

/**
 * Gravity in phone coordinates (x right, y up the screen, z out of the screen), smoothed.
 * Uses the gravity sensor, or the accelerometer on phones without one.
 */
@Composable
fun rememberGravity(smoothing: Float = 0.18f): SensorReading {
    val context = LocalContext.current
    val type = remember {
        if (hasSensor(context, Sensor.TYPE_GRAVITY)) Sensor.TYPE_GRAVITY else Sensor.TYPE_ACCELEROMETER
    }
    return rememberSensor(type, smoothing)
}

/** Keeps the screen awake while the tool is open. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Small settings store for the tools (calibration, history). */
fun toolPrefs(context: Context) = context.getSharedPreferences("nirvor_tools", Context.MODE_PRIVATE)

package com.nirvor.app.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume

/** Current conditions from Open-Meteo. */
data class WeatherNow(
    val temp: Double,
    val code: Int,
    val cloud: Int?,
    val wind: Double?,
    val precip: Double?,
)

/** A search result from Open-Meteo's place search. */
data class Place(val name: String, val region: String, val lat: Double, val lon: Double)

/** Real weather from Open-Meteo (free, no API key) and the phone's location. */
object WeatherService {

    private fun get(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun fetch(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,cloud_cover,wind_speed_10m,precipitation&timezone=auto",
            lat,
            lon,
        )
        val body = get(url) ?: return@withContext null
        try {
            val c = JSONObject(body).getJSONObject("current")
            WeatherNow(
                temp = c.getDouble("temperature_2m"),
                code = c.getInt("weather_code"),
                cloud = if (c.has("cloud_cover") && !c.isNull("cloud_cover")) c.getInt("cloud_cover") else null,
                wind = if (c.has("wind_speed_10m") && !c.isNull("wind_speed_10m")) c.getDouble("wind_speed_10m") else null,
                precip = if (c.has("precipitation") && !c.isNull("precipitation")) c.getDouble("precipitation") else null,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Returns null when the search could not reach the internet. */
    suspend fun searchPlaces(query: String): List<Place>? = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val body = get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=en&format=json")
            ?: return@withContext null
        try {
            val json = JSONObject(body)
            if (!json.has("results")) return@withContext emptyList()
            val arr = json.getJSONArray("results")
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val region = listOf(o.optString("admin1", ""), o.optString("country", ""))
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                Place(o.getString("name"), region, o.getDouble("latitude"), o.getDouble("longitude"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** WMO weather code -> short English text like the reference ("Mostly Clear"). */
    fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"
        45, 48 -> "Foggy"
        51, 53, 55, 56, 57 -> "Drizzle"
        61, 63, 65, 66, 67 -> "Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Showers"
        85, 86 -> "Snow Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "—"
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /**
     * Caller must hold ACCESS_COARSE_LOCATION. Asks every available provider (fused, network and
     * GPS) at the same time and takes the first answer, so it also works on phones where
     * "Google location accuracy" is off and only GPS is enabled.
     */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }
        val providers = candidates.filter { p ->
            try {
                p in lm.allProviders && lm.isProviderEnabled(p)
            } catch (e: Exception) {
                false
            }
        }

        val last = providers.mapNotNull { p ->
            try {
                lm.getLastKnownLocation(p)
            } catch (e: Exception) {
                null
            }
        }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 3 * 60 * 60 * 1000L) return last

        val live = providers.filter { it != LocationManager.PASSIVE_PROVIDER }
        if (live.isEmpty()) return last

        val fresh = withTimeoutOrNull(25_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listeners = mutableListOf<LocationListener>()
                fun stopAll() = listeners.forEach {
                    try {
                        lm.removeUpdates(it)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                for (p in live) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            stopAll()
                            if (cont.isActive) cont.resume(location)
                        }

                        override fun onProviderEnabled(provider: String) {}

                        override fun onProviderDisabled(provider: String) {}

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    }
                    try {
                        lm.requestLocationUpdates(p, 0L, 0f, listener, Looper.getMainLooper())
                        listeners.add(listener)
                    } catch (e: Exception) {
                        // provider refused; try the others
                    }
                }
                if (listeners.isEmpty() && cont.isActive) cont.resume(null)
                cont.invokeOnCancellation { stopAll() }
            }
        }
        return fresh ?: last
    }
}

package com.nirvor.app.data

import android.content.Context

data class CachedWeather(
    val temp: Double,
    val code: Int,
    val at: Long,
    val cloud: Int?,
    val wind: Double?,
    val precip: Double?,
)

/** A place chosen by the user for weather when the phone's location is not used. */
data class SavedPlace(val name: String, val lat: Double, val lon: Double)

/** Small key-value settings: profile name, onboarding flags, weather cache and weather place. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("nirvor_prefs", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(value) {
            sp.edit().putString("name", value).apply()
        }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(value) {
            sp.edit().putBoolean("onboarded", value).apply()
        }

    var askedLocation: Boolean
        get() = sp.getBoolean("asked_location", false)
        set(value) {
            sp.edit().putBoolean("asked_location", value).apply()
        }

    // ---------------------------------------------------------------- weather cache

    fun weather(): CachedWeather? {
        if (!sp.contains("w_temp")) return null
        return CachedWeather(
            temp = sp.getFloat("w_temp", 0f).toDouble(),
            code = sp.getInt("w_code", 0),
            at = sp.getLong("w_at", 0L),
            cloud = if (sp.contains("w_cloud")) sp.getInt("w_cloud", 0) else null,
            wind = if (sp.contains("w_wind")) sp.getFloat("w_wind", 0f).toDouble() else null,
            precip = if (sp.contains("w_precip")) sp.getFloat("w_precip", 0f).toDouble() else null,
        )
    }

    fun saveWeather(temp: Double, code: Int, cloud: Int?, wind: Double?, precip: Double?, at: Long) {
        sp.edit().apply {
            putFloat("w_temp", temp.toFloat())
            putInt("w_code", code)
            putLong("w_at", at)
            if (cloud != null) putInt("w_cloud", cloud) else remove("w_cloud")
            if (wind != null) putFloat("w_wind", wind.toFloat()) else remove("w_wind")
            if (precip != null) putFloat("w_precip", precip.toFloat()) else remove("w_precip")
        }.apply()
    }

    fun clearWeather() {
        sp.edit().remove("w_temp").remove("w_code").remove("w_at").remove("w_cloud")
            .remove("w_wind").remove("w_precip").apply()
    }

    // ---------------------------------------------------------------- places

    /** Last place the phone reported (used for sunrise/sunset and as a weather fallback). */
    fun lastCoords(): Pair<Double, Double>? {
        if (!sp.contains("loc_lat")) return null
        return getDouble("loc_lat") to getDouble("loc_lon")
    }

    fun saveCoords(lat: Double, lon: Double) {
        sp.edit().putLong("loc_lat", lat.toRawBits()).putLong("loc_lon", lon.toRawBits()).apply()
    }

    /** City chosen by hand in More → weather location. Null means "use the phone's location". */
    fun city(): SavedPlace? {
        val n = sp.getString("city_name", null) ?: return null
        return SavedPlace(n, getDouble("city_lat"), getDouble("city_lon"))
    }

    fun setCity(place: SavedPlace?) {
        sp.edit().apply {
            if (place == null) {
                remove("city_name").remove("city_lat").remove("city_lon")
            } else {
                putString("city_name", place.name)
                putLong("city_lat", place.lat.toRawBits())
                putLong("city_lon", place.lon.toRawBits())
            }
        }.apply()
    }

    private fun getDouble(key: String): Double = Double.fromBits(sp.getLong(key, 0L))
}

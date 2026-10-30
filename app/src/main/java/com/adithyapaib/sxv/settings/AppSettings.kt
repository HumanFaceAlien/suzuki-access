package com.adithyapaib.sxv.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.adithyapaib.sxv.ble.SuzukiCommandPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DistanceUnit(val distance: String, val speed: String, val perKm: Double) {
    KILOMETERS("km", "km/h", 1.0),
    MILES("mi", "mph", 0.621371);

    /** Temperature follows the distance unit: °C with kilometres, °F with miles. */
    val temperature: String get() = if (this == MILES) "°F" else "°C"

    fun temperatureFromCelsius(celsius: Int): Int =
        if (this == MILES) Math.round(celsius * 9.0 / 5.0 + 32.0).toInt() else celsius

    companion object {
        fun fromName(name: String?): DistanceUnit = entries.firstOrNull { it.name == name } ?: KILOMETERS
    }
}

/** The cluster this phone connects to; [name] is its advertised name, e.g. `SBM01xxxxxx`. */
data class PairedBike(val address: String, val name: String)

/** User preferences, persisted in SharedPreferences. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _unit = MutableStateFlow(DistanceUnit.fromName(prefs.getString(KEY_UNIT, null)))
    val unit: StateFlow<DistanceUnit> = _unit.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    /** As typed; blank means [DEFAULT_RIDER_NAME]. */
    private val _riderName = MutableStateFlow(prefs.getString(KEY_RIDER_NAME, "").orEmpty())
    val riderName: StateFlow<String> = _riderName.asStateFlow()

    fun setUnit(unit: DistanceUnit) {
        _unit.value = unit
        prefs.edit().putString(KEY_UNIT, unit.name).apply()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun setRiderName(name: String) {
        val cleaned = name.filter { it.code in 32..126 }.take(SuzukiCommandPacket.MAX_IDENTITY_NAME_LENGTH)
        _riderName.value = cleaned
        prefs.edit().putString(KEY_RIDER_NAME, cleaned).apply()
    }

    companion object {
        private const val PREFS_NAME = "sxv_settings"
        private const val KEY_UNIT = "unit"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_RIDER_NAME = "rider_name"
        private const val KEY_BIKE_ADDRESS = "bike_address"
        private const val KEY_BIKE_NAME = "bike_name"

        /** Sent when no rider name is set. */
        const val DEFAULT_RIDER_NAME = "SXV"

        /**
         * Name for the identity frame, which the cluster shows as its welcome
         * greeting on connect. Upper case, like the official app's frames.
         */
        fun identityName(context: Context): String =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_RIDER_NAME, null)
                ?.trim()
                ?.uppercase(java.util.Locale.ROOT)
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_RIDER_NAME

        /**
         * The bike remembered from the first connection that streamed telemetry,
         * or null before then (or after Forget bike). Once set, no other cluster
         * is connected to.
         */
        fun pairedBike(context: Context): PairedBike? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val address = prefs.getString(KEY_BIKE_ADDRESS, null) ?: return null
            return PairedBike(address, prefs.getString(KEY_BIKE_NAME, null).orEmpty())
        }

        fun savePairedBike(context: Context, bike: PairedBike?) {
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (bike == null) {
                editor.remove(KEY_BIKE_ADDRESS).remove(KEY_BIKE_NAME)
            } else {
                editor.putString(KEY_BIKE_ADDRESS, bike.address).putString(KEY_BIKE_NAME, bike.name)
            }
            editor.apply()
        }
    }
}

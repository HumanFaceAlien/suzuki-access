package com.adithyapaib.sxv.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
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

/** User preferences and recent display messages, persisted in SharedPreferences. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("sxv_settings", Context.MODE_PRIVATE)

    private val _unit = MutableStateFlow(DistanceUnit.fromName(prefs.getString(KEY_UNIT, null)))
    val unit: StateFlow<DistanceUnit> = _unit.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _recentMessages = MutableStateFlow(
        prefs.getString(KEY_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() }
    )
    val recentMessages: StateFlow<List<String>> = _recentMessages.asStateFlow()

    fun setUnit(unit: DistanceUnit) {
        _unit.value = unit
        prefs.edit().putString(KEY_UNIT, unit.name).apply()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        _keepScreenOn.value = enabled
        prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    fun addRecentMessage(message: String) {
        val cleaned = message.trim()
        if (cleaned.isEmpty()) return
        persistRecent((listOf(cleaned) + _recentMessages.value.filterNot { it == cleaned }).take(MAX_RECENT))
    }

    fun clearRecentMessages() = persistRecent(emptyList())

    private fun persistRecent(messages: List<String>) {
        _recentMessages.value = messages
        prefs.edit().putString(KEY_RECENT, messages.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY_UNIT = "unit"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_RECENT = "recent_messages"
        const val MAX_RECENT = 5
    }
}

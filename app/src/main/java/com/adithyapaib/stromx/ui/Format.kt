package com.adithyapaib.stromx.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import com.adithyapaib.stromx.settings.DistanceUnit
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Telemetry older than this is shown dimmed with a "no update" warning. Frames normally arrive every ~5 s. */
const val STALE_AFTER_MS = 15_000L

fun formatWhole(value: Int): String = NumberFormat.getIntegerInstance().format(value)

fun formatOdometer(km: Int, unit: DistanceUnit): String = formatWhole((km * unit.perKm).roundToInt())

fun formatTrip(km: Double, unit: DistanceUnit): String =
    String.format(Locale.getDefault(), "%,.1f", km * unit.perKm)

/** Rupees with grouping: ₹1,240 or, with [decimals], ₹104.60. */
fun formatRupees(amount: Double, decimals: Int = 0): String =
    "₹" + String.format(Locale.getDefault(), "%,.${decimals}f", amount)

/** Axis-friendly rupees: ₹0, ₹800, ₹1.2K. */
fun formatRupeesCompact(amount: Double): String = when {
    amount >= 100_000 -> "₹" + String.format(Locale.getDefault(), "%.1fL", amount / 100_000)
    amount >= 1_000 -> "₹" + String.format(Locale.getDefault(), "%.1fK", amount / 1_000).replace(".0K", "K")
    else -> "₹" + String.format(Locale.getDefault(), "%.0f", amount)
}

/** Trip distance without thousands grouping, for the roadbook's fixed-width distance column. */
fun formatTripPlain(km: Double, unit: DistanceUnit): String =
    String.format(Locale.getDefault(), "%.1f", km * unit.perKm)

fun speedIn(unit: DistanceUnit, kmh: Int): Int = (kmh * unit.perKm).roundToInt()

fun formatAge(ageMs: Long): String {
    val seconds = ageMs / 1000
    return when {
        seconds < 2 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> "${seconds / 3600}h ago"
    }
}

/** Wall clock that ticks every [intervalMs], so age labels keep counting between telemetry frames. */
@Composable
fun rememberNowMs(intervalMs: Long = 1_000L): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(intervalMs) {
        while (true) {
            delay(intervalMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

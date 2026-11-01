package com.adithyapaib.sxv.ride

import java.util.Locale

/**
 * One recorded ride: when it ran, what the odometer said, and how hard it was ridden.
 *
 * [endedAtMs] is null while the ride is still running, which is how an in-progress ride is
 * stored — the app can restart mid-ride and pick the same record back up.
 *
 * Fuel use is an *estimate*: the cluster link carries no fuel-flow data, so at save time the
 * distance is divided by the rider's own recent economy (see [RideMath.finish]).
 */
data class RideRecord(
    val id: Long,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    /** Null until the bike has reported a reading while the ride was running. */
    val startOdometerKm: Int?,
    val endOdometerKm: Int?,
    /** Highest speed seen over the link during the ride, km/h. */
    val maxSpeedKph: Int,
    /** Gauge at the moment the ride started, and at the end; null when not linked. */
    val startFuelBars: Int?,
    val endFuelBars: Int?,
    /** Distance divided by the recent measured economy; null with no economy logged yet. */
    val fuelUsedLitres: Double?,
    /** [fuelUsedLitres] priced at the log's average; null with no fill-ups logged. */
    val fuelCost: Double?
) {
    val isActive: Boolean get() = endedAtMs == null

    /** Odometer distance, when both ends are known. Null if the bike never reported. */
    val distanceKm: Double?
        get() = if (startOdometerKm != null && endOdometerKm != null && endOdometerKm >= startOdometerKm) {
            (endOdometerKm - startOdometerKm).toDouble()
        } else {
            null
        }

    /** Wall-clock length of the ride; null while it is running. */
    val durationMs: Long?
        get() = endedAtMs?.let { it - startedAtMs }

    /** Distance over time, km/h; null when either half of the sum is unknown. */
    val avgSpeedKph: Double?
        get() {
            val distance = distanceKm ?: return null
            val duration = durationMs ?: return null
            val hours = duration / 3_600_000.0
            return if (hours > 0) distance / hours else null
        }
}

object RideMath {

    /** Closes an active ride: stamps the end, then derives distance, speed and fuel use. */
    fun finish(
        ride: RideRecord,
        endedAtMs: Long,
        endOdometerKm: Int?,
        endFuelBars: Int?,
        kmPerLitre: Double?,
        pricePerLitre: Double?
    ): RideRecord {
        val closed = ride.copy(
            endedAtMs = endedAtMs,
            endOdometerKm = endOdometerKm,
            endFuelBars = endFuelBars,
            fuelUsedLitres = null,
            fuelCost = null
        )
        val distance = closed.distanceKm
        val litres = if (distance != null && kmPerLitre != null && kmPerLitre > 0) distance / kmPerLitre else null
        return closed.copy(
            fuelUsedLitres = litres,
            fuelCost = if (litres != null && pricePerLitre != null && pricePerLitre > 0) litres * pricePerLitre else null
        )
    }

    /** Newest ride first, so the list and the active ride read top-down. */
    fun newestFirst(rides: List<RideRecord>): List<RideRecord> =
        rides.sortedByDescending { it.startedAtMs }

    /** Running total across every saved ride; live rides count only what they've done so far. */
    data class Totals(val rides: Int, val distanceKm: Double, val fuelLitres: Double)

    fun totals(rides: List<RideRecord>): Totals = Totals(
        rides = rides.count { !it.isActive },
        distanceKm = rides.sumOf { it.distanceKm ?: 0.0 },
        fuelLitres = rides.sumOf { it.fuelUsedLitres ?: 0.0 }
    )

    /** "45s", "12m", "1h 23m" — the duration as the rider reads it. */
    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %02dm", hours, minutes)
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}

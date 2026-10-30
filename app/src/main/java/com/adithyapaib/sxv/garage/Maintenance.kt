package com.adithyapaib.sxv.garage

import kotlin.math.max

/**
 * A recurring job, due every [intervalKm] and/or every [intervalDays], counted from when it
 * was last done. At least one interval is set.
 */
data class MaintenanceItem(
    val id: Long,
    val name: String,
    val intervalKm: Int?,
    val intervalDays: Int?,
    val lastDoneKm: Int?,
    val lastDoneAtMs: Long?
)

enum class DueState {
    /** Never marked done, so there is nothing to count from. */
    NotStarted,
    Ok,
    Soon,
    Due
}

data class MaintenanceStatus(
    val item: MaintenanceItem,
    /** Negative when overdue. Null when there is no km interval or no odometer yet. */
    val remainingKm: Int?,
    /** Negative when overdue. Null when there is no day interval. */
    val remainingDays: Int?,
    /** How much of the interval is used up, by whichever limit is closer; may exceed 1. */
    val used: Float,
    val state: DueState
)

object MaintenanceMath {
    /** "Soon" kicks in at 90% of the interval, or this close in absolute terms. */
    const val SOON_FRACTION = 0.9f
    const val SOON_KM = 300
    const val SOON_DAYS = 7
    private const val DAY_MS = 86_400_000L

    fun status(item: MaintenanceItem, odometerKm: Int?, nowMs: Long): MaintenanceStatus {
        val kmBase = item.lastDoneKm
        val dayBase = item.lastDoneAtMs
        val remainingKm = if (item.intervalKm != null && kmBase != null && odometerKm != null) {
            kmBase + item.intervalKm - odometerKm
        } else {
            null
        }
        val remainingDays = if (item.intervalDays != null && dayBase != null) {
            val elapsedDays = ((nowMs - dayBase) / DAY_MS).toInt()
            item.intervalDays - elapsedDays
        } else {
            null
        }
        val kmUsed = if (remainingKm != null && item.intervalKm != null && item.intervalKm > 0) {
            (item.intervalKm - remainingKm).toFloat() / item.intervalKm
        } else {
            0f
        }
        val dayUsed = if (remainingDays != null && item.intervalDays != null && item.intervalDays > 0) {
            (item.intervalDays - remainingDays).toFloat() / item.intervalDays
        } else {
            0f
        }
        val used = max(kmUsed, dayUsed).coerceAtLeast(0f)
        val state = when {
            remainingKm == null && remainingDays == null -> DueState.NotStarted
            (remainingKm != null && remainingKm <= 0) || (remainingDays != null && remainingDays <= 0) -> DueState.Due
            used >= SOON_FRACTION ||
                (remainingKm != null && remainingKm <= SOON_KM) ||
                (remainingDays != null && remainingDays <= SOON_DAYS) -> DueState.Soon
            else -> DueState.Ok
        }
        return MaintenanceStatus(item, remainingKm, remainingDays, used, state)
    }
}

/**
 * Quick-add templates. The intervals are common starting points, not Suzuki's schedule: the
 * rider sets the real values from the service book before saving.
 */
data class MaintenancePreset(val name: String, val intervalKm: Int?, val intervalDays: Int?)

val MaintenancePresets = listOf(
    MaintenancePreset("Engine oil and filter", 3_000, 180),
    MaintenancePreset("Chain clean and lube", 500, null),
    MaintenancePreset("Periodic service", 6_000, 365),
    MaintenancePreset("Air filter", 6_000, null),
    MaintenancePreset("Brake fluid", null, 730),
    MaintenancePreset("Tyre pressure check", null, 14)
)

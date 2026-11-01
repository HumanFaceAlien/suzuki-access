package com.adithyapaib.stromx.ride

import com.adithyapaib.stromx.ble.BleConnectionState
import com.adithyapaib.stromx.ble.VStromTelemetry

/** What happened at one point of the ride. Every event comes from real telemetry or a real link change. */
enum class RoadbookEvent {
    START,
    CONNECTED,
    WARMED_UP,
    STOPPED,
    MOVING,
    FUEL_DROP,
    LINK_LOST
}

/**
 * One roadbook row. Values are stored raw (km, °C, bars) so the UI can format them in the
 * rider's units; fields that do not apply to the event are null.
 */
data class RoadbookEntry(
    val id: Long,
    val event: RoadbookEvent,
    val atMs: Long,
    val tripKm: Double?,
    val odometerKm: Int? = null,
    val engineTempC: Int? = null,
    val fuelBars: Int? = null,
    val speedKmh: Int? = null
)

/**
 * Turns the telemetry stream and link changes into roadbook rows, newest first.
 *
 * Rules, all from observed frames (one every 5 s):
 * - the first valid frame writes START;
 * - engine temperature reaching [WARM_C] writes WARMED_UP once per session;
 * - [STOP_FRAMES] consecutive zero-speed frames after moving write STOPPED, so a single
 *   zero reading in traffic does not log a stop;
 * - moving again after a stop writes MOVING;
 * - a drop in valid fuel bars writes FUEL_DROP.
 */
class RoadbookRecorder(private val clock: () -> Long = System::currentTimeMillis) {

    private val rows = ArrayDeque<RoadbookEntry>()
    private var nextId = 1L
    private var started = false
    private var warmedUp = false
    private var moving = false
    private var stopLogged = true
    private var zeroFrames = 0
    private var lastFuelBars: Int? = null
    private var lastConnection = BleConnectionState.DISCONNECTED
    private var lastTripKm: Double? = null

    val entries: List<RoadbookEntry> get() = rows.toList()

    fun onTelemetry(t: VStromTelemetry): List<RoadbookEntry> {
        lastTripKm = t.tripA
        if (!started) {
            started = true
            add(RoadbookEvent.START, t.tripA, odometerKm = t.odometer)
        }

        val temp = t.engineTempC
        if (!warmedUp && temp != null && temp >= WARM_C) {
            warmedUp = true
            add(RoadbookEvent.WARMED_UP, t.tripA, engineTempC = temp)
        }

        if (t.speed > 0) {
            zeroFrames = 0
            if (!moving) {
                moving = true
                // Only log "riding" after a logged stop, not on the very first frame.
                if (stopLogged && rows.any { it.event == RoadbookEvent.STOPPED }) {
                    add(RoadbookEvent.MOVING, t.tripA, speedKmh = t.speed)
                }
                stopLogged = false
            }
        } else {
            zeroFrames++
            if (moving && zeroFrames >= STOP_FRAMES) {
                moving = false
                stopLogged = true
                add(RoadbookEvent.STOPPED, t.tripA)
            }
        }

        if (t.isFuelValid) {
            val previous = lastFuelBars
            if (previous != null && t.fuelBars < previous) {
                add(RoadbookEvent.FUEL_DROP, t.tripA, fuelBars = t.fuelBars)
            }
            lastFuelBars = t.fuelBars
        }
        return entries
    }

    fun onConnection(state: BleConnectionState): List<RoadbookEntry> {
        val previous = lastConnection
        lastConnection = state
        when {
            state == BleConnectionState.CONNECTED && previous != BleConnectionState.CONNECTED ->
                add(RoadbookEvent.CONNECTED, lastTripKm)
            previous == BleConnectionState.CONNECTED && state != BleConnectionState.CONNECTED ->
                add(RoadbookEvent.LINK_LOST, lastTripKm)
        }
        return entries
    }

    private fun add(
        event: RoadbookEvent,
        tripKm: Double?,
        odometerKm: Int? = null,
        engineTempC: Int? = null,
        fuelBars: Int? = null,
        speedKmh: Int? = null
    ) {
        rows.addFirst(
            RoadbookEntry(nextId++, event, clock(), tripKm, odometerKm, engineTempC, fuelBars, speedKmh)
        )
        while (rows.size > MAX_ROWS) rows.removeLast()
    }

    companion object {
        const val WARM_C = 80
        const val STOP_FRAMES = 2
        const val MAX_ROWS = 100
    }
}

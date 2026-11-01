package com.adithyapaib.stromx.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adithyapaib.stromx.ble.VStromTelemetry
import com.adithyapaib.stromx.ride.RideMath
import com.adithyapaib.stromx.ride.RideRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The fuel bars rose by at least two while linked: almost certainly a fill-up worth logging. */
data class FillUpPrompt(val odometerKm: Int, val fuelBars: Int, val atMs: Long)

/** Fuel log and service reminders, driven by the bike's own odometer and fuel bars. */
class GarageViewModel(application: Application) : AndroidViewModel(application) {

    private val store = GarageStore(application.filesDir)
    private val notifier = ServiceNotifier(application)
    private val data = MutableStateFlow(store.load())

    val fuel: StateFlow<List<FuelRecord>> = data
        .map { FuelMath.sorted(it.fuel).asReversed() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FuelMath.sorted(data.value.fuel).asReversed())

    val odometerKm: StateFlow<Int?> = data
        .map { it.lastOdometerKm }
        .stateIn(viewModelScope, SharingStarted.Eagerly, data.value.lastOdometerKm)

    /** Every recorded ride, newest first; an entry with a null end is still running. */
    val rides: StateFlow<List<RideRecord>> = data
        .map { RideMath.newestFirst(it.rides) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RideMath.newestFirst(data.value.rides))

    /** Ticks when the app comes back to the foreground, so day-based countdowns move on. */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    /** Every item with its countdown, most urgent first. */
    val service: StateFlow<List<MaintenanceStatus>> = combine(data, clock) { d, now -> statuses(d, now) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, statuses(data.value, clock.value))

    /** One writer at a time, in order, so a quick second save can never land before the first. */
    private val saves = Dispatchers.IO.limitedParallelism(1)

    private val _fillUpPrompt = MutableStateFlow<FillUpPrompt?>(null)
    val fillUpPrompt: StateFlow<FillUpPrompt?> = _fillUpPrompt.asStateFlow()

    /** The gauge as last seen live; null while the bike has not reported a valid reading. */
    private val liveBars = MutableStateFlow<Int?>(null)

    /** Fuel left and range, re-estimated whenever the odometer, gauge or log changes. */
    val estimate: StateFlow<FuelEstimate?> = combine(data, liveBars) { d, bars -> estimateFor(d, bars) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, estimateFor(data.value, null))

    /** The rider's tank size, or null while the default is in use. */
    val tankLitres: StateFlow<Double?> = data
        .map { it.tankLitres }
        .stateIn(viewModelScope, SharingStarted.Eagerly, data.value.tankLitres)

    private fun estimateFor(d: GarageData, bars: Int?) =
        FuelEstimator.estimate(d.fuel, d.barDrops, d.lastOdometerKm, bars, d.tankLitres)

    // Gauge tracking. Fuel sloshes, so a step only counts once it holds for STEADY_FRAMES.
    private var steadyBars: Int? = null
    private var pendingBars: Int? = null
    private var pendingFrames = 0

    fun onTelemetry(t: VStromTelemetry) {
        if (t.odometer > 0 && t.odometer != data.value.lastOdometerKm) {
            update { it.copy(lastOdometerKm = t.odometer) }
            notifyNewlyDue()
        }
        trackRide(t)
        if (t.isFuelValid) {
            liveBars.value = t.fuelBars
            trackGauge(t.fuelBars, t.odometer)
            notifyLowRange()
        }
    }

    /** While a ride runs: catch the start odometer on the first frame and keep the top speed. */
    private fun trackRide(t: VStromTelemetry) {
        val active = data.value.rides.firstOrNull { it.endedAtMs == null } ?: return
        val start = active.startOdometerKm ?: t.odometer.takeIf { it > 0 }
        val maxSpeed = maxOf(active.maxSpeedKph, t.speed)
        if (start == active.startOdometerKm && maxSpeed == active.maxSpeedKph) return
        update { d ->
            d.copy(
                rides = d.rides.map { r ->
                    if (r.id == active.id) r.copy(startOdometerKm = start, maxSpeedKph = maxSpeed) else r
                }
            )
        }
    }

    private fun trackGauge(bars: Int, odometerKm: Int) {
        val steady = steadyBars
        if (steady == null) {
            steadyBars = bars
            return
        }
        if (bars == steady) {
            pendingBars = null
            pendingFrames = 0
            return
        }
        if (bars == pendingBars) pendingFrames++ else {
            pendingBars = bars
            pendingFrames = 1
        }
        if (pendingFrames < STEADY_FRAMES) return
        when {
            bars < steady -> update {
                it.copy(barDrops = (it.barDrops + BarDrop(odometerKm, bars, System.currentTimeMillis())).takeLast(MAX_DROPS))
            }
            bars - steady >= 2 -> _fillUpPrompt.value = FillUpPrompt(odometerKm, bars, System.currentTimeMillis())
            // A single-bar rise without a fill is the float settling; just follow it.
        }
        steadyBars = bars
        pendingBars = null
        pendingFrames = 0
    }

    fun setTankLitres(litres: Double) = update { it.copy(tankLitres = litres) }

    /** One low-range alert per tank, keyed by the last full fill, once range is known. */
    private fun notifyLowRange() {
        // Computed here rather than read from [estimate], which updates a beat later.
        val e = estimateFor(data.value, liveBars.value) ?: return
        val range = e.rangeKm ?: return
        if (range > LOW_RANGE_KM) return
        val tank = FuelMath.sorted(data.value.fuel).lastOrNull { it.fullTank }?.id ?: 0L
        val key = "fuel:$tank"
        if (key in data.value.notified) return
        notifier.notifyLowRange(range, e.litres)
        update { it.copy(notified = it.notified + key) }
    }

    fun addFuel(litres: Double, cost: Double, odometerKm: Int, fullTank: Boolean) {
        update {
            it.copy(
                fuel = it.fuel + FuelRecord(it.nextId, System.currentTimeMillis(), odometerKm, litres, cost, fullTank),
                nextId = it.nextId + 1
            )
        }
        _fillUpPrompt.value = null
    }

    fun deleteFuel(id: Long) = update { it.copy(fuel = it.fuel.filterNot { r -> r.id == id }) }

    // Rides. The running ride is just a record with a null end, persisted like any other,
    // so closing the app mid-ride and reopening picks the same ride back up.
    fun startRide() {
        if (data.value.rides.any { it.endedAtMs == null }) return
        update {
            it.copy(
                rides = it.rides + RideRecord(
                    id = it.nextId,
                    startedAtMs = System.currentTimeMillis(),
                    endedAtMs = null,
                    // Odometer and gauge fill in from the first live frame if the bike hasn't reported yet.
                    startOdometerKm = it.lastOdometerKm,
                    endOdometerKm = null,
                    maxSpeedKph = 0,
                    startFuelBars = liveBars.value,
                    endFuelBars = null,
                    fuelUsedLitres = null,
                    fuelCost = null
                ),
                nextId = it.nextId + 1
            )
        }
    }

    /** Stamps the end and derives distance, average speed and the fuel estimate. */
    fun stopRide() {
        val now = System.currentTimeMillis()
        update { d ->
            val active = d.rides.firstOrNull { it.endedAtMs == null } ?: return@update d
            val finished = RideMath.finish(
                ride = active,
                endedAtMs = now,
                endOdometerKm = d.lastOdometerKm,
                endFuelBars = liveBars.value,
                kmPerLitre = FuelEstimator.recentKmPerLitre(d.fuel),
                pricePerLitre = FuelMath.summary(d.fuel, now).avgPricePerLitre
            )
            d.copy(rides = d.rides.map { if (it.id == active.id) finished else it })
        }
    }

    /** Ends the running ride without keeping it. */
    fun discardRide() = update { it.copy(rides = it.rides.filterNot { r -> r.endedAtMs == null }) }

    fun deleteRide(id: Long) = update { it.copy(rides = it.rides.filterNot { r -> r.id == id }) }

    fun dismissFillUp() {
        _fillUpPrompt.value = null
    }

    fun addItem(name: String, intervalKm: Int?, intervalDays: Int?, lastDoneKm: Int?, lastDoneAtMs: Long?) {
        update {
            it.copy(
                items = it.items + MaintenanceItem(it.nextId, name.trim(), intervalKm, intervalDays, lastDoneKm, lastDoneAtMs),
                nextId = it.nextId + 1
            )
        }
        notifyNewlyDue()
    }

    /** Resets the countdown from today and the current odometer. */
    fun markDone(id: Long) {
        val odometer = data.value.lastOdometerKm
        update {
            it.copy(items = it.items.map { item ->
                if (item.id == id) item.copy(lastDoneKm = odometer ?: item.lastDoneKm, lastDoneAtMs = System.currentTimeMillis()) else item
            })
        }
    }

    fun deleteItem(id: Long) = update { it.copy(items = it.items.filterNot { item -> item.id == id }) }

    /** Called when the app returns to the foreground, so day-based reminders fire without a ride. */
    fun refresh() {
        clock.value = System.currentTimeMillis()
        notifyNewlyDue()
    }

    private fun statuses(d: GarageData, now: Long): List<MaintenanceStatus> {
        return d.items
            .map { MaintenanceMath.status(it, d.lastOdometerKm, now) }
            .sortedWith(compareBy({ urgency(it.state) }, { it.remainingKm ?: Int.MAX_VALUE }))
    }

    private fun urgency(state: DueState) = when (state) {
        DueState.Due -> 0
        DueState.Soon -> 1
        DueState.Ok -> 2
        DueState.NotStarted -> 3
    }

    /** Each item notifies once per cycle per state: once on "soon", once on "due". */
    private fun notifyNewlyDue() {
        val current = data.value
        val now = System.currentTimeMillis()
        val fresh = current.items
            .map { MaintenanceMath.status(it, current.lastOdometerKm, now) }
            .filter { it.state == DueState.Soon || it.state == DueState.Due }
            .filter { key(it) !in current.notified }
        if (fresh.isEmpty()) return
        fresh.forEach(notifier::notify)
        update { it.copy(notified = it.notified + fresh.map(::key)) }
    }

    private fun key(status: MaintenanceStatus) =
        "${status.item.id}:${status.item.lastDoneKm}:${status.item.lastDoneAtMs}:${status.state}"

    private companion object {
        /** 3 frames = 15 s of the same new reading. */
        const val STEADY_FRAMES = 3
        const val MAX_DROPS = 300
        const val LOW_RANGE_KM = 40
    }

    private fun update(change: (GarageData) -> GarageData) {
        val next = change(data.value)
        data.value = next
        viewModelScope.launch(saves) { store.save(next) }
    }
}

package com.adithyapaib.sxv.garage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adithyapaib.sxv.ble.VStromTelemetry
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

    /** Ticks when the app comes back to the foreground, so day-based countdowns move on. */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    /** Every item with its countdown, most urgent first. */
    val service: StateFlow<List<MaintenanceStatus>> = combine(data, clock) { d, now -> statuses(d, now) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, statuses(data.value, clock.value))

    /** One writer at a time, in order, so a quick second save can never land before the first. */
    private val saves = Dispatchers.IO.limitedParallelism(1)

    private val _fillUpPrompt = MutableStateFlow<FillUpPrompt?>(null)
    val fillUpPrompt: StateFlow<FillUpPrompt?> = _fillUpPrompt.asStateFlow()

    private var lastFuelBars: Int? = null

    fun onTelemetry(t: VStromTelemetry) {
        if (t.isFuelValid) {
            val previous = lastFuelBars
            if (previous != null && t.fuelBars - previous >= 2) {
                _fillUpPrompt.value = FillUpPrompt(t.odometer, t.fuelBars, System.currentTimeMillis())
            }
            lastFuelBars = t.fuelBars
        }
        if (t.odometer > 0 && t.odometer != data.value.lastOdometerKm) {
            update { it.copy(lastOdometerKm = t.odometer) }
            notifyNewlyDue()
        }
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

    private fun update(change: (GarageData) -> GarageData) {
        val next = change(data.value)
        data.value = next
        viewModelScope.launch(saves) { store.save(next) }
    }
}

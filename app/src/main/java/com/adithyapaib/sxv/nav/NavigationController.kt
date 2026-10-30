package com.adithyapaib.sxv.nav

import android.content.Context
import android.text.format.DateFormat
import com.adithyapaib.sxv.ble.SuzukiCommandPacket
import com.adithyapaib.sxv.ble.SuzukiDisplaySender
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Sends the current Google Maps step to the cluster as navigation frames.
 *
 * The official app streams nav frames continuously (about 2.7/s) rather than
 * once per change, so the current step is re-sent every [REFRESH_MS] while
 * navigation runs, and an end frame is sent when Maps' notification goes away.
 */
class NavigationController(
    context: Context,
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val send: (ByteArray) -> Boolean,
    private val trace: (String) -> Unit,
    private val vtrace: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("sxv_nav", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    /** Whether Maps steps are sent to the cluster. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val instruction: StateFlow<NavInstruction?> = NavigationBus.instruction
    val listenerConnected: StateFlow<Boolean> = NavigationBus.listenerConnected

    private var showing = false
    private var lastSummary: String? = null
    private var testJob: Job? = null

    fun start() {
        scope.launch {
            combine(NavigationBus.instruction, _enabled) { step, on -> if (on) step else null }
                .collectLatest { step ->
                    if (step == null) {
                        if (showing) endNavigation()
                        return@collectLatest
                    }
                    // collectLatest cancels this loop when Maps posts the next step.
                    while (true) {
                        if (isConnected()) sendStep(step)
                        delay(REFRESH_MS)
                    }
                }
        }
        scope.launch {
            NavigationBus.lastFields.collect { fields ->
                if (fields != null) {
                    vtrace("Maps notification title=\"${fields.title}\" text=\"${fields.text}\" sub=\"${fields.subText}\" views=${fields.views}")
                }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        trace("Google Maps navigation on cluster ${if (enabled) "ON" else "OFF"}")
    }

    /**
     * Shows [maneuverByte] with dummy distances for about [TEST_SECONDS] s, to
     * find out which glyph the V-STROM SX draws for each byte.
     */
    fun sendTest(maneuverByte: Int) {
        testJob?.cancel()
        testJob = scope.launch {
            val packet = SuzukiCommandPacket.buildNavigationPacket(
                maneuver = maneuverByte,
                distanceToManeuver = "0200",
                distanceToManeuverUnit = 'M',
                eta = SuzukiCommandPacket.formatEta(LocalTime.now().plusMinutes(10), twelveHour()),
                remainingDistance = "05.0",
                remainingDistanceUnit = 'K',
            )
            trace("TX nav test, maneuver byte $maneuverByte: ${SuzukiDisplaySender.toHex(packet)}")
            repeat(TEST_SECONDS) {
                if (isConnected()) send(packet)
                delay(REFRESH_MS)
            }
        }
    }

    fun encode(step: NavInstruction): ByteArray {
        val (distance, distanceUnit) = SuzukiCommandPacket.formatDistance(step.distanceMeters)
        val (remaining, remainingUnit) = SuzukiCommandPacket.formatDistance(step.remainingMeters)
        val status = when {
            step.maneuver == Maneuver.ARRIVED -> '5'
            step.rerouting -> '2'
            else -> '1'
        }
        return SuzukiCommandPacket.buildNavigationPacket(
            maneuver = step.maneuver.clusterByte,
            distanceToManeuver = distance,
            distanceToManeuverUnit = distanceUnit,
            eta = SuzukiCommandPacket.formatEta(step.eta, twelveHour()),
            remainingDistance = remaining,
            remainingDistanceUnit = remainingUnit,
            status = status,
        )
    }

    private fun sendStep(step: NavInstruction) {
        val packet = encode(step)
        val summary = "${step.maneuver.label} (byte ${step.maneuver.clusterByte}), " +
            "${step.distanceMeters ?: "?"} m, ${step.remainingMeters ?: "?"} m left, ETA ${step.eta ?: "?"}"
        if (summary != lastSummary) {
            trace("TX nav: $summary \"${step.instruction}\" ${SuzukiDisplaySender.toHex(packet)}")
            lastSummary = summary
        }
        if (send(packet)) showing = true
    }

    private suspend fun endNavigation() {
        val packet = SuzukiCommandPacket.buildNavigationEndPacket()
        trace("TX nav end: ${SuzukiDisplaySender.toHex(packet)}")
        // A second copy in case the first one met a busy link.
        repeat(2) {
            if (isConnected()) send(packet)
            delay(END_RESEND_MS)
        }
        showing = false
        lastSummary = null
    }

    private fun twelveHour(): Boolean = !DateFormat.is24HourFormat(appContext)

    private companion object {
        const val KEY_ENABLED = "maps_navigation_enabled"
        const val REFRESH_MS = 1_000L
        const val END_RESEND_MS = 400L
        const val TEST_SECONDS = 10
    }
}

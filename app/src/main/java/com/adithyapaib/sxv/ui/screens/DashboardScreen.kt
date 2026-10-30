package com.adithyapaib.sxv.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ble.VStromTelemetry
import com.adithyapaib.sxv.ride.RoadbookEntry
import com.adithyapaib.sxv.ride.RoadbookEvent
import com.adithyapaib.sxv.settings.DistanceUnit
import com.adithyapaib.sxv.ui.STALE_AFTER_MS
import com.adithyapaib.sxv.ui.components.HeaderBox
import com.adithyapaib.sxv.ui.components.NowState
import com.adithyapaib.sxv.ui.components.RoadbookRoll
import com.adithyapaib.sxv.ui.components.StatusMark
import com.adithyapaib.sxv.ui.components.TextAction
import com.adithyapaib.sxv.ui.components.rememberLiveTripKm
import com.adithyapaib.sxv.ui.components.statusStyle
import com.adithyapaib.sxv.ui.formatAge
import com.adithyapaib.sxv.ui.rememberNowMs
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing

/** The Ride tab: status, the ruled header box, and the roadbook roll. */
@Composable
fun DashboardScreen(
    connectionState: BleConnectionState,
    telemetry: VStromTelemetry?,
    lastUpdateMs: Long,
    roadbook: List<RoadbookEntry>,
    unit: DistanceUnit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clock = rememberNowMs()
    val ageMs = if (lastUpdateMs == 0L) null else (clock - lastUpdateMs).coerceAtLeast(0L)
    val stale = telemetry != null && (ageMs ?: 0L) > STALE_AFTER_MS
    val connected = connectionState == BleConnectionState.CONNECTED
    val now = when {
        !connected -> NowState.Offline(connectionState)
        telemetry == null -> NowState.Waiting
        stale -> NowState.Stale(telemetry.speed, telemetry.tripB)
        telemetry.speed > 0 -> NowState.Riding(telemetry.speed, telemetry.tripB)
        else -> NowState.Stopped(telemetry.tripB)
    }
    val liveTripKm = rememberLiveTripKm(
        tripKm = telemetry?.tripA,
        speedKmh = telemetry?.speed ?: 0,
        lastUpdateMs = lastUpdateMs,
        live = connected && !stale
    )
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val status: @Composable () -> Unit = {
        StatusBar(connectionState, ageMs, stale, onDisconnect)
    }
    val header: @Composable () -> Unit = {
        HeaderBox(
            speedKmh = telemetry?.speed,
            engineTempC = telemetry?.engineTempC,
            fuelBars = telemetry?.fuelBars ?: 0,
            fuelLiters = telemetry?.fuelLiters ?: 0.0,
            fuelValid = telemetry?.isFuelValid == true,
            unit = unit,
            stale = stale || (!connected && telemetry != null)
        )
    }

    if (landscape) {
        Row(
            modifier.fillMaxSize().padding(Spacing.M),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.M - 4.dp)) {
                status()
                header()
            }
            RoadbookRoll(roadbook, now, liveTripKm, unit, onConnect, onDisconnect, Modifier.weight(1.15f))
        }
    } else {
        Column(
            modifier.fillMaxSize().padding(horizontal = Spacing.M).padding(top = Spacing.S, bottom = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.M - 4.dp)
        ) {
            status()
            header()
            RoadbookRoll(roadbook, now, liveTripKm, unit, onConnect, onDisconnect, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatusBar(state: BleConnectionState, ageMs: Long?, stale: Boolean, onDisconnect: () -> Unit) {
    val style = state.statusStyle()
    val age = when {
        ageMs == null -> null
        stale -> "no update for ${formatAge(ageMs).removeSuffix(" ago")}"
        else -> "updated ${formatAge(ageMs)}"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("SXV", color = Ink, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(Spacing.M))
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = "Connection: ${style.description}${age?.let { ", $it" }.orEmpty()}"
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusMark(state)
            Spacer(Modifier.width(Spacing.S))
            Column {
                Text(style.label.uppercase(), color = Ink, style = MaterialTheme.typography.labelMedium)
                if (age != null && state == BleConnectionState.CONNECTED) {
                    Text(
                        age.uppercase(),
                        color = if (stale) Ink else InkSoft,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        if (state == BleConnectionState.CONNECTED) TextAction("Disconnect", onDisconnect)
    }
}

// --- Previews -------------------------------------------------------------

private val PreviewTelemetry = VStromTelemetry(
    odometer = 12336, tripA = 397.4, tripB = 138.3,
    fuelBars = 5, fuelLiters = 10.0, isFuelValid = true, speed = 42, engineTempC = 82
)

private fun previewRoadbook(): List<RoadbookEntry> {
    val t = System.currentTimeMillis()
    return listOf(
        RoadbookEntry(5, RoadbookEvent.MOVING, t - 60_000, 396.8, speedKmh = 18),
        RoadbookEntry(4, RoadbookEvent.STOPPED, t - 240_000, 396.8),
        RoadbookEntry(3, RoadbookEvent.WARMED_UP, t - 420_000, 396.1, engineTempC = 80),
        RoadbookEntry(2, RoadbookEvent.MESSAGE_SENT, t - 600_000, 395.0, message = "HI | HI"),
        RoadbookEntry(1, RoadbookEvent.START, t - 700_000, 395.0, odometerKm = 12334)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 844)
@Composable
private fun RideRidingPreview() = SXVTheme {
    DashboardScreen(
        BleConnectionState.CONNECTED, PreviewTelemetry, System.currentTimeMillis() - 2_000,
        previewRoadbook(), DistanceUnit.KILOMETERS, {}, {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 844)
@Composable
private fun RideOfflinePreview() = SXVTheme {
    DashboardScreen(BleConnectionState.DISCONNECTED, null, 0L, emptyList(), DistanceUnit.KILOMETERS, {}, {})
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 844)
@Composable
private fun RideStaleReservePreview() = SXVTheme {
    DashboardScreen(
        BleConnectionState.CONNECTED, PreviewTelemetry.copy(speed = 0, fuelBars = 1, fuelLiters = 2.0),
        System.currentTimeMillis() - 40_000, previewRoadbook(), DistanceUnit.MILES, {}, {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 844, heightDp = 390)
@Composable
private fun RideLandscapePreview() = SXVTheme {
    DashboardScreen(
        BleConnectionState.CONNECTED, PreviewTelemetry, System.currentTimeMillis() - 1_000,
        previewRoadbook(), DistanceUnit.KILOMETERS, {}, {}
    )
}

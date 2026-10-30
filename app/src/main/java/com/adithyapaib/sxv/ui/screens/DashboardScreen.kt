package com.adithyapaib.sxv.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ble.VStromTelemetry
import com.adithyapaib.sxv.settings.DistanceUnit
import com.adithyapaib.sxv.ui.STALE_AFTER_MS
import com.adithyapaib.sxv.ui.components.ButtonVariant
import com.adithyapaib.sxv.ui.components.FuelCard
import com.adithyapaib.sxv.ui.components.HeroSpeedCard
import com.adithyapaib.sxv.ui.components.MetricCard
import com.adithyapaib.sxv.ui.components.PrimaryButton
import com.adithyapaib.sxv.ui.components.StatusChip
import com.adithyapaib.sxv.ui.formatAge
import com.adithyapaib.sxv.ui.formatOdometer
import com.adithyapaib.sxv.ui.formatTrip
import com.adithyapaib.sxv.ui.rememberNowMs
import com.adithyapaib.sxv.ui.speedIn
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.StatusAmber
import com.adithyapaib.sxv.ui.theme.TextMuted
import com.adithyapaib.sxv.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    connectionState: BleConnectionState,
    telemetry: VStromTelemetry?,
    lastUpdateMs: Long,
    unit: DistanceUnit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = rememberNowMs()
    val ageMs = if (lastUpdateMs == 0L) null else (now - lastUpdateMs).coerceAtLeast(0L)
    // Values are dimmed once they stop being current, whether the link dropped or the cluster went quiet.
    val stale = telemetry != null && (ageMs ?: 0L) > STALE_AFTER_MS
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val header: @Composable () -> Unit = {
        DashboardHeader(connectionState)
        ConnectionButton(connectionState, onConnect, onDisconnect)
        FreshnessLabel(ageMs, stale)
    }
    val speed: @Composable () -> Unit = {
        HeroSpeedCard(
            speed = speedIn(unit, telemetry?.speed ?: 0),
            unit = unit.speed,
            dimmed = stale || telemetry == null
        )
    }
    val details: @Composable () -> Unit = {
        FuelCard(
            bars = telemetry?.fuelBars ?: 0,
            liters = telemetry?.fuelLiters ?: 0.0,
            isValid = telemetry?.isFuelValid == true,
            dimmed = stale
        )
        MetricCard(
            label = "Engine temp",
            value = telemetry?.engineTempC?.let { unit.temperatureFromCelsius(it).toString() } ?: "—",
            unit = unit.temperature,
            modifier = Modifier.fillMaxWidth(),
            dimmed = stale
        )
        MetricCard(
            label = "Odometer",
            value = telemetry?.let { formatOdometer(it.odometer, unit) } ?: "—",
            unit = unit.distance,
            modifier = Modifier.fillMaxWidth(),
            dimmed = stale
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            MetricCard(
                label = "Trip A",
                value = telemetry?.let { formatTrip(it.tripA, unit) } ?: "—",
                unit = unit.distance,
                modifier = Modifier.weight(1f),
                dimmed = stale
            )
            MetricCard(
                label = "Trip B",
                value = telemetry?.let { formatTrip(it.tripB, unit) } ?: "—",
                unit = unit.distance,
                modifier = Modifier.weight(1f),
                dimmed = stale
            )
        }
    }

    if (landscape) {
        Row(
            modifier = modifier.fillMaxSize().padding(Spacing.M),
            horizontalArrangement = Arrangement.spacedBy(Spacing.M)
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) { header(); speed() }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.M)
            ) { details() }
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.M)
        ) { header(); speed(); details() }
    }
}

@Composable
private fun DashboardHeader(state: BleConnectionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("SXV", style = MaterialTheme.typography.headlineSmall)
            Text("V-Strom instrument link", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        StatusChip(state)
    }
}

/** One stateful action instead of two buttons with one always disabled. */
@Composable
private fun ConnectionButton(state: BleConnectionState, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    when (state) {
        BleConnectionState.CONNECTED -> PrimaryButton("Disconnect", onDisconnect, variant = ButtonVariant.Outlined)
        BleConnectionState.CONNECTING -> PrimaryButton("Cancel", onDisconnect, variant = ButtonVariant.Outlined)
        BleConnectionState.ERROR -> PrimaryButton("Try again", onConnect)
        BleConnectionState.DISCONNECTED -> PrimaryButton("Connect", onConnect)
    }
}

@Composable
private fun FreshnessLabel(ageMs: Long?, stale: Boolean) {
    val (text, color) = when {
        ageMs == null -> "Waiting for data from the bike" to TextMuted
        stale -> "No update for ${formatAge(ageMs).removeSuffix(" ago")}" to StatusAmber
        else -> "Updated ${formatAge(ageMs)}" to TextMuted
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
}

// --- Previews -------------------------------------------------------------

private val PreviewTelemetry = VStromTelemetry(
    odometer = 12336, tripA = 397.4, tripB = 138.3,
    fuelBars = 5, fuelLiters = 10.0, isFuelValid = true, speed = 18, engineTempC = 82
)

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 900)
@Composable
private fun DashboardConnectedPreview() = SXVTheme {
    DashboardScreen(
        BleConnectionState.CONNECTED, PreviewTelemetry, System.currentTimeMillis() - 3_000,
        DistanceUnit.KILOMETERS, {}, {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 900)
@Composable
private fun DashboardConnectingPreview() = SXVTheme {
    DashboardScreen(BleConnectionState.CONNECTING, null, 0L, DistanceUnit.KILOMETERS, {}, {})
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 900)
@Composable
private fun DashboardDisconnectedStalePreview() = SXVTheme {
    DashboardScreen(
        BleConnectionState.DISCONNECTED, PreviewTelemetry.copy(fuelBars = 1, speed = 0),
        System.currentTimeMillis() - 95_000, DistanceUnit.MILES, {}, {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 800, heightDp = 360)
@Composable
private fun DashboardLandscapePreview() = SXVTheme {
    DashboardScreen(
        BleConnectionState.CONNECTED, PreviewTelemetry, System.currentTimeMillis() - 1_000,
        DistanceUnit.KILOMETERS, {}, {}
    )
}

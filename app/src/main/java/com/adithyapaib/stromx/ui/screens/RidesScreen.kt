package com.adithyapaib.stromx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adithyapaib.stromx.ride.RideMath
import com.adithyapaib.stromx.ride.RideRecord
import com.adithyapaib.stromx.settings.DistanceUnit
import com.adithyapaib.stromx.ui.components.HRule
import com.adithyapaib.stromx.ui.components.Label
import com.adithyapaib.stromx.ui.components.PressBlock
import com.adithyapaib.stromx.ui.components.Tag
import com.adithyapaib.stromx.ui.components.TextAction
import com.adithyapaib.stromx.ui.components.VRule
import com.adithyapaib.stromx.ui.components.inkBox
import com.adithyapaib.stromx.ui.formatRupees
import com.adithyapaib.stromx.ui.formatTrip
import com.adithyapaib.stromx.ui.formatWhole
import com.adithyapaib.stromx.ui.rememberNowMs
import com.adithyapaib.stromx.ui.speedIn
import com.adithyapaib.stromx.ui.theme.Dimens
import com.adithyapaib.stromx.ui.theme.HighlightWash
import com.adithyapaib.stromx.ui.theme.Ink
import com.adithyapaib.stromx.ui.theme.InkSoft
import com.adithyapaib.stromx.ui.theme.StromXTheme
import com.adithyapaib.stromx.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/** The rides log: start a ride, watch it live, then keep what it was. */
@Composable
fun RidesScreen(
    rides: List<RideRecord>,
    odometerKm: Int?,
    unit: DistanceUnit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Ticks once a second so the running timer counts up between telemetry frames.
    val now = rememberNowMs()
    val active = rides.firstOrNull { it.isActive }
    val saved = rides.filter { !it.isActive }
    val totals = remember(rides) { RideMath.totals(rides) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text("Rides", color = Ink, style = MaterialTheme.typography.headlineSmall)

        if (active != null) {
            ActiveRideCard(
                ride = active,
                nowMs = now,
                odometerKm = odometerKm,
                unit = unit,
                onStop = onStop,
                onDiscard = onDiscard
            )
        } else {
            Column(
                Modifier.fillMaxWidth().inkBox().padding(Spacing.M),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                Text("Record a ride", color = Ink, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tap start as you set off. The ride keeps its date, times, distance, " +
                        "top speed and an estimate of the fuel it used.",
                    color = InkSoft,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (odometerKm == null) {
                    Text(
                        "No odometer reading yet — connect to the bike and distance will be measured for you.",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                PressBlock("Start ride", onStart)
            }
        }

        if (saved.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().inkBox()) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 84.dp)) {
                    TotalCell("Rides", formatWhole(totals.rides), "", Modifier.weight(1f))
                    VRule()
                    TotalCell(
                        "Distance",
                        String.format(Locale.getDefault(), "%.0f", totals.distanceKm * unit.perKm),
                        unit.distance,
                        Modifier.weight(1f)
                    )
                    VRule()
                    TotalCell(
                        "Fuel used",
                        String.format(Locale.getDefault(), "%.1f", totals.fuelLitres),
                        "L",
                        Modifier.weight(1f)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label("Saved rides", Modifier.weight(1f))
                    Label("${saved.size} kept")
                }
                Column(Modifier.fillMaxWidth().inkBox()) {
                    saved.forEachIndexed { index, ride ->
                        if (index > 0) HRule()
                        RideRow(
                            ride = ride,
                            unit = unit,
                            selected = selected == ride.id,
                            onSelect = { selected = if (selected == ride.id) null else ride.id },
                            onDelete = {
                                onDelete(ride.id)
                                selected = null
                            }
                        )
                    }
                }
            }
        } else if (active == null) {
            Text(
                "Nothing recorded yet. Your finished rides collect here.",
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** The ride in progress: elapsed time ticking, distance and top speed as they happen. */
@Composable
private fun ActiveRideCard(
    ride: RideRecord,
    nowMs: Long,
    odometerKm: Int?,
    unit: DistanceUnit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    val elapsed = (nowMs - ride.startedAtMs).coerceAtLeast(0)
    val liveDistanceKm = if (odometerKm != null && ride.startOdometerKm != null && odometerKm >= ride.startOdometerKm) {
        (odometerKm - ride.startOdometerKm).toDouble()
    } else {
        null
    }

    Column(
        Modifier.fillMaxWidth().inkBox(fill = HighlightWash).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag("Riding")
            Spacer(Modifier.width(Spacing.S))
            Text(
                "Started ${timeFormat.format(Date(ride.startedAtMs))} · ${dayFormat.format(Date(ride.startedAtMs))}",
                color = Ink,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        // Seconds matter while the clock is running, so the timer shows them.
        val seconds = elapsed / 1000
        val hours = seconds / 3600
        val minutes = (seconds / 60) % 60
        val secs = seconds % 60
        Text(
            if (hours > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
            else String.format(Locale.getDefault(), "%d:%02d", minutes, secs),
            color = Ink,
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum")
        )
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            StatCell(
                "Distance",
                liveDistanceKm?.let { formatTrip(it, unit) } ?: "—",
                unit.distance,
                Modifier.weight(1f)
            )
            VRule()
            StatCell(
                "Top speed",
                if (ride.maxSpeedKph > 0) "${speedIn(unit, ride.maxSpeedKph)}" else "—",
                unit.speed,
                Modifier.weight(1f)
            )
            VRule()
            StatCell(
                "Fuel",
                ride.startFuelBars?.let { "$it/6" } ?: "—",
                "bars",
                Modifier.weight(1f)
            )
        }
        PressBlock("End & save", onStop)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextAction("Discard ride", onDiscard)
        }
    }
}

/** One saved ride: the day, the window, and what it added up to. Tapping shows every detail. */
@Composable
private fun RideRow(
    ride: RideRecord,
    unit: DistanceUnit,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) HighlightWash else Color.Transparent)
            .clickable(role = Role.Button, onClickLabel = if (selected) "Hide details" else "Show details", onClick = onSelect)
            .heightIn(min = Dimens.MinTouch)
            .padding(horizontal = Spacing.M, vertical = Spacing.S + 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${dayFormat.format(Date(ride.startedAtMs))} · " +
                        "${timeFormat.format(Date(ride.startedAtMs))} – " +
                        (ride.endedAtMs?.let { timeFormat.format(Date(it)) } ?: "now"),
                    color = Ink,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    buildString {
                        append(ride.durationMs?.let { RideMath.formatDuration(it) } ?: "running")
                        append(" · ")
                        append(ride.distanceKm?.let { "${formatTrip(it, unit)} ${unit.distance}" } ?: "— ${unit.distance}")
                        if (ride.maxSpeedKph > 0) append(" · top ${speedIn(unit, ride.maxSpeedKph)} ${unit.speed}")
                    },
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                ride.avgSpeedKph?.let { "${speedIn(unit, it.roundToInt())}" } ?: "—",
                color = Ink,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
            )
        }
        if (selected) {
            Text("average ${unit.speed}", color = InkSoft, style = MaterialTheme.typography.labelSmall)
            HRule(Modifier.padding(vertical = Spacing.Xs))
            DetailLine("Start", "${dayFormat.format(Date(ride.startedAtMs))} ${timeFormat.format(Date(ride.startedAtMs))}")
            ride.endedAtMs?.let {
                DetailLine("End", "${dayFormat.format(Date(it))} ${timeFormat.format(Date(it))}")
            }
            DetailLine(
                "Odometer",
                if (ride.startOdometerKm != null && ride.endOdometerKm != null) {
                    "${formatWhole(ride.startOdometerKm)} → ${formatWhole(ride.endOdometerKm)} km"
                } else {
                    "not linked"
                }
            )
            DetailLine(
                "Distance",
                ride.distanceKm?.let { "${formatTrip(it, unit)} ${unit.distance}" } ?: "—"
            )
            DetailLine("Duration", ride.durationMs?.let { RideMath.formatDuration(it) } ?: "—")
            DetailLine("Top speed", if (ride.maxSpeedKph > 0) "${speedIn(unit, ride.maxSpeedKph)} ${unit.speed}" else "—")
            ride.avgSpeedKph?.let { DetailLine("Average", "${speedIn(unit, it.roundToInt())} ${unit.speed}") }
            DetailLine(
                "Fuel used",
                buildString {
                    append(ride.fuelUsedLitres?.let { String.format(Locale.getDefault(), "%.1f L (est.)", it) } ?: "—")
                    ride.fuelCost?.let { append(" · ${formatRupees(it)}") }
                }
            )
            if (ride.startFuelBars != null || ride.endFuelBars != null) {
                DetailLine(
                    "Gauge",
                    "${ride.startFuelBars ?: "—"} → ${ride.endFuelBars ?: "—"} bars"
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction("Delete ride", onDelete)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Label(label, Modifier.width(96.dp))
        Text(value, color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StatCell(label: String, value: String, suffix: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.M - 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Ink, style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), maxLines = 1)
            if (suffix.isNotEmpty()) {
                Text(" $suffix", Modifier.padding(bottom = 3.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun TotalCell(label: String, value: String, suffix: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.M - 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(label)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = Ink, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "pnum"), maxLines = 1)
            if (suffix.isNotEmpty()) {
                Text(" $suffix", Modifier.padding(bottom = 5.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 1400)
@Composable
private fun RidesPreview() = StromXTheme {
    val day = 86_400_000L
    val hour = 3_600_000L
    val t = System.currentTimeMillis()
    RidesScreen(
        rides = listOf(
            RideRecord(
                id = 3, startedAtMs = t - 2 * hour, endedAtMs = t - hour - 14 * 60_000,
                startOdometerKm = 8_885, endOdometerKm = 8_921, maxSpeedKph = 78,
                startFuelBars = 4, endFuelBars = 4, fuelUsedLitres = 2.4, fuelCost = 252.0
            ),
            RideRecord(
                id = 2, startedAtMs = t - 4 * day, endedAtMs = t - 4 * day + 46 * 60_000,
                startOdometerKm = 8_830, endOdometerKm = 8_885, maxSpeedKph = 92,
                startFuelBars = 6, endFuelBars = 5, fuelUsedLitres = 3.6, fuelCost = 378.0
            ),
            RideRecord(
                id = 1, startedAtMs = t - 9 * day, endedAtMs = t - 9 * day + 22 * 60_000,
                startOdometerKm = 8_800, endOdometerKm = 8_830, maxSpeedKph = 61,
                startFuelBars = 6, endFuelBars = 6, fuelUsedLitres = 2.0, fuelCost = 210.0
            )
        ),
        odometerKm = 8_921,
        unit = DistanceUnit.KILOMETERS,
        onStart = {}, onStop = {}, onDiscard = {}, onDelete = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 900)
@Composable
private fun RidesActivePreview() = StromXTheme {
    RidesScreen(
        rides = listOf(
            RideRecord(
                id = 1, startedAtMs = System.currentTimeMillis() - 54 * 60_000, endedAtMs = null,
                startOdometerKm = 8_885, endOdometerKm = null, maxSpeedKph = 64,
                startFuelBars = 4, endFuelBars = null, fuelUsedLitres = null, fuelCost = null
            )
        ),
        odometerKm = 8_914,
        unit = DistanceUnit.KILOMETERS,
        onStart = {}, onStop = {}, onDiscard = {}, onDelete = {}
    )
}

package com.adithyapaib.stromx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adithyapaib.stromx.ble.BleConnectionState
import com.adithyapaib.stromx.ride.RoadbookEntry
import com.adithyapaib.stromx.ride.RoadbookEvent
import com.adithyapaib.stromx.settings.DistanceUnit
import com.adithyapaib.stromx.ui.formatOdometer
import com.adithyapaib.stromx.ui.formatTripPlain
import com.adithyapaib.stromx.ui.speedIn
import com.adithyapaib.stromx.ui.theme.Dimens
import com.adithyapaib.stromx.ui.theme.Highlight
import com.adithyapaib.stromx.ui.theme.HighlightWash
import com.adithyapaib.stromx.ui.theme.Ink
import com.adithyapaib.stromx.ui.theme.InkSoft
import com.adithyapaib.stromx.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.min
import kotlinx.coroutines.delay

/** Frames arrive every 5 s; never project the distance further ahead than one missed frame. */
private const val MAX_EXTRAPOLATION_MS = 6_000L

/** The cluster reports trip distance in tenths, so a frame says "somewhere in this 0.1 km band". */
private const val BAND_KM = 0.1

/** Under a millimetre the ease-up is finished; exponential easing alone never quite arrives. */
private const val SNAP_KM = 1e-6

/**
 * Trip distance between frames, dead-reckoned at the reported speed so the roll keeps moving.
 *
 * Each frame only says the true distance is in [T, T + 0.1) km, so the shown value advances
 * freely inside that band (shifted forward by speed x time since the frame). It never runs
 * backwards: below the band it eases up over about a second, above it it waits. A drop of
 * more than 1 km is a trip reset and is followed at once.
 */
@Composable
fun rememberLiveTripKm(tripKm: Double?, speedKmh: Int, lastUpdateMs: Long, live: Boolean): State<Double?> {
    val shown = remember { mutableStateOf(tripKm) }
    val trip by rememberUpdatedState(tripKm)
    val speed by rememberUpdatedState(if (live) speedKmh else 0)
    val frameAt by rememberUpdatedState(lastUpdateMs)

    LaunchedEffect(Unit) {
        var last = withFrameMillis { it }
        while (true) {
            val t = trip
            val current = shown.value
            if (t == null || current == null || t < current - 1.0) {
                shown.value = t
                delay(250)
                last = withFrameMillis { it }
                continue
            }
            val sinceFrame = (System.currentTimeMillis() - frameAt).coerceIn(0L, MAX_EXTRAPOLATION_MS)
            val travelled = speed * sinceFrame / 3_600_000.0
            val lower = t + travelled
            val upper = t + BAND_KM + travelled
            if (speed == 0 && current >= lower) {
                // Parked and inside the band: nothing moves, so don't spend frames.
                delay(250)
                last = withFrameMillis { it }
                continue
            }
            withFrameMillis { frame ->
                val dt = (frame - last).coerceIn(0L, 100L)
                last = frame
                shown.value = when {
                    // Snap the last sliver so the parked check above can pass and the loop sleeps.
                    current < lower && lower - current < SNAP_KM -> lower
                    current < lower -> current + (lower - current) * min(1.0, dt / 350.0)
                    current >= upper -> current
                    else -> current + speed * dt / 3_600_000.0
                }
            }
        }
    }
    return shown
}

// --- Header box -----------------------------------------------------------

/**
 * The ruled header: speed at display size on the left; engine with its sliding needle and
 * fuel with its six cells on the right. Stale readings turn [InkSoft] (still 6.9:1) and the
 * header carries a STALE tag, rather than fading below legibility.
 */
@Composable
fun HeaderBox(
    speedKmh: Int?,
    engineTempC: Int?,
    fuelBars: Int,
    fuelLiters: Double,
    fuelValid: Boolean,
    unit: DistanceUnit,
    stale: Boolean,
    modifier: Modifier = Modifier,
    /** Estimated km to empty, from the fuel estimator; null until economy is measured. */
    rangeKm: Int? = null
) {
    val rangeShown = rangeKm?.let { "≈ ${formatOdometer(it, unit)} ${unit.distance}" }
    val speedShown = speedKmh?.let { speedIn(unit, it).toString() } ?: "—"
    val tempShown = engineTempC?.let { unit.temperatureFromCelsius(it).toString() } ?: "—"
    val bars = if (fuelValid) fuelBars.coerceIn(0, 6) else 0
    val litersText = String.format(Locale.getDefault(), "%.1f", fuelLiters)
    val level = when {
        !fuelValid -> null
        bars <= 1 -> "Reserve"
        bars == 2 -> "Low"
        else -> null
    }
    val reading = if (stale) InkSoft else Ink
    val description = buildString {
        if (stale) append("Last known values. ")
        append("Speed $speedShown ${unit.speed}. ")
        append("Engine $tempShown ${unit.temperature}. ")
        append(if (fuelValid) "Fuel $bars of 6 bars, about $litersText litres${level?.let { ", $it" }.orEmpty()}." else "Fuel syncing.")
        rangeShown?.let { append(" Range $it.") }
    }
    // The speed numeral is already huge; cap it against the system font scale so three
    // digits always fit their box instead of being clipped.
    val fontScale = LocalDensity.current.fontScale
    val speedStyle = MaterialTheme.typography.displayLarge.let {
        it.copy(fontSize = (112f / fontScale.coerceAtLeast(1f)).sp, lineHeight = (104f / fontScale.coerceAtLeast(1f)).sp)
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .inkBox()
            .clearAndSetSemantics { contentDescription = description }
    ) {
        Column(Modifier.weight(1.3f).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Speed · ${unit.speed}", Modifier.weight(1f))
                if (stale) Tag("Stale")
            }
            RollingText(speedShown, speedStyle, color = reading)
        }
        VRule(thickness = Dimens.RuleHeavy)
        Column(Modifier.weight(1f)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Engine")
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(tempShown, MaterialTheme.typography.headlineMedium, color = reading)
                    Text(" ${unit.temperature}", Modifier.padding(bottom = 5.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
                }
                TempScale(engineTempC)
            }
            HRule()
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label(if (rangeShown != null && fuelValid) "Fuel · $rangeShown" else "Fuel")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (fuelValid) "$bars/6" else "—", color = reading, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(Spacing.S))
                    if (fuelValid) Tag(level ?: "≈ $litersText L")
                }
                FuelCells(bars, lowFuel = level != null, ink = reading)
            }
        }
    }
}

/** Highlighter tag: ink text on a highlighter block. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Box(modifier.background(Highlight).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(text.uppercase(), color = Ink, style = MaterialTheme.typography.labelMedium)
    }
}

/** A 40-120 °C ruled scale with a highlighter needle that slides to the engine temperature. */
@Composable
fun TempScale(tempC: Int?, modifier: Modifier = Modifier) {
    val target = ((tempC ?: 40).coerceIn(40, 120) - 40) / 80f
    val position by animateFloatAsState(target, tween(900, easing = FastOutSlowInEasing), label = "temp-needle")
    Canvas(modifier.fillMaxWidth().height(16.dp)) {
        val w = size.width
        val h = size.height
        val rule = 1.5.dp.toPx()
        drawLine(Ink, Offset(0f, h - rule / 2), Offset(w, h - rule / 2), rule)
        for (i in 0..8) {
            val x = (w - rule) * i / 8f + rule / 2
            val warm = i == 4 // 80 °C, where the roadbook logs "warmed up"
            drawLine(if (warm) Ink else InkSoft, Offset(x, if (warm) 0f else h * 0.5f), Offset(x, h), rule)
        }
        if (tempC != null) {
            val needle = 4.dp.toPx()
            drawRect(Highlight, topLeft = Offset((w - needle) * position, 0f), size = Size(needle, h))
        }
    }
}

/** Six fuel cells; filled cells turn highlighter when low or on reserve. */
@Composable
fun FuelCells(bars: Int, lowFuel: Boolean, modifier: Modifier = Modifier, ink: Color = Ink) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(6) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(12.dp)
                    .background(
                        when {
                            index >= bars -> Color.Transparent
                            lowFuel -> Highlight
                            else -> ink
                        }
                    )
                    .border(Dimens.RuleLight, ink, RectangleShape)
            )
        }
    }
}

// --- The roll -------------------------------------------------------------

/** What the top row of the roll shows right now. */
sealed interface NowState {
    data class Offline(val state: BleConnectionState) : NowState
    data object Waiting : NowState
    data class Riding(val speedKmh: Int, val tripBKm: Double) : NowState
    data class Stopped(val tripBKm: Double) : NowState

    /** Linked, but the frames stopped arriving: the last values are shown, nothing is claimed. */
    data class Stale(val lastSpeedKmh: Int, val tripBKm: Double) : NowState
}

/**
 * The roadbook roll: a distance strip down the left edge that scrolls at riding speed (one
 * tick per 10 m, a long tick per 100 m), the live NOW row on top, then every logged event.
 */
@Composable
fun RoadbookRoll(
    entries: List<RoadbookEntry>,
    now: NowState,
    liveTripKm: State<Double?>,
    unit: DistanceUnit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Size the distance column from the widest value the roll can show, at the NOW row's size.
    val measurer = rememberTextMeasurer()
    val nowStyle = MaterialTheme.typography.displaySmall.copy(fontSize = 28.sp)
    val density = LocalDensity.current
    val distanceColumn = remember(nowStyle, density) {
        with(density) { measurer.measure("8888.8", nowStyle).size.width.toDp() } + 22.dp
    }.coerceAtLeast(Dimens.DistanceColumn)

    Row(modifier.fillMaxWidth().inkBox()) {
        DistanceTicks(liveTripKm, Modifier.fillMaxHeight())
        VRule(thickness = Dimens.RuleHeavy)
        LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
            item(key = "now") {
                NowRow(now, liveTripKm, unit, distanceColumn, nowStyle, onConnect, onDisconnect)
                HRule()
            }
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.animateItem()) {
                    EntryRow(entry, unit, distanceColumn, newest = entry.id == entries.first().id)
                    HRule()
                }
            }
            if (entries.isEmpty() && now !is NowState.Offline) {
                item(key = "empty") {
                    Text(
                        "Events appear here as you ride: start, warm-up, stops and fuel.",
                        Modifier.padding(Spacing.M),
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** The signature moving part: drawn in full ink weights so it still reads on a sunlit mount. */
@Composable
private fun DistanceTicks(liveTripKm: State<Double?>, modifier: Modifier = Modifier) {
    Canvas(modifier.width(16.dp)) {
        val spacing = 12.dp.toPx()
        val tenMetreSteps = (liveTripKm.value ?: 0.0) * 100.0
        val phase = ((tenMetreSteps % 1.0) * spacing).toFloat()
        var index = floor(tenMetreSteps).toLong() + 1
        var y = phase - spacing
        val minor = 1.5.dp.toPx()
        val major = 2.5.dp.toPx()
        while (y < size.height + spacing) {
            val isMajor = index % 10L == 0L
            drawLine(
                if (isMajor) Ink else InkSoft,
                Offset(size.width * (if (isMajor) 0.1f else 0.45f), y),
                Offset(size.width, y),
                if (isMajor) major else minor
            )
            y += spacing
            index--
        }
    }
}

@Composable
private fun RowFrame(highlighted: Boolean, minHeight: Dp, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = minHeight)
            .background(if (highlighted) HighlightWash else Color.Transparent),
        content = content
    )
}

@Composable
private fun NowRow(
    now: NowState,
    liveTripKm: State<Double?>,
    unit: DistanceUnit,
    distanceColumn: Dp,
    distanceStyle: androidx.compose.ui.text.TextStyle,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val live = now is NowState.Riding || now is NowState.Stopped
    // Recompose only when the shown tenth changes, not on every frame of the dead-reckoning.
    val kmText by remember(unit) {
        derivedStateOf { liveTripKm.value?.let { formatTripPlain(it, unit) } ?: "—" }
    }
    RowFrame(highlighted = live, minHeight = 104.dp) {
        Column(
            Modifier.width(distanceColumn).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RollingText(kmText, distanceStyle, color = Ink)
            if (live) Tag("Now")
            Label("Trip A · ${unit.distance}")
        }
        VRule()
        Box(Modifier.width(Dimens.TulipColumn).fillMaxHeight(), contentAlignment = Alignment.Center) {
            val (kind, speed) = when (now) {
                is NowState.Riding -> TulipKind.Moving to now.speedKmh
                is NowState.Stopped -> TulipKind.Stopped to 0
                is NowState.Stale -> (if (now.lastSpeedKmh > 0) TulipKind.Moving else TulipKind.Stopped) to 0
                NowState.Waiting -> TulipKind.Connected to 0
                is NowState.Offline -> TulipKind.Offline to 0
            }
            Tulip(kind, live = live, speedKmh = speed)
        }
        VRule()
        Column(Modifier.weight(1f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (now) {
                is NowState.Riding -> {
                    Text("Riding", color = Ink, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${speedIn(unit, now.speedKmh)} ${unit.speed} · trip B ${formatTripPlain(now.tripBKm, unit)} ${unit.distance}",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is NowState.Stopped -> {
                    Text("Stopped", color = Ink, style = MaterialTheme.typography.titleLarge)
                    Text("Trip B ${formatTripPlain(now.tripBKm, unit)} ${unit.distance}", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
                is NowState.Stale -> {
                    Text("No data", color = Ink, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Last reading ${speedIn(unit, now.lastSpeedKmh)} ${unit.speed}. Waiting for the cluster.",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                NowState.Waiting -> {
                    Text("Linked", color = Ink, style = MaterialTheme.typography.titleLarge)
                    Text("Waiting for the first frame from the cluster.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
                is NowState.Offline -> OfflineNote(now.state)
            }
        }
    }
    // The link action spans the whole roll, so it keeps its full width at large font scales.
    if (now is NowState.Offline) {
        val padding = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
        when (now.state) {
            BleConnectionState.CONNECTING -> PressBlock("Cancel", onDisconnect, padding, style = BlockStyle.Paper)
            BleConnectionState.ERROR -> PressBlock("Try again", onConnect, padding)
            else -> PressBlock("Connect", onConnect, padding)
        }
    }
}

@Composable
private fun OfflineNote(state: BleConnectionState) {
    val (title, note) = when (state) {
        BleConnectionState.CONNECTING -> "Connecting" to "Looking for the cluster. Switch the ignition on."
        BleConnectionState.ERROR -> "Link failed" to "The Logs tab has the reason."
        else -> "Offline" to "Connect to start the roadbook."
    }
    Text(title, color = Ink, style = MaterialTheme.typography.titleLarge)
    Text(note, color = InkSoft, style = MaterialTheme.typography.bodySmall)
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

/** A row written in the last few seconds gets marked with a highlighter swipe, then it dries. */
private const val FRESH_MS = 4_000L

@Composable
private fun EntryRow(entry: RoadbookEntry, unit: DistanceUnit, distanceColumn: Dp, newest: Boolean) {
    val (title, note) = entry.describe(unit)
    val motion = rememberMotionEnabled()
    // Only the newest row is marked, so rows written together (start, connected) don't all swipe.
    val fresh = remember(entry.id) { motion && newest && System.currentTimeMillis() - entry.atMs < FRESH_MS }
    val sweep = remember(entry.id) { Animatable(if (fresh) 0f else 1f) }
    val ink = remember(entry.id) { Animatable(if (fresh) 1f else 0f) }
    LaunchedEffect(entry.id) {
        if (fresh) {
            sweep.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            delay(1_400)
            ink.animateTo(0f, tween(900))
        }
    }
    RowFrame(highlighted = false, minHeight = 88.dp) {
        Column(
            Modifier.width(distanceColumn).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                entry.tripKm?.let { formatTripPlain(it, unit) } ?: "—",
                color = Ink,
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum")
            )
            Label(timeFormat.format(Date(entry.atMs)))
        }
        VRule()
        Box(Modifier.width(Dimens.TulipColumn).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Tulip(entry.event.tulip())
        }
        VRule()
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    if (ink.value > 0f) {
                        drawRect(
                            Highlight.copy(alpha = 0.4f * ink.value),
                            size = Size(size.width * sweep.value, size.height)
                        )
                    }
                }
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
            Text(note, color = InkSoft, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun RoadbookEntry.describe(unit: DistanceUnit): Pair<String, String> = when (event) {
    RoadbookEvent.START -> "Start" to (odometerKm?.let { "Odometer ${formatOdometer(it, unit)} ${unit.distance}" } ?: "First frame received")
    RoadbookEvent.CONNECTED -> "Connected" to "Link to the cluster is up."
    RoadbookEvent.WARMED_UP -> "Warmed up" to "Engine reached ${engineTempC?.let { unit.temperatureFromCelsius(it) } ?: "—"} ${unit.temperature}."
    RoadbookEvent.STOPPED -> "Stopped" to "Speed held at zero."
    RoadbookEvent.MOVING -> "Riding again" to "Moving at ${speedKmh?.let { speedIn(unit, it) } ?: "—"} ${unit.speed}."
    RoadbookEvent.FUEL_DROP -> "Fuel down" to "${fuelBars ?: "—"} of 6 bars left."
    RoadbookEvent.LINK_LOST -> "Link lost" to "No data until it reconnects."
}

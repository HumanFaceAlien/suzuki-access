package com.adithyapaib.sxv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.garage.EstimateBasis
import com.adithyapaib.sxv.garage.FuelEstimate
import com.adithyapaib.sxv.garage.FuelEstimator
import com.adithyapaib.sxv.garage.FuelPoint
import com.adithyapaib.sxv.settings.DistanceUnit
import com.adithyapaib.sxv.ui.formatWhole
import com.adithyapaib.sxv.ui.theme.Highlight
import com.adithyapaib.sxv.ui.theme.HighlightWash
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.Paper
import com.adithyapaib.sxv.ui.theme.Spacing
import java.util.Locale

/** Range at or below this reads as low: the highlighter takes the gauge. */
const val LOW_RANGE_KM = 40

private fun litres(value: Double) = String.format(Locale.getDefault(), "%.1f", value)

/**
 * Fuel left, as estimated from the log, the odometer and the gauge, with how sure it is and
 * what it is counted from. Range appears once two full tanks have measured the economy.
 */
@Composable
fun FuelLeftCard(
    estimate: FuelEstimate?,
    unit: DistanceUnit,
    tankSetting: Double?,
    onTankChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingTank by rememberSaveable { mutableStateOf(false) }
    val low = estimate?.rangeKm?.let { it <= LOW_RANGE_KM } == true
    Column(
        modifier.fillMaxWidth().inkBox(fill = if (low) HighlightWash else Paper).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Fuel left", Modifier.weight(1f))
            if (low) Tag("Low range")
        }
        if (estimate == null) {
            Text(
                "Connect once so SXV can read the odometer and fuel gauge.",
                color = InkSoft,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("≈ ${litres(estimate.litres)}", color = Ink, style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "pnum"))
                Text(" L  ± ${litres(estimate.uncertaintyLitres)}", Modifier.padding(bottom = 5.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                estimate.rangeKm?.let { km ->
                    Text(
                        "≈ ${formatWhole((km * unit.perKm).toInt())}",
                        color = Ink,
                        style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "pnum")
                    )
                    Text(" ${unit.distance} left", Modifier.padding(bottom = 5.dp), color = InkSoft, style = MaterialTheme.typography.labelMedium)
                }
            }
            TankGauge(estimate, low)
            Text(basisLine(estimate, unit), color = InkSoft, style = MaterialTheme.typography.bodySmall)
        }

        if (editingTank) {
            var text by rememberSaveable { mutableStateOf(litres(tankSetting ?: FuelEstimator.DEFAULT_TANK_LITRES)) }
            val value = text.replace(',', '.').toDoubleOrNull()
            val error = if (value == null || value < 5 || value > 40) "Enter a tank size between 5 and 40 L." else null
            InkField(
                text, { text = it.take(5) }, label = "Usable tank size (L)", hint = "12",
                keyboardType = KeyboardType.Decimal, capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done, error = error
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PressBlock("Save", { if (value != null && error == null) { onTankChange(value); editingTank = false } }, Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.S))
                PressBlock("Cancel", { editingTank = false }, Modifier.weight(1f), style = BlockStyle.Paper)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Tank ${litres(estimate?.capacityLitres ?: tankSetting ?: FuelEstimator.DEFAULT_TANK_LITRES)} L" +
                        if (tankSetting == null) " (default; check your owner's manual)" else "",
                    Modifier.weight(1f),
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
                TextAction("Edit tank", { editingTank = true })
            }
        }
    }
}

private fun basisLine(e: FuelEstimate, unit: DistanceUnit): String {
    val since = e.kmSinceAnchor?.let { "${formatWhole((it * unit.perKm).toInt())} ${unit.distance}" }
    val economy = e.kmPerLitre?.let { String.format(Locale.getDefault(), "%.1f %s/L", it * unit.perKm, unit.distance) }
    val learned = e.learnedBarLevels.count { it != null }
    return when (e.basis) {
        EstimateBasis.BarCheckpoint ->
            "Counted from the gauge stepping to ${e.anchorBars} bars, $since ago, at $economy. " +
                "Learned $learned of 5 bar levels from ${e.calibrationSamples} gauge steps."
        EstimateBasis.FullTank ->
            "Counted down from your last full tank, $since ago, at $economy. " +
                if (learned == 0) "Bar levels are learned as the gauge steps down." else "Learned $learned of 5 bar levels."
        EstimateBasis.BarsOnly ->
            "From the fuel gauge only. Log two full-tank fill-ups to measure your economy and sharpen this."
    }
}

/**
 * The tank as a ruled bar from empty to full. The fill eases to the estimate; ticks mark the
 * level where the gauge drops to each bar, solid once learned and short while still a guess.
 */
@Composable
private fun TankGauge(estimate: FuelEstimate, low: Boolean) {
    val fraction = (estimate.litres / estimate.capacityLitres).toFloat().coerceIn(0f, 1f)
    val shown by animateFloatAsState(fraction, tween(900, easing = FastOutSlowInEasing), label = "tank-fill")
    val measurer = rememberTextMeasurer()
    val tickStyle = MaterialTheme.typography.labelSmall.copy(color = InkSoft)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics {
                contentDescription = "Tank about ${(fraction * 100).toInt()} percent full"
            }
    ) {
        val barH = 18.dp.toPx()
        val rule = 1.5.dp.toPx()
        drawRect(if (low) Highlight else Ink, size = Size(size.width * shown, barH))
        drawRect(Ink, size = Size(size.width, barH), style = Stroke(rule))
        for (bar in 1..5) {
            val learned = estimate.learnedBarLevels[bar - 1]
            val level = learned ?: estimate.capacityLitres * bar / 6.0
            val x = (level / estimate.capacityLitres).toFloat().coerceIn(0f, 1f) * size.width
            val inside = x < size.width * shown
            drawLine(
                if (inside) Paper else if (learned != null) Ink else InkSoft,
                Offset(x, if (learned != null) 0f else barH * 0.5f),
                Offset(x, barH),
                rule
            )
            val label = measurer.measure(bar.toString(), tickStyle)
            drawText(label, topLeft = Offset((x - label.size.width / 2f).coerceIn(0f, size.width - label.size.width), barH + 4.dp.toPx()))
        }
    }
}

/** Fuel left since the last full tank, as chart points: the fill, each gauge step, now. */
fun fuelHistoryPoints(history: List<FuelPoint>): List<ChartPoint> = history.map {
    val what = when (it.kind) {
        FuelPoint.Kind.Fill -> "full tank"
        FuelPoint.Kind.BarDrop -> "gauge stepped down"
        FuelPoint.Kind.Now -> "now"
    }
    ChartPoint("${formatWhole(it.odometerKm)} km", it.litres, "${litres(it.litres)} L, $what")
}

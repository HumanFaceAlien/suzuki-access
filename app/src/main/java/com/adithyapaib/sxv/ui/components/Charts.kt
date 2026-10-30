package com.adithyapaib.sxv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.ui.theme.Highlight
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkFaint
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.Paper
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** One plotted value. [label] names it on the axis, [readout] is what the tap readout shows. */
data class ChartPoint(val label: String, val value: Double, val readout: String)

private val PlotHeight = 140.dp
private val AxisBand = 22.dp

/**
 * A single-series line in ink over solid hairline gridlines. The latest point wears the
 * highlighter; tapping anywhere selects the nearest point and shows it in the readout above.
 * Values are not printed on every point: the readout and the list below the chart carry them.
 */
@Composable
fun RoadLineChart(
    points: List<ChartPoint>,
    axisFormat: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier
) {
    var selected by remember(points.size) { mutableIntStateOf(points.lastIndex) }
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = InkSoft)
    ChartFrame(points, selected, modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(PlotHeight + AxisBand)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val layout = lineLayout(size.width.toFloat(), points.size, gutterFor(measurer, axisStyle, points, axisFormat))
                        selected = layout.nearest(tap.x)
                    }
                }
        ) {
            if (points.isEmpty()) return@Canvas
            val lo = points.minOf { it.value }
            val hi = points.maxOf { it.value }
            val ticks = niceTicks(lo, hi)
            val gutter = gutterFor(measurer, axisStyle, points, axisFormat)
            val plotH = PlotHeight.toPx()
            val layout = lineLayout(size.width, points.size, gutter)
            fun y(v: Double) = (plotH - (v - ticks.first()) / (ticks.last() - ticks.first()) * plotH).toFloat()

            drawGrid(ticks, ::y, gutter, measurer, axisStyle, axisFormat)
            // Crosshair at the selected point
            val sx = layout.x(selected)
            drawLine(InkFaint, Offset(sx, 0f), Offset(sx, plotH), 1.dp.toPx())

            val path = Path()
            points.forEachIndexed { i, p ->
                val o = Offset(layout.x(i), y(p.value))
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, Ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, p ->
                val emphasis = i == points.lastIndex || i == selected
                marker(Offset(layout.x(i), y(p.value)), if (i == points.lastIndex) Highlight else Ink, if (emphasis) 5.dp.toPx() else 4.dp.toPx())
            }
            // First and last labels only: the axis is time order, not a scale.
            xLabel(measurer, points.first().label, layout.x(0), plotH, axisStyle, align = -1)
            if (points.size > 1) xLabel(measurer, points.last().label, layout.x(points.lastIndex), plotH, axisStyle, align = 1)
        }
    }
}

/**
 * Columns from a zero baseline, square-ended like every roadbook box, at most 24dp wide with
 * air between them. [highlightIndex] (the current month) wears the highlighter.
 */
@Composable
fun RoadColumnChart(
    points: List<ChartPoint>,
    highlightIndex: Int,
    axisFormat: (Double) -> String,
    description: String,
    modifier: Modifier = Modifier
) {
    var selected by remember(points.size) { mutableIntStateOf(highlightIndex.coerceIn(0, max(0, points.lastIndex))) }
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = InkSoft)
    ChartFrame(points, selected, modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(PlotHeight + AxisBand)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val gutter = gutterFor(measurer, axisStyle, points, axisFormat, zeroBased = true)
                        val slot = (size.width - gutter) / max(1, points.size)
                        selected = ((tap.x - gutter) / slot).toInt().coerceIn(0, points.lastIndex)
                    }
                }
        ) {
            if (points.isEmpty()) return@Canvas
            val ticks = niceTicks(0.0, max(points.maxOf { it.value }, 1.0), zeroBased = true)
            val gutter = gutterFor(measurer, axisStyle, points, axisFormat, zeroBased = true)
            val plotH = PlotHeight.toPx()
            fun y(v: Double) = (plotH - v / ticks.last() * plotH).toFloat()

            drawGrid(ticks, ::y, gutter, measurer, axisStyle, axisFormat)
            val slot = (size.width - gutter) / points.size
            val barW = minOf(24.dp.toPx(), slot * 0.6f)
            points.forEachIndexed { i, p ->
                val cx = gutter + slot * (i + 0.5f)
                val top = y(p.value)
                if (p.value > 0) {
                    drawRect(
                        if (i == highlightIndex) Highlight else Ink,
                        topLeft = Offset(cx - barW / 2, top),
                        size = Size(barW, plotH - top)
                    )
                }
                if (i == selected) {
                    // Selection: an ink tick under the column, so it never competes with the highlight.
                    drawRect(Ink, topLeft = Offset(cx - barW / 2, plotH + 2.dp.toPx()), size = Size(barW, 3.dp.toPx()))
                }
                xLabel(measurer, p.label, cx, plotH + 4.dp.toPx(), axisStyle, align = 0)
            }
        }
    }
}

@Composable
private fun ChartFrame(points: List<ChartPoint>, selected: Int, modifier: Modifier, chart: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val point = points.getOrNull(selected)
        Text(
            if (point != null) "${point.label} · ${point.readout}" else " ",
            color = Ink,
            style = MaterialTheme.typography.labelMedium
        )
        chart()
    }
}

// --- Layout helpers -----------------------------------------------------------

private class LineLayout(val left: Float, val width: Float, val count: Int) {
    fun x(i: Int): Float = if (count <= 1) left + width / 2 else left + width * i / (count - 1)
    fun nearest(px: Float): Int = (0 until count).minByOrNull { abs(x(it) - px) } ?: 0
}

private fun lineLayout(totalWidth: Float, count: Int, gutter: Float): LineLayout {
    val inset = 10f
    return LineLayout(gutter + inset, totalWidth - gutter - inset * 2, count)
}

private fun gutterFor(
    measurer: TextMeasurer,
    style: TextStyle,
    points: List<ChartPoint>,
    format: (Double) -> String,
    zeroBased: Boolean = false
): Float {
    if (points.isEmpty()) return 0f
    val lo = if (zeroBased) 0.0 else points.minOf { it.value }
    val hi = max(points.maxOf { it.value }, if (zeroBased) 1.0 else lo)
    val widest = niceTicks(lo, hi, zeroBased).maxOf { measurer.measure(format(it), style).size.width }
    return widest + 12f
}

private fun DrawScope.drawGrid(
    ticks: List<Double>,
    y: (Double) -> Float,
    gutter: Float,
    measurer: TextMeasurer,
    style: TextStyle,
    format: (Double) -> String
) {
    ticks.forEachIndexed { index, tick ->
        val ty = y(tick)
        // The baseline is the one ruled line; the rest are hairlines one step off the paper.
        drawLine(
            if (index == 0) Ink else InkFaint,
            Offset(gutter, ty),
            Offset(size.width, ty),
            if (index == 0) 1.5.dp.toPx() else 1.dp.toPx()
        )
        val label = measurer.measure(format(tick), style)
        drawText(label, topLeft = Offset(0f, ty - label.size.height / 2f))
    }
}

private fun DrawScope.marker(center: Offset, color: androidx.compose.ui.graphics.Color, radius: Float) {
    drawCircle(Paper, radius + 2.dp.toPx(), center)
    drawCircle(color, radius, center)
}

private fun DrawScope.xLabel(measurer: TextMeasurer, text: String, x: Float, top: Float, style: TextStyle, align: Int) {
    val layout = measurer.measure(text, style)
    val w = layout.size.width
    val left = when (align) {
        -1 -> x - 6f
        1 -> x - w + 6f
        else -> x - w / 2f
    }.coerceIn(0f, size.width - w)
    drawText(layout, topLeft = Offset(left, top + 4f))
}

/** Three or four round ticks covering [lo, hi]; zero-based charts always start at 0. */
internal fun niceTicks(lo: Double, hi: Double, zeroBased: Boolean = false): List<Double> {
    val min = if (zeroBased) 0.0 else lo
    var max = hi
    if (max - min < 1e-9) max = min + max(1.0, abs(min) * 0.1)
    val rough = (max - min) / 3
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= rough }
    val start = floor(min / step) * step
    val end = ceil(max / step) * step
    val ticks = mutableListOf<Double>()
    var t = start
    while (t <= end + step / 2) {
        ticks += t
        t += step
    }
    return ticks
}

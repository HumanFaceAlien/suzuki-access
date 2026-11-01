package com.adithyapaib.stromx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.adithyapaib.stromx.ride.RoadbookEvent
import com.adithyapaib.stromx.ui.theme.Highlight
import com.adithyapaib.stromx.ui.theme.Ink

/** The roadbook's pictograms. A dot is where you are; the stroke above it is what happened. */
enum class TulipKind { Start, Connected, WarmedUp, Stopped, Moving, FuelDrop, LinkLost, Offline }

fun RoadbookEvent.tulip(): TulipKind = when (this) {
    RoadbookEvent.START -> TulipKind.Start
    RoadbookEvent.CONNECTED -> TulipKind.Connected
    RoadbookEvent.WARMED_UP -> TulipKind.WarmedUp
    RoadbookEvent.STOPPED -> TulipKind.Stopped
    RoadbookEvent.MOVING -> TulipKind.Moving
    RoadbookEvent.FUEL_DROP -> TulipKind.FuelDrop
    RoadbookEvent.LINK_LOST -> TulipKind.LinkLost
}

/**
 * Draws one tulip. When [live] and moving, the arrow's shaft is a highlighter dash that
 * flows upward at a rate proportional to [speedKmh], so the diagram rides with the bike.
 * Decorative: the row's title carries the meaning for screen readers.
 */
@Composable
fun Tulip(kind: TulipKind, modifier: Modifier = Modifier, live: Boolean = false, speedKmh: Int = 0) {
    val flow = remember { mutableFloatStateOf(0f) }
    // With "Remove animations" on, the moving arrow is drawn as a static dash.
    val motion = rememberMotionEnabled()
    val flowing = live && motion && kind == TulipKind.Moving && speedKmh > 0
    LaunchedEffect(flowing, speedKmh) {
        if (!flowing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // About one dash cycle per second at 40 km/h.
                val cycles = (now - last) / 1_000_000_000f * (speedKmh / 40f)
                flow.floatValue = (flow.floatValue + cycles) % 1f
                last = now
            }
        }
    }
    Canvas(modifier.size(64.dp)) {
        val accent = if (live) Highlight else Ink
        val stroke = 3.dp.toPx()
        val w = size.width
        val dot = Offset(w * 0.5f, w * 0.84f)
        fun shaft(toY: Float, color: Color = Ink, effect: PathEffect? = null) =
            drawLine(color, dot, Offset(dot.x, toY), stroke, StrokeCap.Round, pathEffect = effect)
        fun dotMark(filled: Boolean = true) =
            if (filled) drawCircle(Ink, 5.dp.toPx(), dot) else drawCircle(Ink, 5.dp.toPx(), dot, style = Stroke(stroke))

        when (kind) {
            TulipKind.Moving -> {
                val dash = 9.dp.toPx()
                val effect = when {
                    flowing -> PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f), flow.floatValue * dash * 1.7f)
                    live && speedKmh > 0 -> PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f), 0f)
                    else -> null
                }
                shaft(w * 0.22f, accent, effect)
                arrowHead(Offset(dot.x, w * 0.12f), accent, stroke)
                dotMark()
            }
            TulipKind.Stopped -> {
                shaft(w * 0.24f)
                drawLine(accent, Offset(w * 0.28f, w * 0.24f), Offset(w * 0.72f, w * 0.24f), stroke * 1.4f, StrokeCap.Round)
                dotMark()
            }
            TulipKind.Start -> {
                shaft(w * 0.14f)
                val flag = Path().apply {
                    moveTo(dot.x, w * 0.14f); lineTo(w * 0.8f, w * 0.24f); lineTo(dot.x, w * 0.34f); close()
                }
                drawPath(flag, accent)
                dotMark()
            }
            TulipKind.Connected -> {
                dotMark()
                for (r in listOf(0.2f, 0.36f)) {
                    drawArc(
                        Ink, 225f, 90f, false,
                        topLeft = Offset(dot.x - w * r, dot.y - w * r),
                        size = Size(w * r * 2, w * r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            TulipKind.WarmedUp -> {
                drawRoundRect(
                    Ink, topLeft = Offset(w * 0.42f, w * 0.12f), size = Size(w * 0.16f, w * 0.56f),
                    cornerRadius = CornerRadius(w * 0.08f), style = Stroke(stroke)
                )
                drawCircle(accent, w * 0.13f, Offset(w * 0.5f, w * 0.76f))
                drawLine(accent, Offset(w * 0.5f, w * 0.7f), Offset(w * 0.5f, w * 0.34f), stroke, StrokeCap.Round)
            }
            TulipKind.FuelDrop -> {
                drawRect(Ink, topLeft = Offset(w * 0.24f, w * 0.2f), size = Size(w * 0.32f, w * 0.6f), style = Stroke(stroke))
                drawLine(Ink, Offset(w * 0.24f, w * 0.42f), Offset(w * 0.56f, w * 0.42f), stroke)
                val hose = Path().apply {
                    moveTo(w * 0.56f, w * 0.28f); lineTo(w * 0.72f, w * 0.28f); lineTo(w * 0.72f, w * 0.66f)
                }
                drawPath(hose, Ink, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(accent, w * 0.05f, Offset(w * 0.72f, w * 0.78f))
            }
            TulipKind.LinkLost -> {
                drawLine(Ink, dot, Offset(dot.x, w * 0.56f), stroke, StrokeCap.Round)
                drawLine(Ink, Offset(dot.x, w * 0.36f), Offset(dot.x, w * 0.14f), stroke, StrokeCap.Round)
                drawLine(Ink, Offset(w * 0.36f, w * 0.5f), Offset(w * 0.64f, w * 0.4f), stroke, StrokeCap.Round)
                dotMark()
            }
            TulipKind.Offline -> dotMark(filled = false)
        }
    }
}

private fun DrawScope.arrowHead(tip: Offset, color: Color, stroke: Float) {
    val half = size.width * 0.14f
    val head = Path().apply {
        moveTo(tip.x - half, tip.y + half)
        lineTo(tip.x, tip.y)
        lineTo(tip.x + half, tip.y + half)
    }
    drawPath(head, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

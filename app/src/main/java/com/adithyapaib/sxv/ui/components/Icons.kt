package com.adithyapaib.sxv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class AppTab(val label: String) {
    Dashboard("Ride"),
    Rides("Rides"),
    Fuel("Fuel"),
    Service("Service"),
    Logs("Logs"),
    Settings("Settings")
}

/** Drawn tab icons in the tulip stroke, so the app needs no icon library. Decorative: the label carries the meaning. */
@Composable
fun TabIcon(tab: AppTab, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val px = 2.dp.toPx()
        val stroke = Stroke(width = px, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            AppTab.Dashboard -> {
                drawCircle(tint, w * 0.11f, Offset(w * 0.5f, w * 0.82f))
                drawLine(tint, Offset(w * 0.5f, w * 0.78f), Offset(w * 0.5f, w * 0.16f), px, StrokeCap.Round)
                val head = Path().apply {
                    moveTo(w * 0.3f, w * 0.34f); lineTo(w * 0.5f, w * 0.12f); lineTo(w * 0.7f, w * 0.34f)
                }
                drawPath(head, tint, style = stroke)
            }
            AppTab.Rides -> {
                // Stopwatch: the case, the crown, and a hand on the elapsed time.
                drawCircle(tint, w * 0.32f, Offset(w * 0.5f, w * 0.6f), style = stroke)
                drawLine(tint, Offset(w * 0.5f, w * 0.6f), Offset(w * 0.5f, w * 0.42f), px, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.38f, w * 0.14f), Offset(w * 0.62f, w * 0.14f), px, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.5f, w * 0.14f), Offset(w * 0.5f, w * 0.26f), px, StrokeCap.Round)
            }
            AppTab.Fuel -> {
                // Pump body, a level line, and the hose curling to the nozzle.
                drawRect(tint, topLeft = Offset(w * 0.18f, w * 0.16f), size = Size(w * 0.4f, w * 0.68f), style = stroke)
                drawLine(tint, Offset(w * 0.18f, w * 0.4f), Offset(w * 0.58f, w * 0.4f), px)
                val hose = Path().apply {
                    moveTo(w * 0.58f, w * 0.3f); lineTo(w * 0.78f, w * 0.3f); lineTo(w * 0.78f, w * 0.7f); lineTo(w * 0.86f, w * 0.7f)
                }
                drawPath(hose, tint, style = stroke)
            }
            AppTab.Service -> {
                // Ruled meter with a tick: the service countdown.
                drawRect(tint, topLeft = Offset(w * 0.1f, w * 0.38f), size = Size(w * 0.8f, w * 0.24f), style = stroke)
                drawRect(tint, topLeft = Offset(w * 0.1f, w * 0.38f), size = Size(w * 0.5f, w * 0.24f))
                drawLine(tint, Offset(w * 0.75f, w * 0.18f), Offset(w * 0.75f, w * 0.82f), px, StrokeCap.Round)
            }
            AppTab.Logs -> {
                listOf(0.24f, 0.5f, 0.76f).forEachIndexed { index, y ->
                    drawLine(tint, Offset(w * 0.1f, w * y), Offset(w * 0.22f, w * y), px, StrokeCap.Round)
                    val end = if (index == 2) 0.62f else 0.9f
                    drawLine(tint, Offset(w * 0.34f, w * y), Offset(w * end, w * y), px, StrokeCap.Round)
                }
            }
            AppTab.Settings -> {
                drawLine(tint, Offset(w * 0.1f, w * 0.32f), Offset(w * 0.9f, w * 0.32f), px, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.1f, w * 0.68f), Offset(w * 0.9f, w * 0.68f), px, StrokeCap.Round)
                drawRect(tint, topLeft = Offset(w * 0.54f, w * 0.2f), size = Size(w * 0.24f, w * 0.24f))
                drawRect(tint, topLeft = Offset(w * 0.22f, w * 0.56f), size = Size(w * 0.24f, w * 0.24f))
            }
        }
    }
}

package com.adithyapaib.sxv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppTab(val label: String) {
    Dashboard("Dashboard"),
    Message("Message"),
    Logs("Logs"),
    Settings("Settings")
}

/** Drawn icons so the app needs no icon-library dependency. Decorative: the tab label carries the meaning. */
@Composable
fun TabIcon(tab: AppTab, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val w = size.width
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (tab) {
            AppTab.Dashboard -> {
                drawArc(
                    color = tint, startAngle = 160f, sweepAngle = 220f, useCenter = false,
                    topLeft = Offset(w * 0.1f, w * 0.14f), size = Size(w * 0.8f, w * 0.8f), style = stroke
                )
                drawLine(tint, Offset(w * 0.5f, w * 0.54f), Offset(w * 0.7f, w * 0.34f), 2.dp.toPx(), StrokeCap.Round)
            }
            AppTab.Message -> {
                drawRoundRect(
                    color = tint, topLeft = Offset(w * 0.1f, w * 0.14f), size = Size(w * 0.8f, w * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f), style = stroke
                )
                drawLine(tint, Offset(w * 0.3f, w * 0.7f), Offset(w * 0.26f, w * 0.88f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * 0.26f, w * 0.88f), Offset(w * 0.46f, w * 0.7f), 2.dp.toPx(), StrokeCap.Round)
            }
            AppTab.Logs -> {
                listOf(0.24f, 0.5f, 0.76f).forEachIndexed { index, y ->
                    val end = if (index == 2) 0.6f else 0.88f
                    drawLine(tint, Offset(w * 0.12f, w * y), Offset(w * end, w * y), 2.dp.toPx(), StrokeCap.Round)
                }
            }
            AppTab.Settings -> {
                drawLine(tint, Offset(w * 0.1f, w * 0.32f), Offset(w * 0.9f, w * 0.32f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(tint, Offset(w * 0.1f, w * 0.68f), Offset(w * 0.9f, w * 0.68f), 2.dp.toPx(), StrokeCap.Round)
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.65f, w * 0.32f), style = stroke)
                drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.35f, w * 0.68f), style = stroke)
            }
        }
    }
}

/** Small check mark used for "sensor verified". */
@Composable
fun CheckIcon(tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.18f, w * 0.54f)
            lineTo(w * 0.42f, w * 0.76f)
            lineTo(w * 0.84f, w * 0.26f)
        }
        drawPath(path, tint, style = stroke)
    }
}

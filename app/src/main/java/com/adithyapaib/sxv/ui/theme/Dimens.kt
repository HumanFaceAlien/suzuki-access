package com.adithyapaib.sxv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 8pt grid. Use these instead of raw dp so spacing stays consistent. */
object Spacing {
    val Xs = 4.dp
    val S = 8.dp
    val M = 16.dp
    val L = 24.dp
    val Xl = 32.dp
}

object Dimens {
    /** Glove-friendly minimum for every tappable control. */
    val MinTouch = 56.dp
    val CardShape = RoundedCornerShape(16.dp)
    val FieldShape = RoundedCornerShape(12.dp)
}

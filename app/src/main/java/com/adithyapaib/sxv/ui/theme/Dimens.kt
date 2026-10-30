package com.adithyapaib.sxv.ui.theme

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

    /** The roadbook's heavy box rule: outer frames of every box. */
    val RuleHeavy = 2.5.dp

    /** Row and column separators inside a box. */
    val RuleLight = 1.5.dp

    /** How far a pressed block's inked face sinks inside its rule. */
    val PressInset = 3.dp

    /** Roadbook columns: distance, tulip diagram, then the note takes the rest. */
    val DistanceColumn = 96.dp
    val TulipColumn = 84.dp
}

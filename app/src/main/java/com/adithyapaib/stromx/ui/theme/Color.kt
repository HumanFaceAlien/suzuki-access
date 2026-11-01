package com.adithyapaib.stromx.ui.theme

import androidx.compose.ui.graphics.Color

// Roadbook: white roll paper, ink, and one rally highlighter. Nothing else carries colour.

/** The roll. Every screen sits on it. */
val Paper = Color(0xFFFFFFFF)

/** Rules, numerals, text, pressed blocks. 18.4:1 on paper. */
val Ink = Color(0xFF141414)

/** Secondary text, labels, minor distance ticks and scale marks. 7.2:1 on paper. */
val InkSoft = Color(0xFF575757)

/** Disabled block outlines only. Never text. */
val InkFaint = Color(0xFFD6D6D6)

/**
 * Rally highlighter. Marks what is live right now: the current row, the selected tab, the
 * temperature needle, the one action. 3.1:1 on paper, so it is used for fills, marks and
 * display-size numerals only; text sitting on it is [Ink] (5.9:1).
 */
val Highlight = Color(0xFFFF5A1F)

/** The current roadbook row's wash. */
val HighlightWash = Color(0xFFFFF1EA)

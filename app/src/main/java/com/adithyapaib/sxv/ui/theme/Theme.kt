package com.adithyapaib.sxv.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// The roll is white by brief: the rider asked for a white ground, and it reads in sunlight.
private val RoadbookColorScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Highlight,
    onPrimaryContainer = Ink,
    secondary = Highlight,
    onSecondary = Ink,
    tertiary = Highlight,
    onTertiary = Ink,
    error = Ink,
    onError = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkSoft,
    surfaceContainer = Paper,
    surfaceContainerLow = Paper,
    surfaceContainerHigh = Paper,
    outline = Ink,
    outlineVariant = InkFaint,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Highlight
)

// Roadbook boxes are square; the only rounding is the switch thumb Material draws itself.
private val SquareShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp)
)

/** Material switch in roadbook ink: inked track when on, ruled outline when off. */
@Composable
fun inkSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = Paper,
    checkedTrackColor = Ink,
    checkedBorderColor = Ink,
    uncheckedThumbColor = Ink,
    uncheckedTrackColor = Paper,
    uncheckedBorderColor = Ink
)

@Composable
fun SXVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RoadbookColorScheme,
        typography = Typography,
        shapes = SquareShapes
    ) {
        // Text selection drawn with the highlighter, like marking the roll.
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = Highlight,
                backgroundColor = Highlight.copy(alpha = 0.35f)
            ),
            content = content
        )
    }
}

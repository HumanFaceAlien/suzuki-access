package com.adithyapaib.sxv.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ui.theme.Accent
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.FuelOrange
import com.adithyapaib.sxv.ui.theme.OnAccent
import com.adithyapaib.sxv.ui.theme.OutlineStrong
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.StatusAmber
import com.adithyapaib.sxv.ui.theme.StatusGreen
import com.adithyapaib.sxv.ui.theme.StatusRed
import com.adithyapaib.sxv.ui.theme.SurfaceCard
import com.adithyapaib.sxv.ui.theme.SurfaceInput
import com.adithyapaib.sxv.ui.theme.SurfaceRaised
import com.adithyapaib.sxv.ui.theme.TextMuted
import com.adithyapaib.sxv.ui.theme.TextPrimary
import com.adithyapaib.sxv.ui.theme.TextSecondary

/** The one place caps are used: small section labels. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(text.uppercase(), modifier, color = color, style = MaterialTheme.typography.labelSmall)
}

// --- Status ---------------------------------------------------------------

data class StatusStyle(val label: String, val color: Color, val description: String)

fun BleConnectionState.statusStyle(): StatusStyle = when (this) {
    BleConnectionState.CONNECTED -> StatusStyle("Connected", StatusGreen, "connected to the bike")
    BleConnectionState.CONNECTING -> StatusStyle("Connecting", StatusAmber, "connecting to the bike")
    BleConnectionState.ERROR -> StatusStyle("Connection error", StatusRed, "connection error")
    BleConnectionState.DISCONNECTED -> StatusStyle("Disconnected", StatusRed, "disconnected from the bike")
}

/** Green connected, amber connecting, red disconnected or error. The label repeats the colour for colour-blind riders. */
@Composable
fun StatusChip(state: BleConnectionState, modifier: Modifier = Modifier) {
    val style = state.statusStyle()
    val pulse by rememberInfiniteTransition(label = "connecting-pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "connecting-pulse-alpha"
    )
    val dotAlpha = if (state == BleConnectionState.CONNECTING) pulse else 1f
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = "Connection status: ${style.description}" },
        color = style.color.copy(alpha = 0.16f),
        shape = CircleShape,
        border = BorderStroke(1.dp, style.color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.M - 4.dp, vertical = Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            Box(Modifier.size(10.dp).alpha(dotAlpha).clip(CircleShape).background(style.color))
            Text(style.label, color = style.color, style = MaterialTheme.typography.labelMedium)
        }
    }
}

// --- Buttons --------------------------------------------------------------

enum class ButtonVariant { Filled, Outlined }

/**
 * 56dp-tall full-width button. When disabled it says why, so a muted button never looks broken.
 * Filled uses the yellow accent, which is reserved for the primary action on a screen.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: ButtonVariant = ButtonVariant.Filled,
    disabledReason: String? = null
) {
    val buttonModifier = Modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        when (variant) {
            ButtonVariant.Filled -> Button(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                shape = Dimens.FieldShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = SurfaceRaised,
                    disabledContentColor = TextMuted
                )
            ) { Text(text, style = MaterialTheme.typography.labelLarge) }

            ButtonVariant.Outlined -> OutlinedButton(
                onClick = onClick,
                enabled = enabled,
                modifier = buttonModifier,
                shape = Dimens.FieldShape,
                border = BorderStroke(1.dp, OutlineStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary, disabledContentColor = TextMuted)
            ) { Text(text, style = MaterialTheme.typography.labelLarge) }
        }
        if (!enabled && disabledReason != null) {
            Text(disabledReason, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// --- Cards ----------------------------------------------------------------

@Composable
private fun cardColors() = CardDefaults.cardColors(containerColor = SurfaceCard)

/** Speed is the hero: 88sp, one line with its unit, and the number rolls instead of jumping. */
@Composable
fun HeroSpeedCard(speed: Int, unit: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val shown by animateIntAsState(
        targetValue = speed,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "speed-roll"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Speed $speed $unit" },
        shape = Dimens.CardShape,
        colors = cardColors()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.L).alpha(if (dimmed) 0.5f else 1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            Label("Speed")
            Row {
                Text(
                    shown.toString(),
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.displayLarge,
                    maxLines = 1
                )
                Spacer(Modifier.width(Spacing.S))
                Text(
                    unit,
                    modifier = Modifier.alignByBaseline(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

/** Label on top, "value unit" on one line below. */
@Composable
fun MetricCard(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    Card(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label $value $unit" },
        shape = Dimens.CardShape,
        colors = cardColors()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.M).alpha(if (dimmed) 0.5f else 1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            Label(label)
            Row {
                Text(
                    value,
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1
                )
                Spacer(Modifier.width(Spacing.Xs))
                Text(
                    unit,
                    modifier = Modifier.alignByBaseline(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private enum class FuelLevel(val color: Color, val word: String?) {
    Normal(Accent, null),
    Low(FuelOrange, "Low"),
    Reserve(StatusRed, "Reserve")
}

private fun fuelLevel(bars: Int) = when {
    bars <= 1 -> FuelLevel.Reserve
    bars == 2 -> FuelLevel.Low
    else -> FuelLevel.Normal
}

/**
 * Fuel in litres plus a six-segment bar that turns orange when low and red on reserve.
 * Litres are an estimate (2 L per bar, uncalibrated), so the value carries a "≈".
 * Range is deliberately not shown: the cluster sends no consumption data to derive it from.
 */
@Composable
fun FuelCard(
    bars: Int,
    liters: Double,
    isValid: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false
) {
    val filled = if (isValid) bars.coerceIn(0, 6) else 0
    val level = fuelLevel(filled)
    val litersText = String.format(java.util.Locale.getDefault(), "%.1f", liters)
    val description = if (isValid) {
        "Fuel $filled of 6 bars, about $litersText liters${level.word?.let { ", $it" }.orEmpty()}, sensor verified"
    } else {
        "Fuel level syncing"
    }
    Card(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        shape = Dimens.CardShape,
        colors = cardColors()
    ) {
        Column(
            modifier = Modifier.padding(Spacing.M).alpha(if (dimmed) 0.5f else 1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.S + 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Label("Fuel")
                if (isValid) {
                    CheckIcon(tint = StatusGreen)
                } else {
                    Text("Syncing", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row {
                Text(
                    if (isValid) "≈ $litersText" else "—",
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1
                )
                Spacer(Modifier.width(Spacing.Xs))
                Text(
                    "L",
                    modifier = Modifier.alignByBaseline(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                repeat(6) { index ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (index < filled) level.color else SurfaceRaised)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isValid) "$filled of 6 bars" else "Waiting for the fuel sender",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                level.word?.takeIf { isValid }?.let {
                    Text(it, color = level.color, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// --- Inputs ---------------------------------------------------------------

/** Floating-label field with a quiet sentence-case hint. Caps keyboard stays on: the cluster font is upper-case. */
@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch),
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onSend = { onImeAction() }),
        shape = Dimens.FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceInput,
            unfocusedContainerColor = SurfaceInput,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = TextPrimary,
            unfocusedBorderColor = OutlineStrong,
            focusedLabelColor = TextPrimary,
            unfocusedLabelColor = TextSecondary,
            cursorColor = TextPrimary,
            focusedPlaceholderColor = TextMuted,
            unfocusedPlaceholderColor = TextMuted
        )
    )
}

/** Two-or-more option switch, 56dp tall. Selected segment uses the accent. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Dimens.FieldShape)
            .background(SurfaceRaised)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Dimens.MinTouch - 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Accent else SurfaceRaised)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option,
                    color = if (selected) OnAccent else TextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

package com.adithyapaib.sxv.ui.components

import androidx.compose.animation.AnimatedContent
import android.provider.Settings
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.Highlight
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkFaint
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.Paper
import com.adithyapaib.sxv.ui.theme.Spacing

// --- Roadbook primitives --------------------------------------------------

/** Small typewritten box label. The only place caps are used. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier, color: Color = InkSoft) {
    Text(text.uppercase(), modifier, color = color, style = MaterialTheme.typography.labelSmall)
}

/** A roadbook box: paper fill inside a heavy ink rule, square corners. */
fun Modifier.inkBox(fill: Color = Paper, rule: Dp = Dimens.RuleHeavy): Modifier =
    this.background(fill).border(rule, Ink, RectangleShape)

@Composable
fun HRule(modifier: Modifier = Modifier, thickness: Dp = Dimens.RuleLight, color: Color = Ink) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
fun VRule(modifier: Modifier = Modifier, thickness: Dp = Dimens.RuleLight, color: Color = Ink) {
    Box(modifier.fillMaxHeight().width(thickness).background(color))
}

/**
 * Digits that roll like a mechanical odometer: each changed digit slides up when the value
 * grows and down when it falls. Non-digits stay put. Announced as one value.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    if (!rememberMotionEnabled()) {
        Text(text, modifier, color = color, style = style)
        return
    }
    val previous = remember { object { var value = text } }
    val rising = text.numeric() >= previous.value.numeric()
    previous.value = text
    Row(modifier.clipToBounds().clearAndSetSemantics { contentDescription = text }) {
        text.forEachIndexed { index, char ->
            // Keyed from the right so units stay aligned when the number gains a digit.
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val enter = slideInVertically(tween(320, easing = FastOutSlowInEasing)) { if (rising) it else -it } + fadeIn(tween(160))
                        val exit = slideOutVertically(tween(320, easing = FastOutSlowInEasing)) { if (rising) -it else it } + fadeOut(tween(160))
                        (enter togetherWith exit).using(SizeTransform(clip = true))
                    },
                    label = "roll"
                ) { shown -> Text(shown.toString(), style = style, color = color) }
            }
        }
    }
}

private fun String.numeric(): Double = filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0

// --- Status ---------------------------------------------------------------

data class StatusStyle(val label: String, val description: String)

fun BleConnectionState.statusStyle(): StatusStyle = when (this) {
    BleConnectionState.CONNECTED -> StatusStyle("Connected", "connected to the bike")
    BleConnectionState.CONNECTING -> StatusStyle("Connecting", "connecting to the bike")
    BleConnectionState.ERROR -> StatusStyle("Connection error", "connection error")
    BleConnectionState.DISCONNECTED -> StatusStyle("Offline", "not connected to the bike")
}

/**
 * The link's state as a mark, never colour alone: a solid highlighter square when live, the
 * same square breathing while connecting, an empty ink square offline, a crossed square on error.
 */
@Composable
fun StatusMark(state: BleConnectionState, modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val breathe by rememberInfiniteTransition(label = "connecting").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "connecting-alpha"
    )
    val alpha = if (state == BleConnectionState.CONNECTING && motion) breathe else 1f
    Canvas(modifier.size(14.dp).alpha(alpha)) {
        val w = size.width
        val stroke = 2.dp.toPx()
        when (state) {
            BleConnectionState.CONNECTED, BleConnectionState.CONNECTING -> {
                drawRect(Highlight)
                drawRect(Ink, style = Stroke(stroke))
            }
            BleConnectionState.DISCONNECTED -> drawRect(Ink, style = Stroke(stroke))
            BleConnectionState.ERROR -> {
                drawRect(Ink)
                drawLine(Paper, Offset(w * 0.25f, w * 0.25f), Offset(w * 0.75f, w * 0.75f), stroke)
                drawLine(Paper, Offset(w * 0.75f, w * 0.25f), Offset(w * 0.25f, w * 0.75f), stroke)
            }
        }
    }
}

// --- Press blocks ---------------------------------------------------------

enum class BlockStyle {
    /** The one action on a screen: highlighter face on an ink base. */
    Highlight,
    /** Everything else that presses: paper face on an ink base. */
    Paper
}

/**
 * A ruled block that presses like a key: on press the face floods with ink and sinks
 * [Dimens.PressInset] inside its rule, with a key tick under the thumb. No shadow, no base:
 * depth comes from the ink flood, the roadbook's own mark. Disabled blocks go faint and say
 * why underneath.
 */
@Composable
fun PressBlock(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BlockStyle = BlockStyle.Highlight,
    enabled: Boolean = true,
    disabledReason: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val down = pressed && enabled
    val inset by animateDpAsState(
        targetValue = if (down) Dimens.PressInset else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 1800f),
        label = "press-inset"
    )
    val rest = when {
        !enabled -> Paper
        style == BlockStyle.Highlight -> Highlight
        else -> Paper
    }
    val face by animateColorAsState(if (down) Ink else rest, tween(90), label = "press-face")
    val label by animateColorAsState(
        when {
            !enabled -> InkSoft
            down -> if (style == BlockStyle.Highlight) Highlight else Paper
            else -> Ink
        },
        tween(90),
        label = "press-label"
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dimens.MinTouch)
                .border(Dimens.RuleHeavy, if (enabled) Ink else InkFaint, RectangleShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onClick()
                    }
                )
                .padding(Dimens.RuleHeavy + inset)
                .background(face),
            contentAlignment = Alignment.Center
        ) {
            Text(text.uppercase(), color = label, style = MaterialTheme.typography.labelLarge)
        }
        if (!enabled && disabledReason != null) {
            Text(disabledReason, color = InkSoft, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** False when the rider turned "Remove animations" on (animator duration scale 0). */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

/** A quiet text action at full touch height, for secondary moves like Copy or Disconnect. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = Dimens.MinTouch)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.S),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text.uppercase(),
            color = if (enabled) Ink else InkSoft,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

// --- Inputs ---------------------------------------------------------------

/** Square ruled field with a floating label. Caps keyboard stays on: the cluster font is upper-case. */
@Composable
fun InkField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    hint: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Characters,
    error: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.MinTouch),
        label = { Text(label) },
        placeholder = { Text(hint) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType, imeAction = imeAction),
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onSend = { onImeAction() }),
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Paper,
            unfocusedContainerColor = Paper,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedBorderColor = Ink,
            unfocusedBorderColor = Ink,
            focusedLabelColor = Ink,
            unfocusedLabelColor = InkSoft,
            cursorColor = Highlight,
            focusedPlaceholderColor = InkSoft,
            unfocusedPlaceholderColor = InkSoft,
            // Errors are said in words under the field; the field stays ink, with a paper fill.
            errorContainerColor = Paper,
            errorBorderColor = Ink,
            errorLabelColor = Ink,
            errorSupportingTextColor = Ink,
            errorCursorColor = Highlight,
            errorTextColor = Ink
        )
    )
}

/** Ruled segmented choice; the chosen box is inked solid. */
@Composable
fun InkSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth().inkBox().selectableGroup()) {
        options.forEachIndexed { index, option ->
            if (index > 0) VRule(Modifier.height(Dimens.MinTouch), thickness = Dimens.RuleHeavy)
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(Dimens.MinTouch)
                    .background(if (selected) Ink else Paper)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.uppercase(),
                    color = if (selected) Paper else Ink,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

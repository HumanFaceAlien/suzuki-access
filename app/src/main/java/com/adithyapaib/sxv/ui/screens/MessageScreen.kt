package com.adithyapaib.sxv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ble.SuzukiDisplaySender
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.MessageInput
import com.adithyapaib.sxv.ui.components.PrimaryButton
import com.adithyapaib.sxv.ui.theme.Accent
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.OutlineStrong
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.StatusRed
import com.adithyapaib.sxv.ui.theme.SurfaceCard
import com.adithyapaib.sxv.ui.theme.SurfaceInput
import com.adithyapaib.sxv.ui.theme.TextMuted
import com.adithyapaib.sxv.ui.theme.TextPrimary
import com.adithyapaib.sxv.ui.theme.TextSecondary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

private val Presets = listOf("Fuel stop", "Lunch", "Follow me")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageScreen(
    connectionState: BleConnectionState,
    primary: String,
    secondary: String,
    isSending: Boolean,
    recentMessages: List<String>,
    onPrimaryChange: (String) -> Unit,
    onSecondaryChange: (String) -> Unit,
    onSend: () -> Unit,
    onClearRecent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val limit = SuzukiDisplaySender.MAX_VISIBLE_TEXT_LENGTH
    val message = listOf(primary, secondary).filter { it.isNotBlank() }.joinToString(" | ")
    val over = message.length - limit
    val connected = connectionState == BleConnectionState.CONNECTED
    val canSend = connected && message.isNotBlank() && over <= 0 && !isSending
    val reason = when {
        !connected -> "Connect to the bike to send a message."
        message.isBlank() -> "Type a message to send."
        over > 0 -> "Message is $over ${if (over == 1) "character" else "characters"} too long."
        else -> null
    }

    fun use(text: String, second: String = "") {
        onPrimaryChange(text.take(limit))
        onSecondaryChange(second)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text("Message", style = MaterialTheme.typography.headlineSmall)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Quick messages")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                Presets.forEach { preset -> PresetChip(preset) { use(preset) } }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
            MessageInput(
                value = primary,
                onValueChange = { onPrimaryChange(it.take(limit)) },
                label = "Message",
                hint = "What should the cluster say?"
            )
            MessageInput(
                value = secondary,
                onValueChange = { onSecondaryChange(it.take(limit)) },
                label = "Second line (optional)",
                hint = "Adds to the same display line",
                imeAction = ImeAction.Done
            )
            Text(
                "${message.length}/$limit",
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                color = if (over > 0) StatusRed else TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (message.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Label("Preview")
                // The cluster shows one fixed-pitch line; this approximates it.
                Surface(color = SurfaceInput, shape = Dimens.FieldShape, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        message.take(limit),
                        modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.M - 4.dp),
                        color = Accent,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp
                        )
                    )
                }
            }
        }

        PrimaryButton(
            text = if (isSending) "Sending…" else "Send to display",
            onClick = onSend,
            enabled = canSend,
            disabledReason = reason
        )

        if (recentMessages.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Label("Recent")
                    TextButton(onClick = onClearRecent, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Clear", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                recentMessages.forEach { recent ->
                    RecentRow(recent) { use(recent) }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = Dimens.MinTouch)
            .clip(CircleShape)
            .border(BorderStroke(1.dp, OutlineStrong), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.L),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RecentRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.MinTouch)
            .clip(Dimens.FieldShape)
            .background(SurfaceCard)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.M),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = TextPrimary, maxLines = 1, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("Use", color = TextMuted, style = MaterialTheme.typography.labelMedium)
    }
}

// --- Previews -------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 900)
@Composable
private fun MessageReadyPreview() = SXVTheme {
    MessageScreen(
        BleConnectionState.CONNECTED, "FUEL STOP", "", false,
        listOf("LUNCH", "FOLLOW ME"), {}, {}, {}, {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 900)
@Composable
private fun MessageDisconnectedPreview() = SXVTheme {
    MessageScreen(BleConnectionState.DISCONNECTED, "", "", false, emptyList(), {}, {}, {}, {})
}

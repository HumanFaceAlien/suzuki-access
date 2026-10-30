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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ble.SuzukiDisplaySender
import com.adithyapaib.sxv.ui.components.HRule
import com.adithyapaib.sxv.ui.components.InkField
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.PressBlock
import com.adithyapaib.sxv.ui.components.TextAction
import com.adithyapaib.sxv.ui.components.VRule
import com.adithyapaib.sxv.ui.components.inkBox
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.Highlight
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.Mono
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing

private val Presets = listOf("Fuel stop", "Lunch", "Follow me")

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
        message.isBlank() -> "Type a message or pick a quick one."
        over > 0 -> "Message is $over ${if (over == 1) "character" else "characters"} too long."
        else -> null
    }

    fun use(text: String) {
        onPrimaryChange(text.uppercase().take(limit))
        onSecondaryChange("")
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.L)
    ) {
        Text("Message", color = Ink, style = MaterialTheme.typography.headlineSmall)

        // The cluster's line, as it will read: ink display, highlighter characters.
        Box(
            Modifier.fillMaxWidth().background(Ink).padding(horizontal = Spacing.M, vertical = Spacing.M - 2.dp)
        ) {
            Text(
                message.take(limit).ifEmpty { "_" },
                color = Highlight,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Mono, fontSize = 20.sp, letterSpacing = 2.sp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Label("Quick messages")
            Row(Modifier.fillMaxWidth().inkBox()) {
                Presets.forEachIndexed { index, preset ->
                    if (index > 0) VRule(Modifier.heightIn(min = Dimens.MinTouch), thickness = Dimens.RuleHeavy)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = Dimens.MinTouch)
                            .clickable(role = Role.Button) { use(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(preset.uppercase(), color = Ink, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.M - 4.dp)) {
            InkField(primary, { onPrimaryChange(it.take(limit)) }, label = "Message", hint = "What should the cluster say?")
            InkField(
                secondary,
                { onSecondaryChange(it.take(limit)) },
                label = "Second part (optional)",
                hint = "Joined to the same line",
                imeAction = ImeAction.Done
            )
            Text(
                "${message.length}/$limit",
                Modifier.fillMaxWidth(),
                color = if (over > 0) Ink else InkSoft,
                textAlign = TextAlign.End,
                style = if (over > 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
            )
        }

        PressBlock(
            text = if (isSending) "Sending…" else "Send to cluster",
            onClick = onSend,
            enabled = canSend,
            disabledReason = reason
        )

        if (recentMessages.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Label("Recent", Modifier.weight(1f))
                    TextAction("Clear", onClearRecent)
                }
                Column(Modifier.fillMaxWidth().inkBox()) {
                    recentMessages.forEachIndexed { index, recent ->
                        if (index > 0) HRule()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = Dimens.MinTouch)
                                .clickable(role = Role.Button) { use(recent) }
                                .padding(horizontal = Spacing.M),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(recent, Modifier.weight(1f), color = Ink, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                            Label("Use")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 900)
@Composable
private fun MessageReadyPreview() = SXVTheme {
    MessageScreen(BleConnectionState.CONNECTED, "FUEL STOP", "", false, listOf("LUNCH", "FOLLOW ME"), {}, {}, {}, {})
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 900)
@Composable
private fun MessageOfflinePreview() = SXVTheme {
    MessageScreen(BleConnectionState.DISCONNECTED, "", "", false, emptyList(), {}, {}, {}, {})
}

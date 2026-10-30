package com.adithyapaib.sxv.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ui.components.HRule
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.TextAction
import com.adithyapaib.sxv.ui.components.inkBox
import com.adithyapaib.sxv.ui.components.statusStyle
import com.adithyapaib.sxv.ui.formatAge
import com.adithyapaib.sxv.ui.rememberNowMs
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.Ink
import com.adithyapaib.sxv.ui.theme.InkSoft
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.inkSwitchColors

/** All developer-facing detail lives here: BLE state, GATT characteristics and the raw trace. */
@Composable
@Suppress("DEPRECATION")
fun LogsScreen(
    connectionState: BleConnectionState,
    lastUpdateMs: Long,
    logs: List<String>,
    verbose: Boolean,
    onVerboseChange: (Boolean) -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val now = rememberNowMs()
    val newestFirst = remember(logs) { logs.asReversed() }
    val lastFrame = if (lastUpdateMs == 0L) "None yet" else formatAge((now - lastUpdateMs).coerceAtLeast(0L))

    Column(
        modifier.fillMaxSize().padding(horizontal = Spacing.M).padding(top = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Text("Logs", color = Ink, style = MaterialTheme.typography.headlineSmall)

        Column(Modifier.fillMaxWidth().inkBox()) {
            DetailRow("State", connectionState.statusStyle().label)
            HRule()
            DetailRow("Last frame", lastFrame)
            HRule()
            DetailRow("Service · write · notify", "FFF0 · FFF1 · FFF2")
            HRule()
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Dimens.MinTouch)
                    .toggleable(value = verbose, role = Role.Switch, onValueChange = onVerboseChange)
                    .padding(horizontal = Spacing.M, vertical = Spacing.S),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Verbose BLE logging", color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "GATT table, reads, RSSI, link parameters and a per-byte breakdown of each frame.",
                        color = InkSoft,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = verbose, onCheckedChange = null, colors = inkSwitchColors())
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("Activity", Modifier.weight(1f))
            TextAction("Copy", { clipboard.setText(AnnotatedString(logs.joinToString("\n"))) }, enabled = logs.isNotEmpty())
            TextAction(
                "Share",
                {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "SXV BLE log")
                        putExtra(Intent.EXTRA_TEXT, logs.joinToString("\n"))
                    }
                    context.startActivity(Intent.createChooser(send, "Share BLE log"))
                },
                enabled = logs.isNotEmpty()
            )
            TextAction("Clear", onClearLogs, enabled = logs.isNotEmpty())
        }

        Column(Modifier.weight(1f).fillMaxWidth().inkBox()) {
            if (newestFirst.isEmpty()) {
                Text(
                    "Nothing yet. Bluetooth events will appear here.",
                    Modifier.padding(Spacing.M),
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.M),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
                ) {
                    items(newestFirst) { line ->
                        Text(line, color = Ink, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(name: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = Spacing.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name.uppercase(), Modifier.weight(1f), color = InkSoft, style = MaterialTheme.typography.labelSmall)
        Text(value, color = Ink, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 390, heightDp = 800)
@Composable
private fun LogsPreview() = SXVTheme {
    LogsScreen(
        BleConnectionState.CONNECTED,
        System.currentTimeMillis() - 4_000,
        listOf("08:49:30.045  RX 30 bytes: A5-37-30...", "08:49:30.050  Telemetry parsed: odometer=12334"),
        verbose = true,
        onVerboseChange = {},
        onClearLogs = {}
    )
}

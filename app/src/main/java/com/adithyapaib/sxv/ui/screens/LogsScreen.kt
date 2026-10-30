package com.adithyapaib.sxv.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ui.components.Label
import com.adithyapaib.sxv.ui.components.statusStyle
import com.adithyapaib.sxv.ui.formatAge
import com.adithyapaib.sxv.ui.rememberNowMs
import com.adithyapaib.sxv.ui.theme.Dimens
import com.adithyapaib.sxv.ui.theme.SXVTheme
import com.adithyapaib.sxv.ui.theme.Spacing
import com.adithyapaib.sxv.ui.theme.SurfaceCard
import com.adithyapaib.sxv.ui.theme.SurfaceInput
import com.adithyapaib.sxv.ui.theme.TextMuted
import com.adithyapaib.sxv.ui.theme.TextPrimary
import com.adithyapaib.sxv.ui.theme.TextSecondary

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
        modifier = modifier.fillMaxSize().padding(horizontal = Spacing.M).padding(top = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.M)
    ) {
        Text("Logs", style = MaterialTheme.typography.headlineSmall)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = Dimens.CardShape,
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Label("Connection details")
                DetailRow("State", connectionState.statusStyle().label)
                DetailRow("Last frame", lastFrame)
                DetailRow("Service", "FFF0")
                DetailRow("Write", "FFF1")
                DetailRow("Notify", "FFF2")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Verbose BLE logging", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "GATT table, characteristic reads, RSSI, link parameters and per-byte frame breakdown",
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = verbose, onCheckedChange = onVerboseChange)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Label("Activity")
            Row {
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(logs.joinToString("\n"))) },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Copy", style = MaterialTheme.typography.labelMedium) }
                TextButton(
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "SXV BLE log")
                            putExtra(Intent.EXTRA_TEXT, logs.joinToString("\n"))
                        }
                        context.startActivity(Intent.createChooser(send, "Share BLE log"))
                    },
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Share", style = MaterialTheme.typography.labelMedium) }
                TextButton(
                    onClick = onClearLogs,
                    enabled = logs.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Clear", style = MaterialTheme.typography.labelMedium) }
            }
        }

        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = Dimens.CardShape,
            colors = CardDefaults.cardColors(containerColor = SurfaceInput)
        ) {
            if (newestFirst.isEmpty()) {
                Text(
                    "Nothing yet. Bluetooth events will appear here.",
                    modifier = Modifier.padding(Spacing.M),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.M),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
                ) {
                    items(newestFirst) { line ->
                        Text(
                            line,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, widthDp = 360, heightDp = 800)
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

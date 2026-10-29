package com.adithyapaib.sxv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.text.KeyboardOptions
import com.adithyapaib.sxv.ble.BleConnectionState
import com.adithyapaib.sxv.ble.BleViewModel
import com.adithyapaib.sxv.ble.SuzukiDisplaySender
import com.adithyapaib.sxv.ble.VStromTelemetry
import com.adithyapaib.sxv.ui.theme.Gold
import com.adithyapaib.sxv.ui.theme.Danger
import com.adithyapaib.sxv.ui.theme.SurfaceInput
import com.adithyapaib.sxv.ui.theme.TextMuted
import com.adithyapaib.sxv.ui.theme.TextSecondary
import com.adithyapaib.sxv.ui.theme.SXVTheme

private object DashboardLayout {
    val PagePadding = 20.dp
    val CompactGap = 8.dp
    val ContentGap = 16.dp
    val SectionGap = 24.dp
    val ExpandedBreakpoint = 720.dp
}

@Composable
fun DashboardScreen(viewModel: BleViewModel = viewModel()) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val displayInput by viewModel.displayInput.collectAsStateWithLifecycle()
    val displaySecondaryInput by viewModel.displaySecondaryInput.collectAsStateWithLifecycle()
    val isSendingDisplayText by viewModel.isSendingDisplayText.collectAsStateWithLifecycle()

    DashboardContent(
        telemetry = telemetry,
        connectionState = connectionState,
        logs = logs,
        displayInput = displayInput,
        displaySecondaryInput = displaySecondaryInput,
        isSendingDisplayText = isSendingDisplayText,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onClearLogs = viewModel::clearLogs,
        onDisplayInputChanged = viewModel::updateDisplayInput,
        onDisplaySecondaryInputChanged = viewModel::updateDisplaySecondaryInput,
        onSendDisplayText = viewModel::sendDisplayText
    )
}

@Composable
private fun DashboardContent(
    telemetry: VStromTelemetry?,
    connectionState: BleConnectionState,
    logs: List<String> = emptyList(),
    displayInput: String = "",
    displaySecondaryInput: String = "",
    isSendingDisplayText: Boolean = false,
    onConnect: () -> Unit = {},
    onDisconnect: () -> Unit = {},
    onClearLogs: () -> Unit = {},
    onDisplayInputChanged: (String) -> Unit = {},
    onDisplaySecondaryInputChanged: (String) -> Unit = {},
    onSendDisplayText: () -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints {
            val isExpanded = maxWidth >= DashboardLayout.ExpandedBreakpoint
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(DashboardLayout.PagePadding),
                verticalArrangement = Arrangement.spacedBy(DashboardLayout.SectionGap)
            ) {
                DashboardHeader(connectionState)
                if (isExpanded) {
                    ExpandedDashboardLayout(
                        telemetry = telemetry,
                        connectionState = connectionState,
                        logs = logs,
                        displayInput = displayInput,
                        displaySecondaryInput = displaySecondaryInput,
                        isSendingDisplayText = isSendingDisplayText,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                        onClearLogs = onClearLogs,
                        onDisplayInputChanged = onDisplayInputChanged,
                        onDisplaySecondaryInputChanged = onDisplaySecondaryInputChanged,
                        onSendDisplayText = onSendDisplayText
                    )
                } else {
                    CompactDashboardLayout(
                        telemetry = telemetry,
                        connectionState = connectionState,
                        logs = logs,
                        displayInput = displayInput,
                        displaySecondaryInput = displaySecondaryInput,
                        isSendingDisplayText = isSendingDisplayText,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect,
                        onClearLogs = onClearLogs,
                        onDisplayInputChanged = onDisplayInputChanged,
                        onDisplaySecondaryInputChanged = onDisplaySecondaryInputChanged,
                        onSendDisplayText = onSendDisplayText
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactDashboardLayout(
    telemetry: VStromTelemetry?,
    connectionState: BleConnectionState,
    logs: List<String>,
    displayInput: String,
    displaySecondaryInput: String,
    isSendingDisplayText: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearLogs: () -> Unit,
    onDisplayInputChanged: (String) -> Unit,
    onDisplaySecondaryInputChanged: (String) -> Unit,
    onSendDisplayText: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(DashboardLayout.ContentGap)) {
        ConnectionControls(connectionState, onConnect, onDisconnect)
        FuelStatusCard(telemetry, Modifier.fillMaxWidth())
        TelemetryMetrics(telemetry)
    }
    DisplayComposerCard(
        primaryValue = displayInput,
        secondaryValue = displaySecondaryInput,
        connectionState = connectionState,
        isSending = isSendingDisplayText,
        onPrimaryValueChange = onDisplayInputChanged,
        onSecondaryValueChange = onDisplaySecondaryInputChanged,
        onSend = onSendDisplayText
    )
    TracePanel(logs, onClearLogs)
}

@Composable
private fun ExpandedDashboardLayout(
    telemetry: VStromTelemetry?,
    connectionState: BleConnectionState,
    logs: List<String>,
    displayInput: String,
    displaySecondaryInput: String,
    isSendingDisplayText: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearLogs: () -> Unit,
    onDisplayInputChanged: (String) -> Unit,
    onDisplaySecondaryInputChanged: (String) -> Unit,
    onSendDisplayText: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DashboardLayout.SectionGap),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(0.9f),
            verticalArrangement = Arrangement.spacedBy(DashboardLayout.ContentGap)
        ) {
            ConnectionControls(connectionState, onConnect, onDisconnect)
            FuelStatusCard(telemetry, Modifier.fillMaxWidth())
        }
        TelemetryMetrics(telemetry, Modifier.weight(1.1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DashboardLayout.SectionGap),
        verticalAlignment = Alignment.Top
    ) {
        DisplayComposerCard(
            primaryValue = displayInput,
            secondaryValue = displaySecondaryInput,
            connectionState = connectionState,
            isSending = isSendingDisplayText,
            onPrimaryValueChange = onDisplayInputChanged,
            onSecondaryValueChange = onDisplaySecondaryInputChanged,
            onSend = onSendDisplayText,
            modifier = Modifier.weight(1.2f)
        )
        TracePanel(logs, onClearLogs, Modifier.weight(0.8f))
    }
}

@Composable
private fun DashboardHeader(connectionState: BleConnectionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("SXV", style = MaterialTheme.typography.headlineSmall)
            Text(
                "V-STROM INSTRUMENT LINK",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        ConnectionBadge(connectionState)
    }
}

@Composable
private fun ConnectionBadge(connectionState: BleConnectionState) {
    val (label, color) = when (connectionState) {
        BleConnectionState.CONNECTED -> "CONNECTED" to Gold
        BleConnectionState.CONNECTING -> "CONNECTING" to Gold
        BleConnectionState.ERROR -> "ATTENTION" to Danger
        BleConnectionState.DISCONNECTED -> "OFFLINE" to TextSecondary
    }
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.width(7.dp).height(7.dp).clip(CircleShape).background(color))
            Text(label, color = color, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ConnectionControls(
    state: BleConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onConnect,
            enabled = state != BleConnectionState.CONNECTED && state != BleConnectionState.CONNECTING,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) { Text(if (state == BleConnectionState.CONNECTING) "CONNECTING" else "CONNECT") }
        OutlinedButton(
            onClick = onDisconnect,
            enabled = state != BleConnectionState.DISCONNECTED,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) { Text("DISCONNECT") }
    }
}

@Composable
private fun FuelStatusCard(telemetry: VStromTelemetry?, modifier: Modifier = Modifier) {
    val isValid = telemetry?.isFuelValid == true
    val title = if (isValid) "FUEL SYSTEM READY" else "FUEL SYSTEM SYNCING"
    val detail = if (isValid) "Sender data has been validated from the cluster." else "Waiting for a stable fuel sender reading."

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("FUEL LEVEL", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text(title, color = Gold, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    if (isValid) "${telemetry.fuelLiters.formatLiters()} L" else "—",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(6) { index ->
                    val filled = isValid && index < telemetry.fuelBars.coerceIn(0, 6)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(if (filled) Gold else MaterialTheme.colorScheme.outline)
                    )
                }
            }
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TelemetryMetrics(telemetry: VStromTelemetry?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DashboardLayout.CompactGap)) {
        Text("RIDE DATA", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("SPEED", telemetry?.speed?.toString() ?: "—", Modifier.weight(1f), unit = "KM/H")
            MetricCard("ODOMETER", telemetry?.odometer?.toString() ?: "—", Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("TRIP A", telemetry?.tripA?.formatDistance() ?: "—", Modifier.weight(1f))
            MetricCard("TRIP B", telemetry?.tripB?.formatDistance() ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier, unit: String = "KM") {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(unit, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun DisplayComposerCard(
    primaryValue: String,
    secondaryValue: String,
    connectionState: BleConnectionState,
    isSending: Boolean,
    onPrimaryValueChange: (String) -> Unit,
    onSecondaryValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = listOf(primaryValue, secondaryValue).filter { it.isNotBlank() }.joinToString(" | ")
    val charactersRemaining = SuzukiDisplaySender.MAX_VISIBLE_TEXT_LENGTH - message.length
    val canSend = connectionState == BleConnectionState.CONNECTED && message.isNotBlank() && charactersRemaining >= 0 && !isSending

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("DISPLAY MESSAGE", style = MaterialTheme.typography.titleMedium)
                    Text("Write a short message to the instrument panel.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "$charactersRemaining LEFT",
                    color = if (charactersRemaining >= 0) Gold else Danger,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            MessageTextField("PRIMARY MESSAGE", "e.g. RIDE SAFE", primaryValue, onPrimaryValueChange)
            MessageTextField("SECONDARY MESSAGE", "Optional second line", secondaryValue, onSecondaryValueChange)
            if (message.isNotBlank()) {
                Surface(color = SurfaceInput, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        message.take(SuzukiDisplaySender.MAX_VISIBLE_TEXT_LENGTH),
                        modifier = Modifier.padding(10.dp),
                        color = Gold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Button(
                onClick = onSend,
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text(if (isSending) "SENDING TO CLUSTER…" else "SEND TO INSTRUMENT PANEL") }
            Text(
                connectionHint(connectionState),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MessageTextField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(SuzukiDisplaySender.MAX_VISIBLE_TEXT_LENGTH)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceInput,
            unfocusedContainerColor = SurfaceInput,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = Gold,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = Gold,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Gold,
            focusedPlaceholderColor = TextMuted,
            unfocusedPlaceholderColor = TextMuted
        )
    )
}

@Composable
@Suppress("DEPRECATION")
private fun TracePanel(logs: List<String>, onClearLogs: () -> Unit, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    val text = remember(logs) { logs.joinToString("\n") }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceInput)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("CONNECTION ACTIVITY", style = MaterialTheme.typography.titleMedium)
                    Text("Bluetooth diagnostic trace", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }, enabled = text.isNotBlank()) { Text("COPY") }
                TextButton(onClick = onClearLogs, enabled = text.isNotBlank()) { Text("CLEAR") }
            }
            Text(
                text.ifBlank { "Waiting for Bluetooth events…" },
                modifier = Modifier.fillMaxWidth().heightIn(min = 84.dp, max = 120.dp).verticalScroll(rememberScrollState()),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun connectionHint(state: BleConnectionState): String = when (state) {
    BleConnectionState.CONNECTED -> "Connected — ready to write via FFF1."
    BleConnectionState.CONNECTING -> "Connecting to the instrument cluster…"
    BleConnectionState.ERROR -> "Connection issue — review the activity log."
    BleConnectionState.DISCONNECTED -> "Connect to the instrument cluster to send a message."
}

private fun Double.formatDistance(): String = "%.1f".format(this)
private fun Double.formatLiters(): String = "%.1f".format(this)

@Preview(showBackground = true)
@Composable
private fun DashboardPreview() {
    SXVTheme {
        DashboardContent(
            telemetry = VStromTelemetry(odometer = 8882, tripA = 861.8, tripB = 213.9, fuelBars = 4, fuelLiters = 8.0, isFuelValid = true, speed = 0),
            connectionState = BleConnectionState.CONNECTED,
            logs = listOf("20:49:16  FFF2 notifications active"),
            displayInput = "RIDE SAFE",
            displaySecondaryInput = "SXV"
        )
    }
}


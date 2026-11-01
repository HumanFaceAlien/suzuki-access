package com.adithyapaib.stromx.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adithyapaib.stromx.nav.Maneuver
import com.adithyapaib.stromx.nav.NavigationController

/**
 * Settings block for Google Maps → cluster navigation: the on/off switch,
 * notification access, the step currently being sent, and a test sender for
 * mapping maneuver bytes to the glyphs this cluster draws.
 *
 * Built from plain Material 3 parts so it does not depend on the app's own
 * component set.
 */
@Composable
fun NavigationSection(
    navigation: NavigationController,
    connected: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val enabled by navigation.enabled.collectAsStateWithLifecycle()
    val listenerConnected by navigation.listenerConnected.collectAsStateWithLifecycle()
    val step by navigation.instruction.collectAsStateWithLifecycle()

    // Access is granted in system settings, so re-check whenever the screen comes back.
    var accessGranted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        accessGranted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        onPauseOrDispose { }
    }
    val hasAccess = accessGranted || listenerConnected

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Google Maps navigation", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = navigation::setEnabled),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show turns on the cluster", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Start navigation in Google Maps; StromX reads its notification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = null)
        }

        Text(
            if (hasAccess) "Notification access: granted" else "Notification access: needed to read Google Maps",
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasAccess) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
        )
        if (!hasAccess) {
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: ActivityNotFoundException) {
                        // No settings screen for this on the device; nothing else to offer.
                    }
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            ) { Text("Grant notification access") }
        }

        val current = step
        Text(
            if (current == null) {
                "Now: no navigation running"
            } else {
                "Now: ${current.maneuver.label}" +
                    (current.distanceMeters?.let { " in $it m" } ?: "") +
                    (current.eta?.let { ", ETA $it" } ?: "")
            },
            style = MaterialTheme.typography.bodyMedium
        )

        ManeuverTester(connected = connected, onSend = navigation::sendTest)
    }
}

/** Steps through maneuver bytes so each one can be checked on the cluster. */
@Composable
private fun ManeuverTester(connected: Boolean, onSend: (Int) -> Unit) {
    var byte by rememberSaveable { mutableIntStateOf(Maneuver.STRAIGHT.clusterByte) }
    val known = Maneuver.entries.filter { it.clusterByte == byte }.joinToString { it.label }

    Text("Arrow test (bike parked)", style = MaterialTheme.typography.titleSmall)
    Text(
        "Sends byte $byte for 10 s${if (known.isNotEmpty()) " — StromX uses it for: $known" else ""}.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { byte = (byte - 1).coerceAtLeast(0) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("−") }
        Text("$byte", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(48.dp))
        OutlinedButton(onClick = { byte = (byte + 1).coerceAtMost(MAX_TEST_BYTE) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("+") }
        OutlinedButton(
            onClick = { onSend(byte) },
            enabled = connected,
            modifier = Modifier.weight(1f).heightIn(min = 56.dp)
        ) { Text(if (connected) "Send" else "Not connected") }
    }
}

private const val MAX_TEST_BYTE = 75
